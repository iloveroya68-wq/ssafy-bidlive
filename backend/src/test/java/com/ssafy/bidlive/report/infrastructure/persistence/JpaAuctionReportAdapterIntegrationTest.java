package com.ssafy.bidlive.report.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import com.ssafy.bidlive.report.application.AuctionReportCommand;
import com.ssafy.bidlive.report.application.AuctionReportResult;
import com.ssafy.bidlive.report.application.AuctionReportTarget;
import com.ssafy.bidlive.report.domain.AuctionReportReason;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** 신고 판정 조회와 접수 저장을 실제 PostgreSQL로 검증한다. */
@SpringBootTest
@Transactional
class JpaAuctionReportAdapterIntegrationTest {

	private static final long SELLER_ID = 9601L;
	private static final long REPORTER_ID = 9602L;
	private static final long LIVE_AUCTION_ID = 9601L;
	private static final long DELETED_AUCTION_ID = 9602L;
	private static final Instant RECEIVED_AT = Instant.parse("2026-07-22T06:00:00Z");

	@Autowired
	private JpaAuctionReportAdapter adapter;

	@Autowired
	private EntityManager entityManager;

	@BeforeEach
	void insertFixture() {
		execute("""
			INSERT INTO product_category (category_id, code, name, is_active, display_order)
			VALUES (9601, 'TEST_REPORT_CAT', '신고 테스트', TRUE, 9601)
			""");
		execute("""
			INSERT INTO member (member_id, nickname, role, status)
			VALUES
				(9601, '신고판매자', 'USER', 'ACTIVE'),
				(9602, '신고자', 'USER', 'ACTIVE')
			""");
		execute("""
			INSERT INTO product (product_id, seller_member_id, category_id, title, description)
			VALUES
				(9601, 9601, 9601, '신고 대상 상품', '설명'),
				(9602, 9601, 9601, '삭제된 방 상품', '설명')
			""");
		execute("""
			INSERT INTO auction_room (
				auction_room_id, product_id, reserve_price, duration_minutes,
				start_type, status, deleted_at
			)
			VALUES
				(9601, 9601, 50000, 15, 'IMMEDIATE', 'LIVE', NULL),
				(9602, 9602, 50000, 15, 'IMMEDIATE', 'WAITING', '2026-07-21T00:00:00Z')
			""");
		execute("""
			UPDATE auction_room SET visibility = 'PRIVATE' WHERE auction_room_id = 9602
			""");
		entityManager.flush();
	}

	@Test
	void readsSellerStatusAndDeletionInOneQuery() {
		AuctionReportTarget target = adapter.findReportTarget(LIVE_AUCTION_ID).orElseThrow();

		assertThat(target.auctionId()).isEqualTo(LIVE_AUCTION_ID);
		assertThat(target.sellerId()).isEqualTo(SELLER_ID);
		assertThat(target.status()).isEqualTo("LIVE");
		assertThat(target.visibility()).isEqualTo("PUBLIC");
		assertThat(target.deleted()).isFalse();
	}

	@Test
	void marksDeletedRoom() {
		assertThat(adapter.findReportTarget(DELETED_AUCTION_ID).orElseThrow().deleted()).isTrue();
	}

	@Test
	void readsPrivateVisibility() {
		assertThat(adapter.findReportTarget(DELETED_AUCTION_ID).orElseThrow().visibility())
			.isEqualTo("PRIVATE");
	}

	@Test
	void returnsEmptyForMissingAuction() {
		assertThat(adapter.findReportTarget(999_999L)).isEmpty();
	}

	@Test
	void savesReportAsAuctionRoomTargetWithReceivedStatus() {
		AuctionReportResult result = save(
			LIVE_AUCTION_ID,
			REPORTER_ID,
			new AuctionReportCommand(
				AuctionReportReason.INAPPROPRIATE_BROADCAST,
				"부적절한 발언을 반복했습니다."
			),
			RECEIVED_AT
		);

		assertThat(result.reportId()).isPositive();
		assertThat(result.status()).isEqualTo("RECEIVED");
		assertThat(result.receivedAt()).isEqualTo(RECEIVED_AT);

		Object[] row = (Object[]) entityManager.createNativeQuery("""
			SELECT
				target_type,
				auction_room_id,
				reported_member_id,
				deal_id,
				reason_code,
				description,
				status
			FROM report
			WHERE report_id = :reportId
			""")
			.setParameter("reportId", result.reportId())
			.getSingleResult();

		assertThat(row[0]).isEqualTo("AUCTION_ROOM");
		assertThat(((Number) row[1]).longValue()).isEqualTo(LIVE_AUCTION_ID);
		// ck_report_target_count가 대상 컬럼 하나만 채우도록 강제한다
		assertThat(row[2]).isNull();
		assertThat(row[3]).isNull();
		assertThat(row[4]).isEqualTo("INAPPROPRIATE_BROADCAST");
		assertThat(row[5]).isEqualTo("부적절한 발언을 반복했습니다.");
		assertThat(row[6]).isEqualTo("RECEIVED");
	}

