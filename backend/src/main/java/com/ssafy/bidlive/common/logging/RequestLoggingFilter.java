package com.ssafy.bidlive.common.logging;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {
	private static final String REQUEST_ID_HEADER = "X-Request-ID";
	private static final String CORRELATION_ID_MDC_KEY = "correlationId";

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		String requestId = resolveRequestId(request.getHeader(REQUEST_ID_HEADER));
		long startedAt = System.nanoTime();
		Throwable failure = null;

		MDC.put("requestId", requestId);
		MDC.put(CORRELATION_ID_MDC_KEY, requestId);
		response.setHeader(REQUEST_ID_HEADER, requestId);

		try {
			filterChain.doFilter(request, response);
		} catch (IOException | ServletException | RuntimeException exception) {
			failure = exception;
			throw exception;
		} finally {
			try {
				long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
				LoggingEventBuilder event = selectLogEvent(response.getStatus(), failure);

				event.addKeyValue("httpMethod", request.getMethod())
					.addKeyValue("correlationId", requestId)
					.addKeyValue("requestUri", request.getRequestURI())
					.addKeyValue("httpStatus", response.getStatus())
					.addKeyValue("durationMs", durationMs)
					.log("HTTP request completed");
			} finally {
				MDC.remove("requestId");
				MDC.remove(CORRELATION_ID_MDC_KEY);
			}
		}
	}

	private String resolveRequestId(String candidate) {
		if (candidate != null) {
			try {
				return UUID.fromString(candidate).toString();
			} catch (IllegalArgumentException ignored) {
				// 유효하지 않은 외부 헤더를 로그 식별자로 사용하지 않는다.
			}
		}
		return UUID.randomUUID().toString();
	}

	private LoggingEventBuilder selectLogEvent(int status, Throwable failure) {
		if (failure != null) {
			return log.atError().addKeyValue(
				"failureType",
				failure.getClass().getSimpleName()
			);
		}

		if (status >= 500) {
			return log.atError();
		}

		if (status >= 400) {
			return log.atWarn();
		}

		return log.atInfo();
	}
}
