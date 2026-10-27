package com.ssafy.bidlive.bid.api.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

/** BID_ACCEPTED data의 공개 필드와 유효성 제약을 검증한다. */
class BidAcceptedDataTest {

	@Test
	void containsOnlyContractedPublicFields() {
		assertThat(BidAcceptedData.class.getRecordComponents())
			.extracting(java.lang.reflect.RecordComponent::getName)
			.containsExactly(
				"bidCount",
				"participantCount",
				"vibeMessage"
			);
	}

	@Test
	void rejectsNegativeCountsAndBlankVibeMessage() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new BidAcceptedData(
				-1L,
				9L,
				"누군가 베팅을 진행 중입니다!"
			));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new BidAcceptedData(
				13L,
				-1L,
				"누군가 베팅을 진행 중입니다!"
			));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new BidAcceptedData(13L, 9L, " "));
	}
}
