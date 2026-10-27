package com.ssafy.bidlive.common.application;

import static com.ssafy.bidlive.common.application.RealtimeAuctionStateQueryException.Reason.AUCTION_NOT_FOUND;
import static com.ssafy.bidlive.common.application.RealtimeAuctionStateQueryException.Reason.AUCTION_STATE_UNAVAILABLE;
import static com.ssafy.bidlive.common.application.RealtimeAuctionStateResult.ParticipationType.BID;
import static com.ssafy.bidlive.common.application.RealtimeAuctionStateResult.ParticipationType.NONE;
import static com.ssafy.bidlive.common.application.RealtimeAuctionStateResult.ParticipationType.PREDICTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import com.ssafy.bidlive.auction.application.AuctionResultQueryPersistence;
import com.ssafy.bidlive.auction.application.AuctionRoomDetailPersistence;
import com.ssafy.bidlive.auction.application.AuctionRoomDetailRow;
import com.ssafy.bidlive.auction.application.MyBidRow;
import com.ssafy.bidlive.chat.application.ChatMuteReader;
import com.ssafy.bidlive.chat.application.PinnedNoticeQueryService;
import com.ssafy.bidlive.chat.domain.PinnedNotice;
import com.ssafy.bidlive.common.application.RealtimeAuctionStateSnapshot.Status;
import com.ssafy.bidlive.media.application.ViewerCountQueryService;
import com.ssafy.bidlive.prediction.application.PredictionParticipationPersistence;
import com.ssafy.bidlive.prediction.application.PredictionParticipationPersistence.StoredPrediction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 공개·개인 realtime-state 조합과 저장소 실패 정책을 검증합니다. */
@ExtendWith(MockitoExtension.class)
class RealtimeAuctionStateQueryServiceTest {

	private static final long AUCTION_ID = 42L;
	private static final long MEMBER_ID = 7L;
	private static final long SELLER_ID = 88L;
	private static final Instant NOW = Instant.parse("2026-08-02T12:00:00Z");
	private static final Instant END_AT = Instant.parse("2026-08-02T12:07:11Z");

	@Mock
	private RealtimeAuctionStateReader stateReader;
	@Mock
	private AuctionRoomDetailPersistence auctionPersistence;
	@Mock
	private AuctionResultQueryPersistence bidQueryPersistence;
	@Mock
	private PredictionParticipationPersistence predictionPersistence;
	@Mock
	private ViewerCountQueryService viewerCountQueryService;
	@Mock
	private PinnedNoticeQueryService pinnedNoticeQueryService;
	@Mock
	private ChatMuteReader chatMuteReader;

	private RealtimeAuctionStateQueryService service;

	@BeforeEach
	void setUp() {
		service = new RealtimeAuctionStateQueryService(
			stateReader,
			auctionPersistence,
			bidQueryPersistence,
			predictionPersistence,
			viewerCountQueryService,
			pinnedNoticeQueryService,
			chatMuteReader,
			Clock.fixed(NOW, ZoneOffset.UTC)
		);
	}

	@Test
	void combinesPublicStateForAnonymousViewerWithSafePersonalDefaults() {
		stubPublicState();
		when(pinnedNoticeQueryService.findPinned(AUCTION_ID)).thenReturn(Optional.of(
			new PinnedNotice("01J8W3P2N9KQ4T", "택배비 포함가입니다")
		));

		RealtimeAuctionStateResult result = service.getState(AUCTION_ID, null);

		assertThat(result.stateVersion()).isEqualTo(184L);
		assertThat(result.remainingSeconds()).isEqualTo(431L);
		assertThat(result.remainingExtensions()).isEqualTo(2);
		assertThat(result.bidParticipantCount()).isEqualTo(5L);
		assertThat(result.viewerCount()).isEqualTo(23L);
		assertThat(result.myBidAmount()).isNull();
		assertThat(result.myParticipationType()).isEqualTo(NONE);
		assertThat(result.pinnedNotice().messageId()).isEqualTo("01J8W3P2N9KQ4T");
		assertThat(result.amIMuted()).isFalse();
		assertThat(result.mutedUntil()).isNull();
		verify(bidQueryPersistence, never()).findMyBid(AUCTION_ID, MEMBER_ID);
	}

