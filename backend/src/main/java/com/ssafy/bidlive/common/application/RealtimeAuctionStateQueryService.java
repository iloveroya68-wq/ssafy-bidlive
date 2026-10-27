package com.ssafy.bidlive.common.application;

import static com.ssafy.bidlive.common.application.RealtimeAuctionStateQueryException.Reason.AUCTION_NOT_FOUND;
import static com.ssafy.bidlive.common.application.RealtimeAuctionStateQueryException.Reason.AUCTION_STATE_UNAVAILABLE;
import static com.ssafy.bidlive.auction.domain.AuctionExtensionPolicy.remainingExtensions;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;

import com.ssafy.bidlive.auction.application.AuctionResultQueryPersistence;
import com.ssafy.bidlive.auction.application.AuctionRoomDetailPersistence;
import com.ssafy.bidlive.auction.application.MyBidRow;
import com.ssafy.bidlive.auction.domain.AuctionRoomVisibility;
import com.ssafy.bidlive.chat.application.ChatMuteReader;
import com.ssafy.bidlive.chat.application.PinnedNoticeQueryService;
import com.ssafy.bidlive.common.application.RealtimeAuctionStateResult.ParticipationType;
import com.ssafy.bidlive.common.application.RealtimeAuctionStateResult.PinnedNotice;
import com.ssafy.bidlive.media.application.ViewerCountQueryService;
import com.ssafy.bidlive.prediction.application.PredictionParticipationPersistence;
import com.ssafy.bidlive.prediction.application.PredictionParticipationPersistence.StoredPrediction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Redis 공개 상태와 도메인별 공개 조회 계약을 재접속 응답 하나로 조합합니다. */
@Service
public class RealtimeAuctionStateQueryService {

	private final RealtimeAuctionStateReader stateReader;
	private final AuctionRoomDetailPersistence auctionPersistence;
	private final AuctionResultQueryPersistence bidQueryPersistence;
	private final PredictionParticipationPersistence predictionPersistence;
	private final ViewerCountQueryService viewerCountQueryService;
	private final PinnedNoticeQueryService pinnedNoticeQueryService;
	private final ChatMuteReader chatMuteReader;
	private final Clock clock;

	@Autowired
	public RealtimeAuctionStateQueryService(
		RealtimeAuctionStateReader stateReader,
		AuctionRoomDetailPersistence auctionPersistence,
		AuctionResultQueryPersistence bidQueryPersistence,
		PredictionParticipationPersistence predictionPersistence,
		ViewerCountQueryService viewerCountQueryService,
		PinnedNoticeQueryService pinnedNoticeQueryService,
		ChatMuteReader chatMuteReader
	) {
		this(
			stateReader,
			auctionPersistence,
			bidQueryPersistence,
			predictionPersistence,
			viewerCountQueryService,
			pinnedNoticeQueryService,
			chatMuteReader,
			Clock.systemUTC()
		);
	}

