package com.ssafy.bidlive.auction.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import com.ssafy.bidlive.auction.application.AuctionRoomJoinTarget;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** 입장 판정에 필요한 값 조회를 실제 PostgreSQL로 검증한다. */
@SpringBootTest
@Transactional
class JpaAuctionRoomJoinAdapterIntegrationTest {

	@Autowired
	private JpaAuctionRoomJoinAdapter adapter;

	@Autowired
	private EntityManager entityManager;

	@BeforeEach
	void insertFixture() {
		execute("""
			INSERT INTO product_category (category_id, code, name, is_active, display_order)
			VALUES (9801, 'TEST_JOIN', '입장 테스트', TRUE, 9801)
			""");
		execute("""
			INSERT INTO member (member_id, nickname, role, status)
			VALUES (9801, '입장판매자', 'USER', 'ACTIVE')
			""");
		execute("""
			INSERT INTO product (product_id, seller_member_id, category_id, title, description)
			VALUES
				(9801, 9801, 9801, '예약 대기방 상품', '설명'),
				(9802, 9801, 9801, '즉시 대기방 상품', '설명')
			""");
		execute("""
			INSERT INTO auction_room (
				auction_room_id, product_id, reserve_price, duration_minutes,
				start_type, scheduled_at, status, visibility
			)
			VALUES
				(9801, 9801, 50000, 15, 'SCHEDULED', '2026-12-01T00:00:00Z', 'WAITING', 'PUBLIC'),
				(9802, 9802, 50000, 15, 'IMMEDIATE', NULL, 'WAITING', 'PRIVATE')
			""");
		entityManager.flush();
	}

	@Test
	void readsSellerStatusVisibilityAndSchedule() {
		AuctionRoomJoinTarget target = adapter.findJoinTarget(9801L).orElseThrow();

		assertThat(target.auctionId()).isEqualTo(9801L);
		assertThat(target.sellerId()).isEqualTo(9801L);
		assertThat(target.status()).isEqualTo("WAITING");
		assertThat(target.visibility()).isEqualTo("PUBLIC");
		assertThat(target.deleted()).isFalse();
		assertThat(target.scheduledAt()).isEqualTo(Instant.parse("2026-12-01T00:00:00Z"));
	}

	/** 즉시 시작 방은 예약 시각이 NULL이다 — 대기방 응답의 `scheduledAt`도 null이 된다 */
	@Test
	void readsNullScheduleForImmediateRoom() {
		AuctionRoomJoinTarget target = adapter.findJoinTarget(9802L).orElseThrow();

		assertThat(target.scheduledAt()).isNull();
		assertThat(target.visibility()).isEqualTo("PRIVATE");
	}

	@Test
	void reportsDeletedRoom() {
		execute("UPDATE auction_room SET deleted_at = NOW() WHERE auction_room_id = 9801");
		entityManager.flush();

		assertThat(adapter.findJoinTarget(9801L).orElseThrow().deleted()).isTrue();
	}

	@Test
	void returnsEmptyForMissingRoom() {
		assertThat(adapter.findJoinTarget(999_999L)).isEmpty();
	}

	private void execute(String sql) {
		entityManager.createNativeQuery(sql).executeUpdate();
	}
}
