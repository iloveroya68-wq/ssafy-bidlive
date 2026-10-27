package com.ssafy.bidlive.auction.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.ssafy.bidlive.auction.application.AuctionRoomListCriteria;
import com.ssafy.bidlive.auction.application.AuctionRoomListSlice;
import com.ssafy.bidlive.auction.application.AuctionRoomRow;
import com.ssafy.bidlive.auction.application.AuctionRoomStatusCounts;
import com.ssafy.bidlive.auction.domain.AuctionRoomListCursor;
import com.ssafy.bidlive.auction.domain.AuctionStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상태별 정렬 정책, 커서 페이징 경계, 집계 결합과 검색어 이스케이프를 실제 PostgreSQL로 검증한다.
 */
@SpringBootTest
@Transactional
class JpaAuctionRoomListQueryAdapterIntegrationTest {

	private static final String SCOPE_KEYWORD = "테스트경매";

	@Autowired
	private JpaAuctionRoomListQueryAdapter adapter;

	@Autowired
	private EntityManager entityManager;

	@BeforeEach
	void insertFixture() {
		execute("""
			INSERT INTO product_category (category_id, code, name, is_active, display_order)
			VALUES
				(9001, 'TEST_ROOM_CAT', '테스트 카테고리', TRUE, 9001),
				(9002, 'TEST_OTHER_CAT', '다른 테스트 카테고리', TRUE, 9002)
			""");
		execute("""
			INSERT INTO member (member_id, nickname, role, status)
			VALUES
				(9001, '서연123', 'USER', 'ACTIVE'),
				(9002, '입찰자테스트', 'USER', 'ACTIVE'),
				(9003, '예측자테스트', 'USER', 'ACTIVE')
			""");
		execute("""
			INSERT INTO product (product_id, seller_member_id, category_id, title, description)
			VALUES
				(9001, 9001, 9001, '테스트경매 라이브늦게', '설명'),
				(9002, 9001, 9001, '테스트경매 라이브빨리', '설명'),
				(9003, 9001, 9001, '테스트경매 예약', '설명'),
				(9004, 9001, 9002, '테스트경매 즉시', '설명'),
				(9005, 9001, 9001, '테스트경매 낙찰', '설명'),
				(9006, 9001, 9001, '테스트경매 유찰', '설명')
			""");
		// 대표 이미지가 정렬 순서상 뒤에 있어도 대표가 선택되어야 한다
		execute("""
			INSERT INTO product_image (
				product_image_id, product_id, image_url, sort_order, is_thumbnail,
				image_id, owner_member_id, storage_key, content_type, size_bytes
			)
			VALUES
				(9001, 9001, 'https://cdn.example.com/9001-first.png', 1, FALSE,
					gen_random_uuid(), 9001, 'test/product-images/9001', 'image/png', 1),
				(9002, 9001, 'https://cdn.example.com/9001-thumbnail.png', 2, TRUE,
					gen_random_uuid(), 9001, 'test/product-images/9002', 'image/png', 1)
			""");
		execute("""
			INSERT INTO auction_room (
				auction_room_id, product_id, reserve_price, duration_minutes,
				start_type, scheduled_at, started_at, ends_at, ended_at, status
			)
			VALUES
				(9001, 9001, 50000, 15, 'IMMEDIATE', NULL,
					'2026-07-21T10:55:00Z', '2026-07-21T11:10:00Z', NULL, 'LIVE'),
				(9002, 9002, 50000, 15, 'IMMEDIATE', NULL,
					'2026-07-21T10:55:00Z', '2026-07-21T11:05:00Z', NULL, 'LIVE'),
				(9003, 9003, 50000, 15, 'SCHEDULED', '2026-07-21T12:00:00Z',
					NULL, NULL, NULL, 'WAITING'),
				(9004, 9004, 50000, 15, 'IMMEDIATE', NULL,
					NULL, NULL, NULL, 'WAITING'),
				(9005, 9005, 50000, 15, 'IMMEDIATE', NULL,
					'2026-07-21T09:00:00Z', '2026-07-21T09:15:00Z',
					'2026-07-21T10:00:00Z', 'ENDED'),
				(9006, 9006, 50000, 15, 'IMMEDIATE', NULL,
					'2026-07-21T08:00:00Z', '2026-07-21T08:15:00Z',
					'2026-07-21T09:00:00Z', 'ENDED')
			""");
		execute("""
			INSERT INTO auction_participant (
				auction_participant_id, auction_room_id, member_id, participation_type
			)
			VALUES
				(9001, 9001, 9002, 'BIDDER'),
				(9002, 9001, 9003, 'PREDICTOR'),
				(9005, 9005, 9002, 'BIDDER')
			""");
		execute("""
			INSERT INTO bid (bid_id, auction_participant_id, amount, sequence_no, idempotency_key)
			VALUES
				(9001, 9001, 10000, 1, 'test-bid-9001'),
				(9002, 9001, 20000, 2, 'test-bid-9002'),
				(9005, 9005, 70000, 1, 'test-bid-9005')
			""");
		execute("""
			INSERT INTO prediction (
				prediction_id, auction_participant_id, predicted_price,
				stake_amount, idempotency_key
			)
			VALUES (9002, 9002, 50000, 1000, 'test-prediction-9002')
			""");
		execute("""
			INSERT INTO auction_result (
				auction_result_id, auction_room_id, result_status, termination_reason,
				winner_participant_id, winning_bid_id, highest_bid_amount,
				final_price, valid_bidder_count, total_bid_count
			)
			VALUES
				(9005, 9005, 'SOLD', 'TIME_OVER', 9005, 9005, 70000, 70000, 1, 1),
				(9006, 9006, 'NO_SALE', 'NO_BID', NULL, NULL, NULL, NULL, 0, 0)
			""");
		entityManager.flush();
	}

