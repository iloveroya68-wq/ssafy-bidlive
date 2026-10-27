package com.ssafy.bidlive.common.support.redis;

import java.util.List;
import java.util.UUID;

/** 입찰과 예측이 함께 사용하는 실시간 경매 Redis Key 구성을 한곳에서 제공한다. */
public final class RealtimeAuctionRedisKey {

	private static final String AUCTION_PREFIX = "bid:{bid}:auction:%d:";
	private static final String PREDICTION_REQUEST_PREFIX =
		"bid:{bid}:prediction:processed-request:";

	private RealtimeAuctionRedisKey() {
	}

	/**
	 * 실시간 경매 상태 Hash Key다. 방송 시작(ROOM-05)이 이 Hash를 만들어야 입찰·예측 스크립트가
	 * 경매를 찾을 수 있다 — 없으면 `EXISTS` 가드에서 `AUCTION_NOT_FOUND`로 거절된다.
	 *
	 * <p>목록에서 인덱스로 꺼내 쓰지 않도록 이름을 붙여 노출한다.
	 */
	public static String auctionStateKey(long auctionId) {
		return auctionPrefix(auctionId) + "state";
	}

	/** 경매별 회원의 최신 입찰 금액을 저장하는 Hash Key를 반환합니다. */
	public static String auctionMemberBidsKey(long auctionId) {
		return auctionPrefix(auctionId) + "member-bids";
	}

	public static List<String> bidProcessingKeys(long auctionId) {
		String auctionPrefix = auctionPrefix(auctionId);
		return List.of(
			auctionStateKey(auctionId),
			auctionMemberBidsKey(auctionId),
			auctionPrefix + "submitted-at",
			auctionPrefix + "participation",
			RealtimeWalletRedisKey.tradeAvailableBalancesKey(),
			RealtimeWalletRedisKey.tradeHeldBalancesKey(),
			auctionPrefix + "processed-requests"
		);
	}

	public static List<String> predictionParticipationKeys(
		long auctionId,
		UUID clientRequestId
	) {
		if (clientRequestId == null) {
			throw new IllegalArgumentException(
				"clientRequestId must not be null"
			);
		}
		String auctionPrefix = auctionPrefix(auctionId);
		return List.of(
			auctionPrefix + "state",
			auctionPrefix + "participation",
			PREDICTION_REQUEST_PREFIX + clientRequestId,
			RealtimeWalletRedisKey.bettingAvailableBalancesKey()
		);
	}

	private static String auctionPrefix(long auctionId) {
		if (auctionId <= 0) {
			throw new IllegalArgumentException("auctionId must be positive");
		}
		return AUCTION_PREFIX.formatted(auctionId);
	}
}