	@Test
	void restoresOnlyAuthenticatedMembersOwnBidAndMuteState() {
		stubPublicState();
		when(pinnedNoticeQueryService.findPinned(AUCTION_ID)).thenReturn(Optional.empty());
		when(bidQueryPersistence.findMyBid(AUCTION_ID, MEMBER_ID))
			.thenReturn(Optional.of(new MyBidRow(52_000L, false)));
		when(predictionPersistence.findByAuctionIdAndMemberId(AUCTION_ID, MEMBER_ID))
			.thenReturn(Optional.empty());
		when(chatMuteReader.findMutedUntil(AUCTION_ID, MEMBER_ID))
			.thenReturn(Optional.of(NOW.plusSeconds(30)));

		RealtimeAuctionStateResult result = service.getState(AUCTION_ID, MEMBER_ID);

		assertThat(result.myBidAmount()).isEqualTo(52_000L);
		assertThat(result.myPredictedPrice()).isNull();
		assertThat(result.myStakeAmount()).isNull();
		assertThat(result.myParticipationType()).isEqualTo(BID);
		assertThat(result.amIMuted()).isTrue();
		assertThat(result.mutedUntil()).isEqualTo(
			OffsetDateTime.ofInstant(NOW.plusSeconds(30), ZoneOffset.UTC)
		);
	}

	@Test
	void restoresPredictionWithoutExposingBidAmount() {
		stubPublicState();
		when(pinnedNoticeQueryService.findPinned(AUCTION_ID)).thenReturn(Optional.empty());
		when(bidQueryPersistence.findMyBid(AUCTION_ID, MEMBER_ID)).thenReturn(Optional.empty());
		when(predictionPersistence.findByAuctionIdAndMemberId(AUCTION_ID, MEMBER_ID))
			.thenReturn(Optional.of(new StoredPrediction(
				1L,
				AUCTION_ID,
				MEMBER_ID,
				50_000L,
				1_000L,
				"request-id",
				OffsetDateTime.ofInstant(NOW.minusSeconds(10), ZoneOffset.UTC)
			)));
		when(chatMuteReader.findMutedUntil(AUCTION_ID, MEMBER_ID))
			.thenReturn(Optional.empty());

		RealtimeAuctionStateResult result = service.getState(AUCTION_ID, MEMBER_ID);

		assertThat(result.myBidAmount()).isNull();
		assertThat(result.myPredictedPrice()).isEqualTo(50_000L);
		assertThat(result.myStakeAmount()).isEqualTo(1_000L);
		assertThat(result.myParticipationType()).isEqualTo(PREDICTION);
		assertThat(result.amIMuted()).isFalse();
		assertThat(result.mutedUntil()).isNull();
	}

	@Test
	void hidesPrivateAuctionFromAnonymousViewerAsMissing() {
		stubRoom("PRIVATE", SELLER_ID);

		assertReason(() -> service.getState(AUCTION_ID, null), AUCTION_NOT_FOUND);
		verify(stateReader, never()).read(AUCTION_ID);
		verify(pinnedNoticeQueryService, never()).findPinned(AUCTION_ID);
	}

	@Test
	void hidesPrivateAuctionFromNonSellerAsMissing() {
		stubRoom("PRIVATE", SELLER_ID);

		assertReason(() -> service.getState(AUCTION_ID, MEMBER_ID), AUCTION_NOT_FOUND);
		verify(stateReader, never()).read(AUCTION_ID);
	}

