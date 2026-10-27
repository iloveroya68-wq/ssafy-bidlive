package com.ssafy.bidlive.prediction.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 신규 예측 공개 이벤트가 유효한 경매와 참여자 집계만 허용하는지 검증한다. */
class PredictionAcceptedEventTest {

	@Test
	void keepsAuctionAndParticipantCount() {
		PredictionAcceptedEvent event = new PredictionAcceptedEvent(100L, 9L);

		assertThat(event.auctionId()).isEqualTo(100L);
		assertThat(event.participantCount()).isEqualTo(9L);
	}

	@Test
	void rejectsInvalidValues() {
		assertThatThrownBy(() -> new PredictionAcceptedEvent(0L, 9L))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("auctionId must be positive");
		assertThatThrownBy(() -> new PredictionAcceptedEvent(100L, -1L))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("participantCount must not be negative");
	}
}
