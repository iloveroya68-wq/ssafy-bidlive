package com.ssafy.bidlive.deal.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 자동 확정 배치 실행 설정이다.
 *
 * <p>명세는 주기를 정하지 않았다 — 7일 경계라 분 단위 정밀도가 필요 없다. 기본 5분은 기존
 * 스케줄러들과 같은 감각으로 두었고, 늦게 확정돼도 손해는 판매자 지급이 조금 미뤄지는 것뿐이다.
 *
 * <p>{@code batchSize}는 한 번에 훑는 상한이다. 남은 건은 다음 주기가 가져간다 — 조회를 무한정
 * 열어 두면 한 번의 실행이 길어지고 그만큼 실패 폭도 커진다.
 */
@ConfigurationProperties(prefix = "app.deal.auto-confirm")
public record DealAutoConfirmProperties(
	Integer intervalSeconds,
	Integer batchSize
) {

	static final int DEFAULT_INTERVAL_SECONDS = 300;
	private static final int DEFAULT_BATCH_SIZE = 100;

	public DealAutoConfirmProperties {
		intervalSeconds = intervalSeconds == null ? DEFAULT_INTERVAL_SECONDS : intervalSeconds;
		batchSize = batchSize == null ? DEFAULT_BATCH_SIZE : batchSize;
		if (intervalSeconds <= 0) {
			throw new IllegalArgumentException("자동 확정 실행 주기는 양수여야 합니다.");
		}
		if (batchSize <= 0) {
			throw new IllegalArgumentException("자동 확정 배치 크기는 양수여야 합니다.");
		}
	}
}
