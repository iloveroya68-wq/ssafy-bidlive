package com.ssafy.bidlive.chat.infrastructure;

import java.time.Clock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.bidlive.chat.application.ChatLlmModerationGateway;
import com.ssafy.bidlive.chat.application.ChatModerationJobQueue;
import com.ssafy.bidlive.chat.application.ChatModerationOutcomeHandler;
import com.ssafy.bidlive.chat.application.ChatModerationWorker;
import com.ssafy.bidlive.chat.infrastructure.gms.GmsChatLlmModerationGateway;
import com.ssafy.bidlive.common.observability.RealtimeObservationMetrics;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

/** 활성화된 환경에서만 채팅 LLM gateway와 worker를 조립합니다. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
	name = "chat.moderation.enabled",
	havingValue = "true"
)
public class ChatAsyncModerationConfiguration {

	@Bean
	ChatLlmModerationGateway chatLlmModerationGateway(
		ObjectMapper objectMapper,
		ChatAsyncModerationProperties properties
	) {
		properties.validateEnabled();
		WebClient webClient = WebClient.builder()
			.baseUrl(properties.getBaseUrl())
			.defaultHeader(
				HttpHeaders.AUTHORIZATION,
				"Bearer " + properties.getApiKey()
			)
			.build();
		return new GmsChatLlmModerationGateway(
			webClient,
			objectMapper,
			properties.getModel(),
			properties.getTimeout()
		);
	}

	@Bean
	ChatModerationWorker chatModerationWorker(
		ChatModerationJobQueue jobQueue,
		ChatLlmModerationGateway gateway,
		ChatModerationOutcomeHandler outcomeHandler,
		RealtimeObservationMetrics metrics,
		ChatAsyncModerationProperties properties
	) {
		return new ChatModerationWorker(
			jobQueue,
			gateway,
			outcomeHandler,
			metrics,
			Clock.systemUTC(),
			properties.getMaxAttempts(),
			properties.getBaseBackoff(),
			properties.getLease()
		);
	}
}
