package com.ssafy.bidlive.auction.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.bidlive.auction.application.AuctionOwnershipReader;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

/** 경매 판매자 소유권 조회가 자기 경매·타인 경매·없는 경매를 구분하는지 검증한다. */
@DataJpaTest
@Import(JpaAuctionOwnershipReaderAdapter.class)
class JpaAuctionOwnershipReaderAdapterTest {

	@Autowired
	private AuctionOwnershipReader auctionOwnershipReader;

	@Autowired
	private EntityManager entityManager;

	private Long sellerId;
	private Long otherMemberId;
	private Long auctionId;

	@BeforeEach
	void setUp() {
		sellerId = insertMember("판매자");
		otherMemberId = insertMember("다른회원");
		Long categoryId = insertCategory();
		Long productId = insertProduct(sellerId, categoryId);
		auctionId = insertAuctionRoom(productId);
	}

	@Test
	void returnsTrueForAuctionSeller() {
		assertThat(auctionOwnershipReader.isSeller(auctionId, sellerId)).isTrue();
	}

	@Test
	void returnsFalseForDifferentMember() {
		assertThat(auctionOwnershipReader.isSeller(auctionId, otherMemberId)).isFalse();
	}

	@Test
	void returnsFalseForUnknownAuction() {
		assertThat(auctionOwnershipReader.isSeller(Long.MAX_VALUE, sellerId)).isFalse();
	}

	private Long insertMember(String nickname) {
		return asLong(entityManager.createNativeQuery("""
			INSERT INTO member (nickname, role, status)
			VALUES (:nickname, 'USER', 'ACTIVE')
			RETURNING member_id
			""")
			.setParameter("nickname", nickname)
			.getSingleResult());
	}

	private Long insertCategory() {
		return asLong(entityManager.createNativeQuery("""
			INSERT INTO product_category (code, name)
			VALUES ('DIGITAL', '디지털')
			RETURNING category_id
			""")
			.getSingleResult());
	}

	private Long insertProduct(Long memberId, Long categoryId) {
		return asLong(entityManager.createNativeQuery("""
			INSERT INTO product (
				seller_member_id,
				category_id,
				title,
				description,
				moderation_status
			)
			VALUES (
				:memberId,
				:categoryId,
				'테스트 상품',
				'판매자 소유권 조회 테스트 상품',
				'PASSED'
			)
			RETURNING product_id
			""")
			.setParameter("memberId", memberId)
			.setParameter("categoryId", categoryId)
			.getSingleResult());
	}

	private Long insertAuctionRoom(Long productId) {
		return asLong(entityManager.createNativeQuery("""
			INSERT INTO auction_room (
				product_id,
				reserve_price,
				duration_minutes,
				start_type,
				status
			)
			VALUES (
				:productId,
				10000,
				30,
				'IMMEDIATE',
				'WAITING'
			)
			RETURNING auction_room_id
			""")
			.setParameter("productId", productId)
			.getSingleResult());
	}

	private Long asLong(Object value) {
		return ((Number) value).longValue();
	}
}
