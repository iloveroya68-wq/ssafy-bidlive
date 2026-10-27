package com.ssafy.bidlive.media.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.ssafy.bidlive.auction.application.CatchUpRoomAccess;
import com.ssafy.bidlive.auction.application.CatchUpRoomAccessReader;
import com.ssafy.bidlive.auction.domain.AuctionRoomVisibility;
import com.ssafy.bidlive.media.application.port.out.LiveSessionGateway;
import com.ssafy.bidlive.media.domain.LiveBroadcastRepository;
import com.ssafy.bidlive.media.domain.LiveSessionRole;
import com.ssafy.bidlive.media.domain.LiveSessionToken;
import com.ssafy.bidlive.media.domain.LiveTokenFailureCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 방 단위 라이브 세션을 확보한 뒤 요청한 권한의 접속 토큰을 발급하는 유스케이스입니다.
 *
 * <p>권한별 진입 메서드를 나눠 둔 이유는 두 API의 검증 규칙이 서로 다르기 때문이며,
 * 송출은 판매자 본인만, 시청은 공개 방이거나 판매자 본인일 때만 허용한다. 방 정보는 auction
 * 도메인의 공개 Application 계약({@link CatchUpRoomAccessReader})으로만 읽는다.
 */
@Service
public class LiveSessionService {

	/** API 명세의 `createdAt` 예시가 모두 KST 기준이므로 발급 시각도 같은 오프셋으로 맞춘다. */
	private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

	private final LiveSessionGateway liveSessionGateway;
	private final LiveBroadcastRepository liveBroadcastRepository;
	private final CatchUpRoomAccessReader roomAccessReader;
	private final Clock clock;

	@Autowired
	public LiveSessionService(
		LiveSessionGateway liveSessionGateway,
		LiveBroadcastRepository liveBroadcastRepository,
		CatchUpRoomAccessReader roomAccessReader
	) {
		this(liveSessionGateway, liveBroadcastRepository, roomAccessReader, Clock.system(SERVICE_ZONE));
	}

	/** 발급 시각을 고정해야 하는 테스트에서만 사용한다. */
	LiveSessionService(
		LiveSessionGateway liveSessionGateway,
		LiveBroadcastRepository liveBroadcastRepository,
		CatchUpRoomAccessReader roomAccessReader,
		Clock clock
	) {
		this.liveSessionGateway = liveSessionGateway;
		this.liveBroadcastRepository = liveBroadcastRepository;
		this.roomAccessReader = roomAccessReader;
		this.clock = clock;
	}

	/**
	 * 발급과 함께 방송 개시 사실을 기록해 LIVE-02가 종료 대상과 종료 권한자를 판정할 수 있게 한다.
	 *
	 * <p>판매자 판정을 발급보다 **먼저** 한다. 순서를 바꾸면 거절당한 요청도 방송 개시 행을 선점해,
	 * 실제 판매자의 LIVE-02 종료가 `NOT_BROADCAST_OWNER`로 막힌다.
	 */
	@Transactional
	public LiveSessionToken issuePublisherToken(String roomId, Long broadcasterId) {
		if (broadcasterId == null) {
			throw new IllegalArgumentException("broadcasterId must not be null");
		}

		CatchUpRoomAccess room = findRoom(roomId);
		if (room.sellerId() != broadcasterId) {
			throw new LiveTokenRejectedException(LiveTokenFailureCode.NOT_AUCTION_SELLER);
		}

		LiveSessionToken sessionToken = issueToken(roomId, LiveSessionRole.PUBLISHER);
		liveBroadcastRepository.startBroadcast(roomId, broadcasterId, sessionToken.createdAt());
		return sessionToken;
	}

	/**
	 * 시청 자격은 ROOM-07 입장(`AuctionRoomJoinService.joinRoom`)과 같은 규칙을 쓴다.
	 * 비공개 방을 비판매자에게 403이 아닌 404로 감추는 것까지 같다(S15P11A704-482).
	 */
	public LiveSessionToken issueSubscriberToken(String roomId, Long viewerId) {
		if (viewerId == null) {
			throw new IllegalArgumentException("viewerId must not be null");
		}

		CatchUpRoomAccess room = findRoom(roomId);
		if (room.sellerId() != viewerId && !AuctionRoomVisibility.isPublic(room.visibility())) {
			throw new LiveTokenRejectedException(LiveTokenFailureCode.ROOM_NOT_FOUND);
		}

		return issueToken(roomId, LiveSessionRole.SUBSCRIBER);
	}

	/**
	 * roomId는 media 쪽 계약상 String이지만 경매방 식별자는 수치형이다. 숫자가 아닌 값도
	 * 조회 실패와 같은 결과로 다뤄, 형식 차이가 500으로 새거나 방 존재 여부를 드러내지 않게 한다.
	 */
	private CatchUpRoomAccess findRoom(String roomId) {
		long auctionId = parseAuctionId(roomId);
		return roomAccessReader.findAccess(auctionId)
			.filter(found -> !found.deleted())
			.orElseThrow(() -> new LiveTokenRejectedException(LiveTokenFailureCode.ROOM_NOT_FOUND));
	}

	private static long parseAuctionId(String roomId) {
		if (roomId == null) {
			throw new LiveTokenRejectedException(LiveTokenFailureCode.ROOM_NOT_FOUND);
		}
		try {
			return Long.parseLong(roomId.trim());
		} catch (NumberFormatException exception) {
			throw new LiveTokenRejectedException(LiveTokenFailureCode.ROOM_NOT_FOUND);
		}
	}

	/**
	 * TODO(LIVE-01): 경매방이 방송 가능한 상태인지 확인한다.
	 * 방의 존재와 요청자 자격은 각 진입 메서드에서 확인하지만 상태 판정은 아직 없다.
	 */
	private LiveSessionToken issueToken(String roomId, LiveSessionRole role) {
		String sessionId = liveSessionGateway.ensureSession(roomId);
		String token = liveSessionGateway.issueToken(sessionId, role);
		return new LiveSessionToken(roomId, sessionId, token, role, OffsetDateTime.now(clock));
	}
}
