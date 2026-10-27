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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.ssafy.bidlive.auction.domain.CatchUpSummaryErrorCode;
import com.ssafy.bidlive.auction.domain.CatchUpSummarySourceType;
import com.ssafy.bidlive.auction.infrastructure.CatchUpSummaryProperties;
import com.ssafy.bidlive.member.application.MemberPersistence;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.MemberStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CatchUpSummaryServiceTest {

	private static final long AUCTION_ID = 100L;
	private static final long VIEWER_ID = 77L;
	private static final Instant NOW = Instant.parse("2026-07-20T12:08:12Z");
	private static final Instant END_AT = Instant.parse("2026-07-20T12:15:00Z");

	private CatchUpRoomAccessReader accessReader;
	private MemberPersistence memberPersistence;
	private CatchUpSummaryStore store;
	private CatchUpAiSummaryGateway aiGateway;
	private CatchUpSummaryService service;

	@BeforeEach
	void setUp() {
		accessReader = mock(CatchUpRoomAccessReader.class);
		memberPersistence = mock(MemberPersistence.class);
		store = mock(CatchUpSummaryStore.class);
		aiGateway = mock(CatchUpAiSummaryGateway.class);
		CatchUpSummaryProperties properties = new CatchUpSummaryProperties();
		service = new CatchUpSummaryService(
			accessReader,
			memberPersistence,
			store,
			aiGateway,
			properties,
			Clock.fixed(NOW, ZoneOffset.UTC)
		);
		when(accessReader.findAccess(AUCTION_ID)).thenReturn(Optional.of(
			new CatchUpRoomAccess(AUCTION_ID, 25L, "LIVE", "PUBLIC", false, END_AT)
		));
		Member member = mock(Member.class);
		when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
		when(memberPersistence.findById(VIEWER_ID)).thenReturn(Optional.of(member));
		when(store.findSummary(AUCTION_ID)).thenReturn(Optional.empty());
		when(store.claimSummaryRefresh(AUCTION_ID, Duration.ofMinutes(1))).thenReturn(true);
	}

	@Test
	void combinesRoomEventsAndSanitizedTranscriptThenCachesResult() {
		when(store.findRoomEvents(AUCTION_ID)).thenReturn(Optional.of(
			new CatchUpRoomEventState(7, 5, 1, 5, NOW.minusSeconds(480), END_AT)
		));
		when(store.findTranscripts(AUCTION_ID)).thenReturn(List.of(
			new CatchUpTranscriptSegment(
				1,
				"문의는 seller@example.com, 키캡 하나에 하자가 있습니다",
				NOW.minusSeconds(30),
				NOW.minusSeconds(15)
			)
		));
		when(aiGateway.summarize(any())).thenReturn(List.of("키캡 하나에 하자가 있습니다"));

		CatchUpSummaryResult result = service.getSummary(AUCTION_ID, VIEWER_ID).orElseThrow();

		assertThat(result.sourceType()).isEqualTo(CatchUpSummarySourceType.STT_AND_ROOM_EVENT);
		assertThat(result.bidCount()).isEqualTo(7);
		assertThat(result.sellerMentions()).containsExactly("키캡 하나에 하자가 있습니다");
		verify(aiGateway).summarize(org.mockito.ArgumentMatchers.argThat(
			segments -> !segments.getFirst().text().contains("seller@example.com")
		));
		verify(store).saveSummary(
			eq(AUCTION_ID),
			eq(result),
			eq(0L),
			eq(Duration.ofMinutes(2))
		);
		verify(store).releaseSummaryRefresh(AUCTION_ID);
	}

	@Test
	void overlaysLatestBidCountsWithoutRegeneratingCachedAiSummary() {
		CatchUpSummaryResult cached = cachedSummary(0, 0, List.of("구성품은 모두 포함됩니다"));
		when(store.findSummary(AUCTION_ID)).thenReturn(Optional.of(cached));
		when(store.findSummaryRevision(AUCTION_ID)).thenReturn(3L);
		when(store.findTranscriptRevision(AUCTION_ID)).thenReturn(3L);
		when(store.findRoomEvents(AUCTION_ID)).thenReturn(Optional.of(
			new CatchUpRoomEventState(4, 3, 1, 5, NOW.minusSeconds(480), END_AT)
		));

		CatchUpSummaryResult result = service.getSummary(AUCTION_ID, VIEWER_ID).orElseThrow();

		assertThat(result.bidCount()).isEqualTo(4);
		assertThat(result.participantCount()).isEqualTo(3);
		assertThat(result.extensionCount()).isEqualTo(1);
		assertThat(result.sellerMentions()).containsExactly("구성품은 모두 포함됩니다");
		verify(aiGateway, never()).summarize(any());
	}

	@Test
	void regeneratesAiSummaryWhenANewerTranscriptRevisionExists() {
		CatchUpSummaryResult cached = cachedSummary(1, 1, List.of("기존 요약"));
		when(store.findSummary(AUCTION_ID)).thenReturn(Optional.of(cached));
		when(store.findSummaryRevision(AUCTION_ID)).thenReturn(1L);
		when(store.findTranscriptRevision(AUCTION_ID)).thenReturn(2L);
		when(store.findRoomEvents(AUCTION_ID)).thenReturn(Optional.of(
			new CatchUpRoomEventState(2, 2, 0, 0, NOW.minusSeconds(480), END_AT)
		));
		when(store.findTranscripts(AUCTION_ID)).thenReturn(List.of(
			new CatchUpTranscriptSegment(2, "새로운 구성품 설명", NOW.minusSeconds(20), NOW)
		));
		when(aiGateway.summarize(any())).thenReturn(List.of("새로운 구성품 설명"));

		CatchUpSummaryResult result = service.getSummary(AUCTION_ID, VIEWER_ID).orElseThrow();

		assertThat(result.sellerMentions()).containsExactly("새로운 구성품 설명");
		verify(store).saveSummary(
			eq(AUCTION_ID),
			eq(result),
			eq(2L),
			eq(Duration.ofMinutes(2))
		);
	}

	@Test
	void reusesAiTextDuringMinimumRefreshIntervalButStillUpdatesCounts() {
		CatchUpSummaryResult cached = new CatchUpSummaryResult(
			AUCTION_ID,
			NOW.minusSeconds(10),
			NOW.plusSeconds(110),
			0,
			0,
			0,
			0,
			List.of("기존 요약"),
			NOW.minusSeconds(10),
			END_AT,
			CatchUpSummarySourceType.STT_AND_ROOM_EVENT
		);
		when(store.findSummary(AUCTION_ID)).thenReturn(Optional.of(cached));
		when(store.findSummaryRevision(AUCTION_ID)).thenReturn(1L);
		when(store.findTranscriptRevision(AUCTION_ID)).thenReturn(2L);
		when(store.findRoomEvents(AUCTION_ID)).thenReturn(Optional.of(
			new CatchUpRoomEventState(3, 2, 0, 0, NOW.minusSeconds(480), END_AT)
		));

		CatchUpSummaryResult result = service.getSummary(AUCTION_ID, VIEWER_ID).orElseThrow();

		assertThat(result.sellerMentions()).containsExactly("기존 요약");
		assertThat(result.bidCount()).isEqualTo(3);
		assertThat(result.participantCount()).isEqualTo(2);
		verify(aiGateway, never()).summarize(any());
		verify(store, never()).claimSummaryRefresh(anyLong(), any());
	}

	@Test
	void returnsStaleTextWithLatestCountsWhileAnotherRequestRefreshesIt() {
		CatchUpSummaryResult cached = cachedSummary(0, 0, List.of("기존 요약"));
		when(store.findSummary(AUCTION_ID)).thenReturn(Optional.of(cached));
		when(store.findSummaryRevision(AUCTION_ID)).thenReturn(1L);
		when(store.findTranscriptRevision(AUCTION_ID)).thenReturn(2L);
		when(store.findRoomEvents(AUCTION_ID)).thenReturn(Optional.of(
			new CatchUpRoomEventState(5, 4, 0, 0, NOW.minusSeconds(480), END_AT)
		));
		when(store.findTranscripts(AUCTION_ID)).thenReturn(List.of(
			new CatchUpTranscriptSegment(2, "새 발언", NOW.minusSeconds(20), NOW)
		));
		when(store.claimSummaryRefresh(AUCTION_ID, Duration.ofMinutes(1))).thenReturn(false);

		CatchUpSummaryResult result = service.getSummary(AUCTION_ID, VIEWER_ID).orElseThrow();

		assertThat(result.sellerMentions()).containsExactly("기존 요약");
		assertThat(result.bidCount()).isEqualTo(5);
		assertThat(result.participantCount()).isEqualTo(4);
		verify(aiGateway, never()).summarize(any());
	}

	@Test
	void fallsBackToRoomEventsWhenAiSummaryFails() {
		when(store.findRoomEvents(AUCTION_ID)).thenReturn(Optional.of(
			new CatchUpRoomEventState(3, 2, 0, 0, NOW.minusSeconds(60), END_AT)
		));
		when(store.findTranscripts(AUCTION_ID)).thenReturn(List.of(
			new CatchUpTranscriptSegment(1, "사용감이 있습니다", NOW, NOW)
		));
		when(aiGateway.summarize(any())).thenThrow(new IllegalStateException("GMS unavailable"));

		CatchUpSummaryResult result = service.getSummary(AUCTION_ID, VIEWER_ID).orElseThrow();

		assertThat(result.sourceType()).isEqualTo(CatchUpSummarySourceType.ROOM_EVENT_ONLY);
		assertThat(result.sellerMentions()).isEmpty();
		assertThat(result.bidCount()).isEqualTo(3);
	}

	@Test
	void returnsEmptyWhenThereIsNoSummarySource() {
		when(store.findRoomEvents(AUCTION_ID)).thenReturn(Optional.empty());
		when(store.findTranscripts(AUCTION_ID)).thenReturn(List.of());

		assertThat(service.getSummary(AUCTION_ID, VIEWER_ID)).isEmpty();
	}

	@Test
	void rejectsSummaryBeforeAuctionStarts() {
		when(accessReader.findAccess(AUCTION_ID)).thenReturn(Optional.of(
			new CatchUpRoomAccess(AUCTION_ID, 25L, "WAITING", "PUBLIC", false, END_AT)
		));

		assertThatThrownBy(() -> service.getSummary(AUCTION_ID, VIEWER_ID))
			.isInstanceOfSatisfying(CatchUpSummaryException.class, exception ->
				assertThat(exception.code()).isEqualTo(CatchUpSummaryErrorCode.AUCTION_NOT_STARTED)
			);
	}

	private static CatchUpSummaryResult cachedSummary(
		long bidCount,
		long participantCount,
		List<String> sellerMentions
	) {
		return new CatchUpSummaryResult(
			AUCTION_ID,
			NOW.minusSeconds(30),
			NOW.plusSeconds(90),
			bidCount,
			participantCount,
			0,
			0,
			sellerMentions,
			NOW.minusSeconds(30),
			END_AT,
			CatchUpSummarySourceType.STT_AND_ROOM_EVENT
		);
	}
}
