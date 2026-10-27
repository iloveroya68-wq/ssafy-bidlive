package com.ssafy.bidlive.member.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import com.ssafy.bidlive.common.security.JwtTokenProvider;
import com.ssafy.bidlive.member.application.port.out.NicknameProhibitedWordSource;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.infrastructure.persistence.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 실제 JWT 인증과 JPA 조회를 거쳐 닉네임 사용 가능 여부 API 계약을 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NicknameAvailabilityControllerIntegrationTest {

	private static final String PATH =
		"/api/v1/members/nickname-availability";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private MemberRepository memberRepository;

	@MockitoBean
	private NicknameProhibitedWordSource prohibitedWordSource;

	@BeforeEach
	void setUp() {
		when(prohibitedWordSource.getActiveWords()).thenReturn(Set.of());
	}

	@Test
	void returnsAvailableForTrimmedNickname() throws Exception {
		Member member = member(null);

		mockMvc.perform(get(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(member))
			.queryParam("nickname", "  택림25  "))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.nickname").value("택림25"))
			.andExpect(jsonPath("$.available").value(true))
			.andExpect(jsonPath("$.reason").doesNotExist());
	}

	@Test
	void detectsAnotherMembersNicknameIgnoringCase() throws Exception {
		member("HeeWon");
		Member requester = member(null);

		mockMvc.perform(get(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(requester))
			.queryParam("nickname", "heewon"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.nickname").value("heewon"))
			.andExpect(jsonPath("$.available").value(false))
			.andExpect(jsonPath("$.reason").value("ALREADY_EXISTS"));
	}

	@Test
	void allowsCurrentMembersNicknameIgnoringCase() throws Exception {
		Member member = member("HeeWon");

		mockMvc.perform(get(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(member))
			.queryParam("nickname", "HEEWON"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.available").value(true))
			.andExpect(jsonPath("$.reason").doesNotExist());
	}

	@Test
	void returnsInvalidFormatAndProhibitedWordAsAvailabilityReasons() throws Exception {
		Member member = member(null);

		mockMvc.perform(get(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(member))
			.queryParam("nickname", "닉 네임"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.available").value(false))
			.andExpect(jsonPath("$.reason").value("INVALID_FORMAT"));

		when(prohibitedWordSource.getActiveWords()).thenReturn(Set.of("운영자"));
		mockMvc.perform(get(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(member))
			.queryParam("nickname", "최고운영자1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.available").value(false))
			.andExpect(jsonPath("$.reason").value("PROHIBITED_WORD"));
	}

	@Test
	void rejectsMissingOrBlankNickname() throws Exception {
		Member member = member(null);
		String accessToken = bearerAccessToken(member);

		mockMvc.perform(get(PATH)
			.header(HttpHeaders.AUTHORIZATION, accessToken))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("NICKNAME_REQUIRED"));
		mockMvc.perform(get(PATH)
			.header(HttpHeaders.AUTHORIZATION, accessToken)
			.queryParam("nickname", "   "))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("NICKNAME_REQUIRED"));
	}

	@Test
	void rejectsUnauthenticatedRequest() throws Exception {
		mockMvc.perform(get(PATH)
			.queryParam("nickname", "희원"))
			.andExpect(status().isUnauthorized());
	}

	private Member member(String nickname) {
		Member member = Member.createOAuthMember();
		ReflectionTestUtils.setField(member, "nickname", nickname);
		return memberRepository.saveAndFlush(member);
	}

	private String bearerAccessToken(Member member) {
		return "Bearer " + jwtTokenProvider.issue(member).accessToken();
	}
}
