package com.ssafy.bidlive.media.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.stream.Stream;

import com.ssafy.bidlive.media.application.LiveBroadcastEndRejectedException;
import com.ssafy.bidlive.media.application.LiveBroadcastEndService;
import com.ssafy.bidlive.media.domain.LiveBroadcastEndFailureCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** LIVE-02가 성공 시 본문 없는 204를, 실패 시 사유별 상태 코드와 `code`를 반환하는지 검증합니다. */
class LiveBroadcastEndControllerTest {

	private static final String PATH = "/api/v1/rooms/room-123/live/end";

	private final LiveBroadcastEndService liveBroadcastEndService =
		mock(LiveBroadcastEndService.class);
	private final MockMvc mockMvc = MockMvcBuilders
		.standaloneSetup(new LiveBroadcastEndController(liveBroadcastEndService))
		.setControllerAdvice(new LiveBroadcastEndExceptionHandler())
		.build();

	@Test
	void returnsNoContentWithAuthenticatedBroadcaster() throws Exception {
		mockMvc.perform(post(PATH).principal(authenticated("25")))
			.andExpect(status().isNoContent())
			.andExpect(result -> {
				if (!result.getResponse().getContentAsString().isEmpty()) {
					throw new AssertionError("204 응답에는 본문이 없어야 합니다.");
				}
			});

		verify(liveBroadcastEndService).endBroadcast("room-123", 25L);
	}

	@Test
	void rejectsRequestWithoutAuthentication() throws Exception {
		mockMvc.perform(post(PATH))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

		verify(liveBroadcastEndService, never()).endBroadcast(anyString(), anyLong());
	}

	@Test
	void rejectsAuthenticationWithoutNumericMemberId() throws Exception {
		mockMvc.perform(post(PATH).principal(authenticated("not-a-member-id")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

		verify(liveBroadcastEndService, never()).endBroadcast(anyString(), anyLong());
	}

	@ParameterizedTest
	@MethodSource("failureCodeStatuses")
	void mapsFailureCodeToSpecifiedHttpStatus(
		LiveBroadcastEndFailureCode failureCode,
		HttpStatus expectedStatus
	) throws Exception {
		doThrow(new LiveBroadcastEndRejectedException(failureCode))
			.when(liveBroadcastEndService).endBroadcast(any(), any());

		mockMvc.perform(post(PATH).principal(authenticated("25")))
			.andExpect(status().is(expectedStatus.value()))
			.andExpect(jsonPath("$.code").value(failureCode.name()))
			.andExpect(jsonPath("$.message").isNotEmpty());
	}

	private static Stream<Arguments> failureCodeStatuses() {
		return Stream.of(
			Arguments.of(LiveBroadcastEndFailureCode.LIVE_NOT_FOUND, HttpStatus.NOT_FOUND),
			Arguments.of(LiveBroadcastEndFailureCode.NOT_BROADCAST_OWNER, HttpStatus.FORBIDDEN),
			Arguments.of(LiveBroadcastEndFailureCode.LIVE_NOT_ACTIVE, HttpStatus.CONFLICT),
			Arguments.of(LiveBroadcastEndFailureCode.MEDIA_SERVER_ERROR, HttpStatus.BAD_GATEWAY)
		);
	}

	private Authentication authenticated(String name) {
		return UsernamePasswordAuthenticationToken.authenticated(name, null, List.of());
	}
}
