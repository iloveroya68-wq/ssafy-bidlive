package com.ssafy.bidlive.auction.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import com.ssafy.bidlive.auction.application.UpcomingAuctionSnapshot;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** 실제 PostgreSQL에서 시간·상태·공개 정책과 시작 예정순 정렬을 검증합니다. */
@SpringBootTest
@Transactional
class JpaUpcomingAuctionQueryAdapterIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-07-21T10:00:00Z");
	private static final Instant ONE_HOUR_LATER =
		Instant.parse("2026-07-21T11:00:00Z");

	@Autowired
	private JpaUpcomingAuctionQueryAdapter adapter;

	@Autowired
	private EntityManager entityManager;

	@BeforeEach
	void insertFixture() {
		execute("""
			INSERT INTO product_category (category_id, code, name, is_active, display_order)
			VALUES (9401, 'TEST_UPCOMING_CAT', '곧 시작 테스트', TRUE, 9401)
			""");
		execute("""
			INSERT INTO member (member_id, nickname, role, status)
			VALUES
				(9401, '곧시작판매자', 'USER', 'ACTIVE'),
				(9402, '대기회원1', 'USER', 'ACTIVE'),
				(9403, '대기회원2', 'USER', 'ACTIVE')
			""");
		execute("""
			INSERT INTO product (
				product_id, seller_member_id, category_id, title, description,
				moderation_status
			)
			VALUES
				(9401, 9401, 9401, '10분 후 상품', '설명', 'PENDING'),
				(9402, 9401, 9401, '20분 후 상품', '설명', 'PENDING'),
				(9403, 9401, 9401, '1시간 후 상품', '설명', 'PENDING'),
				(9404, 9401, 9401, '범위 밖 상품', '설명', 'PENDING'),
				(9405, 9401, 9401, '진행 중 상품', '설명', 'PENDING'),
				(9406, 9401, 9401, '현재 시각 상품', '설명', 'PENDING'),
				(9407, 9401, 9401, '비공개 상품', '설명', 'PENDING')
			""");
		execute("""
			INSERT INTO product_image (
				product_image_id, product_id, image_url, sort_order, is_thumbnail,
				image_id, owner_member_id, storage_key, content_type, size_bytes
			)
			SELECT
				product_id,
				product_id,
				'https://cdn.example.com/' || product_id || '.png',
				1,
				TRUE,
				gen_random_uuid(),
				9401,
				'test/product-images/' || product_id,
				'image/png',
				10
			FROM product
			WHERE product_id BETWEEN 9401 AND 9407
			""");
		execute("""
			INSERT INTO auction_room (
				auction_room_id, product_id, reserve_price, duration_minutes,
				start_type, scheduled_at, status, visibility, deleted_at
			)
			VALUES
				(9401, 9401, 10000, 15, 'SCHEDULED', '2026-07-21T10:10:00Z', 'WAITING', 'PUBLIC', NULL),
				(9402, 9402, 10000, 15, 'SCHEDULED', '2026-07-21T10:20:00Z', 'WAITING', 'PUBLIC', NULL),
				(9403, 9403, 10000, 15, 'SCHEDULED', '2026-07-21T11:00:00Z', 'WAITING', 'PUBLIC', NULL),
				(9404, 9404, 10000, 15, 'SCHEDULED', '2026-07-21T11:00:01Z', 'WAITING', 'PUBLIC', NULL),
				(9405, 9405, 10000, 15, 'SCHEDULED', '2026-07-21T10:30:00Z', 'LIVE', 'PUBLIC', NULL),
				(9406, 9406, 10000, 15, 'SCHEDULED', '2026-07-21T10:00:00Z', 'WAITING', 'PUBLIC', NULL),
				(9407, 9407, 10000, 15, 'SCHEDULED', '2026-07-21T10:30:00Z', 'WAITING', 'PRIVATE', NULL)
			""");
		entityManager.flush();
	}

	@Test
	void returnsOnlyPublicWaitingRoomsWithinWindowInScheduledOrder() {
		UpcomingAuctionSnapshot snapshot = adapter.findUpcoming(
			NOW,
			ONE_HOUR_LATER,
			2
		);

		assertThat(snapshot.totalCount()).isEqualTo(3L);
		assertThat(snapshot.rows()).extracting("auctionId")
			.containsExactly(9401L, 9402L);
		assertThat(snapshot.rows().getFirst().productTitle())
			.isEqualTo("10분 후 상품");
		assertThat(snapshot.rows().getFirst().imageUrl())
			.isEqualTo("https://cdn.example.com/9401.png");
	}

	@Test
	void returnsEmptyWhenNoRoomFallsWithinWindow() {
		UpcomingAuctionSnapshot snapshot = adapter.findUpcoming(
			Instant.parse("2026-07-22T10:00:00Z"),
			Instant.parse("2026-07-22T11:00:00Z"),
			5
		);

		assertThat(snapshot.rows()).isEmpty();
		assertThat(snapshot.totalCount()).isZero();
	}

	private void execute(String sql) {
		entityManager.createNativeQuery(sql).executeUpdate();
	}
}
