package com.ssafy.bidlive.auction.application;

import java.time.Clock;

import com.ssafy.bidlive.auction.domain.AuctionRoomJoinErrorCode;
import com.ssafy.bidlive.auction.domain.AuctionRoomVisibility;
import com.ssafy.bidlive.auction.domain.AuctionStatus;
import com.ssafy.bidlive.member.application.MemberPersistence;
import com.ssafy.bidlive.member.domain.MemberStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원의 경매방 입장 가능 여부를 판정하고 상태별 권한과 구독 destination을 구성한다.
 *
 * <p>**접속자 등록은 이 서비스가 하지 않는다.** 집계는 STOMP 세션 단위이고(S15P11A704-249의
 * `ViewerSessionStompEventListener`), 이 API는 REST라 `stompSessionId`가 없어 등록할 수단이 없다.
 * 클라이언트가 아래 `destinations`로 구독하는 순간 그 경로가 멱등하게 등록한다 — 그래서 명세의
 * "중복 입장 요청이 접속자 수를 중복 증가시키지 않는다"도 자연히 지켜진다.
 */
@Service
public class AuctionRoomJoinService {

	private static final String ROOM_DESTINATION_PATTERN = "/topic/auctions/%d";
	private static final String CHAT_DESTINATION_PATTERN = "/topic/auctions/%d/chat";
	private static final String VIEWERS_DESTINATION_PATTERN = "/topic/auctions/%d/viewers";

	private final AuctionRoomJoinPersistence joinPersistence;
	private final MemberPersistence memberPersistence;
	private final CatchUpSummaryAvailability catchUpSummaryAvailability;
	private final Clock clock;

	@Autowired
	public AuctionRoomJoinService(
		AuctionRoomJoinPersistence joinPersistence,
		MemberPersistence memberPersistence,
		CatchUpSummaryAvailability catchUpSummaryAvailability
	) {
		this(
			joinPersistence,
			memberPersistence,
			catchUpSummaryAvailability,
			Clock.systemUTC()
		);
	}

	/** 기존 단위 테스트와 기술 독립 생성 경로는 요약 소스가 없는 상태를 기본으로 둔다. */
	AuctionRoomJoinService(
		AuctionRoomJoinPersistence joinPersistence,
		MemberPersistence memberPersistence,
		Clock clock
	) {
		this(joinPersistence, memberPersistence, auctionId -> false, clock);
	}

	AuctionRoomJoinService(
		AuctionRoomJoinPersistence joinPersistence,
		MemberPersistence memberPersistence,
		CatchUpSummaryAvailability catchUpSummaryAvailability,
		Clock clock
	) {
		this.joinPersistence = joinPersistence;
		this.memberPersistence = memberPersistence;
		this.catchUpSummaryAvailability = catchUpSummaryAvailability;
		this.clock = clock;
	}

	/**
	 * 판정 순서가 계약이다: 인증(401) → 없음·삭제됨(404) → 비공개 + 비판매자(404)
	 * → 정지·탈퇴 회원(403) → 종료됨(409).
	 *
	 * <p>비공개 방을 **404로** 감추는 이유는 목록·상세·수정·삭제와 같다 — 여기서만 403을 주면
	 * 그 방의 존재가 드러난다(S15P11A704-482). 반면 `ROOM_ACCESS_DENIED`(403)는 회원 상태로 막는
	 * 경우에 쓴다. 방의 존재와 무관한 거절이라 감출 것이 없다.
	 */
	@Transactional(readOnly = true)
	public AuctionRoomJoinResult joinRoom(long auctionId, Long memberId) {
		if (memberId == null) {
			throw new AuctionRoomJoinException(AuctionRoomJoinErrorCode.UNAUTHORIZED);
		}
		AuctionRoomJoinTarget target = joinPersistence.findJoinTarget(auctionId)
			.filter(found -> !found.deleted())
			.orElseThrow(() -> new AuctionRoomJoinException(
				AuctionRoomJoinErrorCode.AUCTION_ROOM_NOT_FOUND
			));
		if (!memberId.equals(target.sellerId())
			&& !AuctionRoomVisibility.isPublic(target.visibility())) {
			throw new AuctionRoomJoinException(AuctionRoomJoinErrorCode.AUCTION_ROOM_NOT_FOUND);
		}
		verifyMemberCanEnter(memberId);

		AuctionStatus roomStatus = parseRoomStatus(target.status());
		return roomStatus == AuctionStatus.LIVE
			? liveResult(auctionId)
			: waitingResult(auctionId, target);
	}

	private void verifyMemberCanEnter(Long memberId) {
		memberPersistence.findById(memberId)
			.filter(member -> member.getStatus() == MemberStatus.ACTIVE)
			.orElseThrow(() -> new AuctionRoomJoinException(
				AuctionRoomJoinErrorCode.ROOM_ACCESS_DENIED
			));
	}

	/**
	 * 종료된 방에는 새로 입장할 수 없다. `auction_room.status`에 DB 제약이 없어 알 수 없는 값이
	 * 올 수 있는데, 그런 방을 대기방으로 열어 주면 채팅까지 가능해지므로 종료와 같이 막는다.
	 */
	private static AuctionStatus parseRoomStatus(String status) {
		if (AuctionStatus.LIVE.name().equals(status)) {
			return AuctionStatus.LIVE;
		}
		if (AuctionStatus.WAITING.name().equals(status)) {
			return AuctionStatus.WAITING;
		}
		throw new AuctionRoomJoinException(AuctionRoomJoinErrorCode.AUCTION_ALREADY_ENDED);
	}

	/** 대기방은 채팅만 열린다 — 입찰·예측은 시작 후에 열린다(명세 검증 규칙). */
	private AuctionRoomJoinResult waitingResult(long auctionId, AuctionRoomJoinTarget target) {
		return new AuctionRoomJoinResult(
			clock.instant(),
			auctionId,
			AuctionStatus.WAITING,
			target.scheduledAt(),
			true,
			false,
			false,
			ROOM_DESTINATION_PATTERN.formatted(auctionId),
			CHAT_DESTINATION_PATTERN.formatted(auctionId),
			VIEWERS_DESTINATION_PATTERN.formatted(auctionId),
			null
		);
	}

	private AuctionRoomJoinResult liveResult(long auctionId) {
		return new AuctionRoomJoinResult(
			clock.instant(),
			auctionId,
			AuctionStatus.LIVE,
			null,
			true,
			true,
			true,
			null,
			null,
			null,
			catchUpSummaryAvailability.isAvailable(auctionId)
		);
	}
}
