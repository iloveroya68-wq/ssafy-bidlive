package com.ssafy.bidlive.auction.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 참여 유형별 결과·오차 밴드·환불 금액과 커서 오류 처리를 검증한다. */
class ParticipatedAuctionHistoryQueryServiceTest {

	private ParticipatedAuctionHistoryPersistence persistence;
	private ParticipatedAuctionHistoryQueryService service;

	@BeforeEach
	void setUp() {
		persistence = mock(ParticipatedAuctionHistoryPersistence.class);
		service = new ParticipatedAuctionHistoryQueryService(persistence);
	}

	@Test
	void rejectsMalformedCursorBeforeQueryingDatabase() {
		assertThatThrownBy(() -> service.query(25L, "not*base64*", 8))
			.isInstanceOf(InvalidParticipatedAuctionCursorException.class);

		verify(persistence, never()).find(any());
	}

	@Test
	void mapsSettledAndOngoingBidParticipation() {
		when(persistence.find(any())).thenReturn(
			new ParticipatedAuctionHistoryPage(
				List.of(
					bidRow("SOLD", "TIME_OVER", 74_000L),
					bidRow(null, null, null)
				),
				null,
				false
			)
		);

		ParticipatedAuctionHistorySlice result =
			service.query(25L, null, 8);

		assertThat(result.items().get(0))
			.satisfies(item -> {
				assertThat(item.participationType()).isEqualTo("BID");
				assertThat(item.resultType()).isEqualTo("SOLD");
				assertThat(item.result()).isEqualTo("LOST");
				assertThat(item.resultLabel()).isEqualTo("미낙찰");
				assertThat(item.finalPrice()).isEqualTo(74_000L);
				assertThat(item.myFinalBid()).isEqualTo(61_000L);
				assertThat(item.holdReleasedAmount()).isEqualTo(61_000L);
			});
		assertThat(result.items().get(1))
			.satisfies(item -> {
				assertThat(item.resultType()).isNull();
				assertThat(item.result()).isNull();
				assertThat(item.resultLabel()).isNull();
				assertThat(item.finalPrice()).isNull();
				assertThat(item.myFinalBid()).isEqualTo(61_000L);
				assertThat(item.holdReleasedAmount()).isNull();
			});
	}

	@Test
	void mapsPredictionHitMissAndRefundUsingStandardResults() {
		when(persistence.find(any())).thenReturn(
			new ParticipatedAuctionHistoryPage(
				List.of(
					predictionRow(
						"SOLD",
						"TIME_OVER",
						"HIT",
						new BigDecimal("2.700000"),
						new BigDecimal("3.00"),
						900L
					),
					predictionRow(
						"SOLD",
						"TIME_OVER",
						"MISS",
						new BigDecimal("8.000000"),
						BigDecimal.ZERO,
						0L
					),
					predictionRow(
						"NO_SALE",
						"NO_BID",
						"REFUNDED",
						null,
						BigDecimal.ZERO,
						300L
					)
				),
				"next",
				true
			)
		);

		ParticipatedAuctionHistorySlice result =
			service.query(25L, null, 8);

		assertThat(result.items().get(0))
			.satisfies(item -> {
				assertThat(item.result()).isEqualTo("HIT");
				assertThat(item.resultLabel()).isEqualTo("예측 적중");
				assertThat(item.errorBand()).isEqualTo("WITHIN_10");
				assertThat(item.payoutAmount()).isEqualTo(900L);
				assertThat(item.refundAmount()).isNull();
			});
		assertThat(result.items().get(1))
			.satisfies(item -> {
				assertThat(item.result()).isEqualTo("MISS");
				assertThat(item.resultLabel()).isEqualTo("예측 미적중");
				assertThat(item.errorBand()).isEqualTo("MISS");
				assertThat(item.payoutAmount()).isZero();
				assertThat(item.refundAmount()).isNull();
			});
		assertThat(result.items().get(2))
			.satisfies(item -> {
				assertThat(item.resultType()).isEqualTo("NO_BID");
				assertThat(item.result()).isEqualTo("REFUNDED");
				assertThat(item.resultLabel())
					.isEqualTo("유찰 · 베팅 환불");
				assertThat(item.errorRate()).isNull();
				assertThat(item.errorBand()).isNull();
				assertThat(item.payoutAmount()).isZero();
				assertThat(item.refundAmount()).isEqualTo(300L);
			});
		assertThat(result.nextCursor()).isEqualTo("next");
		assertThat(result.hasNext()).isTrue();
	}

	private ParticipatedAuctionHistoryRow bidRow(
		String auctionResultStatus,
		String terminationReason,
		Long finalPrice
	) {
		Instant now = Instant.parse("2026-07-21T11:00:00Z");
		return new ParticipatedAuctionHistoryRow(
			100L,
			"BIDDER",
			"헤드폰",
			"https://cdn.example.com/products/headphone.jpg",
			auctionResultStatus == null ? "LIVE" : "ENDED",
			auctionResultStatus,
			terminationReason,
			finalPrice,
			61_000L,
			null,
			null,
			null,
			null,
			null,
			null,
			auctionResultStatus == null ? null : now,
			now
		);
	}

	private ParticipatedAuctionHistoryRow predictionRow(
		String auctionResultStatus,
		String terminationReason,
		String predictionResultStatus,
		BigDecimal errorRate,
		BigDecimal rewardMultiplier,
		Long predictionPayoutAmount
	) {
		Instant now = Instant.parse("2026-07-21T11:00:00Z");
		return new ParticipatedAuctionHistoryRow(
			101L,
			"PREDICTOR",
			"한정판 피규어",
			"https://cdn.example.com/products/figure.jpg",
			"ENDED",
			auctionResultStatus,
			terminationReason,
			"SOLD".equals(auctionResultStatus) ? 74_000L : null,
			null,
			72_000L,
			300L,
			predictionResultStatus,
			errorRate,
			rewardMultiplier,
			predictionPayoutAmount,
			now,
			now
		);
	}
}
