package com.ssafy.bidlive.auction.infrastructure.gms;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.bidlive.auction.application.CatchUpAiSummaryGateway;
import com.ssafy.bidlive.auction.application.CatchUpTranscriptSegment;
import com.ssafy.bidlive.auction.infrastructure.CatchUpSummaryProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/** GMS Chat Completions로 판매자 발화를 사실 기반 상품 설명 불릿으로 압축한다. */
@Component
public class GmsCatchUpAiSummaryGateway implements CatchUpAiSummaryGateway {

	private static final int MAX_SEGMENTS = 100;
	private static final int MAX_INPUT_CHARACTERS = 12_000;
	private static final int MAX_MENTIONS = 5;
	private static final String SYSTEM_PROMPT = """
		You summarize a Korean live-auction seller transcript for viewers joining late.
		Return JSON only: {"sellerMentions":["..."]}.
		Use only facts explicitly stated in the transcript. Never infer or invent.
		Keep at most 5 concise Korean bullet sentences about product condition, usage,
		components, defects, shipping, or seller demonstrations. Exclude personal data,
		bid prices, ranks, bidder identity, greetings, filler, and repeated statements.
		""";

	private final WebClient webClient;
	private final ObjectMapper objectMapper;
	private final CatchUpSummaryProperties properties;

	public GmsCatchUpAiSummaryGateway(
		@Qualifier("gmsWebClient") WebClient webClient,
		ObjectMapper objectMapper,
		CatchUpSummaryProperties properties
	) {
		this.webClient = webClient;
		this.objectMapper = objectMapper;
		this.properties = properties;
	}

	@Override
	public List<String> summarize(List<CatchUpTranscriptSegment> transcripts) {
		if (!properties.isAiSummaryEnabled()) {
			throw new IllegalStateException("catch-up AI summary is disabled");
		}
		String input = compact(transcripts);
		Map<String, Object> body = Map.of(
			"model", properties.getSummaryModel(),
			"temperature", 0,
			"messages", List.of(
				Map.of("role", "system", "content", SYSTEM_PROMPT),
				Map.of("role", "user", "content", input)
			)
		);
		String response = webClient.post()
			.uri("/chat/completions")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(body)
			.retrieve()
			.bodyToMono(String.class)
			.block();
		return parse(response);
	}

	private List<String> parse(String response) {
		try {
			JsonNode content = objectMapper.readTree(response)
				.path("choices").path(0).path("message").path("content");
			if (!content.isTextual()) {
				throw new IllegalStateException("missing content");
			}
			String json = stripFence(content.asText());
			JsonNode mentions = objectMapper.readTree(json).path("sellerMentions");
			if (!mentions.isArray()) {
				throw new IllegalStateException("missing sellerMentions");
			}
			List<String> result = new ArrayList<>();
			for (JsonNode mention : mentions) {
				String text = mention.asText("").trim();
				if (!text.isEmpty()) {
					result.add(text.length() > 160 ? text.substring(0, 160) : text);
				}
				if (result.size() == MAX_MENTIONS) {
					break;
				}
			}
			return List.copyOf(result);
		} catch (Exception exception) {
			throw new IllegalStateException("GMS catch-up summary response is invalid", exception);
		}
	}

	private static String compact(List<CatchUpTranscriptSegment> transcripts) {
		int from = Math.max(0, transcripts.size() - MAX_SEGMENTS);
		StringBuilder builder = new StringBuilder();
		for (int index = from; index < transcripts.size(); index++) {
			String line = transcripts.get(index).text().replaceAll("\\s+", " ").trim();
			if (builder.length() + line.length() + 1 > MAX_INPUT_CHARACTERS) {
				break;
			}
			builder.append(line).append('\n');
		}
		return builder.toString();
	}

	private static String stripFence(String value) {
		String trimmed = value.trim();
		if (!trimmed.startsWith("```")) {
			return trimmed;
		}
		int firstLineEnd = trimmed.indexOf('\n');
		int closing = trimmed.lastIndexOf("```");
		return firstLineEnd >= 0 && closing > firstLineEnd
			? trimmed.substring(firstLineEnd + 1, closing).trim()
			: trimmed;
	}
}
