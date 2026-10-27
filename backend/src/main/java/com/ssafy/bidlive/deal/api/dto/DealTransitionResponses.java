package com.ssafy.bidlive.deal.api.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.ssafy.bidlive.deal.application.DealTransitionResults;
import com.ssafy.bidlive.deal.domain.DealStatus;

/**
 * DEAL-05 전이 3종의 응답이다. 세 응답이 시각 필드 이름과 금액 유무만 다르고 나머지는 같아서 한
 * 파일에 모았다 — 필드가 갈릴 이유가 생기면 그때 나누는 편이 싸다
 * ({@code AuctionRoomErrorResponse}와 같은 판단).
 */
public final class DealTransitionResponses {

	private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

	private DealTransitionResponses() {
	}

	public record Address(
		Long dealId,
		DealStatus status,
		OffsetDateTime updatedAt
	) {

		public static Address from(DealTransitionResults.Address result) {
			return new Address(
				result.dealId(),
				result.status(),
				atKorea(result.updatedAt())
			);
		}
	}

	public record Shipment(
		Long dealId,
		DealStatus status,
		OffsetDateTime shippedAt,
		OffsetDateTime autoConfirmAt
	) {

		public static Shipment from(DealTransitionResults.Shipment result) {
			return new Shipment(
				result.dealId(),
				result.status(),
				atKorea(result.shippedAt()),
				atKorea(result.autoConfirmAt())
			);
		}
	}

	public record Receipt(
		Long dealId,
		DealStatus status,
		long sellerPayoutAmount,
		OffsetDateTime completedAt
	) {

		public static Receipt from(DealTransitionResults.Receipt result) {
			return new Receipt(
				result.dealId(),
				result.status(),
				result.sellerPayoutAmount(),
				atKorea(result.completedAt())
			);
		}
	}

	private static OffsetDateTime atKorea(Instant instant) {
		return instant == null ? null : instant.atZone(KOREA_ZONE).toOffsetDateTime();
	}
}
