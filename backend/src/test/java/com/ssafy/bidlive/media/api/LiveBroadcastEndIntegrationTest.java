package com.ssafy.bidlive.media.api;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.bidlive.common.security.JwtTokenProvider;
import com.ssafy.bidlive.media.application.LiveBroadcastEndRejectedException;
import com.ssafy.bidlive.media.application.LiveBroadcastEndService;
import com.ssafy.bidlive.media.domain.LiveBroadcastEndFailureCode;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.MemberRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 실제 Security 필터 체인에서 LIVE-02가 인증을 요구하고 204와 오류 계약을 반환하는지 검증합니다. */
@SpringBootTest
@AutoConfigureMockMvc
class LiveBroadcastEndIntegrationTest {

	private static final String PATH = "/api/v1/rooms/room-123/live/end";
	private static final Long MEMBER_ID = 25L;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	private LiveBroadcastEndService liveBroadcastEndService;

	@Test
	void endsBroadcastWithoutResponseBody() throws Exception {
		mockMvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, bearerAccessToken()))
			.andExpect(status().isNoContent());

		verify(liveBroadcastEndService).endBroadcast("room-123", MEMBER_ID);
	}

	@Test
	void rejectsRequestWithoutAccessToken() throws Exception {
		mockMvc.perform(post(PATH))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void returnsBadGatewayWhenMediaServerFails() throws Exception {
		doThrow(new LiveBroadcastEndRejectedException(
			LiveBroadcastEndFailureCode.MEDIA_SERVER_ERROR))
			.when(liveBroadcastEndService).endBroadcast("room-123", MEMBER_ID);

		mockMvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, bearerAccessToken()))
			.andExpect(status().isBadGateway())
			.andExpect(jsonPath("$.code").value("MEDIA_SERVER_ERROR"));
	}

	private String bearerAccessToken() {
		Member member = mock(Member.class);
		when(member.getId()).thenReturn(MEMBER_ID);
		when(member.getRole()).thenReturn(MemberRole.USER);
		return "Bearer " + jwtTokenProvider.issue(member).accessToken();
	}
}
