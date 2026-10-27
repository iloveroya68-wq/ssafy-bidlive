package com.ssafy.bidlive.common.integration;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.ssafy.bidlive.common.support.redis.RealtimeAuctionRedisKey;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/** 실시간 통합 테스트의 회원·경매·채팅 데이터와 저장소 격리를 일관되게 구성합니다. */
final class RealtimeTestFixture {

	static final long SELLER_ID = 9_420_001L;
	static final long VIEWER_ID = 9_420_002L;
	static final long CATEGORY_ID = 9_420_003L;
	static final long PRODUCT_ID = 9_420_004L;
	static final long AUCTION_ID = 9_420_005L;
	static final String CHAT_MESSAGE_ID = "01JTESTCHAT000000000000001";
	static final String QUESTION_MESSAGE_ID = "01JTESTCHAT000000000000002";
	static final String CHAT_CLIENT_MESSAGE_ID =
		"018f6f4e-2a61-7c00-a1b2-334455667701";
	static final String QUESTION_CLIENT_MESSAGE_ID =
		"018f6f4e-2a61-7c00-a1b2-334455667702";

	private final JdbcTemplate jdbcTemplate;
	private final StringRedisTemplate redisTemplate;

	RealtimeTestFixture(
		JdbcTemplate jdbcTemplate,
		StringRedisTemplate redisTemplate
	) {
		this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
		this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
	}

	void reset() {
		List<String> tables = jdbcTemplate.queryForList("""
			SELECT tablename
			FROM pg_tables
			WHERE schemaname = 'public'
				AND tablename <> 'flyway_schema_history'
			ORDER BY tablename
			""", String.class);
		if (!tables.isEmpty()) {
			String quotedTables = tables.stream()
				.map(RealtimeTestFixture::quoteIdentifier)
				.collect(Collectors.joining(", "));
			jdbcTemplate.execute(
				"TRUNCATE TABLE " + quotedTables + " RESTART IDENTITY CASCADE"
			);
		}

		RedisConnectionFactory connectionFactory = Objects.requireNonNull(
			redisTemplate.getConnectionFactory(),
			"redis connectionFactory"
		);
		try (RedisConnection connection = connectionFactory.getConnection()) {
			connection.serverCommands().flushDb();
		}
	}

	AuctionFixture createLiveAuction() {
		jdbcTemplate.update("""
			INSERT INTO member (
				member_id, nickname, normalized_nickname, role, status
			)
			VALUES
				(?, '통합판매자', '통합판매자', 'USER', 'ACTIVE'),
				(?, '통합시청자', '통합시청자', 'USER', 'ACTIVE')
			""", SELLER_ID, VIEWER_ID);
		jdbcTemplate.update("""
			INSERT INTO product_category (
				category_id, code, name, is_active, display_order
			)
			VALUES (?, 'REALTIME_TEST', '실시간 통합 테스트', TRUE, 9420)
			""", CATEGORY_ID);
		jdbcTemplate.update("""
			INSERT INTO product (
				product_id,
				seller_member_id,
				category_id,
				title,
				description,
				moderation_status
			)
			VALUES (?, ?, ?, '통합 테스트 상품', '실시간 통합 테스트 상품', 'PASSED')
			""", PRODUCT_ID, SELLER_ID, CATEGORY_ID);
		jdbcTemplate.update("""
			INSERT INTO auction_room (
				auction_room_id,
				product_id,
				reserve_price,
				duration_minutes,
				start_type,
				started_at,
				ends_at,
				status,
				visibility
			)
			VALUES (
				?, ?, 1000, 30, 'IMMEDIATE',
				CURRENT_TIMESTAMP,
				CURRENT_TIMESTAMP + INTERVAL '30 minutes',
				'LIVE', 'PUBLIC'
			)
			""", AUCTION_ID, PRODUCT_ID);
		return new AuctionFixture(
			SELLER_ID,
			VIEWER_ID,
			CATEGORY_ID,
			PRODUCT_ID,
			AUCTION_ID
		);
	}

	void insertChatMessages(AuctionFixture fixture) {
		insertChatMessage(
			fixture,
			CHAT_MESSAGE_ID,
			CHAT_CLIENT_MESSAGE_ID,
			fixture.viewerId(),
			"일반 채팅",
			false
		);
		insertChatMessage(
			fixture,
			QUESTION_MESSAGE_ID,
			QUESTION_CLIENT_MESSAGE_ID,
			fixture.viewerId(),
			"상품 상태가 궁금합니다",
			true
		);
	}

	void seedRealtimeState(AuctionFixture fixture, long stateVersion) {
		String stateKey = RealtimeAuctionRedisKey.auctionStateKey(
			fixture.auctionId()
		);
		redisTemplate.opsForHash().putAll(stateKey, java.util.Map.of(
			"version", Long.toString(stateVersion),
			"status", "LIVE",
			"endAt", Long.toString(System.currentTimeMillis() + 1_800_000L),
			"extensionCount", "1",
			"bidCount", "7"
		));
		redisTemplate.opsForHash().put(
			RealtimeAuctionRedisKey.auctionMemberBidsKey(fixture.auctionId()),
			Long.toString(fixture.viewerId()),
			"12000"
		);
	}

	private void insertChatMessage(
		AuctionFixture fixture,
		String messageId,
		String clientMessageId,
		long senderId,
		String content,
		boolean question
	) {
		jdbcTemplate.update("""
			INSERT INTO chat_message (
				auction_room_id,
				sender_member_id,
				message_type,
				content,
				moderation_status,
				question_queue_status,
				message_id,
				client_message_id
			)
			VALUES (?, ?, ?, ?, 'PASSED', ?, ?, ?)
			""",
			fixture.auctionId(),
			senderId,
			question ? "QUESTION" : "CHAT",
			content,
			question ? "PENDING" : null,
			messageId,
			clientMessageId
		);
	}

	private static String quoteIdentifier(String identifier) {
		return '"' + identifier.replace("\"", "\"\"") + '"';
	}

	/** 실시간 통합 테스트에서 함께 사용하는 회원·상품·경매 식별자입니다. */
	record AuctionFixture(
		long sellerId,
		long viewerId,
		long categoryId,
		long productId,
		long auctionId
	) {
	}
}
