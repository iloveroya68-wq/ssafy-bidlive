package com.ssafy.bidlive.bid.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.ssafy.bidlive.auction.domain.AuctionCanceledEvent;
import com.ssafy.bidlive.auction.domain.AuctionStartedEvent;
import com.ssafy.bidlive.bid.application.BidCommand;
import com.ssafy.bidlive.bid.domain.BidFailureCode;
import com.ssafy.bidlive.prediction.application.PredictionParticipationCommand;
import com.ssafy.bidlive.prediction.domain.PredictionParticipationFailureCode;
import com.ssafy.bidlive.prediction.infrastructure.redis.RedisPredictionParticipationProcessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 실제 Redis에서 **강제 종료 후 입찰·예측이 정말 거절되는지** 검증한다 (S15P11A704-537).
 *
 * <p>시작 → 입찰 접수 → 강제 종료 → 입찰 거절을 실제 Lua로 통과시킨다. 초기화와 차단이 같은
 * Key·필드를 쓰는지, 쓰는 값이 게이트를 실제로 닫는지는 이 경로로만 증명된다 — 단위 테스트의
 * 문자열 대조로는 `KEYS[1]`이 그 Hash인지 알 수 없다.
 *
 * <p>⚠️ `redis-server` 바이너리가 있어야 돈다(`RedisBidProcessorIntegrationTest` 등 기존 4개와 같은
 * 방식). <b>다만 형제들과 달리 바이너리가 없으면 실패가 아니라 skip이다.</b> 형제 4개는 바이너리가
 * 없는 로컬에서 늘 빨간색이라 아무도 안 보게 됐는데, 거기 하나를 더 얹으면 이 테스트가 <b>진짜로</b>
 * 깨졌을 때도 같은 빨간색에 묻힌다. skip이면 「환경이 없어 못 돌았다」와 「단언이 깨졌다」가 구분된다.
 * 루트 `.gitlab-ci.yml`에 테스트 잡이 없어 CI도 이 테스트를 돌려 주지 않으므로, 지금 실제로 도는
 * 방어선은 {@link RedisAuctionStateCloserTest}의 Lua 원문 대조다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIf("redisServerAvailable")
class RedisAuctionStateCloserIntegrationTest {

	static boolean redisServerAvailable() {
		try {
			return new ProcessBuilder("redis-server", "--version")
				.redirectErrorStream(true)
				.redirectOutput(ProcessBuilder.Redirect.DISCARD)
				.start()
				.waitFor(5, TimeUnit.SECONDS);
		} catch (IOException exception) {
			return false;
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	private static final long AUCTION_ID = 100L;
	private static final long SELLER_ID = 999L;
	private static final long BIDDER_ID = 25L;
	private static final OffsetDateTime BASE_TIME =
		OffsetDateTime.parse("2026-07-31T21:12:44+09:00");

	private Process redisProcess;
	private LettuceConnectionFactory connectionFactory;
	private StringRedisTemplate redisTemplate;
	private RedisAuctionStateInitializer initializer;
	private RedisAuctionStateCloser closer;
	private RedisBidProcessor bidProcessor;
	private RedisPredictionParticipationProcessor predictionProcessor;

	@BeforeAll
	void startRedis() throws Exception {
		int port = availablePort();
		redisProcess = new ProcessBuilder(
			"redis-server",
			"--bind", "127.0.0.1",
			"--port", Integer.toString(port),
			"--save", "",
			"--appendonly", "no",
			"--loglevel", "warning"
		)
			.redirectErrorStream(true)
			.redirectOutput(ProcessBuilder.Redirect.DISCARD)
			.start();
		waitUntilListening(port);

		connectionFactory = new LettuceConnectionFactory(
			new RedisStandaloneConfiguration("127.0.0.1", port)
		);
		connectionFactory.afterPropertiesSet();
		connectionFactory.start();

		redisTemplate = new StringRedisTemplate(connectionFactory);
		redisTemplate.afterPropertiesSet();
		initializer = new RedisAuctionStateInitializer(redisTemplate);
		closer = new RedisAuctionStateCloser(redisTemplate);
		bidProcessor = new RedisBidProcessor(redisTemplate);
		predictionProcessor = new RedisPredictionParticipationProcessor(redisTemplate);
	}

	@AfterAll
	void stopRedis() throws Exception {
		if (connectionFactory != null) {
			connectionFactory.destroy();
		}
		if (redisProcess != null) {
			redisProcess.destroy();
			if (!redisProcess.waitFor(3, TimeUnit.SECONDS)) {
				redisProcess.destroyForcibly();
				redisProcess.waitFor(3, TimeUnit.SECONDS);
			}
		}
	}

	@Test
	void rejectsBidsAndPredictionsAfterForceEndEvenBeforeTheScheduledDeadline() {
		initializer.onAuctionStarted(new AuctionStartedEvent(
			AUCTION_ID,
			SELLER_ID,
			50_000L,
			BASE_TIME.toInstant(),
			BASE_TIME.plusMinutes(15).toInstant()
		));
		redisTemplate.opsForHash().put(
			BidRedisKey.processingKeys(AUCTION_ID).get(4),
			Long.toString(BIDDER_ID),
			"100000"
		);
		assertThat(bidProcessor.process(bid("before-force-end", 70_000L)).accepted())
			.as("종료 전에는 받아져야 이후 거절이 강제 종료 때문이라고 말할 수 있다")
			.isTrue();

		closer.onAuctionCanceled(new AuctionCanceledEvent(
			AUCTION_ID, "ADMIN_FORCE_END", Instant.parse("2026-07-31T12:20:00Z")
		));

		// 예정 마감(+15분)은 아직 한참 남았다 — 막히는 이유는 오직 `status`다
		assertThat(bidProcessor.process(bid("after-force-end", 82_000L)).failureCode())
			.isEqualTo(BidFailureCode.AUCTION_NOT_LIVE);
		assertThat(predictionProcessor.process(prediction()).failureCode())
			.isEqualTo(PredictionParticipationFailureCode.AUCTION_NOT_LIVE);
	}

	private BidCommand bid(String requestId, long amount) {
		return new BidCommand(AUCTION_ID, BIDDER_ID, amount, requestId, BASE_TIME.plusMinutes(1));
	}

	private PredictionParticipationCommand prediction() {
		return new PredictionParticipationCommand(
			AUCTION_ID, 26L, 70_000L, 1_000L, UUID.randomUUID(), BASE_TIME.plusMinutes(1)
		);
	}

	private static int availablePort() throws IOException {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
	}

	private void waitUntilListening(int port) throws Exception {
		for (int attempt = 0; attempt < 100; attempt++) {
			if (!redisProcess.isAlive()) {
				throw new IllegalStateException("Redis process stopped before accepting connections");
			}
			try (Socket ignored = new Socket("127.0.0.1", port)) {
				return;
			} catch (IOException exception) {
				Thread.sleep(25);
			}
		}
		throw new IllegalStateException("Redis did not start within the expected time");
	}
}
