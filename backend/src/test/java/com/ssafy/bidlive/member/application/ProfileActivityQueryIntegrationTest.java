package com.ssafy.bidlive.member.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.bidlive.deal.application.MemberDealActivity;
import com.ssafy.bidlive.deal.application.MemberDealActivityQuery;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.infrastructure.persistence.MemberRepository;
import com.ssafy.bidlive.prediction.application.MemberPredictionActivity;
import com.ssafy.bidlive.prediction.application.MemberPredictionActivityQuery;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** 실제 PostgreSQL에서 완료 거래와 환불 제외 예측 집계 조건을 검증한다. */
@SpringBootTest
@Transactional
class ProfileActivityQueryIntegrationTest {

	@Autowired
	private MemberDealActivityQuery dealActivityQuery;

	@Autowired
	private MemberPredictionActivityQuery predictionActivityQuery;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void countsOnlyCompletedDealsAndSettledNonRefundedPredictions() {
		Member seller = member("판매자409");
		Member buyer = member("구매자409");
		Member predictor = member("예측자409");
		insertCategory();

		insertSoldAuction(1, seller, buyer);
		insertDeal(1, seller, buyer, "COMPLETED");
		insertPrediction(1, predictor, "HIT");

		insertSoldAuction(2, seller, buyer);
		insertDeal(2, seller, buyer, "PAID");

		insertNoSaleAuction(3, seller);
		insertPrediction(3, predictor, "REFUNDED");
		entityManager.flush();

		MemberDealActivity sellerActivity = dealActivityQuery.query(seller.getId());
		MemberDealActivity buyerActivity = dealActivityQuery.query(buyer.getId());
		MemberPredictionActivity predictionActivity =
			predictionActivityQuery.query(predictor.getId());

		assertThat(sellerActivity.salesCount()).isEqualTo(1);
		assertThat(sellerActivity.purchaseCount()).isZero();
		assertThat(buyerActivity.salesCount()).isZero();
		assertThat(buyerActivity.purchaseCount()).isEqualTo(1);
		assertThat(predictionActivity.participationCount()).isEqualTo(1);
		assertThat(predictionActivity.hitCount()).isEqualTo(1);
	}

	private Member member(String nickname) {
		Member member = Member.createOAuthMember();
		member.changeNickname(nickname);
		return memberRepository.saveAndFlush(member);
	}

	private void insertCategory() {
		update("""
			insert into product_category(category_id, code, name)
			values (409000, 'PROFILE_409', '프로필 테스트')
			""");
	}

	private void insertSoldAuction(int sequence, Member seller, Member buyer) {
		insertProductAndRoom(sequence, seller, "ENDED");
		update("""
			insert into auction_participant(
				auction_participant_id,
				auction_room_id,
				member_id,
				participation_type
			) values (
				:bidderParticipantId,
				:auctionRoomId,
				:buyerId,
				'BIDDER'
			)
			""",
			"bidderParticipantId", id(430000, sequence),
			"auctionRoomId", id(420000, sequence),
			"buyerId", buyer.getId()
		);
		update("""
			insert into bid(
				bid_id,
				auction_participant_id,
				amount,
				sequence_no,
				idempotency_key
			) values (
				:bidId,
				:bidderParticipantId,
				10000,
				1,
				:idempotencyKey
			)
			""",
			"bidId", id(440000, sequence),
			"bidderParticipantId", id(430000, sequence),
			"idempotencyKey", "profile-bid-" + sequence
		);
		update("""
			insert into auction_result(
				auction_result_id,
				auction_room_id,
				result_status,
				termination_reason,
				winner_participant_id,
				winning_bid_id,
				highest_bid_amount,
				final_price,
				valid_bidder_count,
				total_bid_count
			) values (
				:auctionResultId,
				:auctionRoomId,
				'SOLD',
				'SOLD',
				:bidderParticipantId,
				:bidId,
				10000,
				10000,
				1,
				1
			)
			""",
			"auctionResultId", id(450000, sequence),
			"auctionRoomId", id(420000, sequence),
			"bidderParticipantId", id(430000, sequence),
			"bidId", id(440000, sequence)
		);
	}

