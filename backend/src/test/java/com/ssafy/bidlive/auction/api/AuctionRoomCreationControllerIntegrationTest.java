package com.ssafy.bidlive.auction.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.ssafy.bidlive.common.security.JwtTokenProvider;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.infrastructure.persistence.MemberRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 실제 JWT, Flyway와 PostgreSQL을 거쳐 경매방 생성 계약을 검증합니다. */
@SpringBootTest
@AutoConfigureMockMvc
class AuctionRoomCreationControllerIntegrationTest {

	private static final String PATH = "/api/v1/auction-rooms";
	private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private final List<Long> testMemberIds = new ArrayList<>();

	@AfterEach
	void cleanUp() {
		if (testMemberIds.isEmpty()) {
			return;
		}
		inTransaction(() -> {
			entityManager.createNativeQuery("""
				DELETE FROM auction_room
				WHERE product_id IN (
					SELECT product_id
					FROM product
					WHERE seller_member_id IN (:memberIds)
				)
				""")
				.setParameter("memberIds", testMemberIds)
				.executeUpdate();
			entityManager.createNativeQuery("""
				DELETE FROM product
				WHERE seller_member_id IN (:memberIds)
				""")
				.setParameter("memberIds", testMemberIds)
				.executeUpdate();
			entityManager.createNativeQuery("""
				DELETE FROM product_image
				WHERE owner_member_id IN (:memberIds)
				""")
				.setParameter("memberIds", testMemberIds)
				.executeUpdate();
			entityManager.createNativeQuery("""
				DELETE FROM member
				WHERE member_id IN (:memberIds)
				""")
				.setParameter("memberIds", testMemberIds)
				.executeUpdate();
		});
		testMemberIds.clear();
	}

