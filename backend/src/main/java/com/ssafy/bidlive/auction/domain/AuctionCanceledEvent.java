package com.ssafy.bidlive.auction.domain;

import java.time.Instant;

/**
 * 경매가 <b>낙찰 없이 닫힌</b> 사실이다. 운영 강제 종료(DEAL-09)와 판매자 종료(ROOM-09)가 함께 쓴다.
 *
 * <p>둘을 한 이벤트로 두는 이유는 <b>구독자가 같은 일을 해야 하기 때문</b>이다. 입찰 도메인의
 * {@code RedisAuctionStateCloser}가 이 이벤트로 실시간 상태 Hash를 닫는데, 그 처리는 누가 끊었는지와
 * 무관하다 — 닫지 않으면 어느 쪽이든 원래 `endAt`까지 입찰이 계속 접수되고 이미 풀어 준 홀드 뒤로
 * 새 홀드가 쌓인다(S15P11A704-537). 사유별로 이벤트를 나누면 그쪽에 리스너를 하나 더 달아야 하고,
 * 빠뜨리는 순간 537이 <b>판매자 종료 경로에서만</b> 조용히 재현된다.
 *
 * <p>대신 <b>사용자에게 나가는 메시지는 갈린다.</b> {@code StompAuctionCanceledEventPublisher}가
 * {@link #terminationReason()}으로 `AUCTION_CANCELED`와 `AUCTION_NO_SALE`을 가른다 —
 * 「운영팀이 종료했어요」와 「판매자가 종료했어요」는 참여자에게 전혀 다른 사실이다.
 *
 * <p>이벤트 명세(`docs/api/auction/events/auction-canceled.md`)의 「발생 시점」이
 * <b>상태 반영과 포인트 환급 완료 후</b>라서, 구독자는 커밋된 뒤에만 받아야 한다 —
 * 발행은 {@code AFTER_COMMIT} 리스너가 맡는다.
 */
public record AuctionCanceledEvent(
	long auctionId,
	AuctionTerminationReason terminationReason,
	/** 사람이 읽을 사유 문구. 운영 종료는 운영자가 적어 넣고, 판매자 종료는 고정 문구다 */
	String reason,
	Instant endedAt
) {

	/**
	 * 사유를 생략하면 운영 강제 종료다.
	 *
	 * <p>이 이벤트에 사유가 생기기 전(S15P11A704-687)의 유일한 발행자가 DEAL-09였고, 그때의
	 * 호출 형태를 그대로 두기 위한 생성자다. 입찰 도메인의 리스너 테스트가 이 형태로 이벤트를
	 * 만드는데, 그쪽은 auction scope 밖이라 함께 고칠 수 없다.
	 *
	 * <p>⚠️ <b>새 발행 지점은 이 생성자를 쓰지 말 것.</b> 사유를 안 적으면 참여자에게
	 * 「운영팀이 종료했어요」가 나간다 — 판매자가 끝낸 방에 그게 뜨면 화면이 거짓말을 한다.
	 */
	public AuctionCanceledEvent(long auctionId, String reason, Instant endedAt) {
		this(auctionId, AuctionTerminationReason.FORCED, reason, endedAt);
	}

	public AuctionCanceledEvent {
		if (auctionId <= 0) {
			throw new IllegalArgumentException("auctionId must be positive");
		}
		if (terminationReason == null) {
			throw new IllegalArgumentException("terminationReason must not be null");
		}
		if (reason == null || reason.isBlank()) {
			throw new IllegalArgumentException("reason must not be blank");
		}
		if (endedAt == null) {
			throw new IllegalArgumentException("endedAt must not be null");
		}
	}
}
