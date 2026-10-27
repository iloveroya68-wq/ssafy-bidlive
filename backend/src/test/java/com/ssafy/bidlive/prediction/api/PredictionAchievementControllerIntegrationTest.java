package com.ssafy.bidlive.prediction.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;

import com.ssafy.bidlive.common.security.JwtTokenProvider;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.MemberRole;
import com.ssafy.bidlive.member.infrastructure.persistence.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 실제 Security와 PostgreSQL JPA 조회로 PRED-04 응답 계약을 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PredictionAchievementControllerIntegrationTest {

	private static final String PATH =
		"/api/v1/members/me/prediction-achievements";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void returnsAggregatedAchievementFromPersistedPredictionRows()
		throws Exception {
		Member member = memberRepository.saveAndFlush(Member.createOAuthMember());
		Member otherMember =
			memberRepository.saveAndFlush(Member.createOAuthMember());
		Long categoryId = insertCategory(member.getId());

		Long hitResultId = insertSettlement(
			member.getId(),
			categoryId,
			1,
			"HIT",
			5_000L,
			13_400L
		);
		insertSettlement(
			member.getId(), categoryId, 2, "MISS", 4_200L, 0L
		);
		insertSettlement(
			member.getId(), categoryId, 3, "REFUNDED", 1_000L, 1_000L
		);
		insertSettlement(
			otherMember.getId(), categoryId, 4, "HIT", 99_999L, 99_999L);
		saveGrade(member.getId());
		saveBadge(
			member.getId(),
			hitResultId,
			OffsetDateTime.parse("2026-07-20T21:30:00+09:00")
		);

		mockMvc.perform(get(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(member)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.memberId").value(member.getId()))
			.andExpect(jsonPath("$.predictionCount").value(2))
			.andExpect(jsonPath("$.hitCount").value(1))
			.andExpect(jsonPath("$.hitRate").value(50.0))
			.andExpect(jsonPath("$.totalStakedPoints").value(9_200))
			.andExpect(jsonPath("$.totalRewardPoints").value(13_400))
			.andExpect(jsonPath("$.grade.code").value("ORACLE_2"))
			.andExpect(jsonPath("$.grade.name").value("시세 예언자 II"))
			.andExpect(jsonPath("$.grade.score").value(720))
			.andExpect(jsonPath("$.badges.length()").value(1))
			.andExpect(jsonPath("$.badges[0].badgeCode").value("FIRST_HIT"))
			.andExpect(jsonPath("$.badges[0].name").value("첫 적중"))
			.andExpect(jsonPath("$.badges[0].earnedAt")
				.value("2026-07-20T12:30:00Z"));
	}

	@Test
	void returnsInitialAchievementForAuthenticatedMemberWithoutSettlements()
		throws Exception {
		Member member = memberRepository.saveAndFlush(Member.createOAuthMember());

		mockMvc.perform(get(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(member)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.memberId").value(member.getId()))
			.andExpect(jsonPath("$.predictionCount").value(0))
			.andExpect(jsonPath("$.hitCount").value(0))
			.andExpect(jsonPath("$.hitRate").value(0.0))
			.andExpect(jsonPath("$.totalStakedPoints").value(0))
			.andExpect(jsonPath("$.totalRewardPoints").value(0))
			.andExpect(jsonPath("$.grade.code").value("ORACLE_1"))
			.andExpect(jsonPath("$.grade.name").value("시세 예언자 I"))
			.andExpect(jsonPath("$.grade.score").value(0))
			.andExpect(jsonPath("$.badges").isEmpty());
	}

	@Test
	void rejectsUnauthenticatedRequest() throws Exception {
		mockMvc.perform(get(PATH))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void rejectsTokenForMissingMember() throws Exception {
		mockMvc.perform(get(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(999L)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
	}

	private String bearerAccessToken(Member member) {
		return "Bearer " + jwtTokenProvider.issue(member).accessToken();
	}

	private String bearerAccessToken(Long memberId) {
		Member member = mock(Member.class);
		when(member.getId()).thenReturn(memberId);
		when(member.getRole()).thenReturn(MemberRole.USER);
		return bearerAccessToken(member);
	}

	private Long insertCategory(Long memberId) {
		return jdbcTemplate.queryForObject("""
			INSERT INTO product_category (code, name)
			VALUES (?, 'PRED-04 통합 테스트')
			RETURNING category_id
			""", Long.class, "PRED-04-" + memberId);
	}

	private void saveGrade(Long memberId) {
		jdbcTemplate.update("""
			INSERT INTO prediction_achievement (
				member_id,
				grade_code,
				grade_score
			)
			VALUES (?, 'ORACLE_2', 720)
			""", memberId);
	}

	private void saveBadge(
		Long memberId,
		Long predictionResultId,
		OffsetDateTime earnedAt
	) {
		jdbcTemplate.update("""
			INSERT INTO prediction_badge (
				member_id,
				badge_code,
				earned_prediction_result_id,
				earned_at
			)
			VALUES (?, 'FIRST_HIT', ?, ?)
			""", memberId, predictionResultId, earnedAt);
	}

	private Long insertSettlement(
		Long memberId,
		Long categoryId,
		int sequence,
		String resultStatus,
		long stakeAmount,
		long payoutAmount
	) {
		Long productId = jdbcTemplate.queryForObject("""
			INSERT INTO product (
				seller_member_id,
				category_id,
				title,
				description
			)
			VALUES (?, ?, ?, 'PRED-04 통합 테스트 상품')
			RETURNING product_id
			""", Long.class, memberId, categoryId, "예측 상품 " + sequence);
		Long auctionRoomId = jdbcTemplate.queryForObject("""
			INSERT INTO auction_room (
				product_id,
				reserve_price,
				duration_minutes,
				start_type,
				status
			)
			VALUES (?, 1000, 30, 'IMMEDIATE', 'ENDED')
			RETURNING auction_room_id
			""", Long.class, productId);
		Long participantId = jdbcTemplate.queryForObject("""
			INSERT INTO auction_participant (
				auction_room_id,
				member_id,
				participation_type
			)
			VALUES (?, ?, 'PREDICTOR')
			RETURNING auction_participant_id
			""", Long.class, auctionRoomId, memberId);
		Long predictionId = jdbcTemplate.queryForObject("""
			INSERT INTO prediction (
				auction_participant_id,
				predicted_price,
				stake_amount,
				idempotency_key
			)
			VALUES (?, 10000, ?, ?)
			RETURNING prediction_id
			""",
			Long.class,
			participantId,
			stakeAmount,
			"pred-04-" + memberId + "-" + sequence
		);
		Long auctionResultId = jdbcTemplate.queryForObject("""
			INSERT INTO auction_result (
				auction_room_id,
				result_status,
				termination_reason,
				valid_bidder_count,
				total_bid_count
			)
			VALUES (?, 'NO_SALE', 'NO_VALID_BID', 0, 0)
			RETURNING auction_result_id
			""", Long.class, auctionRoomId);
		return jdbcTemplate.queryForObject("""
			INSERT INTO prediction_result (
				prediction_id,
				auction_result_id,
				result_status,
				reward_multiplier,
				payout_amount
			)
			VALUES (?, ?, ?, ?, ?)
			RETURNING prediction_result_id
			""",
			Long.class,
			predictionId,
			auctionResultId,
			resultStatus,
			rewardMultiplier(resultStatus),
			payoutAmount
		);
	}

	private int rewardMultiplier(String resultStatus) {
		return switch (resultStatus) {
			case "HIT" -> 3;
			case "REFUNDED" -> 1;
			default -> 0;
		};
	}
}
