package com.ssafy.bidlive.common.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.bidlive.common.support.redis.RealtimeAuctionRedisKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/** 공통 컨테이너와 Fixture가 PostgreSQL·Redis 상태를 테스트마다 격리하는지 검증합니다. */
@SpringBootTest
class RealtimeIntegrationTestEnvironmentTest
	extends RealtimeIntegrationTestEnvironment {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private StringRedisTemplate redisTemplate;

	private RealtimeTestFixture fixture;

	@BeforeEach
	void setUp() {
		fixture = new RealtimeTestFixture(jdbcTemplate, redisTemplate);
		fixture.reset();
	}

	@Test
	void startsPostgresqlAndRedisAndCreatesSharedFixture() {
		RealtimeTestFixture.AuctionFixture auction = fixture.createLiveAuction();
		fixture.insertChatMessages(auction);
		fixture.seedRealtimeState(auction, 41L);

		assertThat(containersAreRunning()).isTrue();
		assertThat(jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM member",
			Long.class
		)).isEqualTo(2L);
		assertThat(jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM chat_message WHERE auction_room_id = ?",
			Long.class,
			auction.auctionId()
		)).isEqualTo(2L);
		assertThat(redisTemplate.opsForHash().get(
			RealtimeAuctionRedisKey.auctionStateKey(auction.auctionId()),
			"version"
		)).isEqualTo("41");
	}

	@Test
	void resetRemovesDatabaseAndRedisState() {
		RealtimeTestFixture.AuctionFixture auction = fixture.createLiveAuction();
		fixture.insertChatMessages(auction);
		redisTemplate.opsForValue().set("bidlive:test:{9420005}:isolation", "dirty");

		fixture.reset();

		assertThat(jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM member",
			Long.class
		)).isZero();
		assertThat(redisTemplate.keys("*")).isEmpty();
	}
}
