package com.ssafy.bidlive.common.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** 개인 WebSocket 오류 메시지의 형식과 공개 필드 검증 규칙을 확인합니다. */
class UserWebSocketErrorMessageTest {

	private static final String REF_DESTINATION = "/topic/auctions/100/questions";
	private static final String CORRELATION_ID = "receipt-123";

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void createsErrorMessageFromErrorCode() {
		UserWebSocketErrorMessage result = UserWebSocketErrorMessage.error(
			TestWebSocketErrorCode.SUBSCRIPTION_DENIED,
			REF_DESTINATION,
			CORRELATION_ID
		);

		assertThat(result.type()).isEqualTo("ERROR");
		assertThat(result.data().code()).isEqualTo("SUBSCRIPTION_DENIED");
		assertThat(result.data().message())
			.isEqualTo("해당 채널을 구독할 권한이 없습니다.");
		assertThat(result.data().refDestination()).isEqualTo(REF_DESTINATION);
		assertThat(result.data().correlationId()).isEqualTo(CORRELATION_ID);
	}

	@Test
	void serializesCommonErrorEnvelope() throws Exception {
		UserWebSocketErrorMessage message = UserWebSocketErrorMessage.error(
			TestWebSocketErrorCode.SUBSCRIPTION_DENIED,
			REF_DESTINATION,
			CORRELATION_ID
		);

		JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(message));

		assertThat(json.size()).isEqualTo(2);
		assertThat(json.get("type").asText()).isEqualTo("ERROR");

		JsonNode data = json.get("data");

		assertThat(data.size()).isEqualTo(4);
		assertThat(data.get("code").asText()).isEqualTo("SUBSCRIPTION_DENIED");
		assertThat(data.get("message").asText())
			.isEqualTo("해당 채널을 구독할 권한이 없습니다.");
		assertThat(data.get("refDestination").asText()).isEqualTo(REF_DESTINATION);
		assertThat(data.get("correlationId").asText()).isEqualTo(CORRELATION_ID);
	}

	@Test
	void allowsMissingRequestReferenceForInternalError() {
		UserWebSocketErrorMessage result = UserWebSocketErrorMessage.error(
			TestWebSocketErrorCode.INTERNAL_ERROR,
			null,
			null
		);

		assertThat(result.data().refDestination()).isNull();
		assertThat(result.data().correlationId()).isNull();
	}

	@Test
	void rejectsUnsupportedMessageType() {
		UserWebSocketErrorData data = UserWebSocketErrorData.from(
			TestWebSocketErrorCode.INTERNAL_ERROR,
			null,
			null
		);

		assertThatThrownBy(() -> new UserWebSocketErrorMessage("CHAT_MESSAGE", data))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("type은 ERROR여야 합니다.");
	}

	@Test
	void rejectsNullErrorCode() {
		assertThatThrownBy(() -> UserWebSocketErrorMessage.error(
			null,
			REF_DESTINATION,
			CORRELATION_ID
		))
			.isInstanceOf(NullPointerException.class)
			.hasMessage("errorCode");
	}

	@Test
	void rejectsBlankCode() {
		assertThatThrownBy(() -> new UserWebSocketErrorData(
			" ",
			"안전한 오류 메시지",
			REF_DESTINATION,
			CORRELATION_ID
		))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("code은 비어 있을 수 없습니다.");
	}

	@Test
	void rejectsBlankMessage() {
		assertThatThrownBy(() -> new UserWebSocketErrorData(
			"INTERNAL_ERROR",
			" ",
			REF_DESTINATION,
			CORRELATION_ID
		))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("message은 비어 있을 수 없습니다.");
	}

	@Test
	void rejectsBlankReferenceDestination() {
		assertThatThrownBy(() -> new UserWebSocketErrorData(
			"INTERNAL_ERROR",
			"요청 처리에 실패했습니다.",
			" ",
			CORRELATION_ID
		))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("refDestination은 빈 문자열일 수 없습니다.");
	}

	@Test
	void rejectsBlankCorrelationId() {
		assertThatThrownBy(() -> new UserWebSocketErrorData(
			"INTERNAL_ERROR",
			"요청 처리에 실패했습니다.",
			REF_DESTINATION,
			" "
		))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("correlationId은 빈 문자열일 수 없습니다.");
	}

	private enum TestWebSocketErrorCode implements WebSocketErrorCode {

		SUBSCRIPTION_DENIED("해당 채널을 구독할 권한이 없습니다."),
		INTERNAL_ERROR("요청 처리에 실패했습니다.");

		private final String publicMessage;

		TestWebSocketErrorCode(String publicMessage) {
			this.publicMessage = publicMessage;
		}

		@Override
		public String code() {
			return name();
		}

		@Override
		public String publicMessage() {
			return publicMessage;
		}
	}
}
