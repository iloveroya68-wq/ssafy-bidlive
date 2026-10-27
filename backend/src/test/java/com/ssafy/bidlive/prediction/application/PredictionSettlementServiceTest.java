package com.ssafy.bidlive.prediction.application;

import static com.ssafy.bidlive.prediction.application.PredictionSettlementCommand.CompletionType.FORCED;
import static com.ssafy.bidlive.prediction.application.PredictionSettlementCommand.CompletionType.SOLD;
import static com.ssafy.bidlive.wallet.domain.BettingPointSettlementType.PAYOUT;
import static com.ssafy.bidlive.wallet.domain.BettingPointSettlementType.REFUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.ssafy.bidlive.prediction.application.PredictionSettlementPersistence.NewSettlement;
import com.ssafy.bidlive.prediction.application.PredictionSettlementPersistence.SaveResult;
import com.ssafy.bidlive.prediction.application.PredictionSettlementPersistence.SettlementTarget;
import com.ssafy.bidlive.prediction.application.PredictionSettlementPersistence.StoredSettlement;
import com.ssafy.bidlive.prediction.domain.PredictionResultStatus;
import com.ssafy.bidlive.prediction.domain.PredictionSettlementBand;
import com.ssafy.bidlive.prediction.domain.PredictionSettlementDecision;
import com.ssafy.bidlive.wallet.application.BettingPointSettlementCommand;
import com.ssafy.bidlive.wallet.application.BettingPointSettlementResult;
import com.ssafy.bidlive.wallet.application.BettingPointSettlementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** PRED-03 최초 결과 반영과 정산 완료 이벤트 등록 흐름을 검증한다. */
@ExtendWith(MockitoExtension.class)
class PredictionSettlementServiceTest {

	@Mock
	private PredictionSettlementPersistence persistence;

	@Mock
	private BettingPointSettlementService walletSettlementService;

	@Mock
	private PredictionAchievementSettlementService achievementService;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	private PredictionSettlementService service;

	@BeforeEach
	void setUp() {
		service = new PredictionSettlementService(
			persistence,
			walletSettlementService,
			achievementService,
			eventPublisher
		);
	}

	@Test
	void savesHitAndPaysOutThroughWallet() {
		SettlementTarget target = target(10_100L, 300L);
		when(persistence.findUnsettledTargets(100L))
			.thenReturn(List.of(target));
		when(persistence.saveIfAbsent(any(NewSettlement.class)))
			.thenAnswer(invocation -> created(
				invocation.getArgument(0),
				801L
			));
		when(walletSettlementService.settle(any()))
			.thenReturn(walletResult(PAYOUT, 1_500L));

		PredictionSettlementResult result = service.settle(
			new PredictionSettlementCommand(100L, 501L, 10_000L, SOLD)
		);

		assertThat(result).isEqualTo(new PredictionSettlementResult(
			100L,
			1,
			1,
			0,
			300L,
			1_500L
		));
		ArgumentCaptor<NewSettlement> resultCaptor =
			ArgumentCaptor.forClass(NewSettlement.class);
		verify(persistence).saveIfAbsent(resultCaptor.capture());
		assertThat(resultCaptor.getValue().decision().resultStatus())
			.isEqualTo(PredictionResultStatus.HIT);
		assertThat(resultCaptor.getValue().decision().band())
			.isEqualTo(PredictionSettlementBand.WITHIN_5);

		ArgumentCaptor<BettingPointSettlementCommand> walletCaptor =
			ArgumentCaptor.forClass(BettingPointSettlementCommand.class);
		verify(walletSettlementService).settle(walletCaptor.capture());
		assertThat(walletCaptor.getValue())
			.isEqualTo(new BettingPointSettlementCommand(
				25L,
				701L,
				801L,
				PAYOUT,
				1_500L,
				"PREDICTION_SETTLEMENT:701"
			));
		verify(achievementService).apply(
			25L,
			801L,
			resultCaptor.getValue().decision()
		);
	}

