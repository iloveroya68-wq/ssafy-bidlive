package com.ssafy.bidlive.wallet.domain;

import java.time.Instant;
import java.time.ZoneId;

/** 가입·출석·라이브 시청 베팅 포인트 지급 정책이다. */
public final class BettingPointGrantPolicy {

	public static final String VERSION = "2026-07-01";

	private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
	private static final long SIGNUP_AMOUNT = 1_000L;
	private static final long DAILY_ATTENDANCE_AMOUNT = 100L;
	private static final long LIVE_WATCH_AMOUNT = 100L;

	private BettingPointGrantPolicy() {
	}

	public static long amountFor(BettingPointGrantType grantType) {
		if (grantType == null) {
			throw new IllegalArgumentException(
				"grantType must not be null"
			);
		}
		return switch (grantType) {
			case SIGNUP -> SIGNUP_AMOUNT;
			case DAILY_ATTENDANCE -> DAILY_ATTENDANCE_AMOUNT;
			case LIVE_WATCH -> LIVE_WATCH_AMOUNT;
		};
	}

	public static boolean matches(
		BettingPointGrantType grantType,
		long amount,
		String policyVersion
	) {
		return amount > 0
			&& amount == amountFor(grantType)
			&& VERSION.equals(policyVersion);
	}

	public static boolean isValidSource(
		BettingPointGrantType grantType,
		Long memberId,
		String sourceId,
		Instant occurredAt
	) {
		if (grantType == null
			|| memberId == null
			|| memberId <= 0
			|| sourceId == null
			|| occurredAt == null) {
			return false;
		}
		return switch (grantType) {
			case SIGNUP -> sourceId.equals("MEMBER:" + memberId);
			case DAILY_ATTENDANCE -> sourceId.equals(
				"DATE:"
					+ occurredAt.atZone(SERVICE_ZONE).toLocalDate()
			);
			case LIVE_WATCH -> isPositiveAuctionSource(sourceId);
		};
	}

	private static boolean isPositiveAuctionSource(String sourceId) {
		if (!sourceId.startsWith("AUCTION:")) {
			return false;
		}
		try {
			return Long.parseLong(
				sourceId.substring("AUCTION:".length())
			) > 0;
		} catch (NumberFormatException exception) {
			return false;
		}
	}
}
