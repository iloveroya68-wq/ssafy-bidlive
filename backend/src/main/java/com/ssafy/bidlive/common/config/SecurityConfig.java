package com.ssafy.bidlive.common.config;

import com.ssafy.bidlive.auth.infrastructure.oauth2.KakaoOAuth2AuthenticationFailureHandler;
import com.ssafy.bidlive.auth.infrastructure.oauth2.KakaoOAuth2AuthenticationSuccessHandler;
import com.ssafy.bidlive.auth.infrastructure.oauth2.OAuth2CallbackProperties;
import com.ssafy.bidlive.common.security.JwtAuthenticationFilter;
import com.ssafy.bidlive.common.security.JwtProperties;
import java.net.URI;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** OAuth2 로그인과 API별 JWT 인증·CORS·CSRF 정책을 구성한다. */
@Configuration
@EnableConfigurationProperties({
	OAuth2CallbackProperties.class,
	JwtProperties.class
})
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(
		HttpSecurity http,
		KakaoOAuth2AuthenticationSuccessHandler successHandler,
		KakaoOAuth2AuthenticationFailureHandler failureHandler,
		JwtAuthenticationFilter jwtAuthenticationFilter
	) throws Exception {
		http
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers("/actuator/health").permitAll()
				.requestMatchers(
					"/actuator/metrics",
					"/actuator/metrics/**",
					"/actuator/prometheus"
				).hasRole("ADMIN")
				.requestMatchers("/actuator/**").denyAll()
				.requestMatchers(
					"/oauth2/authorization/kakao",
					"/login/oauth2/code/kakao",
					"/api/v1/auth/oauth2/token"
				).permitAll()
				.requestMatchers(HttpMethod.GET, "/api/v1/terms").authenticated()
				.requestMatchers(
					HttpMethod.GET,
					"/api/v1/members/nickname-availability"
				).authenticated()
				.requestMatchers(
					HttpMethod.POST,
					"/api/v1/members/me/terms-agreements"
				).authenticated()
				.requestMatchers(
					HttpMethod.GET,
					"/api/v1/members/me"
				).authenticated()
				.requestMatchers(
					HttpMethod.GET,
					"/api/v1/members/me/settlement-account"
				).authenticated()
				.requestMatchers(
					HttpMethod.GET,
					"/api/v1/members/me/wallets"
				).authenticated()
				.requestMatchers(
					HttpMethod.GET,
					"/api/v1/members/me/point-ledgers"
				).authenticated()
				.requestMatchers(
					HttpMethod.PUT,
					"/api/v1/members/me/settlement-account"
				).authenticated()
				.requestMatchers(
					HttpMethod.POST,
					"/api/v1/members/me/wallets/trade/charge"
				).authenticated()
				.requestMatchers(
					HttpMethod.POST,
					"/api/v1/members/me/wallets/trade/refunds"
				).authenticated()
				.requestMatchers(
					HttpMethod.PATCH,
					"/api/v1/members/me"
				).authenticated()
				.requestMatchers(
					HttpMethod.POST,
					"/api/v1/product-images"
				).authenticated()
				.requestMatchers(
					HttpMethod.POST,
					"/api/v1/auction-rooms"
				).authenticated()
				.requestMatchers(
					HttpMethod.POST,
					"/api/v1/auctions/*/bids",
					"/api/v1/auctions/*/extensions",
					// ROOM-09 판매자 종료 (S15P11A704-687)
					"/api/v1/auctions/*/end"
				).authenticated()
				.requestMatchers(
					HttpMethod.POST,
					"/api/v1/auctions/*/chat/pin"
				).authenticated()
				.requestMatchers(
					HttpMethod.GET,
					"/api/v1/auctions/*/chats"
				).authenticated()
				.requestMatchers(
					HttpMethod.DELETE,
					"/api/v1/auctions/*/chat/pin",
					"/api/v1/auctions/*/chats/*"
				).authenticated()
				.requestMatchers(
					HttpMethod.GET,
					"/api/v1/auctions/*/questions"
				).authenticated()
				.requestMatchers(
					HttpMethod.DELETE,
					"/api/v1/auctions/*/questions/*"
				).authenticated()
				.requestMatchers(
					HttpMethod.POST,
					"/api/v1/auctions/*/predictions"
				).authenticated()
				.requestMatchers(
					"/api/v1/predictions/leaderboards/daily"
				).authenticated()
				.requestMatchers(
					HttpMethod.POST,
					"/api/v1/rooms/*/live/publisher",
					// LIVE-01 시청 토큰. 빠뜨리면 `anyRequest().permitAll()`로 새어
					// **인증 없이** 비공개 방의 OpenVidu 토큰까지 발급된다.
					"/api/v1/rooms/*/live/subscriber",
					"/api/v1/rooms/*/live/end",
					"/api/v1/rooms/*/live/snapshot"
				).authenticated()
				.requestMatchers(HttpMethod.POST, "/api/v1/ai/products/inspect").authenticated()
				.requestMatchers(
					HttpMethod.DELETE,
					"/api/v1/auction-rooms/*"
				).authenticated()
				.requestMatchers(
					HttpMethod.PATCH,
					"/api/v1/auction-rooms/*"
				).authenticated()
				.requestMatchers(
					HttpMethod.POST,
					"/api/v1/auction-rooms/*/start",
					"/api/v1/auction-rooms/*/join",
					"/api/v1/auction-rooms/*/stt/chunks"
				).authenticated()
				.requestMatchers(
					HttpMethod.GET,
					"/api/v1/auction-rooms/*/catch-up-summary"
				).authenticated()
				.requestMatchers(
					HttpMethod.GET,
					// ⚠️ 하위 경로는 별개로 등록해야 한다. `/deals`만 적으면 `/deals/todos`가 안 덮이고,
					// 반대도 마찬가지다 — 빠뜨리면 `anyRequest().permitAll()`로 새어 공개 조회가 된다.
					"/api/v1/members/me/deals",
					"/api/v1/members/me/deals/todos",
					// DEAL-05 배송 정보 조회 (S15P11A704-694). 당사자 판정은 조회 조건이 하지만,
					// 여기에 없으면 `anyRequest().permitAll()`로 새어 **인증 없이** 열린다.
					"/api/v1/deals/*/shipping"
				).authenticated()
				.requestMatchers(
					HttpMethod.PATCH,
					"/api/v1/deals/*/shipping-address"
				).authenticated()
				.requestMatchers(
					HttpMethod.POST,
					"/api/v1/deals/*/shipment-confirmation",
					"/api/v1/deals/*/receipt-confirmation"
				).authenticated()
				.requestMatchers(
					HttpMethod.POST,
					"/api/v1/auctions/*/reports"
				).authenticated()
				// DEAL-09 운영 강제 종료. 이 레포에서 유일하게 역할까지 보는 경로다 —
				// `JwtAuthenticationFilter`가 토큰의 role을 `ROLE_ADMIN` 권한으로 심어 둔다.
				.requestMatchers(
					HttpMethod.POST,
					"/api/v1/admin/auctions/*/force-end"
				).hasRole("ADMIN")
				// ADMIN-01 운영자 신고 목록. 강제 종료와 같은 게이트다.
				// CSRF 예외에는 넣지 않는다 — GET은 CsrfFilter가 검사하지 않는다.
				// ⚠️ 메서드를 지정하지 **않는다.** `HttpMethod.GET`으로 좁히면 HEAD가
				// `anyRequest().permitAll()`로 새어 인증 없이 집계 쿼리가 돈다.
				.requestMatchers(
					"/api/v1/admin/reports"
				).hasRole("ADMIN")
				.anyRequest().permitAll()
			)
			.cors(Customizer.withDefaults())
			.csrf(csrf -> csrf
				.ignoringRequestMatchers(
					"/api/v1/auth/oauth2/token",
					"/api/v1/auth/refresh",
					"/api/v1/auth/logout",
					"/api/v1/members/me/terms-agreements",
					"/api/v1/members/me",
					"/api/v1/members/me/settlement-account",
					"/api/v1/members/me/wallets/trade/charge",
					"/api/v1/members/me/wallets/trade/refunds",
					"/api/v1/product-images",
					"/api/v1/auction-rooms",
					// 경매방 삭제(ROOM-04 DELETE)·수정(ROOM-04 PATCH). 빠뜨리면 CsrfFilter가
					// 인증 판정 **전에** 403으로 끊어, 명세의 401 UNAUTHORIZED가 나가지 않는다.
					"/api/v1/auction-rooms/*",
					// 하위 경로는 위 한 단계 패턴이 덮지 않아 따로 등록해야 한다
					// (ROOM-05 방송 시작 · ROOM-07 대기방 입장)
					"/api/v1/auction-rooms/*/start",
					"/api/v1/auction-rooms/*/join",
					"/api/v1/auction-rooms/*/stt/chunks",
					"/api/v1/auctions/*/bids",
					"/api/v1/auctions/*/extensions",
					"/api/v1/auctions/*/chat/pin",
					"/api/v1/auctions/*/chats/*",
					"/api/v1/auctions/*/questions/*",
					"/api/v1/auctions/*/predictions",
					"/api/v1/rooms/*/live/publisher",
					"/api/v1/rooms/*/live/subscriber",
					"/api/v1/rooms/*/live/end",
					"/api/v1/rooms/*/live/snapshot",
					"/api/v1/ai/products/inspect",
					// 거래 전이 3종(DEAL-05). 위 경매방과 같은 이유로 빠뜨리면 403이 401을 덮는다.
					"/api/v1/deals/*/shipping-address",
					"/api/v1/deals/*/shipment-confirmation",
					"/api/v1/deals/*/receipt-confirmation",
					// 경매 신고 접수(DEAL-04)도 같다.
					"/api/v1/auctions/*/reports",
					// 운영 강제 종료(DEAL-09). 빠뜨리면 CsrfFilter가 권한 판정 **전에** 403으로
					// 끊어, ADMIN이 아닌 호출과 CSRF 누락이 구분되지 않는다.
					"/api/v1/admin/auctions/*/force-end",
					// 판매자 종료(ROOM-09, S15P11A704-687). 위 강제 종료와 같은 이유이고,
					// 여기서는 403이 명세의 401·403(판매자 아님)을 함께 덮어 구분이 사라진다.
					"/api/v1/auctions/*/end"
				)
			)
			.exceptionHandling(exception -> exception
				.defaultAuthenticationEntryPointFor(
					new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
					PathPatternRequestMatcher.withDefaults().matcher("/api/**")
				)
				.defaultAuthenticationEntryPointFor(
					new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
					PathPatternRequestMatcher.withDefaults().matcher(
						"/actuator/**"
					)
				)
			)
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session
				.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
			)
			.oauth2Login(oauth2 -> oauth2
				.successHandler(successHandler)
				.failureHandler(failureHandler)
			)
			.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(
		OAuth2CallbackProperties oauth2Properties
	) {
		URI callbackUri = oauth2Properties.frontendCallbackUri();
		String frontendOrigin = callbackUri.getScheme() + "://" + callbackUri.getAuthority();

		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(List.of(frontendOrigin));
		// DELETE는 경매방 삭제(ROOM-04)가 쓴다. 빠뜨리면 서버 로직이 맞아도 브라우저가 preflight에서
		// 막는데, curl로는 통과하므로 브라우저로 확인하지 않으면 발견되지 않는다.
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
		configuration.setAllowCredentials(true);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", configuration);
		return source;
	}
}
