package com.ssafy.bidlive.report.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.ssafy.bidlive.report.application.ReportedAuction;
import com.ssafy.bidlive.report.domain.ReportReasonCount;
import com.ssafy.bidlive.report.domain.ReportedAuctionCursor;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 그룹 집계·상관 서브쿼리·{@code HAVING} 캐스트 커서·파라미터 {@code LIMIT}·{@code IN} 확장을
 * 실제 PostgreSQL로 검증한다 — 목은 SQL을 파싱하지 않아 컬럼 오타와 문법 오류가 전부 통과한다.
 */
@SpringBootTest
@Transactional
class JpaAdminReportQueryAdapterIntegrationTest {

	private static final Instant LATEST = Instant.parse("2026-07-31T10:05:00Z");
	private static final Instant TIED = Instant.parse("2026-07-31T10:03:00Z");

	@Autowired
	private JpaAdminReportQueryAdapter adapter;

	@Autowired
	private EntityManager entityManager;

	@BeforeEach
	void insertFixture() {
		execute("""
			INSERT INTO product_category (category_id, code, name, is_active, display_order)
			VALUES (9101, 'TEST_REPORT_CAT', '테스트 카테고리', TRUE, 9101)
			""");
		execute("""
			INSERT INTO member (member_id, nickname, role, status)
			VALUES
				(9101, '서연123', 'USER', 'ACTIVE'),
				(9102, '신고자1', 'USER', 'ACTIVE'),
				(9103, '신고자2', 'USER', 'ACTIVE'),
				(9104, '신고자3', 'USER', 'ACTIVE')
			""");
		execute("""
			INSERT INTO product (product_id, seller_member_id, category_id, title, description)
			VALUES
				(9101, 9101, 9101, '빈티지 카메라', '설명'),
				(9102, 9101, 9101, '대기 매물', '설명'),
				(9103, 9101, 9101, '종료 매물', '설명'),
				(9104, 9101, 9101, '삭제된 매물', '설명'),
				(9105, 9101, 9101, '동시각 매물', '설명')
			""");
		// 썸네일 서브쿼리가 대표 이미지를 고르는지 본다 — 정렬상 뒤에 있어도 대표가 이겨야 한다
		execute("""
			INSERT INTO product_image (
				product_image_id, product_id, image_url, sort_order, is_thumbnail,
				image_id, owner_member_id, storage_key, content_type, size_bytes
			)
			VALUES
				(9101, 9101, 'https://cdn.example.com/9101-first.png', 1, FALSE,
					gen_random_uuid(), 9101, 'test/report-images/9101', 'image/png', 1),
				(9102, 9101, 'https://cdn.example.com/9101-thumbnail.png', 2, TRUE,
					gen_random_uuid(), 9101, 'test/report-images/9102', 'image/png', 1)
			""");
		execute("""
			INSERT INTO auction_room (
				auction_room_id, product_id, reserve_price, duration_minutes,
				start_type, status, deleted_at
			)
			VALUES
				(9101, 9101, 50000, 15, 'IMMEDIATE', 'LIVE', NULL),
				(9102, 9102, 50000, 15, 'IMMEDIATE', 'WAITING', NULL),
				(9103, 9103, 50000, 15, 'IMMEDIATE', 'ENDED', NULL),
				(9104, 9104, 50000, 15, 'IMMEDIATE', 'LIVE', '2026-07-31T09:00:00Z'),
				(9105, 9105, 50000, 15, 'IMMEDIATE', 'LIVE', NULL)
			""");
		// 같은 경매를 같은 사람이 두 번 신고할 수 없다(uq_report_auction_room_reporter) — 신고자를 나눈다
		// `status`는 프로덕션 접수 경로(`JpaAuctionReportAdapter`)와 같은 `RECEIVED`다. 지금 쿼리는
		// 읽지 않지만, 나중에 필터를 붙이는 사람이 이 픽스처를 근거로 삼는다
		execute("""
			INSERT INTO report (
				report_id, reporter_member_id, target_type, reported_member_id,
				auction_room_id, reason_code, description, status, created_at
			)
			VALUES
				(9101, 9102, 'AUCTION_ROOM', NULL, 9101, 'PROHIBITED_ITEM', NULL, 'RECEIVED',
					'2026-07-31T10:00:00Z'),
				(9102, 9103, 'AUCTION_ROOM', NULL, 9101, 'PROHIBITED_ITEM', NULL, 'RECEIVED',
					'2026-07-31T10:01:00Z'),
				(9103, 9104, 'AUCTION_ROOM', NULL, 9101, 'FALSE_LISTING', NULL, 'RECEIVED',
					'2026-07-31T10:05:00Z'),
				(9104, 9102, 'AUCTION_ROOM', NULL, 9102, 'OTHER', '기타 사유', 'RECEIVED',
					'2026-07-31T10:03:00Z'),
				(9105, 9103, 'AUCTION_ROOM', NULL, 9102, 'FALSE_LISTING', NULL, 'RECEIVED',
					'2026-07-31T09:00:00Z'),
				(9106, 9102, 'AUCTION_ROOM', NULL, 9105, 'INAPPROPRIATE_BROADCAST', NULL, 'RECEIVED',
					'2026-07-31T10:03:00Z'),
				(9107, 9102, 'AUCTION_ROOM', NULL, 9103, 'OTHER', '기타 사유', 'RECEIVED',
					'2026-07-31T10:02:00Z'),
				(9108, 9102, 'AUCTION_ROOM', NULL, 9104, 'OTHER', '기타 사유', 'RECEIVED',
					'2026-07-31T10:10:00Z'),
				(9109, 9102, 'MEMBER', 9101, NULL, 'OTHER', '기타 사유', 'RECEIVED',
					'2026-07-31T10:20:00Z')
			""");
		entityManager.flush();
	}