	/** 설명 없는 사유는 컬럼에 NULL로 들어간다 — 빈 문자열을 남기지 않는다. */
	@Test
	void savesNullDescriptionWhenAbsent() {
		AuctionReportResult result = save(
			LIVE_AUCTION_ID,
			REPORTER_ID,
			new AuctionReportCommand(AuctionReportReason.FALSE_LISTING, null),
			RECEIVED_AT
		);

		Object description = entityManager.createNativeQuery("""
			SELECT description FROM report WHERE report_id = :reportId
			""")
			.setParameter("reportId", result.reportId())
			.getSingleResult();

		assertThat(description).isNull();
	}

	@Test
	void detectsDuplicateReportFromSameMemberOnly() {
		assertThat(adapter.existsReport(LIVE_AUCTION_ID, REPORTER_ID)).isFalse();

		save(
			LIVE_AUCTION_ID,
			REPORTER_ID,
			new AuctionReportCommand(AuctionReportReason.FALSE_LISTING, null),
			RECEIVED_AT
		);

		assertThat(adapter.existsReport(LIVE_AUCTION_ID, REPORTER_ID)).isTrue();
		// 다른 회원과 다른 경매는 영향받지 않는다
		assertThat(adapter.existsReport(LIVE_AUCTION_ID, SELLER_ID)).isFalse();
		assertThat(adapter.existsReport(DELETED_AUCTION_ID, REPORTER_ID)).isFalse();
	}

	/**
	 * 같은 회원이 같은 경매를 두 번 신고하면 두 번째는 저장되지 않는다. 앱 EXISTS를 우회해
	 * 어댑터를 직접 두 번 호출하는 것이 요점이다 — 막는 주체가 유니크 인덱스(V16)임을 확인한다.
	 */
	@Test
	void rejectsSecondReportFromSameMemberOnSameAuction() {
		assertThat(adapter.save(
			LIVE_AUCTION_ID,
			REPORTER_ID,
			new AuctionReportCommand(AuctionReportReason.FALSE_LISTING, null),
			RECEIVED_AT
		)).isPresent();

		assertThat(adapter.save(
			LIVE_AUCTION_ID,
			REPORTER_ID,
			new AuctionReportCommand(AuctionReportReason.PROHIBITED_ITEM, null),
			RECEIVED_AT
		)).isEmpty();

		Number count = (Number) entityManager.createNativeQuery("""
			SELECT COUNT(*) FROM report WHERE auction_room_id = :auctionId
			""")
			.setParameter("auctionId", LIVE_AUCTION_ID)
			.getSingleResult();
		assertThat(count.longValue()).isEqualTo(1L);
	}

	/** 인덱스는 (경매, 신고자) 쌍에만 걸린다 — 다른 방·다른 회원은 막히지 않는다. */
	@Test
	void allowsOtherReporterAndOtherAuction() {
		save(LIVE_AUCTION_ID, REPORTER_ID,
			new AuctionReportCommand(AuctionReportReason.FALSE_LISTING, null), RECEIVED_AT);

		assertThat(adapter.save(LIVE_AUCTION_ID, SELLER_ID,
			new AuctionReportCommand(AuctionReportReason.FALSE_LISTING, null), RECEIVED_AT))
			.isPresent();
		assertThat(adapter.save(DELETED_AUCTION_ID, REPORTER_ID,
			new AuctionReportCommand(AuctionReportReason.FALSE_LISTING, null), RECEIVED_AT))
			.isPresent();
	}

	private AuctionReportResult save(
		long auctionId,
		long reporterMemberId,
		AuctionReportCommand command,
		Instant receivedAt
	) {
		return adapter.save(auctionId, reporterMemberId, command, receivedAt).orElseThrow();
	}

	private void execute(String sql) {
		entityManager.createNativeQuery(sql).executeUpdate();
	}
}
