package com.ssafy.bidlive.prediction.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.ssafy.bidlive.prediction.application.PredictionLeaderboardAggregationService;
import com.ssafy.bidlive.prediction.application.PredictionLeaderboardSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 동일 날짜의 일일 집계가 PostgreSQL advisory lock으로 직렬화되는지 검증한다. */
@SpringBootTest
class PredictionLeaderboardAdvisoryLockIntegrationTest {

	private static final int ADVISORY_LOCK_NAMESPACE = 704_169;
	private static final LocalDate RANKING_DATE =
		LocalDate.of(2026, 7, 16);

	@Autowired
	private PredictionLeaderboardAggregationService service;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Test
	void waitsUntilSameDateTransactionLockIsReleased() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch lockAcquired = new CountDownLatch(1);
		CountDownLatch releaseLock = new CountDownLatch(1);
		TransactionTemplate transaction =
			new TransactionTemplate(transactionManager);

		Future<?> lockHolder = executor.submit(() ->
			transaction.executeWithoutResult(status -> {
				acquireRankingDateLock();
				lockAcquired.countDown();
				await(releaseLock);
			})
		);

		try {
			assertThat(lockAcquired.await(5, TimeUnit.SECONDS)).isTrue();
			Future<PredictionLeaderboardSnapshot> aggregation =
				executor.submit(() -> service.aggregate(RANKING_DATE));

			assertThatThrownBy(() ->
				aggregation.get(300, TimeUnit.MILLISECONDS)
			).isInstanceOf(TimeoutException.class);

			releaseLock.countDown();
			lockHolder.get(5, TimeUnit.SECONDS);
			assertThat(aggregation.get(5, TimeUnit.SECONDS).entries())
				.isEmpty();
		} finally {
			releaseLock.countDown();
			executor.shutdownNow();
		}
	}

	private void acquireRankingDateLock() {
		jdbcTemplate.query(
			"SELECT pg_advisory_xact_lock(?, ?)",
			resultSet -> null,
			ADVISORY_LOCK_NAMESPACE,
			Math.toIntExact(RANKING_DATE.toEpochDay())
		);
	}

	private void await(CountDownLatch latch) {
		try {
			latch.await();
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(
				"Interrupted while holding advisory lock",
				exception
			);
		}
	}
}
