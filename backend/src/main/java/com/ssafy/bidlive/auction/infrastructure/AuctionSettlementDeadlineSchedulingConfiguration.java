package com.ssafy.bidlive.auction.infrastructure;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 경매 마감 정산 작업을 WebSocket heartbeat와 분리된 실행기에서 처리한다. */
@Configuration(proxyBeanMethods = false)
public class AuctionSettlementDeadlineSchedulingConfiguration {

	static final String DEADLINE_EXECUTOR_BEAN =
		"auctionSettlementDeadlineExecutor";

	@Bean(name = DEADLINE_EXECUTOR_BEAN, destroyMethod = "shutdown")
	ScheduledExecutorService auctionSettlementDeadlineExecutor() {
		ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
			2,
			Thread.ofPlatform()
				.name("auction-settlement-deadline-", 0)
				.factory()
		);
		executor.setRemoveOnCancelPolicy(true);
		executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
		executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
		return executor;
	}
}
