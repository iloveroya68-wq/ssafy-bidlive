package com.ssafy.bidlive.deal.infrastructure;

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.ssafy.bidlive.deal.application.DealAutoConfirmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 자동 확정 대상을 주기적으로 훑는다(DEAL-05 INTERNAL).
 *
 * <p>반복은 여기서 돌고 트랜잭션은 건별로 서비스가 잡는다 — 이 컴포넌트가 직접 잡으면 자기 호출이라
 * 프록시를 거치지 않아 {@code @Transactional}이 걸리지 않고, 한 건 실패가 전체를 되돌린다.
 *
 * <p>한 건이 터져도 배치는 계속 간다. 자동 확정은 사용자가 기다리는 요청이 아니라 밀린 일을 치우는
 * 처리여서, 한 거래의 문제로 나머지 지급을 멈출 이유가 없다. 실패는 다음 주기가 다시 집는다.
 */
@Component
public class DealAutoConfirmScheduler {

	private static final Logger log = LoggerFactory.getLogger(DealAutoConfirmScheduler.class);

	private final DealAutoConfirmService autoConfirmService;
	private final DealAutoConfirmProperties properties;

	public DealAutoConfirmScheduler(
		DealAutoConfirmService autoConfirmService,
		DealAutoConfirmProperties properties
	) {
		this.autoConfirmService = autoConfirmService;
		this.properties = properties;
	}

	@Scheduled(
		fixedDelayString = "${app.deal.auto-confirm.interval-seconds:"
			+ DealAutoConfirmProperties.DEFAULT_INTERVAL_SECONDS
			+ "}",
		timeUnit = TimeUnit.SECONDS
	)
	void confirmDueDeals() {
		List<Long> dueDealIds = autoConfirmService.findDueDealIds(properties.batchSize());
		if (dueDealIds.isEmpty()) {
			return;
		}

		int confirmed = 0;
		int failed = 0;
		for (Long dealId : dueDealIds) {
			try {
				if (autoConfirmService.confirmDueDeal(dealId)) {
					confirmed++;
				}
			} catch (RuntimeException exception) {
				failed++;
				// dealId는 남긴다 — 어느 거래가 막혀 있는지 모르면 다음 주기도 같은 곳에서 막힌다
				log.warn(
					"Deal auto-confirmation failed: dealId={}, failureType={}",
					dealId,
					exception.getClass().getSimpleName()
				);
			}
		}
		log.info(
			"Deal auto-confirmation finished: due={}, confirmed={}, failed={}",
			dueDealIds.size(),
			confirmed,
			failed
		);
	}
}