	@Test
	void aggregatesReportsPerAuctionWithLatestFirst() {
		List<ReportedAuction> items = adapter.findReportedAuctions(false, null, 10);

		// 10:03 동시각이면 auction_room_id 역순 — 9105가 9102보다 앞이다
		assertThat(items).extracting(ReportedAuction::auctionId)
			.containsExactly(9101L, 9105L, 9102L);
		ReportedAuction first = items.getFirst();
		assertThat(first.title()).isEqualTo("빈티지 카메라");
		assertThat(first.thumbnailUrl()).isEqualTo("https://cdn.example.com/9101-thumbnail.png");
		assertThat(first.sellerNickname()).isEqualTo("서연123");
		assertThat(first.status()).isEqualTo("LIVE");
		assertThat(first.reportCount()).isEqualTo(3);
		assertThat(first.latestReportedAt()).isEqualTo(LATEST);
		assertThat(first.reasonCounts()).isEmpty();
	}

	@Test
	void excludesEndedRoomsUnlessIncludeEnded() {
		assertThat(adapter.findReportedAuctions(false, null, 10))
			.extracting(ReportedAuction::auctionId)
			.doesNotContain(9103L);

		assertThat(adapter.findReportedAuctions(true, null, 10))
			.extracting(ReportedAuction::auctionId)
			.containsExactly(9101L, 9105L, 9102L, 9103L);
	}

	/** 판매자가 지운 방이 운영자 목록에 남으면 존재하지 않는 방을 조치하게 된다. */
	@Test
	void excludesSoftDeletedRooms() {
		assertThat(adapter.findReportedAuctions(true, null, 10))
			.extracting(ReportedAuction::auctionId)
			.doesNotContain(9104L);
	}

	@Test
	void pagesWithCursorWithoutSkippingOrRepeating() {
		List<ReportedAuction> firstPage = adapter.findReportedAuctions(false, null, 2);

		// LIMIT :limit 바인딩이 먹지 않으면 여기서 3건이 온다
		assertThat(firstPage).extracting(ReportedAuction::auctionId)
			.containsExactly(9101L, 9105L);

		ReportedAuction last = firstPage.getLast();
		assertThat(last.latestReportedAt()).isEqualTo(TIED);
		List<ReportedAuction> secondPage = adapter.findReportedAuctions(
			false,
			new ReportedAuctionCursor(last.latestReportedAt(), last.auctionId()),
			2
		);

		// 동시각 타이브레이커가 없으면 9102가 통째로 빠지거나 9105가 다시 나온다
		assertThat(secondPage).extracting(ReportedAuction::auctionId)
			.containsExactly(9102L);
	}

	@Test
	void groupsReasonCountsByCountThenCode() {
		Map<Long, List<ReportReasonCount>> grouped =
			adapter.findReasonCounts(List.of(9101L, 9102L));

		assertThat(grouped.get(9101L)).containsExactly(
			new ReportReasonCount("PROHIBITED_ITEM", 2),
			new ReportReasonCount("FALSE_LISTING", 1)
		);
		// 동수면 사유 코드 사전순 — 흔들리면 새로고침마다 순서가 뒤바뀐다
		assertThat(grouped.get(9102L)).containsExactly(
			new ReportReasonCount("FALSE_LISTING", 1),
			new ReportReasonCount("OTHER", 1)
		);
	}

	private void execute(String sql) {
		entityManager.createNativeQuery(sql).executeUpdate();
	}
}
