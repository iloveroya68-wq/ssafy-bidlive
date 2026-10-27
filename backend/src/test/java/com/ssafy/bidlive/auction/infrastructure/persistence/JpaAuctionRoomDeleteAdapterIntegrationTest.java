package com.ssafy.bidlive.auction.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.bidlive.auction.application.AuctionRoomDeletionTarget;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** 삭제 판정 조회와 논리 삭제 표시를 실제 PostgreSQL로 검증한다. */
@SpringBootTest
@Transactional
class JpaAuctionRoomDeleteAdapterIntegrationTest {

	@Autowired
	private JpaAuctionRoomDeleteAdapter adapter;

	@Autowired
	private EntityManager entityManager;

	@BeforeEach
	void insertFixture() {
		execute("""
			INSERT INTO product_category (category_id, code, name, is_active, display_order)
			VALUES (9101, 'TEST_DELETE_CAT', '삭제 테스트', TRUE, 9101)
			""");
		execute("""
			INSERT INTO member (member_id, nickname, role, status)
			VALUES
				(9101, '삭제판매자', 'USER', 'ACTIVE'),
				(9102, '삭제참여자', 'USER', 'ACTIVE')
			""");
		execute("""
			INSERT INTO product (product_id, seller_member_id, category_id, title, description)
			VALUES
				(9101, 9101, 9101, '삭제 대상 방', '설명'),
				(9102, 9101, 9101, '참여 있는 방', '설명')
			""");
		execute("""
			INSERT INTO auction_room (
				auction_room_id, product_id, reserve_price, duration_minutes,
				start_type, status
			)
			VALUES
				(9101, 9101, 50000, 15, 'IMMEDIATE', 'WAITING'),
				(9102, 9102, 50000, 15, 'IMMEDIATE', 'WAITING')
			""");
		execute("""
			INSERT INTO auction_participant (
				auction_participant_id, auction_room_id, member_id, participation_type
			)
			VALUES (9102, 9102, 9102, 'BIDDER')
			""");
		entityManager.flush();
	}

	@Test
	void readsSellerStatusAndParticipationInOneQuery() {
		AuctionRoomDeletionTarget target = adapter.findDeletionTarget(9101L).orElseThrow();

		assertThat(target.sellerId()).isEqualTo(9101L);
		assertThat(target.status()).isEqualTo("WAITING");
		assertThat(target.deleted()).isFalse();
		assertThat(target.hasParticipation()).isFalse();
	}

	@Test
	void detectsParticipation() {
		assertThat(adapter.findDeletionTarget(9102L).orElseThrow().hasParticipation()).isTrue();
	}

	@Test
	void returnsEmptyForMissingRoom() {
		assertThat(adapter.findDeletionTarget(999_999L)).isEmpty();
	}

	@Test
	void marksDeletedOnceAndReportsZeroOnSecondAttempt() {
		assertThat(adapter.markDeleted(9101L)).isEqualTo(1);
		entityManager.flush();
		entityManager.clear();

		assertThat(adapter.findDeletionTarget(9101L).orElseThrow().deleted()).isTrue();
		// 두 번째 요청은 이미 지워진 행을 건드리지 않는다 — 서비스가 이걸로 경합을 판정한다
		assertThat(adapter.markDeleted(9101L)).isZero();
	}

	private void execute(String sql) {
		entityManager.createNativeQuery(sql).executeUpdate();
	}
}
