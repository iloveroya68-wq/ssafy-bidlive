package com.ssafy.bidlive.auction.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import com.ssafy.bidlive.auction.domain.AuctionRoomStartErrorCode;
import com.ssafy.bidlive.auction.domain.AuctionRoomVisibility;
import com.ssafy.bidlive.auction.domain.AuctionStartedEvent;
import com.ssafy.bidlive.auction.domain.AuctionStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매자가 대기 중인 방의 방송을 직접 시작하고, 서버 기준 종료 시각을 확정한다.
 *
 * <p>예약 시각(`scheduledAt`)은 대기방 안내용이라 **판정에 쓰지 않는다** — 명세가 "예약 시각 전후와
 * 관계없이 판매자가 직접 시작할 수 있다"고 못 박았다.
 */
@Service
public class AuctionRoomStartService {

	/** 검열이 확정 차단한 상품만 시작을 막는다. `PENDING`·`FLAGGED`는 판정 전이거나 미확정이다. */
	private static final String BLOCKED_MODERATION = "BLOCKED";

	private final AuctionRoomStartPersistence startPersistence;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	@Autowired
	public AuctionRoomStartService(
		AuctionRoomStartPersistence startPersistence,
		ApplicationEventPublisher eventPublisher
	) {
		this(startPersistence, eventPublisher, Clock.systemUTC());
	}

	AuctionRoomStartService(
		AuctionRoomStartPersistence startPersistence,
		ApplicationEventPublisher eventPublisher,
		Clock clock
	) {
		this.startPersistence = startPersistence;
		this.eventPublisher = eventPublisher;
		this.clock = clock;
	}

	/**
	 * 판정 순서가 계약이다: 인증(401) → 없음·삭제됨(404) → 비공개 + 비판매자(404) → 판매자 아님(403)
	 * → 이미 시작됨(409) → 시작 불가 상태(409) → 검열 차단(422).
	 *
	 * <p>전이는 `WAITING`인 행만 갱신하는 조건부 UPDATE로 하고, 갱신 행이 0이면 그 사이 다른 요청이
	 * 먼저 시작한 것으로 본다 — 명세의 "중복 시작 요청은 두 번 처리하지 않는다"를 DB가 보장한다.
	 */
	@Transactional
	public AuctionRoomStartResult startRoom(long auctionId, Long memberId) {
		if (memberId == null) {
			throw new AuctionRoomStartException(AuctionRoomStartErrorCode.UNAUTHORIZED);
		}
		AuctionRoomStartTarget target = startPersistence.findStartTarget(auctionId)
			.filter(found -> !found.deleted())
			.orElseThrow(() -> new AuctionRoomStartException(
				AuctionRoomStartErrorCode.AUCTION_NOT_FOUND
			));

		boolean seller = memberId.equals(target.sellerId());
		if (!seller && !AuctionRoomVisibility.isPublic(target.visibility())) {
			throw new AuctionRoomStartException(AuctionRoomStartErrorCode.AUCTION_NOT_FOUND);
		}
		if (!seller) {
			throw new AuctionRoomStartException(AuctionRoomStartErrorCode.NOT_AUCTION_SELLER);
		}
		verifyStartable(target);

		Instant startedAt = clock.instant();
		Instant endAt = startedAt.plus(Duration.ofMinutes(target.durationMinutes()));
		if (startPersistence.markStarted(auctionId, startedAt, endAt) == 0) {
			throw new AuctionRoomStartException(
				AuctionRoomStartErrorCode.AUCTION_ALREADY_STARTED
			);
		}

		// 커밋 이후에 구독자 브로드캐스트와 입찰 상태 초기화가 일어난다 — 롤백되면 둘 다 안 나간다
		eventPublisher.publishEvent(new AuctionStartedEvent(
			auctionId,
			target.sellerId(),
			target.reservePrice(),
			startedAt,
			endAt
		));

		return new AuctionRoomStartResult(
			auctionId,
			AuctionStatus.LIVE,
			startedAt,
			endAt,
			target.durationMinutes(),
			clock.instant()
		);
	}

	private static void verifyStartable(AuctionRoomStartTarget target) {
		if (AuctionStatus.LIVE.name().equals(target.status())) {
			throw new AuctionRoomStartException(
				AuctionRoomStartErrorCode.AUCTION_ALREADY_STARTED
			);
		}
		// `ENDED`이거나 DB 제약이 없어 들어온 알 수 없는 값은 시작할 수 있는 상태가 아니다
		if (!AuctionStatus.WAITING.name().equals(target.status())) {
			throw new AuctionRoomStartException(
				AuctionRoomStartErrorCode.AUCTION_START_NOT_ALLOWED
			);
		}
		if (BLOCKED_MODERATION.equals(target.moderationStatus())) {
			throw new AuctionRoomStartException(
				AuctionRoomStartErrorCode.AUCTION_MODERATION_BLOCKED
			);
		}
	}
}
