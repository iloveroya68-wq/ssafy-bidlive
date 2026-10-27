package com.ssafy.bidlive.product.infrastructure.persistence;

import java.util.List;

import com.ssafy.bidlive.product.application.ProductRegistrationCommand;
import com.ssafy.bidlive.product.application.ProductRegistrationException;
import com.ssafy.bidlive.product.application.ProductRegistrationPersistence;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

/** PostgreSQL에서 상품 생성과 판매자 소유 이미지 연결을 한 트랜잭션으로 처리합니다. */
@Repository
public class JpaProductRegistrationPersistenceAdapter
	implements ProductRegistrationPersistence {

	private static final String ACTIVE_CATEGORY_QUERY = """
		SELECT category_id
		FROM product_category
		WHERE code = :categoryCode
		  AND is_active = TRUE
		""";

	private static final String PRODUCT_INSERT_QUERY = """
		INSERT INTO product (
			seller_member_id,
			category_id,
			title,
			description,
			moderation_status
		)
		SELECT
			:sellerMemberId,
			category_id,
			:title,
			:description,
			'PENDING'
		FROM product_category
		WHERE code = :categoryCode
		  AND is_active = TRUE
		RETURNING product_id
		""";

	private static final String OWNED_IMAGE_CLAIM_QUERY = """
		WITH target_image AS (
			SELECT product_image_id
			FROM product_image
			WHERE owner_member_id = :sellerMemberId
			  AND image_url = :imageUrl
			  AND product_id IS NULL
			ORDER BY created_at DESC, product_image_id DESC
			LIMIT 1
			FOR UPDATE
		)
		UPDATE product_image image
		SET product_id = :productId
		FROM target_image
		WHERE image.product_image_id = target_image.product_image_id
		""";

	private final EntityManager entityManager;

	public JpaProductRegistrationPersistenceAdapter(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public boolean existsActiveCategory(String categoryCode) {
		List<?> rows = entityManager.createNativeQuery(ACTIVE_CATEGORY_QUERY)
			.setParameter("categoryCode", categoryCode)
			.setMaxResults(1)
			.getResultList();
		return !rows.isEmpty();
	}

	@Override
	public Long saveProductWithOwnedImage(ProductRegistrationCommand command) {
		Number productId = (Number) entityManager.createNativeQuery(
				PRODUCT_INSERT_QUERY)
			.setParameter("sellerMemberId", command.sellerMemberId())
			.setParameter("categoryCode", command.categoryCode())
			.setParameter("title", command.title())
			.setParameter("description", command.description())
			.getSingleResult();

		int claimedImageCount = entityManager.createNativeQuery(
				OWNED_IMAGE_CLAIM_QUERY)
			.setParameter("sellerMemberId", command.sellerMemberId())
			.setParameter("imageUrl", command.imageUrl())
			.setParameter("productId", productId.longValue())
			.executeUpdate();
		if (claimedImageCount != 1) {
			throw new ProductRegistrationException();
		}
		return productId.longValue();
	}
}
