package com.ssafy.bidlive.media.infrastructure.openvidu;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;

import com.ssafy.bidlive.media.application.port.out.LiveSessionGateway;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/** OpenVidu 전용 WebClient와 세션 어댑터를 등록합니다. */
@Configuration
@EnableConfigurationProperties(OpenViduProperties.class)
public class OpenViduConfiguration {

	@Bean
	public WebClient openViduWebClient(OpenViduProperties openViduProperties) {
		return WebClient.builder()
			.baseUrl(openViduProperties.baseUrl())
			.defaultHeader(HttpHeaders.AUTHORIZATION, openViduProperties.basicAuthorization())
			.clientConnector(new ReactorClientHttpConnector(insecureHttpClient()))
			.build();
	}

	@Bean
	public LiveSessionGateway liveSessionGateway(WebClient openViduWebClient) {
		return new OpenViduLiveSessionGateway(openViduWebClient);
	}

	/**
	 * 현재 OpenVidu 서버가 self-signed 인증서를 사용하므로 인증서 신뢰 검증과 호스트명 검증을 모두 끈다.
	 *
	 * <p>이 우회는 한시적이며 OpenVidu 호출 전용이다. 다음 두 가지를 반드시 지킨다.
	 * <ul>
	 *     <li>이 HttpClient를 다른 외부 연동에 재사용하지 않는다.</li>
	 *     <li>OpenVidu에 정식 인증서를 적용하면 이 메서드와
	 *         {@code clientConnector(...)} 호출을 함께 제거해 기본 검증을 복구한다.</li>
	 * </ul>
	 */
	private static HttpClient insecureHttpClient() {
		SslContext sslContext;
		try {
			sslContext = SslContextBuilder.forClient()
				.trustManager(InsecureTrustManagerFactory.INSTANCE)
				.build();
		} catch (SSLException exception) {
			throw new IllegalStateException("Failed to build OpenVidu SSL context", exception);
		}

		return HttpClient.create()
			.secure(spec -> spec
				.sslContext(sslContext)
				.handlerConfigurator(sslHandler -> {
					SSLEngine engine = sslHandler.engine();
					SSLParameters parameters = engine.getSSLParameters();
					parameters.setEndpointIdentificationAlgorithm(null);
					engine.setSSLParameters(parameters);
				}));
	}
}
