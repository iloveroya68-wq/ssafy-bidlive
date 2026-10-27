package com.ssafy.bidlive.product.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import com.ssafy.bidlive.product.application.ProductSnapshot;
import com.ssafy.bidlive.product.application.ProductUpdatePersistence;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

/** PostgreSQL에서 상품 부분 수정과 대표 이미지 교체를 한 트랜잭션으로 처리합니다. */
@Repository
public class JpaProductUpdatePersistenceAdapter implements ProductUpdatePersistence {

	private static final String SNAPSHOT_QUERY = """
		SELECT
			p.product_id,
			p.seller_member_id,
			p.title,
			p.description,
			pc.code,
			(
				SELECT image.image_url
				FROM product_image image
				WHERE image.product_id = p.product_id
				  AND image.moderation_status <> 'BLOCKED'
				ORDER BY image.is_thumbnail DESC, image.sort_order ASC
				LIMIT 1
			)
		FROM product p
		JOIN product_category pc ON pc.category_id = p.category_id
		WHERE p.product_id = :productId
		""";

	private static final String ACTIVE_CATEGORY_QUERY = """
		SELECT category_id
		FROM product_category
		WHERE code = :categoryCode
		  AND is_active = TRUE
		""";

	private static final String UPDATE_FIELDS_QUERY = """
		UPDATE product
		SET title = :title,
			description = :description,
			category_id = (
				SELECT category_id
				FROM product_category
				WHERE code = :categoryCode
				  AND is_active = TRUE
			)
		WHERE product_id = :productId
		""";

	/**
	 * 기존 대표 이미지를 떼어낸다. 지우지 않는 이유: 업로드 시점의 저장소 객체가 남아 있고,
	 * `product_id IS NULL`은 이 스키마에서 이미 "아직 안 붙은 업로드"를 뜻한다(V12).
	 * 즉 되돌릴 수 있는 상태로 남고, 미사용 업로드 정리 경로를 그대로 탄다.
	 */
	private static final String DETACH_CURRENT_IMAGE_QUERY = """
		UPDATE product_image
		SET product_id = NULL
		WHERE product_id = :productId
		""";

	/** 등록 쪽과 같은 조건으로 판매자의 미첨부 업로드 한 건만 잠그고 붙인다. */
	private static final String CLAIM_IMAGE_QUERY = """
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

	public JpaProductUpdatePersistenceAdapter(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public Optional<ProductSnapshot> findSnapshot(long productId) {
		List<?> rows = entityManager.createNativeQuery(SNAPSHOT_QUERY)
			.setParameter("productId", productId)
			.getResultList();
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		Object[] row = (Object[]) rows.getFirst();
		return Optional.of(new ProductSnapshot(
			((Number) row[0]).longValue(),
			((Number) row[1]).longValue(),
			(String) row[2],
			(String) row[3],
			(String) row[4],
			(String) row[5]
		));
	}

	@Override
	public boolean existsActiveCategory(String categoryCode) {
		return !entityManager.createNativeQuery(ACTIVE_CATEGORY_QUERY)
			.setParameter("categoryCode", categoryCode)
			.setMaxResults(1)
			.getResultList()
			.isEmpty();
	}

	@Override
	public void updateFields(
		long productId,
		String title,
		String description,
		String categoryCode
	) {
		entityManager.createNativeQuery(UPDATE_FIELDS_QUERY)
			.setParameter("title", title)
			.setParameter("description", description)
			.setParameter("categoryCode", categoryCode)
			.setParameter("productId", productId)
			.executeUpdate();
	}

	@Override
	public boolean replaceRepresentativeImage(
		long productId,
		long sellerMemberId,
		String imageUrl
	) {
		entityManager.createNativeQuery(DETACH_CURRENT_IMAGE_QUERY)
			.setParameter("productId", productId)
			.executeUpdate();

		int claimed = entityManager.createNativeQuery(CLAIM_IMAGE_QUERY)
			.setParameter("sellerMemberId", sellerMemberId)
			.setParameter("imageUrl", imageUrl)
			.setParameter("productId", productId)
			.executeUpdate();
		return claimed > 0;
	}
}
