package com.ssafy.bidlive.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestLoggingFilterTest {

	private RequestLoggingFilter filter;
	private Logger filterLogger;
	private ListAppender<ILoggingEvent> logAppender;

	@BeforeEach
	void setUp() {
		filter = new RequestLoggingFilter();

		filterLogger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
		logAppender = new ListAppender<>();
		logAppender.start();
		filterLogger.addAppender(logAppender);
	}

	@AfterEach
	void tearDown() {
		filterLogger.detachAppender(logAppender);
		logAppender.stop();
		MDC.clear();
	}

	@Test
	void addsRequestIdAndContinuesRequest() throws Exception {
		var request = new MockHttpServletRequest("GET", "/products");
		var response = new MockHttpServletResponse();
		var chainCalled = new AtomicBoolean(false);

		FilterChain chain = (servletRequest, servletResponse) -> {
			chainCalled.set(true);
			assertThat(MDC.get("requestId")).isNotBlank();
			assertThat(MDC.get("correlationId"))
				.isEqualTo(MDC.get("requestId"));
			((HttpServletResponse) servletResponse).setStatus(204);
		};

		filter.doFilter(request, response, chain);

		String requestId = response.getHeader("X-Request-ID");

		assertThat(chainCalled).isTrue();
		assertThat(response.getStatus()).isEqualTo(204);
		assertThat(requestId).isNotBlank();
		assertThat(UUID.fromString(requestId).toString()).isEqualTo(requestId);
		assertThat(MDC.get("requestId")).isNull();
		assertThat(MDC.get("correlationId")).isNull();
	}

	@Test
	void propagatesValidRequestIdAndReplacesInvalidValue() throws Exception {
		String existing = "85eb7fc1-b99c-4e81-b973-b1203ce91809";
		var validRequest = new MockHttpServletRequest("GET", "/products");
		validRequest.addHeader("X-Request-ID", existing);
		var validResponse = new MockHttpServletResponse();

		filter.doFilter(validRequest, validResponse, (request, response) -> {
			assertThat(MDC.get("correlationId")).isEqualTo(existing);
		});

		assertThat(validResponse.getHeader("X-Request-ID"))
			.isEqualTo(existing);

		var invalidRequest = new MockHttpServletRequest("GET", "/products");
		invalidRequest.addHeader("X-Request-ID", "Bearer secret-token");
		var invalidResponse = new MockHttpServletResponse();
		filter.doFilter(invalidRequest, invalidResponse, (request, response) -> { });

		String replacement = invalidResponse.getHeader("X-Request-ID");
		assertThat(replacement).isNotEqualTo("Bearer secret-token");
		assertThat(UUID.fromString(replacement).toString())
			.isEqualTo(replacement);
	}

	@ParameterizedTest
	@CsvSource({
		"204, INFO",
		"302, INFO",
		"404, WARN",
		"500, ERROR"
	})
	void logsLevelAccordingToHttpStatus(int status, String expectedLevel) throws Exception {
		var request = new MockHttpServletRequest("GET", "/products");
		var response = new MockHttpServletResponse();

		FilterChain chain = (servletRequest, servletResponse) ->
			((HttpServletResponse) servletResponse).setStatus(status);

		filter.doFilter(request, response, chain);

		assertThat(logAppender.list).hasSize(1);
		assertThat(logAppender.list.get(0).getLevel().toString())
			.isEqualTo(expectedLevel);
	}
}
