package com.ssafy.bidlive.member.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.bidlive.common.security.JwtTokenProvider;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.MemberRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 실제 JWT와 PostgreSQL을 거쳐 판매자 본인의 개최 경매 응답과 커서를 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HostedAuctionQueryControllerIntegrationTest {

	private static final String PATH =
		"/api/v1/members/me/auctions/hosted";
	private static final long SELLER_ID = 98401L;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private EntityManager entityManager;

	@BeforeEach
	void insertFixture() {
		execute("""
			INSERT INTO product_category (
				category_id, code, name, is_active, display_order
			)
			VALUES (98401, 'HOSTED_TEST', '개최 이력 테스트', TRUE, 98401)
			""");
		execute("""
			INSERT INTO member (member_id, nickname, role, status)
			VALUES
				(98401, '판매자테스트', 'USER', 'ACTIVE'),
				(98402, '준호123', 'USER', 'ACTIVE'),
				(98403, '예측자테스트', 'USER', 'ACTIVE'),
				(98404, '다른판매자', 'USER', 'ACTIVE')
			""");
		execute("""
			INSERT INTO product (
				product_id, seller_member_id, category_id, title, description
			)
			VALUES
				(98401, 98401, 98401, '키크론 Q1 Pro', '설명'),
				(98402, 98401, 98401, '빈티지 카메라', '설명'),
				(98403, 98404, 98401, '다른 판매자 상품', '설명'),
				(98404, 98401, 98401, '지운 방 상품', '설명')
			""");
		execute("""
			INSERT INTO product_image (
				product_image_id, product_id, image_url, sort_order,
				is_thumbnail, image_id, owner_member_id, storage_key,
				content_type, size_bytes
			)
			VALUES (
				98401, 98401, 'https://cdn.example.com/products/abc.jpg',
				1, TRUE, gen_random_uuid(), 98401,
				'test/product-images/98401', 'image/jpeg', 1
			)
			""");
		execute("""
			INSERT INTO auction_room (
				auction_room_id, product_id, reserve_price, duration_minutes,
				start_type, scheduled_at, started_at, ends_at, ended_at, status
			)
			VALUES
				(98401, 98401, 50000, 15, 'SCHEDULED',
					'2026-07-23T11:00:00Z', '2026-07-23T11:02:13Z',
					'2026-07-23T11:17:13Z', '2026-07-23T11:17:13Z', 'ENDED'),
				(98402, 98402, 70000, 20, 'SCHEDULED',
					'2026-07-22T11:00:00Z', NULL, NULL, NULL, 'WAITING'),
				(98403, 98403, 30000, 10, 'SCHEDULED',
					'2026-07-24T11:00:00Z', NULL, NULL, NULL, 'WAITING')
			""");
		// 같은 판매자가 시작 전에 지운 방. 가장 오래된 시각이라 남은 두 건의 순서·커서를
		// 건드리지 않는다 — 필터가 없으면 items가 3건이 되어 첫 단언부터 깨진다.
		execute("""
			INSERT INTO auction_room (
				auction_room_id, product_id, reserve_price, duration_minutes,
				start_type, scheduled_at, started_at, ends_at, ended_at,
				status, deleted_at
			)
			VALUES (
				98404, 98404, 40000, 15, 'SCHEDULED',
				'2026-07-21T11:00:00Z', NULL, NULL, NULL,
				'WAITING', '2026-07-21T12:00:00Z'
			)
			""");
		execute("""
			INSERT INTO auction_participant (
				auction_participant_id, auction_room_id, member_id,
				participation_type
			)
			VALUES
				(98401, 98401, 98402, 'BIDDER'),
				(98402, 98401, 98403, 'PREDICTOR')
			""");
		execute("""
			INSERT INTO bid (
				bid_id, auction_participant_id, amount, sequence_no,
				idempotency_key
			)
			VALUES
				(98401, 98401, 74000, 1, 'hosted-test-bid-98401'),
				(98402, 98401, 82000, 2, 'hosted-test-bid-98402')
			""");
		execute("""
			INSERT INTO prediction (
				prediction_id, auction_participant_id, predicted_price,
				stake_amount, idempotency_key
			)
			VALUES (
				98401, 98402, 74000, 300, 'hosted-test-prediction-98401'
			)
			""");
		execute("""
			INSERT INTO auction_result (
				auction_result_id, auction_room_id, result_status,
				termination_reason, winner_participant_id, winning_bid_id,
				highest_bid_amount, second_highest_bid_amount, final_price,
				valid_bidder_count, total_bid_count
			)
			VALUES (
				98401, 98401, 'SOLD', 'TIME_OVER', 98401, 98402,
				82000, 74000, 74000, 1, 2
			)
			""");
		execute("""
			INSERT INTO deal (
				deal_id, auction_result_id, seller_member_id, buyer_member_id,
				final_price, escrow_amount, escrow_status, deal_status
			)
			VALUES (
				98401, 98401, 98401, 98402,
				74000, 74000, 'HELD', 'WAITING_SHIPMENT'
			)
			""");
		entityManager.flush();
		entityManager.clear();
	}

	@Test
	void returnsOnlyCurrentSellersHistoryWithAggregatesAndAction() throws Exception {
		mockMvc.perform(get(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(SELLER_ID)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.items[0].auctionId").value(98401))
			.andExpect(jsonPath("$.items[0].dealId").value(98401))
			.andExpect(jsonPath("$.items[0].productTitle").value("키크론 Q1 Pro"))
			.andExpect(jsonPath("$.items[0].roomStatus").value("ENDED"))
			.andExpect(jsonPath("$.items[0].resultType").value("SOLD"))
			.andExpect(jsonPath("$.items[0].finalPrice").value(74000))
			.andExpect(jsonPath("$.items[0].winnerNickname").value("준호***"))
			.andExpect(jsonPath("$.items[0].dealStatus").value("WAITING_SHIPMENT"))
			.andExpect(jsonPath("$.items[0].actionRequired").value(true))
			.andExpect(jsonPath("$.items[0].actionCode").value("CONFIRM_SHIPMENT"))
			.andExpect(jsonPath("$.items[0].actionLabel").value("발송 확인"))
			.andExpect(jsonPath("$.items[0].bidCount").value(2))
			.andExpect(jsonPath("$.items[0].participantCount").value(1))
			.andExpect(jsonPath("$.items[0].predictionParticipantCount").value(1))
			.andExpect(jsonPath("$.items[1].auctionId").value(98402))
			.andExpect(jsonPath("$.items[1].dealId").doesNotExist())
			.andExpect(jsonPath("$.items[1].actionRequired").value(false))
			.andExpect(jsonPath("$.nextCursor").doesNotExist())
			.andExpect(jsonPath("$.hasNext").value(false));
	}

	/**
	 * 삭제는 소프트 삭제라 행이 남는다. 이 조회가 걸러 주지 않으면 지운 방이 `WAITING`으로
	 * 계속 나와서, 헤더의 「진행 콘솔」 바로가기가 그 방을 골라 잡는다.
	 */
	@Test
	void excludesSoftDeletedRooms() throws Exception {
		mockMvc.perform(get(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(SELLER_ID)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.items[?(@.auctionId == 98404)]").isEmpty());
	}

	@Test
	void returnsOpaqueNextCursorAndAcceptsItForNextPage() throws Exception {
		String response = mockMvc.perform(get(PATH)
				.param("size", "1")
				.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(SELLER_ID)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].auctionId").value(98401))
			.andExpect(jsonPath("$.nextCursor").isString())
			.andExpect(jsonPath("$.hasNext").value(true))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String nextCursor = new com.fasterxml.jackson.databind.ObjectMapper()
			.readTree(response)
			.get("nextCursor")
			.asText();

		mockMvc.perform(get(PATH)
				.param("size", "1")
				.param("cursor", nextCursor)
				.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(SELLER_ID)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].auctionId").value(98402))
			.andExpect(jsonPath("$.nextCursor").doesNotExist())
			.andExpect(jsonPath("$.hasNext").value(false));
	}

	@Test
	void rejectsMalformedCursorAndUnauthenticatedRequest() throws Exception {
		mockMvc.perform(get(PATH)
				.param("cursor", "not*base64*")
				.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(SELLER_ID)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_CURSOR"));

		mockMvc.perform(get(PATH))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	private String bearerAccessToken(long memberId) {
		Member member = mock(Member.class);
		when(member.getId()).thenReturn(memberId);
		when(member.getRole()).thenReturn(MemberRole.USER);
		return "Bearer " + jwtTokenProvider.issue(member).accessToken();
	}

	private void execute(String sql) {
		entityManager.createNativeQuery(sql).executeUpdate();
	}
}
