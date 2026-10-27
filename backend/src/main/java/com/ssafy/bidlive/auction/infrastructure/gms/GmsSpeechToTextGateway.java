package com.ssafy.bidlive.auction.infrastructure.gms;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.bidlive.auction.application.SpeechToTextGateway;
import com.ssafy.bidlive.auction.infrastructure.CatchUpSummaryProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.reactive.function.client.WebClient;

/** OpenAI 호환 GMS의 Whisper 음성 변환 경로를 호출한다. 실패 시 상위 폴백이 RoomEvent만 사용한다. */
@Component
public class GmsSpeechToTextGateway implements SpeechToTextGateway {

	private final WebClient webClient;
	private final ObjectMapper objectMapper;
	private final CatchUpSummaryProperties properties;

	public GmsSpeechToTextGateway(
		@Qualifier("gmsWebClient") WebClient webClient,
		ObjectMapper objectMapper,
		CatchUpSummaryProperties properties
	) {
		this.webClient = webClient;
		this.objectMapper = objectMapper;
		this.properties = properties;
	}

	@Override
	public String transcribe(String contentType, byte[] audio) {
		if (!properties.isSttEnabled()) {
			throw new IllegalStateException("GMS STT is disabled");
		}
		MultipartPayload payload = multipartPayload(contentType, audio);

		String response = webClient.post()
			.uri(properties.getTranscriptionPath())
			.contentType(payload.contentType())
			.contentLength(payload.body().length)
			.bodyValue(payload.body())
			.retrieve()
			.onStatus(
				status -> status.isError(),
				clientResponse -> clientResponse.bodyToMono(String.class)
					.defaultIfEmpty("")
					.map(responseBody -> new IllegalStateException(
						errorMessage(clientResponse.statusCode().value(), responseBody)
					))
			)
			.bodyToMono(String.class)
			.block();
		try {
			JsonNode root = objectMapper.readTree(response);
			String text = root.path("text").asText("").trim();
			if (text.isEmpty()) {
				throw new IllegalStateException("GMS STT response has no text");
			}
			return text;
		} catch (Exception exception) {
			throw new IllegalStateException("GMS STT response is invalid", exception);
		}
	}

	/**
	 * GMS 프록시는 길이를 알 수 없는 chunked multipart를 OpenAI로 전달할 때 400을 반환한다.
	 * 짧은 음성 청크를 완성된 multipart 바이트로 만들어 Content-Length를 확정한다.
	 */
	private MultipartPayload multipartPayload(String contentType, byte[] audio) {
		String boundary = MimeTypeUtils.generateMultipartBoundaryString();
		String filename = "chunk." + extension(contentType);
		try (ByteArrayOutputStream output = new ByteArrayOutputStream(audio.length + 512)) {
			write(output, "--" + boundary + "\r\n");
			write(output, "Content-Disposition: form-data; name=\"file\"; filename=\""
				+ filename + "\"\r\n");
			write(output, "Content-Type: " + contentType + "\r\n\r\n");
			output.write(audio);
			write(output, "\r\n--" + boundary + "\r\n");
			write(output, "Content-Disposition: form-data; name=\"model\"\r\n\r\n");
			write(output, properties.getTranscriptionModel());
			write(output, "\r\n--" + boundary + "--\r\n");
			return new MultipartPayload(
				MediaType.parseMediaType("multipart/form-data; boundary=" + boundary),
				output.toByteArray()
			);
		} catch (IOException exception) {
			throw new IllegalStateException("GMS STT multipart request could not be built", exception);
		}
	}

	private static void write(ByteArrayOutputStream output, String value) throws IOException {
		output.write(value.getBytes(StandardCharsets.UTF_8));
	}

	/** 응답 원문이나 인증정보 대신 GMS가 공개한 오류 코드와 짧은 메시지만 남긴다. */
	private String errorMessage(int status, String responseBody) {
		try {
			JsonNode root = objectMapper.readTree(responseBody);
			JsonNode error = root.path("error");
			String code = firstText(error.path("code"), root.path("code"));
			String message = firstText(
				error.path("message"),
				root.path("message"),
				root.path("detail")
			);
			return "GMS STT request failed: status=%d, code=%s, message=%s".formatted(
				status,
				safe(code, "unknown"),
				safe(message, "unavailable")
			);
		} catch (Exception ignored) {
			return "GMS STT request failed: status=%d, code=unknown, message=unavailable"
				.formatted(status);
		}
	}

	private static String firstText(JsonNode... nodes) {
		for (JsonNode node : nodes) {
			if (node.isTextual() && !node.asText().isBlank()) {
				return node.asText();
			}
		}
		return "";
	}

	private static String safe(String value, String fallback) {
		String normalized = value == null ? "" : value.replaceAll("[\\r\\n\\t]+", " ").trim();
		if (normalized.isEmpty()) {
			return fallback;
		}
		return normalized.length() <= 200 ? normalized : normalized.substring(0, 200);
	}

	private static String extension(String contentType) {
		return switch (contentType) {
			case "audio/ogg" -> "ogg";
			case "audio/wav", "audio/x-wav" -> "wav";
			default -> "webm";
		};
	}

	private record MultipartPayload(MediaType contentType, byte[] body) {
	}
}
