package com.ssafy.bidlive.prediction.infrastructure;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 일일 예측 리더보드 집계의 Spring 스케줄링을 활성화한다. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class PredictionLeaderboardSchedulingConfiguration {
}
