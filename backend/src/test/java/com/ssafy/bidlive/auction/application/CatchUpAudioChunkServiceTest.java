package com.ssafy.bidlive.auction.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import com.ssafy.bidlive.auction.domain.CatchUpSummaryErrorCode;
import com.ssafy.bidlive.auction.infrastructure.CatchUpSummaryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CatchUpAudioChunkServiceTest {

	private static final long AUCTION_ID = 100L;
	private static final long SELLER_ID = 25L;
	private static final Instant STARTED_AT = Instant.parse("2026-07-20T12:00:00Z");
	private static final Instant ENDED_AT = STARTED_AT.plusSeconds(15);

	private CatchUpRoomAccessReader accessReader;
	private CatchUpSummaryStore store;
	private SpeechToTextGateway sttGateway;
	private CatchUpAudioChunkService service;

	@BeforeEach
	void setUp() {
		accessReader = mock(CatchUpRoomAccessReader.class);
		store = mock(CatchUpSummaryStore.class);
		sttGateway = mock(SpeechToTextGateway.class);
		service = new CatchUpAudioChunkService(
			accessReader,
			store,
			sttGateway,
			new CatchUpSummaryProperties(),
			Runnable::run
		);
		when(accessReader.findAccess(AUCTION_ID)).thenReturn(Optional.of(
			new CatchUpRoomAccess(AUCTION_ID, SELLER_ID, "LIVE", "PUBLIC", false, null)
		));
	}

	@Test
	void acceptsOneChunkOnceAndAppendsItsTranscript() {
		when(store.claimAudioChunk(AUCTION_ID, 3, Duration.ofHours(6))).thenReturn(true);
		when(sttGateway.transcribe(eq("audio/webm"), any(byte[].class)))
			.thenReturn("  구성품은 모두 포함됩니다  ");

		service.submit(
			AUCTION_ID,
			SELLER_ID,
			3,
			STARTED_AT,
			ENDED_AT,
			"audio/webm;codecs=opus",
			new byte[] {1, 2, 3}
		);

		verify(store).appendTranscript(
			eq(AUCTION_ID),
			any(CatchUpTranscriptSegment.class),
			eq(Duration.ofHours(6))
		);
	}

	@Test
	void treatsRepeatedSequenceAsIdempotentSuccess() {
		when(store.claimAudioChunk(AUCTION_ID, 3, Duration.ofHours(6))).thenReturn(false);

		service.submit(
			AUCTION_ID,
			SELLER_ID,
			3,
			STARTED_AT,
			ENDED_AT,
			"audio/webm",
			new byte[] {1}
		);

		verify(sttGateway, never()).transcribe(any(), any());
	}

	@Test
	void rejectsUnsupportedAudioBeforeClaimingChunk() {
		assertThatThrownBy(() -> service.submit(
			AUCTION_ID,
			SELLER_ID,
			3,
			STARTED_AT,
			ENDED_AT,
			"audio/mpeg",
			new byte[] {1}
		)).isInstanceOfSatisfying(CatchUpSummaryException.class, exception ->
			assertThat(exception.code()).isEqualTo(CatchUpSummaryErrorCode.UNSUPPORTED_AUDIO_TYPE)
		);
		verify(store, never()).claimAudioChunk(anyLong(), anyLong(), any());
	}
}
