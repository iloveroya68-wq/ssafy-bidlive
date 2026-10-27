package com.ssafy.bidlive.auction.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.ssafy.bidlive.auction.domain.AuctionResultErrorCode;
import com.ssafy.bidlive.auction.domain.AuctionResultViewerRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 결과 조회의 역할 판정·마스킹·정산 계산을 검증한다.
 *
 * <p>여기서 잡는 것들은 전부 <b>화면이 멀쩡해 보이는 실패</b>다 — 남의 닉네임이 원문으로 새거나,
 * 비크리인데 해제액이 0으로 나오거나, 유찰인데 미적중으로 표시되는 것들이다.
 */
class AuctionResultQueryServiceTest {

	private static final long AUCTION_ID = 100L;
	private static final long SELLER_ID = 25L;
	private static final long WINNER_ID = 7L;
	private static final long LOSER_ID = 8L;
	private static final long PREDICTOR_ID = 9L;
	private static final String SNAPSHOT_URL = "https://cdn.example.com/snapshots/100.jpg";

	private AuctionResultQueryPersistence persistence;
	private AuctionResultQueryService service;

	@BeforeEach
	void setUp() {
		persistence = mock(AuctionResultQueryPersistence.class);
		service = new AuctionResultQueryService(persistence);
		when(persistence.findBids(anyLong())).thenReturn(List.of());
		when(persistence.findPredictionBands(anyLong())).thenReturn(List.of());
		when(persistence.findMyBid(anyLong(), anyLong())).thenReturn(Optional.empty());
		when(persistence.findMyPrediction(anyLong(), anyLong())).thenReturn(Optional.empty());
	}

	private void givenSold() {
		givenSold(null);
	}

	private void givenSold(String snapshotUrl) {
		when(persistence.findHeader(AUCTION_ID)).thenReturn(Optional.of(new AuctionResultHeaderRow(
			AUCTION_ID, SELLER_ID, "SOLD", "SOLD", 74_000L, 501L, "김판매", snapshotUrl
		)));
	}

	private void givenNoSale(String reason) {
		when(persistence.findHeader(AUCTION_ID)).thenReturn(Optional.of(new AuctionResultHeaderRow(
			AUCTION_ID, SELLER_ID, "NO_SALE", reason, null, null, null, SNAPSHOT_URL
		)));
	}

