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

/** 실제 JWT와 PostgreSQL을 거쳐 구매자 본인의 낙찰 경매와 커서를 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WonAuctionQueryControllerIntegrationTest {

	private static final String PATH =
		"/api/v1/members/me/auctions/won";
	private static final long BUYER_ID = 98601L;

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
			VALUES (98601, 'WON_TEST', '낙찰 이력 테스트', TRUE, 98601)
			""");
		execute("""
			INSERT INTO member (member_id, nickname, role, status)
			VALUES
				(98601, '구매자테스트', 'USER', 'ACTIVE'),
				(98602, '서연123', 'USER', 'ACTIVE'),
				(98603, '지훈123', 'USER', 'ACTIVE'),
				(98604, '다른구매자', 'USER', 'ACTIVE')
			""");
		execute("""
			INSERT INTO product (
				product_id, seller_member_id, category_id, title, description
			)
			VALUES
				(98601, 98602, 98601, '키크론 Q1 Pro', '설명'),
				(98602, 98603, 98601, '한정판 피규어', '설명'),
				(98603, 98602, 98601, '다른 구매자의 상품', '설명')
			""");
		execute("""
			INSERT INTO product_image (
				product_image_id, product_id, image_url, sort_order,
				is_thumbnail, image_id, owner_member_id, storage_key,
				content_type, size_bytes
			)
			VALUES
				(98601, 98601,
					'https://cdn.example.com/products/abc.jpg',
					1, TRUE, gen_random_uuid(), 98602,
					'test/product-images/98601', 'image/jpeg', 1),
				(98602, 98602,
					'https://cdn.example.com/products/figure.jpg',
					1, TRUE, gen_random_uuid(), 98603,
					'test/product-images/98602', 'image/jpeg', 1),
				(98603, 98603,
					'https://cdn.example.com/products/other.jpg',
					1, TRUE, gen_random_uuid(), 98602,
					'test/product-images/98603', 'image/jpeg', 1)
			""");
		execute("""
			INSERT INTO auction_room (
				auction_room_id, product_id, reserve_price, duration_minutes,
				start_type, scheduled_at, started_at, ends_at, ended_at, status
			)
			VALUES
				(98601, 98601, 50000, 15, 'SCHEDULED',
					'2026-07-21T10:00:00Z', '2026-07-21T11:02:13Z',
					'2026-07-21T11:17:13Z', '2026-07-21T11:17:13Z', 'ENDED'),
				(98602, 98602, 70000, 20, 'SCHEDULED',
					'2026-07-19T10:00:00Z', '2026-07-19T11:00:00Z',
					'2026-07-19T11:10:00Z', '2026-07-19T11:10:00Z', 'ENDED'),
				(98603, 98603, 30000, 10, 'SCHEDULED',
					'2026-07-22T10:00:00Z', '2026-07-22T11:00:00Z',
					'2026-07-22T11:10:00Z', '2026-07-22T11:10:00Z', 'ENDED')
			""");
		execute("""
			INSERT INTO auction_participant (
				auction_participant_id, auction_room_id, member_id,
				participation_type
			)
			VALUES
				(98601, 98601, 98601, 'BIDDER'),
				(98602, 98602, 98601, 'BIDDER'),
				(98603, 98603, 98604, 'BIDDER')
			""");
		execute("""
			INSERT INTO bid (
				bid_id, auction_participant_id, amount, sequence_no,
				idempotency_key
			)
			VALUES
				(98601, 98601, 82000, 1, 'won-test-bid-98601'),
				(98602, 98602, 145000, 1, 'won-test-bid-98602'),
				(98603, 98603, 45000, 1, 'won-test-bid-98603')
			""");
		execute("""
			INSERT INTO auction_result (
				auction_result_id, auction_room_id, result_status,
				termination_reason, winner_participant_id, winning_bid_id,
				highest_bid_amount, second_highest_bid_amount, final_price,
				valid_bidder_count, total_bid_count
			)
			VALUES
				(98601, 98601, 'SOLD', 'TIME_OVER', 98601, 98601,
					82000, 74000, 74000, 1, 1),
				(98602, 98602, 'SOLD', 'TIME_OVER', 98602, 98602,
					145000, 120000, 120000, 1, 1),
				(98603, 98603, 'SOLD', 'TIME_OVER', 98603, 98603,
					45000, 40000, 40000, 1, 1)
			""");
		execute("""
			INSERT INTO deal (
				deal_id, auction_result_id, seller_member_id, buyer_member_id,
				final_price, escrow_amount, escrow_status, deal_status,
				snapshot_image_url, completed_at
			)
			VALUES
				(98601, 98601, 98602, 98601,
					74000, 74000, 'HELD', 'WAITING_ADDRESS', NULL, NULL),
				(98602, 98602, 98603, 98601,
					120000, 120000, 'RELEASED', 'COMPLETED',
					'https://cdn.example.com/snapshots/deal-98602.jpg',
					'2026-07-20T11:00:00Z'),
				(98603, 98603, 98602, 98604,
					40000, 40000, 'HELD', 'WAITING_ADDRESS', NULL, NULL)
			""");
		execute("""
			INSERT INTO live_snapshot (
				live_snapshot_id, room_id, auction_id, image_url, captured_at
			)
			VALUES
				(98601, 'room-98601', 98601,
					'https://cdn.example.com/snapshots/live-98601.jpg',
					'2026-07-21T11:17:13Z'),
				(98602, 'room-98602', 98602,
					'https://cdn.example.com/snapshots/live-98602.jpg',
					'2026-07-19T11:10:00Z')
			""");
		entityManager.flush();
		entityManager.clear();
	}

	@Test
	void returnsOnlyCurrentBuyersWonAuctionsWithActionsAndSnapshots()
		throws Exception {
		mockMvc.perform(get(PATH)
				.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(BUYER_ID)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.items[0].auctionId").value(98601))
			.andExpect(jsonPath("$.items[0].dealId").value(98601))
			.andExpect(jsonPath("$.items[0].productTitle")
				.value("키크론 Q1 Pro"))
			.andExpect(jsonPath("$.items[0].imageUrl")
				.value("https://cdn.example.com/products/abc.jpg"))
			.andExpect(jsonPath("$.items[0].sellerNickname")
				.value("서연***"))
			.andExpect(jsonPath("$.items[0].finalPrice").value(74000))
			.andExpect(jsonPath("$.items[0].myFinalBid").value(82000))
			.andExpect(jsonPath("$.items[0].dealStatus")
				.value("WAITING_ADDRESS"))
			.andExpect(jsonPath("$.items[0].actionRequired").value(true))
			.andExpect(jsonPath("$.items[0].actionCode")
				.value("SUBMIT_ADDRESS"))
			.andExpect(jsonPath("$.items[0].actionLabel")
				.value("배송지 입력"))
			.andExpect(jsonPath("$.items[0].snapshotUrl")
				.value("https://cdn.example.com/snapshots/live-98601.jpg"))
			.andExpect(jsonPath("$.items[1].auctionId").value(98602))
			.andExpect(jsonPath("$.items[1].sellerNickname")
				.value("지훈***"))
			.andExpect(jsonPath("$.items[1].dealStatus").value("COMPLETED"))
			.andExpect(jsonPath("$.items[1].actionRequired").value(false))
			.andExpect(jsonPath("$.items[1].actionCode").doesNotExist())
			.andExpect(jsonPath("$.items[1].actionLabel").doesNotExist())
			.andExpect(jsonPath("$.items[1].snapshotUrl")
				.value("https://cdn.example.com/snapshots/deal-98602.jpg"))
			.andExpect(jsonPath("$.nextCursor").doesNotExist())
			.andExpect(jsonPath("$.hasNext").value(false));
	}

	@Test
	void returnsOpaqueNextCursorAndAcceptsItForNextPage() throws Exception {
		String response = mockMvc.perform(get(PATH)
				.param("size", "1")
				.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(BUYER_ID)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].auctionId").value(98601))
			.andExpect(jsonPath("$.nextCursor").isString())
			.andExpect(jsonPath("$.hasNext").value(true))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String nextCursor = new ObjectMapper()
			.readTree(response)
			.get("nextCursor")
			.asText();

		mockMvc.perform(get(PATH)
				.param("size", "1")
				.param("cursor", nextCursor)
				.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(BUYER_ID)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].auctionId").value(98602))
			.andExpect(jsonPath("$.nextCursor").doesNotExist())
			.andExpect(jsonPath("$.hasNext").value(false));
	}

	@Test
	void rejectsMalformedCursorAndUnauthenticatedRequest() throws Exception {
		mockMvc.perform(get(PATH)
				.param("cursor", "not*base64*")
				.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(BUYER_ID)))
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
