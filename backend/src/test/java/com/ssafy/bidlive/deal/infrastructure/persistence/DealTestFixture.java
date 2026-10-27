package com.ssafy.bidlive.deal.infrastructure.persistence;

import jakarta.persistence.EntityManager;

/**
 * 거래 통합 테스트용 고정 데이터다.
 *
 * <p>거래 한 건을 만들려면 회원·상품·경매방·참여·입찰·경매결과까지 사슬 전체가 필요하다
 * (`deal.auction_result_id`가 NOT NULL이고 `auction_result`의 SOLD 제약이 낙찰 입찰까지 요구한다).
 * 그 사슬을 테스트마다 다시 쓰지 않도록 한곳에 모았다.
 *
 * <p>ID는 9400번대를 쓴다 — 다른 통합 테스트(9001·9101번대)와 겹치지 않게.
 */
final class DealTestFixture {

	static final long SELLER_ID = 9401L;
	static final long BUYER_ID = 9402L;
	static final long STRANGER_ID = 9403L;

	/** 배송지 입력 대기 (구매자 할 일). `deal_shipping` 행이 아직 없다. */
	static final long WAITING_ADDRESS_DEAL_ID = 9401L;
	/** 발송 대기 (판매자 할 일). */
	static final long WAITING_SHIPMENT_DEAL_ID = 9402L;
	/** 수령 대기 (구매자 할 일). 자동 확정 예정 시각이 있다. */
	static final long WAITING_RECEIPT_DEAL_ID = 9403L;
	/** 완료된 거래 — 어느 쪽 할 일에도 나오면 안 된다. */
	static final long COMPLETED_DEAL_ID = 9404L;

	static final long FINAL_PRICE = 74_000L;

	private DealTestFixture() {
	}