	@Test
	void savesMissWithoutCallingWallet() {
		SettlementTarget target = target(12_001L, 300L);
		when(persistence.findUnsettledTargets(100L))
			.thenReturn(List.of(target));
		when(persistence.saveIfAbsent(any(NewSettlement.class)))
			.thenAnswer(invocation -> created(
				invocation.getArgument(0),
				801L
			));

		PredictionSettlementResult result = service.settle(
			new PredictionSettlementCommand(100L, 501L, 10_000L, SOLD)
		);

		assertThat(result.totalPayoutPoints()).isZero();
		ArgumentCaptor<NewSettlement> captor =
			ArgumentCaptor.forClass(NewSettlement.class);
		verify(persistence).saveIfAbsent(captor.capture());
		assertThat(captor.getValue().decision().resultStatus())
			.isEqualTo(PredictionResultStatus.MISS);
		verify(walletSettlementService, never()).settle(any());
		verify(achievementService).apply(
			25L,
			801L,
			captor.getValue().decision()
		);
	}

	@Test
	void refundsFullStakeForForcedTermination() {
		SettlementTarget target = target(20_000L, 300L);
		when(persistence.findUnsettledTargets(100L))
			.thenReturn(List.of(target));
		when(persistence.saveIfAbsent(any(NewSettlement.class)))
			.thenAnswer(invocation -> created(
				invocation.getArgument(0),
				801L
			));
		when(walletSettlementService.settle(any()))
			.thenReturn(walletResult(REFUND, 300L));

		PredictionSettlementResult result = service.settle(
			new PredictionSettlementCommand(100L, 501L, null, FORCED)
		);

		assertThat(result.totalPayoutPoints()).isEqualTo(300L);
		ArgumentCaptor<BettingPointSettlementCommand> captor =
			ArgumentCaptor.forClass(BettingPointSettlementCommand.class);
		verify(walletSettlementService).settle(captor.capture());
		assertThat(captor.getValue().settlementType()).isEqualTo(REFUND);
		assertThat(captor.getValue().amount()).isEqualTo(300L);
		verify(achievementService, never()).apply(any(), any(), any());
	}

	@Test
	void skipsWalletWhenConcurrentSettlementAlreadyCreatedResult() {
		SettlementTarget target = target(10_100L, 300L);
		when(persistence.findUnsettledTargets(100L))
			.thenReturn(List.of(target));
		when(persistence.saveIfAbsent(any(NewSettlement.class)))
			.thenAnswer(invocation -> duplicated(
				invocation.getArgument(0),
				801L
			));

		PredictionSettlementResult result = service.settle(
			new PredictionSettlementCommand(100L, 501L, 10_000L, SOLD)
		);

		assertThat(result).isEqualTo(new PredictionSettlementResult(
			100L,
			1,
			0,
			1,
			0L,
			0L
		));
		verify(walletSettlementService, never()).settle(any());
		verify(achievementService, never()).apply(any(), any(), any());
	}

	@Test
	void publishesCompletionWhenBatchFinishesWithoutTargets() {
		when(persistence.findUnsettledTargets(100L))
			.thenReturn(List.of());

		PredictionSettlementResult result = service.settle(
			new PredictionSettlementCommand(100L, 501L, 10_000L, SOLD)
		);

		assertThat(result).isEqualTo(new PredictionSettlementResult(
			100L,
			0,
			0,
			0,
			0L,
			0L
		));
		verify(eventPublisher).publishEvent(
			new PredictionSettlementCompletedEvent(100L)
		);
	}

	@Test
	void rejectsInvalidCommandState() {
		assertThatThrownBy(() -> new PredictionSettlementCommand(
			100L,
			501L,
			null,
			SOLD
		)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("finalPrice must be positive");
		assertThatThrownBy(() -> new PredictionSettlementCommand(
			100L,
			501L,
			10_000L,
			FORCED
		)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("refunded settlement must not have finalPrice");
	}

	private SettlementTarget target(long predictedPrice, long stakeAmount) {
		return new SettlementTarget(
			701L,
			25L,
			predictedPrice,
			stakeAmount
		);
	}

	private SaveResult created(NewSettlement settlement, Long resultId) {
		return saveResult(settlement, resultId, true);
	}

	private SaveResult duplicated(NewSettlement settlement, Long resultId) {
		return saveResult(settlement, resultId, false);
	}

	private SaveResult saveResult(
		NewSettlement settlement,
		Long resultId,
		boolean created
	) {
		return new SaveResult(
			new StoredSettlement(
				resultId,
				settlement.predictionId(),
				settlement.auctionResultId(),
				settlement.decision()
			),
			created
		);
	}

	private BettingPointSettlementResult walletResult(
		com.ssafy.bidlive.wallet.domain.BettingPointSettlementType type,
		long amount
	) {
		return new BettingPointSettlementResult(
			10L,
			1_001L,
			type,
			amount,
			2_000L + amount,
			false
		);
	}
}
