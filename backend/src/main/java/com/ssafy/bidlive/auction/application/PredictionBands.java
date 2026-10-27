package com.ssafy.bidlive.auction.application;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 오차 밴드 4종. 「예측 고정 배당 정산」(PRED-03)이 단일 소스다 —
 * <i>5% 이내 ×5, 10% 이내 ×3, 20% 이내 ×1.5, 그 외 0</i>.
 *
 * <p>⚠️ 여기서 오차율을 계산하지 않는다. 저장된 {@code reward_multiplier}를 밴드 이름으로 옮길 뿐이다 —
 * 배수 정책이 바뀌어도 지난 경매 결과가 흔들리면 안 되기 때문이다.
 *
 * <p>⚠️ {@code prediction.domain.PredictionSettlementBand}와 <b>같은 값 집합을 공유한다.</b>
 * 모듈 경계를 넘지 않으려고 이름만 다시 적었으므로, 저쪽이 바뀌면 여기도 같이 고쳐야 한다.
 * ({@code REFUNDED}는 오차 밴드가 아니라 유찰 환불이라 여기 없다 — 환불 배수는
 * 정산 밴드 배수와 별도로 관리한다.)
 */
public final class PredictionBands {

	private static final BigDecimal LEGACY_HIT_MULTIPLIER = BigDecimal.ONE;

	/**
	 * 밴드·배수·응답 순서의 <b>단일 소스</b>. 예전에는 이름→배수, 배수→이름, 순서 목록을 따로 들고
	 * 있었는데 셋이 어긋날 자리가 생긴다 — 하나에서 파생한다.
	 */
	private enum Band {
		WITHIN_5("5"),
		WITHIN_10("3"),
		WITHIN_20("1.5"),
		MISS("0");

		private final BigDecimal multiplier;

		Band(String multiplier) {
			this.multiplier = new BigDecimal(multiplier);
		}
	}

	private PredictionBands() {
	}

	/** 저장된 배수 → 밴드 이름. 알 수 없는 값은 {@code null}이다 — 조용히 MISS로 만들지 않는다 */
	public static String nameOf(BigDecimal multiplier) {
		if (multiplier == null) {
			return null;
		}
		return Arrays.stream(Band.values())
			.filter(band -> band.multiplier.compareTo(multiplier) == 0)
			.findFirst()
			.map(Enum::name)
			.orElse(null);
	}

	/** 구 정책의 HIT 배수 1을 새 정책의 5% 이내 밴드로 읽어 과거 결과를 보존한다. */
	public static String nameOf(String resultStatus, BigDecimal multiplier) {
		if ("HIT".equals(resultStatus)
			&& multiplier != null
			&& multiplier.compareTo(LEGACY_HIT_MULTIPLIER) == 0) {
			return "WITHIN_5";
		}
		return nameOf(multiplier);
	}

	public static String nameOf(int multiplier) {
		return nameOf(BigDecimal.valueOf(multiplier));
	}

	/**
	 * 비어 있는 밴드를 0으로 채워 <b>항상 4줄</b>을 만든다.
	 * PRED-02 「예측 결과 분포 조회」도 4밴드를 항상 내려주므로 두 화면이 같은 모양을 받는다 —
	 * 없는 밴드를 빼면 화면이 "0명"과 "집계 안 됨"을 구분하지 못한다.
	 *
	 * <p>같은 밴드가 두 줄로 오면 <b>합산한다</b>. 집계 쿼리가 {@code result_status}까지 묶은 뒤로는
	 * 이상 데이터(예: {@code HIT}인데 배수 0)에서 두 줄이 나올 수 있는데, 덮어쓰면 인원이 조용히 준다.
	 */
	public static List<PredictionBandRow> fill(List<PredictionBandRow> counted) {
		Map<String, PredictionBandRow> byBand = new LinkedHashMap<>();
		counted.forEach(row -> byBand.merge(row.band(), row, PredictionBands::sum));
		return Arrays.stream(Band.values())
			.map(band -> {
				PredictionBandRow row = byBand.get(band.name());
				return row == null
					? new PredictionBandRow(band.name(), 0L, band.multiplier, 0L)
					: new PredictionBandRow(
						band.name(),
						row.participantCount(),
						band.multiplier,
						row.totalPayoutAmount()
					);
			})
			.toList();
	}

	private static PredictionBandRow sum(PredictionBandRow left, PredictionBandRow right) {
		return new PredictionBandRow(
			left.band(),
			left.participantCount() + right.participantCount(),
			left.multiplier(),
			left.totalPayoutAmount() + right.totalPayoutAmount()
		);
	}
}