	private void insertNoSaleAuction(int sequence, Member seller) {
		insertProductAndRoom(sequence, seller, "ENDED");
		update("""
			insert into auction_result(
				auction_result_id,
				auction_room_id,
				result_status,
				termination_reason,
				valid_bidder_count,
				total_bid_count
			) values (
				:auctionResultId,
				:auctionRoomId,
				'NO_SALE',
				'NO_BID',
				0,
				0
			)
			""",
			"auctionResultId", id(450000, sequence),
			"auctionRoomId", id(420000, sequence)
		);
	}

	private void insertProductAndRoom(
		int sequence,
		Member seller,
		String status
	) {
		update("""
			insert into product(
				product_id,
				seller_member_id,
				category_id,
				title,
				description,
				moderation_status
			) values (
				:productId,
				:sellerId,
				409000,
				:title,
				'프로필 집계 테스트 상품',
				'PASSED'
			)
			""",
			"productId", id(410000, sequence),
			"sellerId", seller.getId(),
			"title", "테스트 상품 " + sequence
		);
		update("""
			insert into auction_room(
				auction_room_id,
				product_id,
				reserve_price,
				duration_minutes,
				start_type,
				status
			) values (
				:auctionRoomId,
				:productId,
				10000,
				15,
				'IMMEDIATE',
				:status
			)
			""",
			"auctionRoomId", id(420000, sequence),
			"productId", id(410000, sequence),
			"status", status
		);
	}

	private void insertDeal(
		int sequence,
		Member seller,
		Member buyer,
		String status
	) {
		update("""
			insert into deal(
				deal_id,
				auction_result_id,
				seller_member_id,
				buyer_member_id,
				final_price,
				escrow_amount,
				escrow_status,
				deal_status
			) values (
				:dealId,
				:auctionResultId,
				:sellerId,
				:buyerId,
				10000,
				10000,
				'HELD',
				:status
			)
			""",
			"dealId", id(460000, sequence),
			"auctionResultId", id(450000, sequence),
			"sellerId", seller.getId(),
			"buyerId", buyer.getId(),
			"status", status
		);
	}

	private void insertPrediction(
		int sequence,
		Member predictor,
		String resultStatus
	) {
		long participantId = id(470000, sequence);
		long predictionId = id(480000, sequence);
		update("""
			insert into auction_participant(
				auction_participant_id,
				auction_room_id,
				member_id,
				participation_type
			) values (
				:participantId,
				:auctionRoomId,
				:predictorId,
				'PREDICTOR'
			)
			""",
			"participantId", participantId,
			"auctionRoomId", id(420000, sequence),
			"predictorId", predictor.getId()
		);
		update("""
			insert into prediction(
				prediction_id,
				auction_participant_id,
				predicted_price,
				stake_amount,
				idempotency_key
			) values (
				:predictionId,
				:participantId,
				10000,
				300,
				:idempotencyKey
			)
			""",
			"predictionId", predictionId,
			"participantId", participantId,
			"idempotencyKey", "profile-prediction-" + sequence
		);
		update("""
			insert into prediction_result(
				prediction_result_id,
				prediction_id,
				auction_result_id,
				result_status,
				error_rate,
				reward_multiplier,
				payout_amount
			) values (
				:predictionResultId,
				:predictionId,
				:auctionResultId,
				:resultStatus,
				0,
				1,
				300
			)
			""",
			"predictionResultId", id(490000, sequence),
			"predictionId", predictionId,
			"auctionResultId", id(450000, sequence),
			"resultStatus", resultStatus
		);
	}

	private long id(long prefix, int sequence) {
		return prefix + sequence;
	}

	private void update(String sql, Object... parameters) {
		var query = entityManager.createNativeQuery(sql);
		for (int index = 0; index < parameters.length; index += 2) {
			query.setParameter(
				(String)parameters[index],
				parameters[index + 1]
			);
		}
		query.executeUpdate();
	}
}