	RealtimeAuctionStateQueryService(
		RealtimeAuctionStateReader stateReader,
		AuctionRoomDetailPersistence auctionPersistence,
		AuctionResultQueryPersistence bidQueryPersistence,
		PredictionParticipationPersistence predictionPersistence,
		ViewerCountQueryService viewerCountQueryService,
		PinnedNoticeQueryService pinnedNoticeQueryService,
		ChatMuteReader chatMuteReader,
		Clock clock
	) {
		this.stateReader = Objects.requireNonNull(stateReader, "stateReader");
		this.auctionPersistence = Objects.requireNonNull(
			auctionPersistence,
			"auctionPersistence"
		);
		this.bidQueryPersistence = Objects.requireNonNull(
			bidQueryPersistence,
			"bidQueryPersistence"
		);
		this.predictionPersistence = Objects.requireNonNull(
			predictionPersistence,
			"predictionPersistence"
		);
		this.viewerCountQueryService = Objects.requireNonNull(
			viewerCountQueryService,
			"viewerCountQueryService"
		);
		this.pinnedNoticeQueryService = Objects.requireNonNull(
			pinnedNoticeQueryService,
			"pinnedNoticeQueryService"
		);
		this.chatMuteReader = Objects.requireNonNull(chatMuteReader, "chatMuteReader");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/**
	 * 이 엔드포인트는 공개 방을 익명 시청자에게도 그대로 내주므로 인증을 요구하지 않는다.
	 * 그래서 방 공개 여부를 여기서 걸러내지 않으면, 토큰 없는 호출만으로 비공개 방의 존재와
	 * 고정 공지·입찰 수 같은 내용까지 드러난다. 익명 호출자는 결코 판매자일 수 없으므로
	 * 비공개 방은 익명과 비판매자 모두에게 없는 방과 같은 404로 감춘다(S15P11A704-482).
	 */
	public RealtimeAuctionStateResult getState(long auctionId, Long memberId) {
		if (auctionId <= 0) {
			throw new RealtimeAuctionStateQueryException(AUCTION_NOT_FOUND);
		}

		try {
			var auction = auctionPersistence.findByAuctionId(auctionId)
				.filter(row -> row.deletedAt() == null)
				.orElseThrow(() -> new RealtimeAuctionStateQueryException(
					AUCTION_NOT_FOUND
				));
			if (auction.auctionId() != auctionId) {
				throw new RealtimeAuctionStateQueryException(AUCTION_NOT_FOUND);
			}
			if (!AuctionRoomVisibility.isPublic(auction.visibility())
				&& (memberId == null || auction.sellerId() != memberId)) {
				throw new RealtimeAuctionStateQueryException(AUCTION_NOT_FOUND);
			}

			RealtimeAuctionStateSnapshot state = stateReader.read(auctionId);
			long viewerCount = viewerCountQueryService.activeViewerCount(auctionId);
			PinnedNotice pinnedNotice = pinnedNoticeQueryService.findPinned(auctionId)
				.map(notice -> new PinnedNotice(notice.messageId(), notice.content()))
				.orElse(null);
			PersonalState personalState = readPersonalState(auctionId, memberId);
			OffsetDateTime endAt = OffsetDateTime.ofInstant(
				state.endAt(),
				ZoneOffset.UTC
			);
			long remainingSeconds = Math.max(
				0L,
				Duration.between(clock.instant(), state.endAt()).toSeconds()
			);

			return new RealtimeAuctionStateResult(
				state.stateVersion(),
				state.status(),
				endAt,
				remainingSeconds,
				state.extendCount(),
				remainingExtensions(state.extendCount()),
				state.bidCount(),
				state.bidParticipantCount(),
				viewerCount,
				personalState.myBidAmount(),
				personalState.myPredictedPrice(),
				personalState.myStakeAmount(),
				personalState.participationType(),
				pinnedNotice,
				personalState.mutedUntil() != null,
				personalState.mutedUntil()
			);
		} catch (RealtimeAuctionStateQueryException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw new RealtimeAuctionStateQueryException(
				AUCTION_STATE_UNAVAILABLE,
				exception
			);
		}
	}

	private PersonalState readPersonalState(long auctionId, Long memberId) {
		if (memberId == null) {
			return PersonalState.anonymous();
		}
		if (memberId <= 0) {
			throw new RealtimeAuctionStateQueryException(AUCTION_STATE_UNAVAILABLE);
		}

		Optional<MyBidRow> bid = bidQueryPersistence.findMyBid(auctionId, memberId);
		Optional<StoredPrediction> prediction = predictionPersistence
			.findByAuctionIdAndMemberId(auctionId, memberId);
		if (bid.isPresent() && prediction.isPresent()) {
			throw new RealtimeAuctionStateQueryException(AUCTION_STATE_UNAVAILABLE);
		}

		OffsetDateTime mutedUntil = chatMuteReader.findMutedUntil(auctionId, memberId)
			.filter(until -> until.isAfter(clock.instant()))
			.map(until -> OffsetDateTime.ofInstant(until, ZoneOffset.UTC))
			.orElse(null);
		if (bid.isPresent()) {
			return new PersonalState(
				bid.get().amount(),
				null,
				null,
				ParticipationType.BID,
				mutedUntil
			);
		}
		if (prediction.isPresent()) {
			StoredPrediction stored = prediction.get();
			return new PersonalState(
				null,
				stored.predictedPrice(),
				stored.stakeAmount(),
				ParticipationType.PREDICTION,
				mutedUntil
			);
		}
		return new PersonalState(
			null,
			null,
			null,
			ParticipationType.NONE,
			mutedUntil
		);
	}

	private record PersonalState(
		Long myBidAmount,
		Long myPredictedPrice,
		Long myStakeAmount,
		ParticipationType participationType,
		OffsetDateTime mutedUntil
	) {
		private static PersonalState anonymous() {
			return new PersonalState(
				null,
				null,
				null,
				ParticipationType.NONE,
				null
			);
		}
	}
}