	@Test
	void sortsLiveRoomsByClosingTimeAscending() {
		List<AuctionRoomRow> rows = adapter.findRooms(criteria(AuctionStatus.LIVE, null, 8)).rows();

		assertThat(rows).extracting(AuctionRoomRow::auctionId)
			.containsExactly(9002L, 9001L);
	}

	@Test
	void putsWaitingRoomsWithoutScheduleLast() {
		List<AuctionRoomRow> rows =
			adapter.findRooms(criteria(AuctionStatus.WAITING, null, 8)).rows();

		assertThat(rows).extracting(AuctionRoomRow::auctionId)
			.containsExactly(9003L, 9004L);
		assertThat(rows.get(1).scheduledAt()).isNull();
	}

	@Test
	void sortsEndedRoomsByMostRecentlyEndedAndExposesSoldPriceOnlyWhenSold() {
		List<AuctionRoomRow> rows =
			adapter.findRooms(criteria(AuctionStatus.ENDED, null, 8)).rows();

		assertThat(rows).extracting(AuctionRoomRow::auctionId)
			.containsExactly(9005L, 9006L);
		assertThat(rows.get(0).soldPrice()).isEqualTo(70_000L);
		assertThat(rows.get(0).endedAt()).isNotNull();
		assertThat(rows.get(1).soldPrice()).isNull();
	}

	@Test
	void joinsBidAndPredictionAggregates() {
		List<AuctionRoomRow> rows = adapter.findRooms(criteria(AuctionStatus.LIVE, null, 8)).rows();

		AuctionRoomRow withActivity = rows.stream()
			.filter(row -> row.auctionId() == 9001L)
			.findFirst()
			.orElseThrow();
		AuctionRoomRow withoutActivity = rows.stream()
			.filter(row -> row.auctionId() == 9002L)
			.findFirst()
			.orElseThrow();

		assertThat(withActivity.bidCount()).isEqualTo(2L);
		assertThat(withActivity.predictionParticipantCount()).isEqualTo(1L);
		assertThat(withoutActivity.bidCount()).isZero();
		assertThat(withoutActivity.predictionParticipantCount()).isZero();
		assertThat(withoutActivity.bidParticipantCount()).isZero();
	}

	/** 같은 회원이 두 번 입찰한 방에서 건수는 2, 참여 인원은 1이어야 두 축이 구분된다. */
	@Test
	void countsRepeatedBidsFromSameMemberAsSingleParticipant() {
		AuctionRoomRow withActivity = adapter.findRooms(criteria(AuctionStatus.LIVE, null, 8))
			.rows()
			.stream()
			.filter(row -> row.auctionId() == 9001L)
			.findFirst()
			.orElseThrow();

		assertThat(withActivity.bidCount()).isEqualTo(2L);
		assertThat(withActivity.bidParticipantCount()).isEqualTo(1L);
	}

