package com.ssafy.bidlive.media.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;

import com.ssafy.bidlive.common.security.JwtTokenProvider;
import com.ssafy.bidlive.media.application.LiveSessionService;
import com.ssafy.bidlive.media.application.LiveTokenRejectedException;
import com.ssafy.bidlive.media.domain.LiveSessionRole;
import com.ssafy.bidlive.media.domain.LiveSessionToken;
import com.ssafy.bidlive.media.domain.LiveTokenFailureCode;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.MemberRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 라이브 토큰 발급 응답이 CSRF 토큰 없이도 공통 type·data 형식으로 반환되고,
 * 송출·시청 토큰 모두 인증 회원만 발급받으며 방 접근 거절이 명세 상태 코드로 나가는지 검증합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LiveTokenIntegrationTest {

	private static final Long MEMBER_ID = 25L;
	private static final OffsetDateTime CREATED_AT =
		OffsetDateTime.parse("2026-07-20T21:05:00+09:00");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	private LiveSessionService liveSessionService;

	@Test
	void returnsPublisherTokenIssuedEvent() throws Exception {
		when(liveSessionService.issuePublisherToken("room-123", MEMBER_ID))
			.thenReturn(new LiveSessionToken(
				"room-123",
				"room-123",
				"wss://openvidu.test?sessionId=room-123&token=tok_pub",
				LiveSessionRole.PUBLISHER,
				CREATED_AT
			));

		mockMvc.perform(post("/api/v1/rooms/room-123/live/publisher")
				.header(HttpHeaders.AUTHORIZATION, bearerAccessToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.type").value("LIVE_PUBLISHER_TOKEN_ISSUED"))
			.andExpect(jsonPath("$.data.roomId").value("room-123"))
			.andExpect(jsonPath("$.data.sessionId").value("room-123"))
			.andExpect(jsonPath("$.data.token")
				.value("wss://openvidu.test?sessionId=room-123&token=tok_pub"))
			.andExpect(jsonPath("$.data.role").value("PUBLISHER"))
			.andExpect(jsonPath("$.data.createdAt").value("2026-07-20T21:05:00+09:00"));
	}

	@Test
	void rejectsUnauthenticatedPublisherTokenRequest() throws Exception {
		mockMvc.perform(post("/api/v1/rooms/room-123/live/publisher"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void returnsSubscriberTokenIssuedEvent() throws Exception {
		when(liveSessionService.issueSubscriberToken("room-123", MEMBER_ID))
			.thenReturn(new LiveSessionToken(
				"room-123",
				"room-123",
				"wss://openvidu.test?sessionId=room-123&token=tok_sub",
				LiveSessionRole.SUBSCRIBER,
				CREATED_AT
			));

		mockMvc.perform(post("/api/v1/rooms/room-123/live/subscriber")
				.header(HttpHeaders.AUTHORIZATION, bearerAccessToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.type").value("LIVE_SUBSCRIBER_TOKEN_ISSUED"))
			.andExpect(jsonPath("$.data.roomId").value("room-123"))
			.andExpect(jsonPath("$.data.role").value("SUBSCRIBER"))
			.andExpect(jsonPath("$.data.createdAt").value("2026-07-20T21:05:00+09:00"));
	}

	/** 시청 토큰이 필터 체인에서 막히지 않으면 익명 요청이 비공개 방 영상까지 받아 간다. */
	@Test
	void rejectsUnauthenticatedSubscriberTokenRequest() throws Exception {
		mockMvc.perform(post("/api/v1/rooms/room-123/live/subscriber"))
			.andExpect(status().isUnauthorized());

		verifyNoInteractions(liveSessionService);
	}

	@Test
	void mapsNotSellerRejectionToForbidden() throws Exception {
		when(liveSessionService.issuePublisherToken("room-123", MEMBER_ID))
			.thenThrow(new LiveTokenRejectedException(LiveTokenFailureCode.NOT_AUCTION_SELLER));

		mockMvc.perform(post("/api/v1/rooms/room-123/live/publisher")
				.header(HttpHeaders.AUTHORIZATION, bearerAccessToken()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("NOT_AUCTION_SELLER"));
	}

	@Test
	void mapsHiddenRoomRejectionToNotFound() throws Exception {
		when(liveSessionService.issueSubscriberToken("room-123", MEMBER_ID))
			.thenThrow(new LiveTokenRejectedException(LiveTokenFailureCode.ROOM_NOT_FOUND));

		mockMvc.perform(post("/api/v1/rooms/room-123/live/subscriber")
				.header(HttpHeaders.AUTHORIZATION, bearerAccessToken()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
	}

	private String bearerAccessToken() {
		Member member = mock(Member.class);
		when(member.getId()).thenReturn(MEMBER_ID);
		when(member.getRole()).thenReturn(MemberRole.USER);
		return "Bearer " + jwtTokenProvider.issue(member).accessToken();
	}
}
