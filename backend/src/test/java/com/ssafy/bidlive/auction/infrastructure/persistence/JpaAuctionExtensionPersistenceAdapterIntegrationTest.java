package com.ssafy.bidlive.auction.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.transaction.annotation.Transactional;

/** Redis 연장 결과의 PostgreSQL 반영과 오래된 동시 결과의 덮어쓰기 방지를 검증한다. */
@SpringBootTest
@Transactional
class JpaAuctionExtensionPersistenceAdapterIntegrationTest {

	private static final long AUCTION_ID = 9751L;
	private static final Instant INITIAL_END_AT =
		Instant.parse("2026-07-20T12:21:00Z");

	@Autowired
	private JpaAuctionExtensionPersistenceAdapter adapter;

	@Autowired
	private EntityManager entityManager;

	@BeforeEach
	void insertFixture() {
		execute("""
			INSERT INTO product_category (
				category_id, code, name, is_active, display_order
			)
			VALUES (9751, 'TEST_EXTENSION', '연장 테스트', TRUE, 9751)
			""");
		execute("""
			INSERT INTO member (member_id, nickname, role, status)
			VALUES (9751, '연장판매자', 'USER', 'ACTIVE')
			""");
		execute("""
			INSERT INTO product (
				product_id, seller_member_id, category_id,
				title, description, moderation_status
			)
			VALUES (
				9751, 9751, 9751,
				'연장할 방 상품', '설명', 'PASSED'
			)
			""");
		execute("""
			INSERT INTO auction_room (
				auction_room_id, product_id, reserve_price,
				duration_minutes, start_type, started_at,
				ends_at, extension_count, status, visibility
			)
			VALUES (
				9751, 9751, 50000,
				15, 'IMMEDIATE', '2026-07-20T12:06:00Z',
				'2026-07-20T12:21:00Z', 0, 'LIVE', 'PUBLIC'
			)
			""");
		entityManager.flush();
	}

	@Test
	void synchronizesRedisAcceptedDeadlineAndCount() {
		Instant extendedEndAt = INITIAL_END_AT.plusSeconds(300);

		adapter.synchronizeFinalState(AUCTION_ID, extendedEndAt, 1);
		entityManager.flush();
		entityManager.clear();

		assertThat(endsAt()).isEqualTo(extendedEndAt);
		assertThat(extensionCount()).isEqualTo(1);
	}

	@Test
	void doesNotOverwriteNewerStateWithLateOlderResult() {
		Instant firstEndAt = INITIAL_END_AT.plusSeconds(300);
		Instant secondEndAt = INITIAL_END_AT.plusSeconds(600);
		adapter.synchronizeFinalState(AUCTION_ID, secondEndAt, 2);

		adapter.synchronizeFinalState(AUCTION_ID, firstEndAt, 1);
		entityManager.flush();
		entityManager.clear();

		assertThat(endsAt()).isEqualTo(secondEndAt);
		assertThat(extensionCount()).isEqualTo(2);
	}

	@Test
	void permitsSameCountRetryToRepairDeadline() {
		Instant extendedEndAt = INITIAL_END_AT.plusSeconds(300);
		execute("""
			UPDATE auction_room
			SET extension_count = 1
			WHERE auction_room_id = 9751
			""");

		adapter.synchronizeFinalState(AUCTION_ID, extendedEndAt, 1);
		entityManager.flush();
		entityManager.clear();

		assertThat(endsAt()).isEqualTo(extendedEndAt);
		assertThat(extensionCount()).isEqualTo(1);
	}

	@Test
	void reportsMissingPostgresAuctionRoom() {
		assertThatThrownBy(() -> adapter.synchronizeFinalState(
			999_999L,
			INITIAL_END_AT.plusSeconds(300),
			1
		))
			.isInstanceOf(InvalidDataAccessApiUsageException.class)
			.hasRootCauseInstanceOf(IllegalStateException.class)
			.hasRootCauseMessage(
				"PostgreSQL auction room is missing: 999999"
			);
	}

	private Instant endsAt() {
		Object value = entityManager.createNativeQuery("""
			SELECT ends_at
			FROM auction_room
			WHERE auction_room_id = :auctionId
			""")
			.setParameter("auctionId", AUCTION_ID)
			.getSingleResult();
		return switch (value) {
			case java.sql.Timestamp timestamp -> timestamp.toInstant();
			case java.time.OffsetDateTime offsetDateTime ->
				offsetDateTime.toInstant();
			case Instant instant -> instant;
			default -> throw new IllegalStateException(
				"지원하지 않는 시각 타입: " + value.getClass()
			);
		};
	}

	private int extensionCount() {
		Number value = (Number) entityManager.createNativeQuery("""
			SELECT extension_count
			FROM auction_room
			WHERE auction_room_id = :auctionId
			""")
			.setParameter("auctionId", AUCTION_ID)
			.getSingleResult();
		return value.intValue();
	}

	private void execute(String sql) {
		entityManager.createNativeQuery(sql).executeUpdate();
	}
}