	@Test
	void picksRepresentativeImageAndKeepsSellerNicknameRaw() {
		List<AuctionRoomRow> rows = adapter.findRooms(criteria(AuctionStatus.LIVE, null, 8)).rows();

		AuctionRoomRow withImage = rows.stream()
			.filter(row -> row.auctionId() == 9001L)
			.findFirst()
			.orElseThrow();

		assertThat(withImage.thumbnailUrl())
			.isEqualTo("https://cdn.example.com/9001-thumbnail.png");
		assertThat(withImage.categoryCode()).isEqualTo("TEST_ROOM_CAT");
		assertThat(withImage.sellerNickname()).isEqualTo("서연123");
		assertThat(rows.stream()
			.filter(row -> row.auctionId() == 9002L)
			.findFirst()
			.orElseThrow()
			.thumbnailUrl()).isNull();
	}

	@Test
	void pagesThroughLiveRoomsWithoutSkippingOrRepeating() {
		AuctionRoomListSlice firstPage = adapter.findRooms(criteria(AuctionStatus.LIVE, null, 1));

		assertThat(firstPage.rows()).extracting(AuctionRoomRow::auctionId).containsExactly(9002L);
		assertThat(firstPage.hasNext()).isTrue();
		assertThat(firstPage.nextCursor()).isNotNull();

		AuctionRoomListSlice secondPage = adapter.findRooms(criteria(
			AuctionStatus.LIVE,
			AuctionRoomListCursor.decode(firstPage.nextCursor()),
			1
		));

		assertThat(secondPage.rows()).extracting(AuctionRoomRow::auctionId).containsExactly(9001L);
		assertThat(secondPage.hasNext()).isFalse();
		assertThat(secondPage.nextCursor()).isNull();
	}

	@Test
	void pagesPastWaitingRoomsWithoutScheduleUsingInfinitySentinel() {
		AuctionRoomListSlice firstPage =
			adapter.findRooms(criteria(AuctionStatus.WAITING, null, 1));
		AuctionRoomListSlice secondPage = adapter.findRooms(criteria(
			AuctionStatus.WAITING,
			AuctionRoomListCursor.decode(firstPage.nextCursor()),
			1
		));

		assertThat(secondPage.rows()).extracting(AuctionRoomRow::auctionId).containsExactly(9004L);
		assertThat(secondPage.hasNext()).isFalse();
	}

	@Test
	void countsIgnoreStatusFilterButApplyKeyword() {
		AuctionRoomStatusCounts counts =
			adapter.countByStatus(criteria(AuctionStatus.LIVE, null, 8));

		assertThat(counts.live()).isEqualTo(2L);
		assertThat(counts.waiting()).isEqualTo(2L);
	}

	@Test
	void countsApplyCategoryFilter() {
		AuctionRoomStatusCounts counts = adapter.countByStatus(new AuctionRoomListCriteria(
			AuctionStatus.LIVE,
			9001L,
			SCOPE_KEYWORD,
			null,
			8
		));

		assertThat(counts.live()).isEqualTo(2L);
		assertThat(counts.waiting()).isEqualTo(1L);
	}

	@Test
	void treatsWildcardCharactersInKeywordAsLiterals() {
		AuctionRoomListSlice slice = adapter.findRooms(new AuctionRoomListCriteria(
			null,
			null,
			"%",
			null,
			8
		));

		assertThat(slice.rows()).isEmpty();
	}

