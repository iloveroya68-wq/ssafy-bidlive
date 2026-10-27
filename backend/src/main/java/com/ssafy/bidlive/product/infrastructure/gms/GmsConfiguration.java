package com.ssafy.bidlive.product.infrastructure.gms;

import java.time.Clock;
import java.time.ZoneId;
import java.util.concurrent.Executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.bidlive.product.application.ProductInspectionService;
import com.ssafy.bidlive.product.application.port.out.ContentModerationGateway;
import com.ssafy.bidlive.product.domain.InspectionPolicy;
import com.ssafy.bidlive.product.domain.ProductInspectionRepository;
import io.netty.channel.ChannelOption;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/** GMS 검열 호출에 필요한 WebClient, 병렬 실행기, 판정 정책과 유스케이스를 등록합니다. */
@Configuration
@EnableConfigurationProperties({GmsProperties.class, InspectionPolicyProperties.class})
public class GmsConfiguration {

	/** 검열 시각도 다른 API와 같은 서비스 기준 오프셋을 사용한다. */
	private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

	/**
	 * baseUrl은 GMS 엔드포인트, Authorization은 팀 GMS Key로 고정한다. OpenAI 호환 게이트웨이라
	 * 경로와 본문 형식은 그대로 쓰고 접속 정보만 바꾸는 구조다.
	 */
	@Bean
	public WebClient gmsWebClient(GmsProperties gmsProperties) {
		HttpClient httpClient = HttpClient.create()
			.responseTimeout(gmsProperties.getTimeout())
			.option(
				ChannelOption.CONNECT_TIMEOUT_MILLIS,
				(int) gmsProperties.getTimeout().toMillis()
			);

		return WebClient.builder()
			.baseUrl(gmsProperties.getBaseUrl())
			.defaultHeader(HttpHeaders.AUTHORIZATION, gmsProperties.bearerAuthorization())
			.clientConnector(new ReactorClientHttpConnector(httpClient))
			.build();
	}

	@Bean
	public ContentModerationGateway contentModerationGateway(
		WebClient gmsWebClient,
		ObjectMapper objectMapper,
		GmsProperties gmsProperties
	) {
		return new GmsContentModerationGateway(gmsWebClient, objectMapper, gmsProperties);
	}

	/**
	 * 풀 크기와 큐 없는 설정으로 동시 실행 상한을 만든다. 상한을 넘는 작업은 호출 스레드에서
	 * 실행되어 결과를 버리지 않으면서도 GMS 동시 호출 수는 설정값을 넘지 않는다.
	 */
	@Bean(destroyMethod = "shutdown")
	public Executor imageInspectionExecutor(GmsProperties gmsProperties) {
		int concurrency = gmsProperties.getMaxConcurrentImageInspections();

		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(concurrency);
		executor.setMaxPoolSize(concurrency);
		executor.setQueueCapacity(0);
		executor.setThreadNamePrefix("ai-image-inspect-");
		executor.setRejectedExecutionHandler(
			new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
		executor.initialize();
		return executor;
	}

	@Bean
	public InspectionPolicy inspectionPolicy(InspectionPolicyProperties properties) {
		return new InspectionPolicy(
			properties.getRejectThreshold(),
			properties.getReviewThreshold()
		);
	}

	@Bean
	public ProductInspectionService productInspectionService(
		ContentModerationGateway contentModerationGateway,
		ProductInspectionRepository productInspectionRepository,
		InspectionPolicy inspectionPolicy,
		Executor imageInspectionExecutor,
		GmsProperties gmsProperties
	) {
		return new ProductInspectionService(
			contentModerationGateway,
			productInspectionRepository,
			inspectionPolicy,
			imageInspectionExecutor,
			gmsProperties.getMaxImageCount(),
			Clock.system(SERVICE_ZONE)
		);
	}
}
