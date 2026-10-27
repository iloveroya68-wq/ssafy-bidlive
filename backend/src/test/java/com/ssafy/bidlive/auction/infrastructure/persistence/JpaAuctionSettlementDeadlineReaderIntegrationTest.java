package com.ssafy.bidlive.auction.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** 실제 PostgreSQL에서 삭제되지 않은 LIVE 경매 마감만 복구 대상으로 읽는지 검증한다. */
@SpringBootTest
@Transactional
class JpaAuctionSettlementDeadlineReaderIntegrationTest {

	private static final Instant FIRST_END_AT =
		Instant.parse("2026-08-02T10:10:00Z");
	private static final Instant SECOND_END_AT =
		Instant.parse("2026-08-02T10:20:00Z");

	@Autowired
	private JpaAuctionSettlementDeadlineReader reader;

	@Autowired
	private EntityManager entityManager;

	@BeforeEach
	void insertFixture() {
		execute("""
			INSERT INTO product_category (
				category_id, code, name, is_active, display_order
			)
			VALUES (9870, 'TEST_DEADLINE', '마감 복구 테스트', TRUE, 9870)
			""");
		execute("""
			INSERT INTO member (member_id, nickname, role, status)
			VALUES (9870, '마감복구판매자', 'USER', 'ACTIVE')
			""");
		execute("""
			INSERT INTO product (
				product_id, seller_member_id, category_id,
				title, description, moderation_status
			)
			VALUES
				(9871, 9870, 9870, 'LIVE 1', '설명', 'PASSED'),
				(9872, 9870, 9870, 'LIVE 2', '설명', 'PASSED'),
				(9873, 9870, 9870, 'WAITING', '설명', 'PASSED'),
				(9874, 9870, 9870, 'ENDED', '설명', 'PASSED'),
				(9875, 9870, 9870, 'DELETED', '설명', 'PASSED')
			""");
		execute("""
			INSERT INTO auction_room (
				auction_room_id, product_id, reserve_price,
				duration_minutes, start_type, started_at,
				ends_at, ended_at, status, visibility, deleted_at
			)
			VALUES
				(9871, 9871, 1000, 10, 'IMMEDIATE',
					'2026-08-02T10:00:00Z', '2026-08-02T10:10:00Z',
					NULL, 'LIVE', 'PUBLIC', NULL),
				(9872, 9872, 1000, 20, 'IMMEDIATE',
					'2026-08-02T10:00:00Z', '2026-08-02T10:20:00Z',
					NULL, 'LIVE', 'PUBLIC', NULL),
				(9873, 9873, 1000, 30, 'IMMEDIATE',
					NULL, '2026-08-02T10:30:00Z',
					NULL, 'WAITING', 'PUBLIC', NULL),
				(9874, 9874, 1000, 40, 'IMMEDIATE',
					'2026-08-02T10:00:00Z', '2026-08-02T10:40:00Z',
					'2026-08-02T10:40:00Z', 'ENDED', 'PUBLIC', NULL),
				(9875, 9875, 1000, 50, 'IMMEDIATE',
					'2026-08-02T10:00:00Z', '2026-08-02T10:50:00Z',
					NULL, 'LIVE', 'PUBLIC', '2026-08-02T10:01:00Z')
			""");
		entityManager.flush();
		entityManager.clear();
	}

	@Test
	void readsOnlyActiveLiveDeadlinesInDeadlineOrder() {
		assertThat(reader.findLiveDeadlines())
			.extracting("auctionId", "endAt")
			.containsExactly(
				org.assertj.core.groups.Tuple.tuple(9871L, FIRST_END_AT),
				org.assertj.core.groups.Tuple.tuple(9872L, SECOND_END_AT)
			);
	}

	private void execute(String sql) {
		entityManager.createNativeQuery(sql).executeUpdate();
	}
}