	@Test
	void createsProductAndWaitingAuctionRoomWithOwnedImage() throws Exception {
		Member seller = createMember();
		String imageUrl = "https://cdn.example.com/products/owned.jpg";
		insertPreUploadedImage(seller.getId(), imageUrl);
		String scheduledAt = OffsetDateTime.now(SERVICE_ZONE)
			.plusDays(1)
			.withNano(0)
			.toString();

		mockMvc.perform(post(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(seller))
			.contentType(MediaType.APPLICATION_JSON)
			.content(scheduledRequest(imageUrl, scheduledAt)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.auctionId").isNumber())
			.andExpect(jsonPath("$.productId").isNumber())
			.andExpect(jsonPath("$.sellerId").value(seller.getId()))
			.andExpect(jsonPath("$.status").value("WAITING"))
			.andExpect(jsonPath("$.reservePrice").value(50000))
			.andExpect(jsonPath("$.durationMinutes").value(15))
			.andExpect(jsonPath("$.startType").value("SCHEDULED"))
			.andExpect(jsonPath("$.scheduledAt").value(scheduledAt))
			.andExpect(jsonPath("$.createdAt").isString());

		Object[] room = new TransactionTemplate(transactionManager).execute(
			status -> (Object[]) entityManager.createNativeQuery("""
				SELECT
					room.status,
					room.start_type,
					product.seller_member_id,
					product.title,
					category.code,
					image.product_id
				FROM auction_room room
				JOIN product ON product.product_id = room.product_id
				JOIN product_category category ON category.category_id = product.category_id
				JOIN product_image image ON image.product_id = product.product_id
				WHERE product.seller_member_id = :sellerId
				  AND product.title = '키크론 Q1 Pro'
				""")
				.setParameter("sellerId", seller.getId())
				.getSingleResult()
		);
		assertThat(room[0]).isEqualTo("WAITING");
		assertThat(room[1]).isEqualTo("SCHEDULED");
		assertThat(((Number) room[2]).longValue()).isEqualTo(seller.getId());
		assertThat(room[3]).isEqualTo("키크론 Q1 Pro");
		assertThat(room[4]).isEqualTo("ELECTRONICS");
		assertThat(room[5]).isNotNull();
	}

	@Test
	void rejectsAnotherSellersImageAndRollsBackProduct() throws Exception {
		Member owner = createMember();
		Member requester = createMember();
		String imageUrl = "https://cdn.example.com/products/not-owned.jpg";
		insertPreUploadedImage(owner.getId(), imageUrl);
		String scheduledAt = OffsetDateTime.now(SERVICE_ZONE)
			.plusDays(1)
			.withNano(0)
			.toString();

		mockMvc.perform(post(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(requester))
			.contentType(MediaType.APPLICATION_JSON)
			.content(scheduledRequest(imageUrl, scheduledAt)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_PRODUCT_REQUEST"));

		assertThat(productCount(requester.getId())).isZero();
	}

	@Test
	void rejectsPastScheduleBeforeCreatingProduct() throws Exception {
		Member seller = createMember();
		String imageUrl = "https://cdn.example.com/products/past.jpg";
		insertPreUploadedImage(seller.getId(), imageUrl);
		String scheduledAt = OffsetDateTime.now(SERVICE_ZONE)
			.minusMinutes(1)
			.withNano(0)
			.toString();

		mockMvc.perform(post(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(seller))
			.contentType(MediaType.APPLICATION_JSON)
			.content(scheduledRequest(imageUrl, scheduledAt)))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("INVALID_SCHEDULE_TIME"));

		assertThat(unusedImageCount(seller.getId(), imageUrl)).isEqualTo(1L);
	}

	@Test
	void rejectsUnauthenticatedRequest() throws Exception {
		mockMvc.perform(post(PATH)
			.contentType(MediaType.APPLICATION_JSON)
			.content(scheduledRequest(
				"https://cdn.example.com/products/image.jpg",
				OffsetDateTime.now(SERVICE_ZONE).plusDays(1).toString()
			)))
			.andExpect(status().isUnauthorized());
	}

	private void insertPreUploadedImage(Long ownerMemberId, String imageUrl) {
		inTransaction(() -> entityManager.createNativeQuery("""
				INSERT INTO product_image (
					product_id,
					image_url,
					sort_order,
					is_thumbnail,
					moderation_status,
					image_id,
					owner_member_id,
					storage_key,
					content_type,
					size_bytes
				)
				VALUES (
					NULL,
					:imageUrl,
					1,
					TRUE,
					'PENDING',
					:imageId,
					:ownerMemberId,
					:storageKey,
					'image/jpeg',
					4
				)
				""")
			.setParameter("imageUrl", imageUrl)
			.setParameter("imageId", UUID.randomUUID())
			.setParameter("ownerMemberId", ownerMemberId)
			.setParameter("storageKey", "products/" + UUID.randomUUID() + ".jpg")
			.executeUpdate());
	}

	private long unusedImageCount(Long ownerMemberId, String imageUrl) {
		return new TransactionTemplate(transactionManager).execute(status -> {
			Number count = (Number) entityManager.createNativeQuery("""
				SELECT COUNT(*)
				FROM product_image
				WHERE owner_member_id = :ownerMemberId
				  AND image_url = :imageUrl
				  AND product_id IS NULL
				""")
				.setParameter("ownerMemberId", ownerMemberId)
				.setParameter("imageUrl", imageUrl)
				.getSingleResult();
			return count.longValue();
		});
	}

	private long productCount(Long sellerId) {
		return new TransactionTemplate(transactionManager).execute(status -> {
			Number count = (Number) entityManager.createNativeQuery("""
				SELECT COUNT(*)
				FROM product
				WHERE seller_member_id = :sellerId
				  AND title = '키크론 Q1 Pro'
				""")
				.setParameter("sellerId", sellerId)
				.getSingleResult();
			return count.longValue();
		});
	}

	private Member createMember() {
		Member member = memberRepository.saveAndFlush(Member.createOAuthMember());
		testMemberIds.add(member.getId());
		return member;
	}

	private void inTransaction(Runnable operation) {
		new TransactionTemplate(transactionManager)
			.executeWithoutResult(status -> operation.run());
	}

	private String scheduledRequest(String imageUrl, String scheduledAt) {
		return """
			{
			  "product": {
			    "title": "키크론 Q1 Pro",
			    "description": "사용 기간 6개월, 구성품 포함",
			    "category": "ELECTRONICS",
			    "imageUrl": "%s"
			  },
			  "auction": {
			    "reservePrice": 50000,
			    "durationMinutes": 15,
			    "startType": "SCHEDULED",
			    "scheduledAt": "%s"
			  }
			}
			""".formatted(imageUrl, scheduledAt);
	}

	private String bearerAccessToken(Member member) {
		return "Bearer " + jwtTokenProvider.issue(member).accessToken();
	}
}