	@Test
	void 방이_없으면_404다() {
		when(persistence.findHeader(AUCTION_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getResult(AUCTION_ID, null))
			.isInstanceOf(AuctionResultQueryException.class)
			.extracting(exception -> ((AuctionResultQueryException) exception).getErrorCode())
			.isEqualTo(AuctionResultErrorCode.AUCTION_NOT_FOUND);
	}

	@Test
	void 정산_전에는_409다() {
		// 방은 있는데 auction_result가 아직 없다 — 집계 엔진이 붙기 전의 정상 상태다
		when(persistence.findHeader(AUCTION_ID)).thenReturn(Optional.of(new AuctionResultHeaderRow(
			AUCTION_ID, SELLER_ID, null, null, null, null, null, null
		)));

		assertThatThrownBy(() -> service.getResult(AUCTION_ID, PREDICTOR_ID))
			.isInstanceOf(AuctionResultQueryException.class)
			.extracting(exception -> ((AuctionResultQueryException) exception).getErrorCode())
			.isEqualTo(AuctionResultErrorCode.PREDICTION_RESULT_NOT_AVAILABLE);
	}

	@Test
	void 비공개_방은_정산_여부를_알려주지_않고_404다() {
		// 비공개 방은 헤더 조회에서 아예 안 잡힌다. 409를 먼저 판정하면 "그 방은 있다"가 새어 나간다
		when(persistence.findHeader(AUCTION_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getResult(AUCTION_ID, SELLER_ID))
			.extracting(exception -> ((AuctionResultQueryException) exception).getErrorCode())
			.isEqualTo(AuctionResultErrorCode.AUCTION_NOT_FOUND);
	}

	@Test
	void 비로그인은_관전자이고_개인_정산이_비어_있다() {
		givenSold();

		AuctionResultQueryResult result = service.getResult(AUCTION_ID, null);

		assertThat(result.viewerRole()).isEqualTo(AuctionResultViewerRole.SPECTATOR);
		assertThat(result.myBidSettlement()).isNull();
		assertThat(result.myPredictionSettlement()).isNull();
	}

	@Test
	void 판매자는_참여자보다_먼저_판정된다() {
		givenSold();

		AuctionResultQueryResult result = service.getResult(AUCTION_ID, SELLER_ID);

		assertThat(result.viewerRole()).isEqualTo(AuctionResultViewerRole.SELLER);
	}

	@Test
	void 낙찰자는_결제액이_2등가고_차액이_풀린다() {
		// 비크리의 핵심이다. 제출가로 결제하게 만들면 그냥 최고가 경매가 된다
		givenSold();
		when(persistence.findMyBid(AUCTION_ID, WINNER_ID))
			.thenReturn(Optional.of(new MyBidRow(82_000L, true)));

		AuctionResultQueryResult result = service.getResult(AUCTION_ID, WINNER_ID);

		assertThat(result.viewerRole()).isEqualTo(AuctionResultViewerRole.WINNING_BIDDER);
		assertThat(result.myBidSettlement())
			.isEqualTo(new BidSettlement("WON", 82_000L, 74_000L, 8_000L));
		// 본인이 낙찰자면 라벨을 마스킹하지 않는다
		assertThat(result.winnerLabel()).isEqualTo("나");
	}

	@Test
	void 미낙찰자는_제출가_전액이_풀린다() {
		givenSold();
		when(persistence.findMyBid(AUCTION_ID, LOSER_ID))
			.thenReturn(Optional.of(new MyBidRow(70_000L, false)));

		AuctionResultQueryResult result = service.getResult(AUCTION_ID, LOSER_ID);

		assertThat(result.viewerRole()).isEqualTo(AuctionResultViewerRole.LOSING_BIDDER);
		assertThat(result.myBidSettlement())
			.isEqualTo(new BidSettlement("LOST", 70_000L, null, 70_000L));
		// 남의 낙찰자 이름은 마스킹된다
		assertThat(result.winnerLabel()).isEqualTo("김**");
	}

	@Test
	void 예측자는_저장된_정산_결과를_그대로_받는다() {
		givenSold();
		when(persistence.findMyPrediction(AUCTION_ID, PREDICTOR_ID)).thenReturn(Optional.of(
			new MyPredictionRow(701L, 74_500L, 300L, "HIT", 5, 1_500L)
		));

		AuctionResultQueryResult result = service.getResult(AUCTION_ID, PREDICTOR_ID);

		assertThat(result.viewerRole()).isEqualTo(AuctionResultViewerRole.PREDICTOR);
		PredictionSettlement settlement = result.myPredictionSettlement();
		assertThat(settlement.result()).isEqualTo("HIT");
		assertThat(settlement.errorBand()).isEqualTo("WITHIN_5");
		assertThat(settlement.multiplier()).isEqualByComparingTo(new BigDecimal("5"));
		// payoutAmount는 원금 포함이다(PRED-03) — 화면이 스테이크를 또 더하면 안 된다
		assertThat(settlement.payoutAmount()).isEqualTo(1_500L);
	}

	@Test
	void 미적중은_배수가_0이고_밴드가_MISS다() {
		givenSold();
		when(persistence.findMyPrediction(AUCTION_ID, PREDICTOR_ID)).thenReturn(Optional.of(
			new MyPredictionRow(702L, 90_000L, 300L, "MISS", 0, 0L)
		));

		PredictionSettlement settlement =
			service.getResult(AUCTION_ID, PREDICTOR_ID).myPredictionSettlement();

		assertThat(settlement.result()).isEqualTo("MISS");
		assertThat(settlement.errorBand()).isEqualTo("MISS");
		assertThat(settlement.multiplier()).isZero();
	}

	@Test
	void 예측_정산_전이면_정산_객체를_아예_비운다() {
		// 낙찰은 났는데 예측 정산이 안 끝난 구간. result 없는 객체를 내보내면 계약상 필수 필드가
		// 빠진 채로 나간다 — 「아직 없다」는 필드 누락이 아니라 객체 부재로 말한다
		givenSold();
		when(persistence.findMyPrediction(AUCTION_ID, PREDICTOR_ID)).thenReturn(Optional.of(
			new MyPredictionRow(704L, 74_000L, 300L, null, null, null)
		));

		AuctionResultQueryResult result = service.getResult(AUCTION_ID, PREDICTOR_ID);

		assertThat(result.myPredictionSettlement()).isNull();
		// 역할은 그대로 예측자다 — 참여한 사실 자체는 정산과 무관하다
		assertThat(result.viewerRole()).isEqualTo(AuctionResultViewerRole.PREDICTOR);
	}

	@Test
	void 낙찰_건의_환불_행은_배수_지급액_없이_환불액만_준다() {
		// 정상 경로로는 안 온다(정산기의 refunded()가 completionType != SOLD).
		// 그래도 들어오면 배수 ×1이 함께 나가 카드에 "×1 지급"이 뜬다
		givenSold();
		when(persistence.findMyPrediction(AUCTION_ID, PREDICTOR_ID)).thenReturn(Optional.of(
			new MyPredictionRow(705L, 74_000L, 300L, "REFUNDED", 1, 300L)
		));

		PredictionSettlement settlement =
			service.getResult(AUCTION_ID, PREDICTOR_ID).myPredictionSettlement();

		assertThat(settlement.result()).isEqualTo("REFUNDED");
		assertThat(settlement.refundAmount()).isEqualTo(300L);
		assertThat(settlement.multiplier()).isNull();
		assertThat(settlement.payoutAmount()).isNull();
		assertThat(settlement.errorBand()).isNull();
	}

	@Test
	void 같은_밴드가_두_줄_오면_합산한다() {
		// GROUP BY에 result_status가 들어간 뒤로는 이상 데이터에서 두 줄이 나올 수 있다.
		// 덮어쓰면 인원이 조용히 준다
		givenSold();
		when(persistence.findPredictionBands(AUCTION_ID)).thenReturn(List.of(
			new PredictionBandRow("MISS", 3L, 0, 0L),
			new PredictionBandRow("MISS", 2L, 0, 100L)
		));

		List<PredictionBandRow> bands = service.getResult(AUCTION_ID, null).predictionBands();

		PredictionBandRow miss = bands.get(3);
		assertThat(miss.band()).isEqualTo("MISS");
		assertThat(miss.participantCount()).isEqualTo(5L);
		assertThat(miss.totalPayoutAmount()).isEqualTo(100L);
	}

	@Test
	void 밴드는_저장된_배수에서_읽는다() {
		// ⚠️ 오차율을 다시 계산하지 않는다. error_rate는 **퍼센트**(0.68 = 0.68%)인데 비율로 읽어
		// 전원 미적중이 됐던 버그가 여기서 났다(MR !226 리뷰 1). 저장값 매핑만 검증한다.
		assertThat(PredictionBands.nameOf(5)).isEqualTo("WITHIN_5");
		assertThat(PredictionBands.nameOf(3)).isEqualTo("WITHIN_10");
		assertThat(PredictionBands.nameOf(new java.math.BigDecimal("1.5")))
			.isEqualTo("WITHIN_20");
		assertThat(PredictionBands.nameOf("HIT", BigDecimal.ONE))
			.isEqualTo("WITHIN_5");
		assertThat(PredictionBands.nameOf("REFUNDED", BigDecimal.ONE))
			.isNull();
		assertThat(PredictionBands.nameOf(0)).isEqualTo("MISS");
		// 모르는 배수를 조용히 MISS로 만들지 않는다 — 집계에서 빠지고 드러난다
		assertThat(PredictionBands.nameOf(7)).isNull();
	}

	@Test
	void 밴드는_0건도_채워_항상_네_줄이다() {
		// PRED-02도 4밴드를 항상 준다. 빠뜨리면 화면이 "0명"과 "집계 안 됨"을 구분하지 못한다
		givenSold();
		when(persistence.findPredictionBands(AUCTION_ID))
			.thenReturn(List.of(new PredictionBandRow("WITHIN_5", 2L, 5, 2_500L)));

		List<PredictionBandRow> bands = service.getResult(AUCTION_ID, null).predictionBands();

		assertThat(bands).extracting(PredictionBandRow::band)
			.containsExactly("WITHIN_5", "WITHIN_10", "WITHIN_20", "MISS");
		assertThat(bands.get(1).participantCount()).isZero();
		assertThat(bands.get(1).multiplier()).isEqualByComparingTo("3");
	}

	@Test
	void 낙찰이면_스냅샷_URL을_그대로_내려준다() {
		// LIVE-06 캡처가 결과 화면의 「낙찰 순간」 자리에 그대로 들어간다
		givenSold(SNAPSHOT_URL);

		assertThat(service.getResult(AUCTION_ID, null).snapshotUrl()).isEqualTo(SNAPSHOT_URL);
	}

	@Test
	void 스냅샷이_없어도_결과는_나온다() {
		// 판매자가 캡처를 못 올린 경매가 정상 경로다 — 없다고 조회가 죽으면 결과 화면 전체가 막힌다
		givenSold(null);

		AuctionResultQueryResult result = service.getResult(AUCTION_ID, WINNER_ID);

		assertThat(result.snapshotUrl()).isNull();
		assertThat(result.sold()).isTrue();
	}

	@Test
	void 유찰에는_스냅샷을_주지_않는다() {
		// 「낙찰 순간」 캡처라 낙찰이 없으면 가리킬 순간도 없다. 행이 남아 있어도 내보내지 않는다
		givenNoSale("HOST_LEFT");

		assertThat(service.getResult(AUCTION_ID, SELLER_ID).snapshotUrl()).isNull();
	}

	@Test
	void 유찰이면_예측은_적중_여부가_아니라_환불이다() {
		// 유찰인데 MISS로 표시하면 "틀려서 잃었다"로 읽힌다 — 실제로는 전액 돌려받는다
		givenNoSale("HOST_LEFT");
		when(persistence.findMyPrediction(AUCTION_ID, PREDICTOR_ID)).thenReturn(Optional.of(
			new MyPredictionRow(703L, 74_000L, 300L, null, null, null)
		));

		AuctionResultQueryResult result = service.getResult(AUCTION_ID, PREDICTOR_ID);

		assertThat(result.sold()).isFalse();
		assertThat(result.resultType()).isEqualTo("HOST_LEFT");
		PredictionSettlement settlement = result.myPredictionSettlement();
		assertThat(settlement.result()).isEqualTo("REFUNDED");
		assertThat(settlement.refundAmount()).isEqualTo(300L);
		assertThat(settlement.errorBand()).isNull();
	}

	@Test
	void 유찰이면_입찰_홀드도_전액_해제된다() {
		givenNoSale("FORCED");
		when(persistence.findMyBid(AUCTION_ID, LOSER_ID))
			.thenReturn(Optional.of(new MyBidRow(50_000L, false)));

		AuctionResultQueryResult result = service.getResult(AUCTION_ID, LOSER_ID);

		assertThat(result.myBidSettlement())
			.isEqualTo(new BidSettlement("LOST", 50_000L, null, 50_000L));
		// 유찰에는 낙찰가·입찰 내역·밴드가 없다
		assertThat(result.finalPrice()).isNull();
		assertThat(result.bids()).isEmpty();
		assertThat(result.predictionBands()).isEmpty();
	}

	@Test
	void 판매자에게만_재등록_가능_플래그가_참이다() {
		givenNoSale("NO_BID");

		assertThat(service.getResult(AUCTION_ID, SELLER_ID).canRelist()).isTrue();
		assertThat(service.getResult(AUCTION_ID, PREDICTOR_ID).canRelist()).isFalse();
	}

	@Test
	void 강제_종료는_판매자에게도_재등록을_숨긴다() {
		// 명세 「종료 사유」표: FORCED는 재등록 CTA 숨김, 대신 운영 검토 안내.
		// 프론트가 viewerRole === SELLER && canRelist === true로 버튼을 그린다(MR !226 리뷰 3)
		givenNoSale("FORCED");

		assertThat(service.getResult(AUCTION_ID, SELLER_ID).canRelist()).isFalse();
	}

	@Test
	void 모르는_종료_사유는_강제_종료로_좁힌다() {
		// termination_reason에 CHECK 제약이 없다. 집계가 다른 문자열을 넣으면 프론트 유니온을
		// 조용히 벗어나므로 3값으로 좁히고, 모르는 값은 재등록 CTA가 없는 쪽으로 떨어뜨린다
		givenNoSale("CANCELLED_BY_ADMIN");

		AuctionResultQueryResult result = service.getResult(AUCTION_ID, SELLER_ID);

		assertThat(result.resultType()).isEqualTo("FORCED");
		assertThat(result.canRelist()).isFalse();
	}

	@Test
	void 입찰_내역은_본인만_나로_나머지는_마스킹된다() {
		// 비크리 비공개 원칙(규칙 6) — 종료 후에만, 그것도 마스킹해서 공개한다
		givenSold();
		when(persistence.findBids(AUCTION_ID)).thenReturn(List.of(
			new MaskedBidRow(WINNER_ID, "김판매", 82_000L, Instant.parse("2026-07-21T11:10:00Z")),
			new MaskedBidRow(LOSER_ID, "박구매", 74_000L, Instant.parse("2026-07-21T11:08:00Z"))
		));
		when(persistence.findMyBid(AUCTION_ID, LOSER_ID))
			.thenReturn(Optional.of(new MyBidRow(74_000L, false)));

		List<MaskedBidEntry> bids = service.getResult(AUCTION_ID, LOSER_ID).bids();

		assertThat(bids).extracting(MaskedBidEntry::bidderLabel).containsExactly("김**", "나");
	}

	@Test
	void 비로그인_조회에는_본인_행이_없다() {
		givenSold();
		when(persistence.findBids(AUCTION_ID)).thenReturn(List.of(
			new MaskedBidRow(WINNER_ID, "김판매", 82_000L, Instant.parse("2026-07-21T11:10:00Z"))
		));

		List<MaskedBidEntry> bids = service.getResult(AUCTION_ID, null).bids();

		assertThat(bids).extracting(MaskedBidEntry::bidderLabel).containsExactly("김**");
	}
}
