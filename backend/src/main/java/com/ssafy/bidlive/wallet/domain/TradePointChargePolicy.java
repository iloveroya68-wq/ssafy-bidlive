package com.ssafy.bidlive.wallet.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** P0 거래 포인트 충전의 금액 한도와 일일 집계 구간을 정의한다. */
public final class TradePointChargePolicy {

	public static final long MAX_AMOUNT_PER_CHARGE = 1_000_000L;
	public static final long MAX_DAILY_CHARGE_AMOUNT = 3_000_000L;
	public static final ZoneId DAILY_LIMIT_ZONE = ZoneId.of("Asia/Seoul");

	private TradePointChargePolicy() {
	}

	public static boolean isPositive(Long amount) {
		return amount != null && amount > 0;
	}

	public static boolean exceedsSingleChargeLimit(long amount) {
		return amount > MAX_AMOUNT_PER_CHARGE;
	}

	public static boolean exceedsDailyChargeLimit(
		long chargedAmountToday,
		long requestedAmount
	) {
		return chargedAmountToday < 0
			|| requestedAmount < 0
			|| requestedAmount > MAX_DAILY_CHARGE_AMOUNT
			|| chargedAmountToday
				> MAX_DAILY_CHARGE_AMOUNT - requestedAmount;
	}

	public static DailyWindow dailyWindow(Instant now) {
		LocalDate currentDate = now.atZone(DAILY_LIMIT_ZONE).toLocalDate();
		Instant startInclusive = currentDate
			.atStartOfDay(DAILY_LIMIT_ZONE)
			.toInstant();
		Instant endExclusive = currentDate
			.plusDays(1)
			.atStartOfDay(DAILY_LIMIT_ZONE)
			.toInstant();
		return new DailyWindow(startInclusive, endExclusive);
	}

	public record DailyWindow(
		Instant startInclusive,
		Instant endExclusive
	) {
	}
}
