package com.ssafy.bidlive.common.config;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** OpenAPI JSON과 Swagger UI 엔드포인트가 공통 설정으로 노출되는지 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiConfigTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void servesOpenApiDocumentWithCommonMetadata() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
			.andExpect(jsonPath("$.openapi", startsWith("3.")))
			.andExpect(jsonPath("$.info.title").value("BidLive API"))
			.andExpect(jsonPath("$.info.description").value("BidLive 비크리 경매 서비스 API"))
			.andExpect(jsonPath("$.info.version").value("v1"));
	}

	@Test
	void redirectsSwaggerUiEntryPoint() throws Exception {
		mockMvc.perform(get("/swagger-ui.html"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().exists("Location"))
			.andExpect(redirectedUrl("/swagger-ui/index.html"));
	}
}
