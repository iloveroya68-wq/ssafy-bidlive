package com.ssafy.bidlive.auction.application;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;

import com.ssafy.bidlive.auction.domain.AuctionStatus;
import com.ssafy.bidlive.auction.domain.CatchUpSummaryErrorCode;
import com.ssafy.bidlive.auction.infrastructure.CatchUpSummaryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** 판매자 음성 청크를 빠르게 접수하고 별도 실행기에서 STT로 변환한다. */
@Service
public class CatchUpAudioChunkService {

	private static final Logger log = LoggerFactory.getLogger(CatchUpAudioChunkService.class);

	private final CatchUpRoomAccessReader accessReader;
	private final CatchUpSummaryStore store;
	private final SpeechToTextGateway speechToTextGateway;
	private final CatchUpSummaryProperties properties;
	private final Executor executor;

	public CatchUpAudioChunkService(
		CatchUpRoomAccessReader accessReader,
		CatchUpSummaryStore store,
		SpeechToTextGateway speechToTextGateway,
		CatchUpSummaryProperties properties,
		@Qualifier("catchUpSttExecutor") Executor executor
	) {
		this.accessReader = accessReader;
		this.store = store;
		this.speechToTextGateway = speechToTextGateway;
		this.properties = properties;
		this.executor = executor;
	}

	public void submit(
		long auctionId,
		Long memberId,
		long sequence,
		Instant startedAt,
		Instant endedAt,
		String contentType,
		byte[] audio
	) {
		verifySeller(auctionId, memberId);
		String normalizedType = validate(sequence, startedAt, endedAt, contentType, audio);
		if (!store.claimAudioChunk(auctionId, sequence, properties.getSourceTtl())) {
			return;
		}
		byte[] copiedAudio = audio.clone();
		executor.execute(() -> transcribe(
			auctionId,
			sequence,
			startedAt,
			endedAt,
			normalizedType,
			copiedAudio
		));
	}

	private void verifySeller(long auctionId, Long memberId) {
		if (memberId == null || memberId <= 0) {
			throw new CatchUpSummaryException(CatchUpSummaryErrorCode.UNAUTHORIZED);
		}
		CatchUpRoomAccess access = accessReader.findAccess(auctionId)
			.filter(found -> !found.deleted())
			.orElseThrow(() -> new CatchUpSummaryException(
				CatchUpSummaryErrorCode.AUCTION_ROOM_NOT_FOUND
			));
		if (access.sellerId() != memberId) {
			throw new CatchUpSummaryException(CatchUpSummaryErrorCode.NOT_AUCTION_SELLER);
		}
		if (!AuctionStatus.LIVE.name().equals(access.status())) {
			throw new CatchUpSummaryException(CatchUpSummaryErrorCode.AUCTION_NOT_STARTED);
		}
	}

	private String validate(
		long sequence,
		Instant startedAt,
		Instant endedAt,
		String contentType,
		byte[] audio
	) {
		if (sequence < 0 || startedAt == null || endedAt == null || endedAt.isBefore(startedAt)) {
			throw new CatchUpSummaryException(CatchUpSummaryErrorCode.INVALID_AUDIO_CHUNK);
		}
		if (audio == null || audio.length == 0) {
			throw new CatchUpSummaryException(CatchUpSummaryErrorCode.INVALID_AUDIO_CHUNK);
		}
		if (audio.length > properties.getMaxAudioChunkBytes()) {
			throw new CatchUpSummaryException(CatchUpSummaryErrorCode.AUDIO_CHUNK_TOO_LARGE);
		}
		String normalized = contentType == null
			? ""
			: contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
		Set<String> allowed = properties.getAllowedAudioTypes();
		if (!allowed.contains(normalized)) {
			throw new CatchUpSummaryException(CatchUpSummaryErrorCode.UNSUPPORTED_AUDIO_TYPE);
		}
		return normalized;
	}

	private void transcribe(
		long auctionId,
		long sequence,
		Instant startedAt,
		Instant endedAt,
		String contentType,
		byte[] audio
	) {
		try {
			String transcript = speechToTextGateway.transcribe(contentType, audio);
			if (transcript != null && !transcript.isBlank()) {
				store.appendTranscript(
					auctionId,
					new CatchUpTranscriptSegment(sequence, transcript.trim(), startedAt, endedAt),
					properties.getSourceTtl()
				);
			}
		} catch (RuntimeException exception) {
			store.releaseAudioChunk(auctionId, sequence);
			log.warn(
				"catch-up STT failed: auctionId={}, sequence={}",
				auctionId,
				sequence,
				exception
			);
		}
	}
}
