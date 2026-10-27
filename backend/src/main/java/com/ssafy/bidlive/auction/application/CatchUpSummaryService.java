package com.ssafy.bidlive.auction.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.ssafy.bidlive.auction.domain.AuctionRoomVisibility;
import com.ssafy.bidlive.auction.domain.AuctionStatus;
import com.ssafy.bidlive.auction.domain.CatchUpSummaryErrorCode;
import com.ssafy.bidlive.auction.domain.CatchUpSummarySourceType;
import com.ssafy.bidlive.auction.domain.CatchUpTranscriptSanitizer;
import com.ssafy.bidlive.auction.infrastructure.CatchUpSummaryProperties;
import com.ssafy.bidlive.member.application.MemberPersistence;
import com.ssafy.bidlive.member.domain.MemberStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 공개 RoomEvent와 확정 STT 문장을 조합해 2분 캐시된 중간 입장 요약을 반환한다. */
@Service
public class CatchUpSummaryService implements CatchUpSummaryAvailability {

	private static final Logger log = LoggerFactory.getLogger(CatchUpSummaryService.class);

	private final CatchUpRoomAccessReader accessReader;
	private final MemberPersistence memberPersistence;
	private final CatchUpSummaryStore store;
	private final CatchUpAiSummaryGateway aiSummaryGateway;
	private final CatchUpSummaryProperties properties;
	private final Clock clock;

	@Autowired
	public CatchUpSummaryService(
		CatchUpRoomAccessReader accessReader,
		MemberPersistence memberPersistence,
		CatchUpSummaryStore store,
		CatchUpAiSummaryGateway aiSummaryGateway,
		CatchUpSummaryProperties properties
	) {
		this(accessReader, memberPersistence, store, aiSummaryGateway, properties, Clock.systemUTC());
	}

	CatchUpSummaryService(
		CatchUpRoomAccessReader accessReader,
		MemberPersistence memberPersistence,
		CatchUpSummaryStore store,
		CatchUpAiSummaryGateway aiSummaryGateway,
		CatchUpSummaryProperties properties,
		Clock clock
	) {
		this.accessReader = accessReader;
		this.memberPersistence = memberPersistence;
		this.store = store;
		this.aiSummaryGateway = aiSummaryGateway;
		this.properties = properties;
		this.clock = clock;
	}

	public Optional<CatchUpSummaryResult> getSummary(long auctionId, Long memberId) {
		CatchUpRoomAccess access = verifyViewerAccess(auctionId, memberId);
		Instant now = clock.instant();
		Optional<CatchUpSummaryResult> cached = store.findSummary(auctionId);
		Optional<CatchUpRoomEventState> eventState = store.findRoomEvents(auctionId);
		long transcriptRevision = store.findTranscriptRevision(auctionId);
		if (cached.isPresent()
			&& store.findSummaryRevision(auctionId) >= transcriptRevision) {
			return Optional.of(withLatestState(cached.get(), eventState, access, now));
		}
		if (cached.isPresent()
			&& now.isBefore(cached.get().generatedAt().plus(properties.getMinRefreshInterval()))) {
			return Optional.of(withLatestState(cached.get(), eventState, access, now));
		}

		List<CatchUpTranscriptSegment> transcripts = store.findTranscripts(auctionId);
		if (eventState.isEmpty() && transcripts.isEmpty()) {
			return cached.isEmpty()
				? Optional.empty()
				: Optional.of(withLatestState(cached.get(), eventState, access, now));
		}
		if (!store.claimSummaryRefresh(auctionId, properties.getRefreshLockTtl())) {
			if (cached.isPresent()) {
				return Optional.of(withLatestState(cached.get(), eventState, access, now));
			}
			return eventState.map(events -> buildResult(
				auctionId,
				events,
				List.of(),
				access,
				now
			));
		}

		try {
			long sourceRevision = store.findTranscriptRevision(auctionId);
			eventState = store.findRoomEvents(auctionId);
			transcripts = store.findTranscripts(auctionId);
			Optional<List<String>> summarized = summarizeSafely(auctionId, transcripts);
			if (summarized.isEmpty()) {
				if (cached.isPresent()) {
					return Optional.of(withLatestState(cached.get(), eventState, access, now));
				}
				return eventState.map(events -> buildResult(
					auctionId,
					events,
					List.of(),
					access,
					now
				));
			}

			List<String> sellerMentions = summarized.get();
			if (sellerMentions.isEmpty() && cached.isPresent()) {
				sellerMentions = cached.get().sellerMentions();
			}
			if (eventState.isEmpty() && sellerMentions.isEmpty()) {
				return Optional.empty();
			}

			CatchUpRoomEventState events = eventState.orElseGet(
				() -> emptyEventState(access)
			);
			CatchUpSummaryResult result = buildResult(
				auctionId,
				events,
				sellerMentions,
				access,
				now
			);
			store.saveSummary(
				auctionId,
				result,
				sourceRevision,
				properties.getCacheTtl()
			);
			return Optional.of(result);
		} finally {
			store.releaseSummaryRefresh(auctionId);
		}
	}