	static void insert(EntityManager entityManager) {
		execute(entityManager, """
			INSERT INTO product_category (category_id, code, name, is_active, display_order)
			VALUES (9401, 'TEST_DEAL_CAT', '거래 테스트', TRUE, 9401)
			""");
		execute(entityManager, """
			INSERT INTO member (member_id, nickname, role, status)
			VALUES
				(9401, '거래판매자', 'USER', 'ACTIVE'),
				(9402, '거래구매자', 'USER', 'ACTIVE'),
				(9403, '거래제3자', 'USER', 'ACTIVE')
			""");
		execute(entityManager, """
			INSERT INTO product (product_id, seller_member_id, category_id, title, description)
			VALUES
				(9401, 9401, 9401, '키크론 Q1 Pro', '설명'),
				(9402, 9401, 9401, '한정판 피규어', '설명'),
				(9403, 9401, 9401, '빈티지 카메라', '설명'),
				(9404, 9401, 9401, '완료된 상품', '설명')
			""");
		// 대표 이미지가 정렬 순서상 뒤에 있어도 대표가 선택되어야 한다
		execute(entityManager, """
			INSERT INTO product_image (
				product_image_id, product_id, image_url, sort_order, is_thumbnail,
				image_id, owner_member_id, storage_key, content_type, size_bytes
			)
			VALUES
				(9401, 9401, 'https://cdn.example.com/9401-first.png', 1, FALSE,
					gen_random_uuid(), 9401, 'test/deal-images/9401', 'image/png', 1),
				(9402, 9401, 'https://cdn.example.com/9401-thumbnail.png', 2, TRUE,
					gen_random_uuid(), 9401, 'test/deal-images/9402', 'image/png', 1)
			""");
		execute(entityManager, """
			INSERT INTO auction_room (
				auction_room_id, product_id, reserve_price, duration_minutes,
				start_type, started_at, ends_at, ended_at, status
			)
			VALUES
				(9401, 9401, 50000, 15, 'IMMEDIATE',
					'2026-07-21T11:00:00Z', '2026-07-21T11:15:00Z',
					'2026-07-21T11:17:13Z', 'ENDED'),
				(9402, 9402, 50000, 15, 'IMMEDIATE',
					'2026-07-20T11:00:00Z', '2026-07-20T11:15:00Z',
					'2026-07-20T12:10:00Z', 'ENDED'),
				(9403, 9403, 50000, 15, 'IMMEDIATE',
					'2026-07-18T10:00:00Z', '2026-07-18T10:15:00Z',
					'2026-07-18T10:30:00Z', 'ENDED'),
				(9404, 9404, 50000, 15, 'IMMEDIATE',
					'2026-07-17T10:00:00Z', '2026-07-17T10:15:00Z',
					'2026-07-17T10:30:00Z', 'ENDED')
			""");
		execute(entityManager, """
			INSERT INTO auction_participant (
				auction_participant_id, auction_room_id, member_id, participation_type
			)
			VALUES
				(9401, 9401, 9402, 'BIDDER'),
				(9402, 9402, 9402, 'BIDDER'),
				(9403, 9403, 9402, 'BIDDER'),
				(9404, 9404, 9402, 'BIDDER')
			""");
		execute(entityManager, """
			INSERT INTO bid (bid_id, auction_participant_id, amount, sequence_no, idempotency_key)
			VALUES
				(9401, 9401, 74000, 1, 'test-deal-bid-9401'),
				(9402, 9402, 74000, 1, 'test-deal-bid-9402'),
				(9403, 9403, 74000, 1, 'test-deal-bid-9403'),
				(9404, 9404, 74000, 1, 'test-deal-bid-9404')
			""");
		execute(entityManager, """
			INSERT INTO auction_result (
				auction_result_id, auction_room_id, result_status, termination_reason,
				winner_participant_id, winning_bid_id, highest_bid_amount, final_price,
				valid_bidder_count, total_bid_count
			)
			VALUES
				(9401, 9401, 'SOLD', 'NORMAL', 9401, 9401, 74000, 74000, 1, 1),
				(9402, 9402, 'SOLD', 'NORMAL', 9402, 9402, 74000, 74000, 1, 1),
				(9403, 9403, 'SOLD', 'NORMAL', 9403, 9403, 74000, 74000, 1, 1),
				(9404, 9404, 'SOLD', 'NORMAL', 9404, 9404, 74000, 74000, 1, 1)
			""");
		// created_at을 명시한다 — 배송지 입력 전 거래의 정렬 키가 이 값이다
		execute(entityManager, """
			INSERT INTO deal (
				deal_id, auction_result_id, seller_member_id, buyer_member_id,
				final_price, escrow_amount, escrow_status, deal_status, created_at
			)
			VALUES
				(9401, 9401, 9401, 9402, 74000, 74000, 'HELD', 'WAITING_ADDRESS',
					'2026-07-21T11:18:00Z'),
				(9402, 9402, 9401, 9402, 74000, 74000, 'HELD', 'WAITING_SHIPMENT',
					'2026-07-20T12:11:00Z'),
				(9403, 9403, 9401, 9402, 74000, 74000, 'HELD', 'WAITING_RECEIPT',
					'2026-07-18T10:31:00Z'),
				(9404, 9404, 9401, 9402, 74000, 74000, 'RELEASED', 'COMPLETED',
					'2026-07-17T10:31:00Z')
			""");
		execute(entityManager, """
			INSERT INTO deal_shipping (
				deal_id, recipient_name, recipient_phone, postal_code, address,
				address_detail, address_submitted_at, shipped_at, auto_confirm_at, updated_at
			)
			VALUES
				(9402, '김서연', '010-1234-5678', '06236', '서울시 강남구', '101동 202호',
					'2026-07-20T12:11:00Z', NULL, NULL, '2026-07-21T00:30:00Z'),
				(9403, '김서연', '010-1234-5678', '06236', '서울시 강남구', '101동 202호',
					'2026-07-18T10:31:00Z', '2026-07-20T01:00:00Z', '2026-07-27T01:00:00Z',
					'2026-07-20T01:00:00Z')
			""");
		entityManager.flush();
	}

	static void execute(EntityManager entityManager, String sql) {
		entityManager.createNativeQuery(sql).executeUpdate();
	}
}
