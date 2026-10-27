package com.ssafy.bidlive.common.observability;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.bidlive.common.security.JwtTokenProvider;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.MemberRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/** Actuator 상태 공개 범위와 운영 메트릭 ADMIN 접근 정책을 검증합니다. */
@SpringBootTest(properties = {
	"management.endpoints.web.exposure.include=health,metrics,prometheus",
	"management.prometheus.metrics.export.enabled=true"
})
@AutoConfigureMockMvc
class ObservabilityActuatorIntegrationTest {

	private static final long MEMBER_ID = 25L;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Test
	void exposesHealthWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/actuator/health"))
			.andExpect(status().isOk());
	}

	@Test
	void protectsMetricsAndPrometheusWithAdminRole() throws Exception {
		mockMvc.perform(get("/actuator/metrics"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/actuator/metrics")
			.header(HttpHeaders.AUTHORIZATION, bearer(MemberRole.USER)))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/actuator/metrics")
			.header(HttpHeaders.AUTHORIZATION, bearer(MemberRole.ADMIN)))
			.andExpect(status().isOk());
		mockMvc.perform(get("/actuator/prometheus")
			.header(HttpHeaders.AUTHORIZATION, bearer(MemberRole.ADMIN)))
			.andExpect(status().isOk());
	}

	@Test
	void keepsUnapprovedActuatorEndpointsUnexposed() throws Exception {
		mockMvc.perform(get("/actuator/env")
			.header(HttpHeaders.AUTHORIZATION, bearer(MemberRole.ADMIN)))
			.andExpect(status().isForbidden());
	}

	private String bearer(MemberRole role) {
		Member member = mock(Member.class);
		when(member.getId()).thenReturn(MEMBER_ID);
		when(member.getRole()).thenReturn(role);
		return "Bearer " + jwtTokenProvider.issue(member).accessToken();
	}
}