	@Override
	public boolean isAvailable(long auctionId) {
		return store.hasSource(auctionId);
	}

	private CatchUpRoomAccess verifyViewerAccess(long auctionId, Long memberId) {
		if (memberId == null || memberId <= 0) {
			throw new CatchUpSummaryException(CatchUpSummaryErrorCode.UNAUTHORIZED);
		}
		CatchUpRoomAccess access = accessReader.findAccess(auctionId)
			.filter(found -> !found.deleted())
			.orElseThrow(() -> new CatchUpSummaryException(
				CatchUpSummaryErrorCode.AUCTION_ROOM_NOT_FOUND
			));
		if (access.sellerId() != memberId
			&& !AuctionRoomVisibility.isPublic(access.visibility())) {
			throw new CatchUpSummaryException(CatchUpSummaryErrorCode.AUCTION_ROOM_NOT_FOUND);
		}
		memberPersistence.findById(memberId)
			.filter(member -> member.getStatus() == MemberStatus.ACTIVE)
			.orElseThrow(() -> new CatchUpSummaryException(
				CatchUpSummaryErrorCode.ROOM_ACCESS_DENIED
			));
		if (!AuctionStatus.LIVE.name().equals(access.status())) {
			throw new CatchUpSummaryException(CatchUpSummaryErrorCode.AUCTION_NOT_STARTED);
		}
		return access;
	}

	private Optional<List<String>> summarizeSafely(
		long auctionId,
		List<CatchUpTranscriptSegment> transcripts
	) {
		if (transcripts.isEmpty()) {
			return Optional.of(List.of());
		}
		try {
			List<CatchUpTranscriptSegment> sanitized = transcripts.stream()
				.map(CatchUpSummaryService::sanitize)
				.flatMap(Optional::stream)
				.toList();
			return Optional.of(
				sanitized.isEmpty() ? List.of() : aiSummaryGateway.summarize(sanitized)
			);
		} catch (RuntimeException exception) {
			log.warn("catch-up AI summary failed: auctionId={}", auctionId, exception);
			return Optional.empty();
		}
	}

	private static Optional<CatchUpTranscriptSegment> sanitize(
		CatchUpTranscriptSegment segment
	) {
		String sanitized = CatchUpTranscriptSanitizer.sanitize(segment.text());
		if (sanitized.isBlank()) {
			return Optional.empty();
		}
		return Optional.of(new CatchUpTranscriptSegment(
			segment.sequence(),
			sanitized,
			segment.startedAt(),
			segment.endedAt()
		));
	}

	private CatchUpSummaryResult buildResult(
		long auctionId,
		CatchUpRoomEventState events,
		List<String> sellerMentions,
		CatchUpRoomAccess access,
		Instant now
	) {
		CatchUpSummarySourceType sourceType = sellerMentions.isEmpty()
			? CatchUpSummarySourceType.ROOM_EVENT_ONLY
			: CatchUpSummarySourceType.STT_AND_ROOM_EVENT;
		return new CatchUpSummaryResult(
			auctionId,
			now,
			now.plus(properties.getCacheTtl()),
			events.bidCount(),
			events.participantCount(),
			events.extensionCount(),
			events.extendedMinutes(),
			sellerMentions,
			now,
			events.endAt() == null ? access.endAt() : events.endAt(),
			sourceType
		);
	}

	private static CatchUpRoomEventState emptyEventState(CatchUpRoomAccess access) {
		return new CatchUpRoomEventState(0, 0, 0, 0, null, access.endAt());
	}

	private static CatchUpSummaryResult withLatestState(
		CatchUpSummaryResult summary,
		Optional<CatchUpRoomEventState> eventState,
		CatchUpRoomAccess access,
		Instant serverTime
	) {
		CatchUpRoomEventState events = eventState.orElse(null);
		return new CatchUpSummaryResult(
			summary.auctionId(),
			summary.generatedAt(),
			summary.expiresAt(),
			events == null ? summary.bidCount() : events.bidCount(),
			events == null ? summary.participantCount() : events.participantCount(),
			events == null ? summary.extensionCount() : events.extensionCount(),
			events == null ? summary.extendedMinutes() : events.extendedMinutes(),
			summary.sellerMentions(),
			serverTime,
			events == null || events.endAt() == null
				? (summary.endAt() == null ? access.endAt() : summary.endAt())
				: events.endAt(),
			summary.sourceType()
		);
	}
}
