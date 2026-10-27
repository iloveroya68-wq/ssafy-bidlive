package com.ssafy.bidlive.auction.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import com.ssafy.bidlive.auction.domain.AuctionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** TIME_SYNC가 완전한 LIVE 실시간 상태에 대해서만 발행되는지 검증한다. */
class AuctionTimeSyncServiceTest {

	private static final Instant END_AT =
		Instant.parse("2026-08-01T12:30:00Z");

	private AuctionTimeSyncStateReader stateReader;
	private AuctionTimeSyncPublisher publisher;
	private AuctionTimeSyncService service;

	@BeforeEach
	void setUp() {
		stateReader = mock(AuctionTimeSyncStateReader.class);
		publisher = mock(AuctionTimeSyncPublisher.class);
		service = new AuctionTimeSyncService(stateReader, publisher);
	}

	@Test
	void publishesCurrentEndAtForLiveAuction() {
		when(stateReader.findByAuctionId(100L)).thenReturn(Optional.of(
			new AuctionTimeSyncState(AuctionStatus.LIVE, END_AT)
		));

		boolean synchronizedTime = service.synchronizeIfLive(100L);

		assertThat(synchronizedTime).isTrue();
		verify(publisher).publish(100L, END_AT);
	}

	@Test
	void ignoresWaitingEndedAndMissingRealtimeState() {
		when(stateReader.findByAuctionId(100L)).thenReturn(Optional.of(
			new AuctionTimeSyncState(AuctionStatus.WAITING, END_AT)
		));
		when(stateReader.findByAuctionId(101L)).thenReturn(Optional.of(
			new AuctionTimeSyncState(AuctionStatus.ENDED, END_AT)
		));
		when(stateReader.findByAuctionId(102L)).thenReturn(Optional.empty());

		assertThat(service.synchronizeIfLive(100L)).isFalse();
		assertThat(service.synchronizeIfLive(101L)).isFalse();
		assertThat(service.synchronizeIfLive(102L)).isFalse();
		verifyNoInteractions(publisher);
	}
}
