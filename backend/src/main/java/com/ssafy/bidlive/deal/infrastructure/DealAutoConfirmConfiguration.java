package com.ssafy.bidlive.deal.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 자동 확정 배치의 스케줄링과 설정을 구성한다.
 *
 * <p>{@code @EnableScheduling}을 도메인 인프라에 두는 것이 이 레포 방식이다
 * ({@code ViewerSessionConfiguration}·{@code ChatModerationConfiguration}이 같다). 여러 번 켜도
 * 스프링이 스케줄러를 한 번만 세운다 — 그래서 `common/**`을 건드리지 않아도 된다.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(DealAutoConfirmProperties.class)
public class DealAutoConfirmConfiguration {
}
