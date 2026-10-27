package com.ssafy.bidlive.media.infrastructure;

import com.ssafy.bidlive.media.application.ViewerCountService;
import com.ssafy.bidlive.media.application.port.out.ViewerCountPublisher;
import com.ssafy.bidlive.media.application.port.out.ViewerSessionStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 접속 세션 집계 서비스와 정리 스케줄링에 필요한 미디어 Bean을 구성합니다. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(ViewerSessionProperties.class)
public class ViewerSessionConfiguration {

	@Bean
	ViewerCountService viewerCountService(
		ViewerSessionStore sessionStore,
		ViewerCountPublisher publisher,
		ViewerSessionProperties properties
	) {
		return new ViewerCountService(
			sessionStore,
			publisher,
			properties.viewerTtl()
		);
	}
}
