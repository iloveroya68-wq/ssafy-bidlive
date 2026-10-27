package com.ssafy.bidlive.auction.domain;

import java.time.Instant;

/**
 * 방송이 시작됐다는 사실이다 (ROOM-05). 커밋 이후에만 수신되므로 롤백된 시작은 아무 데도 퍼지지 않는다.
 *
 * <p>이 사실을 세 곳이 구독한다.
 * <ul>
 *   <li>구독자 브로드캐스트 — `AUCTION_STARTED`를 `/topic/auctions/{auctionId}`로 발행
 *   <li>입찰 도메인 — 실시간 경매 상태(Redis Hash)를 초기화. 그 Hash가 없으면 시작된 방인데도
 *       입찰이 `AUCTION_NOT_FOUND`로 거절된다
 *   <li>정산 예약 — 확정된 {@code endAt}에 비크리 정산 서비스가 실행되도록 일회성 작업을 등록
 * </ul>
 *
 * <p>그래서 payload에 브로드캐스트용 시각뿐 아니라 입찰 상태 초기화에 필요한 값
 * ({@code sellerId}·{@code reservePrice})도 담는다 — 구독자가 다시 DB를 읽지 않게 한다.
 */
public record AuctionStartedEvent(
	long auctionId,
	long sellerId,
	long reservePrice,
	Instant startedAt,
	Instant endAt
) {
}
