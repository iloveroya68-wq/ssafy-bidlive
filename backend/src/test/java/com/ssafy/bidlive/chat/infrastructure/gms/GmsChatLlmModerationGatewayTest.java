package com.ssafy.bidlive.chat.infrastructure.gms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.bidlive.chat.application.ChatLlmFailureCode;
import com.ssafy.bidlive.chat.application.ChatLlmModerationException;
import com.ssafy.bidlive.chat.application.ChatModerationDecision;
import com.ssafy.bidlive.chat.application.ChatModerationJudgement;
import com.ssafy.bidlive.chat.application.ChatModerationJob;
import com.ssafy.bidlive.chat.application.ChatModerationReason;
import com.ssafy.bidlive.chat.domain.ChatMessageId;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

/** GMS 채팅 검열 응답의 정상 판정과 비공개 오류 변환을 검증합니다. */
class GmsChatLlmModerationGatewayTest {

	private final GmsChatLlmModerationGateway gateway =
		new GmsChatLlmModerationGateway(
			WebClient.builder().baseUrl("http://localhost").build(),
			new ObjectMapper(),
			"test-model",
			Duration.ofSeconds(1)
		);

	@Test
	void parsesValidJudgement() {
		String response = "{\"choices\":[{\"message\":{\"content\":"
			+ "\"{\\\"decision\\\":\\\"BLOCKED\\\",\\\"reason\\\":"
			+ "\\\"OFF_PLATFORM_TRADE\\\"}\"}}]}";

		assertThat(gateway.parse(response)).isEqualTo(
			new ChatModerationJudgement(
				ChatModerationDecision.BLOCKED,
				ChatModerationReason.OFF_PLATFORM_TRADE
			)
		);
	}

	@Test
	void rejectsInvalidSchemaWithoutLeakingBody() {
		assertThatThrownBy(() -> gateway.parse("{\"unexpected\":true}"))
			.isInstanceOf(ChatLlmModerationException.class)
			.satisfies(exception -> assertThat(
				((ChatLlmModerationException) exception).failureCode()
			).isEqualTo(ChatLlmFailureCode.LLM_RESPONSE_INVALID))
			.hasMessage("LLM_RESPONSE_INVALID");
	}

	@Test
	void includesAuctionRoomContextForQuestionModeration() {
		ChatModerationJob job = new ChatModerationJob(
			42L,
			new ChatMessageId("01J8W3P2N9KQ4T123456789ABC"),
			7L,
			"이거 스위치 교체됐나요?",
			true,
			"키보드 경매",
			"스위치 윤활 상품입니다.",
			"ELECTRONICS",
			Instant.parse("2026-08-02T01:00:00Z")
		);

		assertThat(gateway.buildInput(job))
			.containsEntry("roomTitle", "키보드 경매")
			.containsEntry("productDescription", "스위치 윤활 상품입니다.")
			.containsEntry("productCategoryCode", "ELECTRONICS");
	}

	@Test
	void keepsOrdinaryChatModerationLenient() {
		assertThat(GmsChatLlmModerationGateway.SYSTEM_PROMPT)
			.contains("For question=false (ordinary chat), use only ALLOW or BLOCKED")
			.contains("Use BLOCKED with reason TOXIC_CONTENT")
			.contains("Use BLOCKED with reason PROHIBITED_GOODS")
			.contains("Use BLOCKED with reason OFF_PLATFORM_TRADE")
			.contains("Use BLOCKED with reason AUCTION_MANIPULATION")
			.contains("Use BLOCKED with reason ADVERTISING_SPAM")
			.contains("OFF_TOPIC_QUESTION")
			.contains("If the evidence is")
			.contains("ambiguous or incomplete, use ALLOW");
	}

	@Test
	void filtersClearlyOffTopicQuestions() {
		assertThat(GmsChatLlmModerationGateway.SYSTEM_PROMPT)
			.contains("use ALLOW only when the question is substantively related")
			.contains("QUESTION_FILTERED with reason")
			.contains("OFF_TOPIC_QUESTION")
			.contains("weather, politics, general trivia, personal advice")
			.contains("오늘 저녁에 배그 어떤가요?")
			.contains("relationship must be explicit in the message itself")
			.contains("Do not infer relevance");
	}
}
