package com.ssafy.bidlive.bid.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

/** 승인 입찰 이벤트가 공개 집계 계약과 식별자 유효성을 지키는지 검증한다. */
class BidAcceptedEventTest {

	@Test
	void containsOnlyAuctionIdentifierAndPublicAggregateFields() {
		assertThat(BidAcceptedEvent.class.getRecordComponents())
			.extracting(java.lang.reflect.RecordComponent::getName)
			.containsExactly(
				"auctionId",
				"bidCount",
				"participantCount"
			);
	}

	@Test
	void acceptsValidEventValues() {
		BidAcceptedEvent event = new BidAcceptedEvent(100L, 13L, 9L);

		assertThat(event.auctionId()).isEqualTo(100L);
		assertThat(event.bidCount()).isEqualTo(13L);
		assertThat(event.participantCount()).isEqualTo(9L);
	}

	@Test
	void rejectsNonPositiveAuctionIdentifier() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new BidAcceptedEvent(0L, 13L, 9L))
			.withMessage("auctionId must be positive");
	}

	@Test
	void rejectsNegativeAggregateValues() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new BidAcceptedEvent(100L, -1L, 9L))
			.withMessage("bidCount must not be negative");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new BidAcceptedEvent(100L, 13L, -1L))
			.withMessage("participantCount must not be negative");
	}
}
