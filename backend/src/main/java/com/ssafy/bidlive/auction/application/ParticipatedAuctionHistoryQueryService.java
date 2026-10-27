package com.ssafy.bidlive.auction.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import com.ssafy.bidlive.auction.domain.ParticipatedAuctionHistoryCursor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 참여 경매 커서를 검증하고 입찰·예측별 공개 결과와 정산 금액을 계산한다. */
@Service
public class ParticipatedAuctionHistoryQueryService
	implements ParticipatedAuctionHistoryQuery {

	private static final Set<String> PUBLIC_RESULT_TYPES = Set.of(
		"NO_BID",
		"HOST_LEFT",
		"FORCED"
	);
	private static final BigDecimal MULTIPLIER_WITHIN_FIVE =
		BigDecimal.valueOf(5);
	private static final BigDecimal LEGACY_HIT_MULTIPLIER = BigDecimal.ONE;
	private static final BigDecimal MULTIPLIER_WITHIN_TEN =
		BigDecimal.valueOf(3);
	private static final BigDecimal MULTIPLIER_WITHIN_TWENTY =
		new BigDecimal("1.5");

	private final ParticipatedAuctionHistoryPersistence persistence;

	public ParticipatedAuctionHistoryQueryService(
		ParticipatedAuctionHistoryPersistence persistence
	) {
		this.persistence = persistence;
	}

	@Override
	@Transactional(readOnly = true)
	public ParticipatedAuctionHistorySlice query(
		long memberId,
		String cursor,
		int size
	) {
		ParticipatedAuctionHistoryPage page = persistence.find(
			new ParticipatedAuctionHistoryCriteria(
				memberId,
				decodeCursor(cursor),
				size
			)
		);
		return new ParticipatedAuctionHistorySlice(
			toItems(page.rows()),
			page.nextCursor(),
			page.hasNext()
		);
	}

	private ParticipatedAuctionHistoryCursor decodeCursor(String cursor) {
		if (cursor == null || cursor.isBlank()) {
			return null;
		}
		try {
			return ParticipatedAuctionHistoryCursor.decode(cursor.trim());
		} catch (IllegalArgumentException exception) {
			throw new InvalidParticipatedAuctionCursorException(exception);
		}
	}

	private List<ParticipatedAuctionHistoryItem> toItems(
		List<ParticipatedAuctionHistoryRow> rows
	) {
		return rows.stream()
			.map(this::toItem)
			.toList();
	}

	private ParticipatedAuctionHistoryItem toItem(
		ParticipatedAuctionHistoryRow row
	) {
		return switch (row.participationType()) {
			case "BIDDER" -> toBidItem(row);
			case "PREDICTOR" -> toPredictionItem(row);
			default -> throw new IllegalStateException(
				"지원하지 않는 경매 참여 유형: " + row.participationType()
			);
		};
	}

	private ParticipatedAuctionHistoryItem toBidItem(
		ParticipatedAuctionHistoryRow row
	) {
		boolean settled = row.auctionResultStatus() != null;
		return new ParticipatedAuctionHistoryItem(
			row.auctionId(),
			"BID",
			row.productTitle(),
			row.imageUrl(),
			row.roomStatus(),
			resultType(row),
			settled ? "LOST" : null,
			settled ? "미낙찰" : null,
			settled ? row.finalPrice() : null,
			row.myFinalBid(),
			settled ? row.myFinalBid() : null,
			null,
			null,
			null,
			null,
			null,
			null,
			row.endedAt()
		);
	}

	private ParticipatedAuctionHistoryItem toPredictionItem(
		ParticipatedAuctionHistoryRow row
	) {
		String result = row.predictionResultStatus();
		return new ParticipatedAuctionHistoryItem(
			row.auctionId(),
			"PREDICTION",
			row.productTitle(),
			row.imageUrl(),
			row.roomStatus(),
			resultType(row),
			result,
			predictionResultLabel(result, resultType(row)),
			row.auctionResultStatus() == null ? null : row.finalPrice(),
			null,
			null,
			row.predictedPrice(),
			predictionErrorRate(row),
			predictionErrorBand(row),
			row.stakeAmount(),
			predictionPayoutAmount(row),
			predictionRefundAmount(row),
			row.endedAt()
		);
	}

	private String resultType(ParticipatedAuctionHistoryRow row) {
		if (row.auctionResultStatus() == null) {
			return null;
		}
		if ("SOLD".equals(row.auctionResultStatus())) {
			return "SOLD";
		}
		if (PUBLIC_RESULT_TYPES.contains(row.terminationReason())) {
			return row.terminationReason();
		}
		if ("NO_SALE".equals(row.auctionResultStatus())) {
			return "NO_BID";
		}
		if ("CANCELLED".equals(row.auctionResultStatus())) {
			return "FORCED";
		}
		return null;
	}

	private String predictionResultLabel(
		String result,
		String resultType
	) {
		if (result == null) {
			return null;
		}
		return switch (result) {
			case "HIT" -> "예측 적중";
			case "MISS" -> "예측 미적중";
			case "REFUNDED" -> refundLabel(resultType);
			default -> throw new IllegalStateException(
				"지원하지 않는 예측 결과: " + result
			);
		};
	}

	private String refundLabel(String resultType) {
		return switch (resultType) {
			case "NO_BID" -> "유찰 · 베팅 환불";
			case "HOST_LEFT" -> "판매자 이탈 · 베팅 환불";
			case "FORCED" -> "강제 종료 · 베팅 환불";
			case null, default -> "베팅 환불";
		};
	}

	private BigDecimal predictionErrorRate(
		ParticipatedAuctionHistoryRow row
	) {
		return switch (row.predictionResultStatus()) {
			case "HIT", "MISS" -> row.errorRate();
			case null -> null;
			case "REFUNDED" -> null;
			default -> throw new IllegalStateException(
				"지원하지 않는 예측 결과: "
					+ row.predictionResultStatus()
			);
		};
	}

	private String predictionErrorBand(
		ParticipatedAuctionHistoryRow row
	) {
		return switch (row.predictionResultStatus()) {
			case null -> null;
			case "REFUNDED" -> null;
			case "MISS" -> "MISS";
			case "HIT" -> hitErrorBand(row.rewardMultiplier());
			default -> throw new IllegalStateException(
				"지원하지 않는 예측 결과: "
					+ row.predictionResultStatus()
			);
		};
	}

	private String hitErrorBand(BigDecimal multiplier) {
		if (sameValue(multiplier, LEGACY_HIT_MULTIPLIER)) {
			return "WITHIN_5";
		}
		if (sameValue(multiplier, MULTIPLIER_WITHIN_FIVE)) {
			return "WITHIN_5";
		}
		if (sameValue(multiplier, MULTIPLIER_WITHIN_TEN)) {
			return "WITHIN_10";
		}
		if (sameValue(multiplier, MULTIPLIER_WITHIN_TWENTY)) {
			return "WITHIN_20";
		}
		throw new IllegalStateException(
			"지원하지 않는 예측 보상 배수: " + multiplier
		);
	}

	private boolean sameValue(BigDecimal value, BigDecimal expected) {
		return value != null && value.compareTo(expected) == 0;
	}

	private Long predictionPayoutAmount(
		ParticipatedAuctionHistoryRow row
	) {
		return switch (row.predictionResultStatus()) {
			case null -> null;
			case "HIT" -> row.predictionPayoutAmount();
			case "MISS", "REFUNDED" -> 0L;
			default -> throw new IllegalStateException(
				"지원하지 않는 예측 결과: "
					+ row.predictionResultStatus()
			);
		};
	}

	private Long predictionRefundAmount(
		ParticipatedAuctionHistoryRow row
	) {
		return "REFUNDED".equals(row.predictionResultStatus())
			? row.stakeAmount()
			: null;
	}
}
