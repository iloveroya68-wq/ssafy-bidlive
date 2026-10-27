package com.ssafy.bidlive.auction.infrastructure;

import java.util.concurrent.Executor;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** 중간 입장 요약 설정과 방송 경로에서 분리된 STT 실행기를 등록한다. */
@Configuration
@EnableConfigurationProperties(CatchUpSummaryProperties.class)
public class CatchUpSummaryConfiguration {

	@Bean(name = "catchUpSttExecutor", destroyMethod = "shutdown")
	public Executor catchUpSttExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(2);
		executor.setQueueCapacity(50);
		executor.setThreadNamePrefix("catch-up-stt-");
		executor.initialize();
		return executor;
	}
}
