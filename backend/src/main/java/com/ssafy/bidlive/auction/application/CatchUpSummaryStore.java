package com.ssafy.bidlive.auction.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** RoomEvent, STT 문장과 완성된 요약을 Redis에 보관하는 계약이다. */
public interface CatchUpSummaryStore {

	Optional<CatchUpSummaryResult> findSummary(long auctionId);

	void saveSummary(
		long auctionId,
		CatchUpSummaryResult summary,
		long transcriptRevision,
		Duration ttl
	);

	long findTranscriptRevision(long auctionId);

	long findSummaryRevision(long auctionId);

	boolean claimSummaryRefresh(long auctionId, Duration ttl);

	void releaseSummaryRefresh(long auctionId);

	Optional<CatchUpRoomEventState> findRoomEvents(long auctionId);

	List<CatchUpTranscriptSegment> findTranscripts(long auctionId);

	boolean hasSource(long auctionId);

	void recordStarted(long auctionId, Instant startedAt, Instant endAt, Duration ttl);

	void recordBidCounts(long auctionId, long bidCount, long participantCount, Duration ttl);

	void recordExtension(long auctionId, Instant endAt, int extensionCount, Duration ttl);

	boolean claimAudioChunk(long auctionId, long sequence, Duration ttl);

	void releaseAudioChunk(long auctionId, long sequence);

	void appendTranscript(long auctionId, CatchUpTranscriptSegment segment, Duration ttl);
}
