package com.ssafy.bidlive.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** BidLive API 문서의 공통 OpenAPI 메타데이터를 구성한다. */
@Configuration
public class OpenApiConfig {

	@Bean
	OpenAPI bidliveOpenApi() {
		return new OpenAPI()
			.info(new Info()
				.title("BidLive API")
				.description("BidLive 비크리 경매 서비스 API")
				.version("v1")
			);
	}
}
