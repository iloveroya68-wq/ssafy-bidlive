package com.ssafy.bidlive.common.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.ssafy.bidlive.common.security.StompTokenRenewalErrorCode;
import com.ssafy.bidlive.common.security.StompTokenRenewalException;
import com.ssafy.bidlive.common.security.WebSocketSessionAttributes;
import org.junit.jupiter.api.Test;

/** STOMP 세션 만료 시각의 단조 증가와 동시 갱신 안전성을 검증합니다. */
class StompSessionTokenExpirationUpdaterTest {

	private static final Instant CONNECTED_AT = Instant.parse("2026-07-29T08:00:00Z");
	private static final Instant CURRENT_EXPIRES_AT = Instant.parse("2026-07-29T09:00:00Z");
	private static final Instant LATER_EXPIRES_AT = Instant.parse("2026-07-29T10:00:00Z");
	private static final Instant LATEST_EXPIRES_AT = Instant.parse("2026-07-29T11:00:00Z");

	private final StompSessionTokenExpirationUpdater updater =
		new StompSessionTokenExpirationUpdater();

	@Test
	void replacesCurrentExpirationWithLaterExpiration() {
		Map<String, Object> sessionAttributes = authenticatedSessionAttributes();

		Instant result = updater.update(sessionAttributes, LATER_EXPIRES_AT);

		assertThat(result).isEqualTo(LATER_EXPIRES_AT);
		assertThat(sessionAttributes).containsEntry(
			WebSocketSessionAttributes.TOKEN_EXPIRES_AT,
			LATER_EXPIRES_AT
		);
	}

	@Test
	void keepsCurrentExpirationWhenCandidateIsEarlier() {
		Map<String, Object> sessionAttributes = authenticatedSessionAttributes();

		Instant result = updater.update(sessionAttributes, CONNECTED_AT);

		assertThat(result).isEqualTo(CURRENT_EXPIRES_AT);
		assertThat(sessionAttributes)
			.containsEntry(
				WebSocketSessionAttributes.TOKEN_EXPIRES_AT,
				CURRENT_EXPIRES_AT
			)
			.containsEntry(
				WebSocketSessionAttributes.CONNECTED_AT,
				CONNECTED_AT
			)
			.containsEntry("unchanged", "value");
	}

	@Test
	void keepsCurrentExpirationWhenCandidateIsEqual() {
		Map<String, Object> sessionAttributes = authenticatedSessionAttributes();

		Instant result = updater.update(sessionAttributes, CURRENT_EXPIRES_AT);

		assertThat(result).isEqualTo(CURRENT_EXPIRES_AT);
		assertThat(sessionAttributes).containsEntry(
			WebSocketSessionAttributes.TOKEN_EXPIRES_AT,
			CURRENT_EXPIRES_AT
		);
	}

	@Test
	void keepsLatestExpirationWhenConcurrentRenewalsOverlap() throws Exception {
		Map<String, Object> sessionAttributes = authenticatedSessionAttributes();
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			Future<Instant> laterUpdate = executor.submit(() -> updateAfterStart(
				sessionAttributes,
				LATER_EXPIRES_AT,
				ready,
				start
			));
			Future<Instant> latestUpdate = executor.submit(() -> updateAfterStart(
				sessionAttributes,
				LATEST_EXPIRES_AT,
				ready,
				start
			));

			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			laterUpdate.get(5, TimeUnit.SECONDS);
			latestUpdate.get(5, TimeUnit.SECONDS);

			assertThat(sessionAttributes).containsEntry(
				WebSocketSessionAttributes.TOKEN_EXPIRES_AT,
				LATEST_EXPIRES_AT
			);
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void rejectsSessionWithoutCurrentExpiration() {
		Map<String, Object> sessionAttributes = new HashMap<>();

		assertInvalidRenewal(() -> updater.update(
			sessionAttributes,
			LATER_EXPIRES_AT
		));
		assertThat(sessionAttributes).doesNotContainKey(
			WebSocketSessionAttributes.TOKEN_EXPIRES_AT
		);
	}

	@Test
	void rejectsSessionWithInvalidCurrentExpirationType() {
		Map<String, Object> sessionAttributes = new HashMap<>();
		sessionAttributes.put(
			WebSocketSessionAttributes.TOKEN_EXPIRES_AT,
			"2026-07-29T09:00:00Z"
		);

		assertInvalidRenewal(() -> updater.update(
			sessionAttributes,
			LATER_EXPIRES_AT
		));
	}

	@Test
	void rejectsMissingSessionAttributes() {
		assertThatThrownBy(() -> updater.update(null, LATER_EXPIRES_AT))
			.isInstanceOf(NullPointerException.class)
			.hasMessage("sessionAttributes");
	}

	@Test
	void rejectsMissingCandidateExpiration() {
		assertThatThrownBy(() -> updater.update(
			authenticatedSessionAttributes(),
			null
		))
			.isInstanceOf(NullPointerException.class)
			.hasMessage("candidateExpiresAt");
	}

	private Instant updateAfterStart(
		Map<String, Object> sessionAttributes,
		Instant candidateExpiresAt,
		CountDownLatch ready,
		CountDownLatch start
	) throws InterruptedException {
		ready.countDown();
		start.await();
		return updater.update(sessionAttributes, candidateExpiresAt);
	}

	private Map<String, Object> authenticatedSessionAttributes() {
		Map<String, Object> sessionAttributes = new HashMap<>();
		sessionAttributes.put(
			WebSocketSessionAttributes.TOKEN_EXPIRES_AT,
			CURRENT_EXPIRES_AT
		);
		sessionAttributes.put(
			WebSocketSessionAttributes.CONNECTED_AT,
			CONNECTED_AT
		);
		sessionAttributes.put("unchanged", "value");
		return sessionAttributes;
	}

	private void assertInvalidRenewal(
		org.assertj.core.api.ThrowableAssert.ThrowingCallable callable
	) {
		assertThatThrownBy(callable)
			.isInstanceOfSatisfying(
				StompTokenRenewalException.class,
				exception -> assertThat(exception.errorCode())
					.isEqualTo(StompTokenRenewalErrorCode.AUTH_RENEW_INVALID)
			);
	}
}