	/**
	 * 검열로 차단된 상품이 공개 목록에 뜨면 시연장에서 부적절 이미지가 그대로 보인다.
	 * `PENDING`(등록 직후)·`FLAGGED`(AI 미확정)는 남기고 `BLOCKED`만 뺀다.
	 */
	@Test
	void excludesBlockedProductsFromListAndCounts() {
		execute("""
			INSERT INTO product (
				product_id, seller_member_id, category_id, title, description, moderation_status
			)
			VALUES
				(9007, 9001, 9001, '테스트경매 차단됨', '설명', 'BLOCKED'),
				(9008, 9001, 9001, '테스트경매 검토중', '설명', 'FLAGGED')
			""");
		execute("""
			INSERT INTO auction_room (
				auction_room_id, product_id, reserve_price, duration_minutes,
				start_type, scheduled_at, started_at, ends_at, ended_at, status
			)
			VALUES
				(9007, 9007, 50000, 15, 'IMMEDIATE', NULL,
					'2026-07-21T10:55:00Z', '2026-07-21T11:20:00Z', NULL, 'LIVE'),
				(9008, 9008, 50000, 15, 'IMMEDIATE', NULL,
					'2026-07-21T10:55:00Z', '2026-07-21T11:25:00Z', NULL, 'LIVE')
			""");
		entityManager.flush();

		List<AuctionRoomRow> rows = adapter.findRooms(criteria(AuctionStatus.LIVE, null, 8)).rows();

		assertThat(rows).extracting(AuctionRoomRow::auctionId)
			.contains(9008L)
			.doesNotContain(9007L);
		// 목록에서 뺐으면 탭 숫자도 같이 빠져야 한다 — 안 맞으면 카드 개수와 어긋난다
		assertThat(adapter.countByStatus(criteria(AuctionStatus.LIVE, null, 8)).live())
			.isEqualTo(3L);
	}

	@Test
	void excludesPrivateAndDeletedRoomsFromListAndCounts() {
		execute("""
			UPDATE auction_room
			SET visibility = 'PRIVATE'
			WHERE auction_room_id = 9001
			""");
		execute("""
			UPDATE auction_room
			SET deleted_at = '2026-07-21T11:00:00Z'
			WHERE auction_room_id = 9002
			""");
		entityManager.flush();

		List<AuctionRoomRow> rows =
			adapter.findRooms(criteria(AuctionStatus.LIVE, null, 8)).rows();
		AuctionRoomStatusCounts counts =
			adapter.countByStatus(criteria(AuctionStatus.LIVE, null, 8));

		assertThat(rows).isEmpty();
		assertThat(counts.live()).isZero();
		assertThat(counts.waiting()).isEqualTo(2L);
	}

	@Test
	void excludesBlockedImageFromThumbnailSelection() {
		// 대표 이미지가 차단되면 그 장을 고르지 말고 남은 장으로 내려가야 한다
		execute("""
			UPDATE product_image SET moderation_status = 'BLOCKED' WHERE product_image_id = 9002
			""");
		entityManager.flush();

		List<AuctionRoomRow> rows = adapter.findRooms(criteria(AuctionStatus.LIVE, null, 8)).rows();

		assertThat(rows.stream()
			.filter(row -> row.auctionId() == 9001L)
			.findFirst()
			.orElseThrow()
			.thumbnailUrl()).isEqualTo("https://cdn.example.com/9001-first.png");
	}

	/**
	 * 논리 삭제(ROOM-04)된 방이 목록에 남으면 판매자가 지웠는데도 화면에 계속 보인다.
	 * 목록과 집계 양쪽에서 빠져야 한다 — 한쪽만 빠지면 탭 숫자와 카드 개수가 어긋난다.
	 */
	@Test
	void excludesSoftDeletedRoomsFromListAndCounts() {
		execute("UPDATE auction_room SET deleted_at = NOW() WHERE auction_room_id = 9001");
		entityManager.flush();

		List<AuctionRoomRow> rows = adapter.findRooms(criteria(AuctionStatus.LIVE, null, 8)).rows();

		assertThat(rows).extracting(AuctionRoomRow::auctionId)
			.containsExactly(9002L);
		assertThat(adapter.countByStatus(criteria(AuctionStatus.LIVE, null, 8)).live())
			.isEqualTo(1L);
	}

	@Test
	void reportsCategoryExistence() {
		assertThat(adapter.existsCategory(9001L)).isTrue();
		assertThat(adapter.existsCategory(999_999L)).isFalse();
	}

	private static AuctionRoomListCriteria criteria(
		AuctionStatus status,
		AuctionRoomListCursor cursor,
		int size
	) {
		return new AuctionRoomListCriteria(status, null, SCOPE_KEYWORD, cursor, size);
	}

	private void execute(String sql) {
		entityManager.createNativeQuery(sql).executeUpdate();
	}
}
