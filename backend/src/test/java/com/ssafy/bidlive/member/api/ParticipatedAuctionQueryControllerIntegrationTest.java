package com.ssafy.bidlive.member.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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

/** 실제 JWT와 PostgreSQL로 참여 결과, 낙찰자 제외와 커서 페이징을 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ParticipatedAuctionQueryControllerIntegrationTest {

	private static final String PATH =
		"/api/v1/members/me/auctions/participated";
	private static final long MEMBER_ID = 98501L;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private ObjectMapper objectMapper;

	@BeforeEach
	void insertFixture() {
		insertCategoryAndMembers();
		insertProductsAndImages();
		insertAuctionRooms();
		insertParticipantsAndActivity();
		insertAuctionAndPredictionResults();
		entityManager.flush();
		entityManager.clear();
	}

	@Test
	void returnsBidPredictionAndRefundResultsWithoutWonAuction()
		throws Exception {
		mockMvc.perform(get(PATH)
				.header(HttpHeaders.AUTHORIZATION, bearerAccessToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(5))
			.andExpect(jsonPath("$.items[0].auctionId").value(98506))
			.andExpect(jsonPath("$.items[0].participationType").value("BID"))
			.andExpect(jsonPath("$.items[0].roomStatus").value("LIVE"))
			.andExpect(jsonPath("$.items[0].result").doesNotExist())
			.andExpect(jsonPath("$.items[0].myFinalBid").value(45000))
			.andExpect(jsonPath("$.items[0].holdReleasedAmount")
				.doesNotExist())
			.andExpect(jsonPath("$.items[1].auctionId").value(98501))
			.andExpect(jsonPath("$.items[1].resultType").value("SOLD"))
			.andExpect(jsonPath("$.items[1].result").value("LOST"))
			.andExpect(jsonPath("$.items[1].resultLabel").value("미낙찰"))
			.andExpect(jsonPath("$.items[1].finalPrice").value(74000))
			.andExpect(jsonPath("$.items[1].myFinalBid").value(74000))
			.andExpect(jsonPath("$.items[1].holdReleasedAmount").value(74000))
			.andExpect(jsonPath("$.items[2].auctionId").value(98503))
			.andExpect(jsonPath("$.items[2].result").value("HIT"))
			.andExpect(jsonPath("$.items[2].resultLabel").value("예측 적중"))
			.andExpect(jsonPath("$.items[2].errorRate").value(2.7))
			.andExpect(jsonPath("$.items[2].errorBand")
				.value("WITHIN_10"))
			.andExpect(jsonPath("$.items[2].payoutAmount").value(900))
			.andExpect(jsonPath("$.items[2].refundAmount").doesNotExist())
			.andExpect(jsonPath("$.items[3].auctionId").value(98504))
			.andExpect(jsonPath("$.items[3].result").value("MISS"))
			.andExpect(jsonPath("$.items[3].errorBand").value("MISS"))
			.andExpect(jsonPath("$.items[3].payoutAmount").value(0))
			.andExpect(jsonPath("$.items[4].auctionId").value(98505))
			.andExpect(jsonPath("$.items[4].resultType").value("NO_BID"))
			.andExpect(jsonPath("$.items[4].result").value("REFUNDED"))
			.andExpect(jsonPath("$.items[4].resultLabel")
				.value("유찰 · 베팅 환불"))
			.andExpect(jsonPath("$.items[4].payoutAmount").value(0))
			.andExpect(jsonPath("$.items[4].refundAmount").value(300))
			.andExpect(jsonPath("$.nextCursor").doesNotExist())
			.andExpect(jsonPath("$.hasNext").value(false));
	}

	@Test
	void returnsOpaqueCursorAndAcceptsItForFollowingPages()
		throws Exception {
		String firstResponse = mockMvc.perform(get(PATH)
				.param("size", "2")
				.header(HttpHeaders.AUTHORIZATION, bearerAccessToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].auctionId").value(98506))
			.andExpect(jsonPath("$.items[1].auctionId").value(98501))
			.andExpect(jsonPath("$.nextCursor").isString())
			.andExpect(jsonPath("$.hasNext").value(true))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String firstCursor = objectMapper.readTree(firstResponse)
			.get("nextCursor")
			.asText();

		String secondResponse = mockMvc.perform(get(PATH)
				.param("size", "2")
				.param("cursor", firstCursor)
				.header(HttpHeaders.AUTHORIZATION, bearerAccessToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].auctionId").value(98503))
			.andExpect(jsonPath("$.items[1].auctionId").value(98504))
			.andExpect(jsonPath("$.nextCursor").isString())
			.andExpect(jsonPath("$.hasNext").value(true))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String secondCursor = objectMapper.readTree(secondResponse)
			.get("nextCursor")
			.asText();

		mockMvc.perform(get(PATH)
				.param("size", "2")
				.param("cursor", secondCursor)
				.header(HttpHeaders.AUTHORIZATION, bearerAccessToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].auctionId").value(98505))
			.andExpect(jsonPath("$.nextCursor").doesNotExist())
			.andExpect(jsonPath("$.hasNext").value(false));
	}

	@Test
	void rejectsMalformedCursorAndUnauthenticatedRequest()
		throws Exception {
		mockMvc.perform(get(PATH)
				.param("cursor", "not*base64*")
				.header(HttpHeaders.AUTHORIZATION, bearerAccessToken()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_CURSOR"));

		mockMvc.perform(get(PATH))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	private void insertCategoryAndMembers() {
		execute("""
			INSERT INTO product_category (
				category_id, code, name, is_active, display_order
			)
			VALUES (98501, 'PARTICIPATED_TEST', '참여 이력 테스트', TRUE, 98501)
			""");
		execute("""
			INSERT INTO member (member_id, nickname, role, status)
			VALUES
				(98501, '참여자테스트', 'USER', 'ACTIVE'),
				(98502, '판매자테스트', 'USER', 'ACTIVE'),
				(98503, '낙찰자테스트', 'USER', 'ACTIVE')
			""");
	}

	private void insertProductsAndImages() {
		execute("""
			INSERT INTO product (
				product_id, seller_member_id, category_id, title, description
			)
			VALUES
				(98501, 98502, 98501, '헤드폰', '설명'),
				(98502, 98502, 98501, '낙찰 받은 상품', '설명'),
				(98503, 98502, 98501, '한정판 피규어', '설명'),
				(98504, 98502, 98501, '빈티지 시계', '설명'),
				(98505, 98502, 98501, '빈티지 앰프', '설명'),
				(98506, 98502, 98501, '진행 중 키보드', '설명')
			""");
		execute("""
			INSERT INTO product_image (
				product_image_id, product_id, image_url, sort_order,
				is_thumbnail, image_id, owner_member_id, storage_key,
				content_type, size_bytes
			)
			SELECT
				product_id,
				product_id,
				'https://cdn.example.com/products/' || product_id || '.jpg',
				1,
				TRUE,
				gen_random_uuid(),
				98502,
				'test/participated/' || product_id,
				'image/jpeg',
				1
			FROM product
			WHERE product_id BETWEEN 98501 AND 98506
			""");
	}

	private void insertAuctionRooms() {
		execute("""
			INSERT INTO auction_room (
				auction_room_id, product_id, reserve_price, duration_minutes,
				start_type, scheduled_at, started_at, ends_at, ended_at, status
			)
			VALUES
				(98501, 98501, 50000, 15, 'SCHEDULED',
					'2026-07-20T10:00:00Z', '2026-07-20T10:00:00Z',
					'2026-07-20T10:15:00Z', '2026-07-20T10:15:00Z', 'ENDED'),
				(98502, 98502, 50000, 15, 'SCHEDULED',
					'2026-07-21T10:00:00Z', '2026-07-21T10:00:00Z',
					'2026-07-21T10:15:00Z', '2026-07-21T10:15:00Z', 'ENDED'),
				(98503, 98503, 50000, 15, 'SCHEDULED',
					'2026-07-19T10:00:00Z', '2026-07-19T10:00:00Z',
					'2026-07-19T10:15:00Z', '2026-07-19T10:15:00Z', 'ENDED'),
				(98504, 98504, 50000, 15, 'SCHEDULED',
					'2026-07-18T10:00:00Z', '2026-07-18T10:00:00Z',
					'2026-07-18T10:15:00Z', '2026-07-18T10:15:00Z', 'ENDED'),
				(98505, 98505, 50000, 15, 'SCHEDULED',
					'2026-07-17T10:00:00Z', '2026-07-17T10:00:00Z',
					'2026-07-17T10:15:00Z', '2026-07-17T10:15:00Z', 'ENDED'),
				(98506, 98506, 50000, 15, 'SCHEDULED',
					'2026-07-22T10:00:00Z', '2026-07-22T10:00:00Z',
					'2026-07-22T10:15:00Z', NULL, 'LIVE')
			""");
	}

	private void insertParticipantsAndActivity() {
		execute("""
			INSERT INTO auction_participant (
				auction_participant_id, auction_room_id, member_id,
				participation_type
			)
			VALUES
				(98501, 98501, 98501, 'BIDDER'),
				(98502, 98501, 98503, 'BIDDER'),
				(98503, 98502, 98501, 'BIDDER'),
				(98504, 98503, 98501, 'PREDICTOR'),
				(98505, 98503, 98503, 'BIDDER'),
				(98506, 98504, 98501, 'PREDICTOR'),
				(98507, 98504, 98503, 'BIDDER'),
				(98508, 98505, 98501, 'PREDICTOR'),
				(98509, 98506, 98501, 'BIDDER')
			""");
		execute("""
			INSERT INTO bid (
				bid_id, auction_participant_id, amount, sequence_no,
				idempotency_key
			)
			VALUES
				(98501, 98501, 61000, 1, 'participated-bid-98501'),
				(98502, 98501, 74000, 2, 'participated-bid-98502'),
				(98503, 98502, 82000, 1, 'participated-bid-98503'),
				(98504, 98503, 90000, 1, 'participated-bid-98504'),
				(98505, 98505, 74000, 1, 'participated-bid-98505'),
				(98506, 98507, 100000, 1, 'participated-bid-98506'),
				(98507, 98509, 45000, 1, 'participated-bid-98507')
			""");
		execute("""
			INSERT INTO prediction (
				prediction_id, auction_participant_id, predicted_price,
				stake_amount, idempotency_key
			)
			VALUES
				(98501, 98504, 72000, 300, 'participated-prediction-98501'),
				(98502, 98506, 50000, 300, 'participated-prediction-98502'),
				(98503, 98508, 180000, 300, 'participated-prediction-98503')
			""");
	}

	private void insertAuctionAndPredictionResults() {
		execute("""
			INSERT INTO auction_result (
				auction_result_id, auction_room_id, result_status,
				termination_reason, winner_participant_id, winning_bid_id,
				highest_bid_amount, second_highest_bid_amount, final_price,
				valid_bidder_count, total_bid_count
			)
			VALUES
				(98501, 98501, 'SOLD', 'TIME_OVER', 98502, 98503,
					82000, 74000, 74000, 2, 3),
				(98502, 98502, 'SOLD', 'TIME_OVER', 98503, 98504,
					90000, NULL, 90000, 1, 1),
				(98503, 98503, 'SOLD', 'TIME_OVER', 98505, 98505,
					74000, NULL, 74000, 1, 1),
				(98504, 98504, 'SOLD', 'TIME_OVER', 98507, 98506,
					100000, NULL, 100000, 1, 1),
				(98505, 98505, 'NO_SALE', 'NO_BID', NULL, NULL,
					NULL, NULL, NULL, 0, 0)
			""");
		execute("""
			INSERT INTO prediction_result (
				prediction_result_id, prediction_id, auction_result_id,
				result_status, error_rate, reward_multiplier, payout_amount
			)
			VALUES
				(98501, 98501, 98503, 'HIT', 2.700000, 3, 900),
				(98502, 98502, 98504, 'MISS', 8.000000, 0, 0),
				(98503, 98503, 98505, 'REFUNDED', NULL, 0, 300)
			""");
	}

	private String bearerAccessToken() {
		Member member = mock(Member.class);
		when(member.getId()).thenReturn(MEMBER_ID);
		when(member.getRole()).thenReturn(MemberRole.USER);
		return "Bearer " + jwtTokenProvider.issue(member).accessToken();
	}

	private void execute(String sql) {
		entityManager.createNativeQuery(sql).executeUpdate();
	}
}