	@Test
	void servesPrivateAuctionToItsSeller() {
		stubRoom("PRIVATE", MEMBER_ID);
		when(stateReader.read(AUCTION_ID)).thenReturn(new RealtimeAuctionStateSnapshot(
			184L,
			Status.LIVE,
			END_AT,
			1,
			7L,
			5L
		));
		when(viewerCountQueryService.activeViewerCount(AUCTION_ID)).thenReturn(23L);
		when(pinnedNoticeQueryService.findPinned(AUCTION_ID)).thenReturn(Optional.empty());
		when(bidQueryPersistence.findMyBid(AUCTION_ID, MEMBER_ID)).thenReturn(Optional.empty());
		when(predictionPersistence.findByAuctionIdAndMemberId(AUCTION_ID, MEMBER_ID))
			.thenReturn(Optional.empty());
		when(chatMuteReader.findMutedUntil(AUCTION_ID, MEMBER_ID))
			.thenReturn(Optional.empty());

		RealtimeAuctionStateResult result = service.getState(AUCTION_ID, MEMBER_ID);

		assertThat(result.stateVersion()).isEqualTo(184L);
		assertThat(result.myParticipationType()).isEqualTo(NONE);
	}

	@Test
	void rejectsMissingOrDeletedAuctionBeforeReadingRedis() {
		when(auctionPersistence.findByAuctionId(AUCTION_ID)).thenReturn(Optional.empty());

		assertReason(() -> service.getState(AUCTION_ID, MEMBER_ID), AUCTION_NOT_FOUND);
		verify(stateReader, never()).read(AUCTION_ID);
	}

	@Test
	void rejectsMutuallyInconsistentBidAndPredictionParticipation() {
		stubPublicState();
		when(pinnedNoticeQueryService.findPinned(AUCTION_ID)).thenReturn(Optional.empty());
		when(bidQueryPersistence.findMyBid(AUCTION_ID, MEMBER_ID))
			.thenReturn(Optional.of(new MyBidRow(52_000L, false)));
		when(predictionPersistence.findByAuctionIdAndMemberId(AUCTION_ID, MEMBER_ID))
			.thenReturn(Optional.of(new StoredPrediction(
				1L,
				AUCTION_ID,
				MEMBER_ID,
				50_000L,
				1_000L,
				"request-id",
				OffsetDateTime.ofInstant(NOW.minusSeconds(10), ZoneOffset.UTC)
			)));

		assertReason(
			() -> service.getState(AUCTION_ID, MEMBER_ID),
			AUCTION_STATE_UNAVAILABLE
		);
	}

	@Test
	void mapsRequiredStoreFailureToUnavailable() {
		when(auctionPersistence.findByAuctionId(AUCTION_ID))
			.thenThrow(new IllegalStateException("database unavailable"));

		assertReason(
			() -> service.getState(AUCTION_ID, MEMBER_ID),
			AUCTION_STATE_UNAVAILABLE
		);
	}

	private void stubPublicState() {
		stubRoom("PUBLIC", SELLER_ID);
		when(stateReader.read(AUCTION_ID)).thenReturn(new RealtimeAuctionStateSnapshot(
			184L,
			Status.LIVE,
			END_AT,
			1,
			7L,
			5L
		));
		when(viewerCountQueryService.activeViewerCount(AUCTION_ID)).thenReturn(23L);
	}

	private void stubRoom(String visibility, long sellerId) {
		AuctionRoomDetailRow auction = mock(AuctionRoomDetailRow.class);
		when(auction.auctionId()).thenReturn(AUCTION_ID);
		when(auction.deletedAt()).thenReturn(null);
		when(auction.visibility()).thenReturn(visibility);
		lenient().when(auction.sellerId()).thenReturn(sellerId);
		when(auctionPersistence.findByAuctionId(AUCTION_ID))
			.thenReturn(Optional.of(auction));
	}

	private static void assertReason(
		Runnable invocation,
		RealtimeAuctionStateQueryException.Reason reason
	) {
		assertThatThrownBy(invocation::run)
			.isInstanceOfSatisfying(
				RealtimeAuctionStateQueryException.class,
				exception -> assertThat(exception.reason()).isEqualTo(reason)
			);
	}
}
