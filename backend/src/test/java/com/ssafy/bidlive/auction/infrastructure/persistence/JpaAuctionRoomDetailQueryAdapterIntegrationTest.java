package com.ssafy.bidlive.auction.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.bidlive.auction.application.AuctionRoomDetailRow;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** 실제 PostgreSQL 조인과 활성 알림 신청 중복 제거 및 공개 상태 필드를 검증합니다. */
@SpringBootTest
@Transactional
class JpaAuctionRoomDetailQueryAdapterIntegrationTest {

	@Autowired
	private JpaAuctionRoomDetailQueryAdapter adapter;

	@Autowired
	private EntityManager entityManager;

	@BeforeEach
	void insertFixture() {
		execute("""
			INSERT INTO product_category (category_id, code, name, is_active, display_order)
			VALUES (9301, 'TEST_DETAIL_CAT', '상세 테스트', TRUE, 9301)
			""");
		execute("""
			INSERT INTO member (member_id, nickname, role, status)
			VALUES
				(9301, '판매왕', 'USER', 'ACTIVE'),
				(9302, '알림회원1', 'USER', 'ACTIVE'),
				(9303, '알림회원2', 'USER', 'ACTIVE')
			""");
		execute("""
			INSERT INTO product (
				product_id, seller_member_id, category_id, title, description,
				moderation_status
			)
			VALUES (9301, 9301, 9301, '상세 테스트 상품', '상세 설명', 'PENDING')
			""");
		execute("""
			INSERT INTO product_image (
				product_image_id, product_id, image_url, sort_order, is_thumbnail,
				image_id, owner_member_id, storage_key, content_type, size_bytes
			)
			VALUES (
				9301, 9301, 'https://cdn.example.com/detail.png', 1, TRUE,
				gen_random_uuid(), 9301, 'test/product-images/detail', 'image/png', 10
			)
			""");
		execute("""
			INSERT INTO auction_room (
				auction_room_id, product_id, reserve_price, duration_minutes,
				start_type, scheduled_at, status
			)
			VALUES (
				9301, 9301, 50000, 20, 'SCHEDULED',
				'2026-07-21T11:00:00Z', 'WAITING'
			)
			""");
		execute("""
			INSERT INTO auction_room_notification_subscription (
				auction_room_notification_subscription_id, auction_room_id,
				member_id, is_active
			)
			VALUES
				(9301, 9301, 9302, TRUE),
				(9302, 9301, 9303, FALSE)
			""");
		entityManager.flush();
	}

	@Test
	void joinsRoomProductSellerImageAndActiveNotificationCount() {
		AuctionRoomDetailRow row = adapter.findByAuctionId(9301L).orElseThrow();

		assertThat(row.sellerId()).isEqualTo(9301L);
		assertThat(row.productTitle()).isEqualTo("상세 테스트 상품");
		assertThat(row.productDescription()).isEqualTo("상세 설명");
		assertThat(row.productCategoryCode()).isEqualTo("TEST_DETAIL_CAT");
		assertThat(row.productImageUrl())
			.isEqualTo("https://cdn.example.com/detail.png");
		assertThat(row.sellerNickname()).isEqualTo("판매왕");
		assertThat(row.visibility()).isEqualTo("PUBLIC");
		assertThat(row.deletedAt()).isNull();
		assertThat(row.notificationCount()).isEqualTo(1);
	}

	@Test
	void exposesPrivateAndDeletedFlagsForApplicationPolicy() {
		execute("""
			UPDATE auction_room
			SET visibility = 'PRIVATE', deleted_at = '2026-07-21T12:00:00Z'
			WHERE auction_room_id = 9301
			""");
		entityManager.flush();

		AuctionRoomDetailRow row = adapter.findByAuctionId(9301L).orElseThrow();

		assertThat(row.visibility()).isEqualTo("PRIVATE");
		assertThat(row.deletedAt()).isNotNull();
	}

	@Test
	void returnsEmptyForUnknownAuction() {
		assertThat(adapter.findByAuctionId(999_999L)).isEmpty();
	}

	private void execute(String sql) {
		entityManager.createNativeQuery(sql).executeUpdate();
	}
}
