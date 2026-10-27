package com.ssafy.bidlive.product.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.bidlive.product.application.ProductSnapshot;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상품 부분 수정과 대표 이미지 교체를 실제 PostgreSQL로 검증한다.
 *
 * <p>특히 이미지 교체는 `product_image`에 `UNIQUE (product_id, sort_order)`가 걸려 있고 업로드가
 * 항상 `sort_order = 1`이라, 떼기보다 붙이기를 먼저 하면 제약 위반으로 터진다.
 */
@SpringBootTest
@Transactional
class JpaProductUpdatePersistenceAdapterIntegrationTest {

	@Autowired
	private JpaProductUpdatePersistenceAdapter adapter;

	@Autowired
	private EntityManager entityManager;

	@BeforeEach
	void insertFixture() {
		execute("""
			INSERT INTO product_category (category_id, code, name, is_active, display_order)
			VALUES
				(9301, 'TEST_UPD_A', '수정 테스트 A', TRUE, 9301),
				(9302, 'TEST_UPD_B', '수정 테스트 B', TRUE, 9302),
				(9303, 'TEST_UPD_OFF', '비활성 카테고리', FALSE, 9303)
			""");
		execute("""
			INSERT INTO member (member_id, nickname, role, status)
			VALUES
				(9301, '수정판매자', 'USER', 'ACTIVE'),
				(9302, '남의판매자', 'USER', 'ACTIVE')
			""");
		execute("""
			INSERT INTO product (product_id, seller_member_id, category_id, title, description)
			VALUES (9301, 9301, 9301, '원래 제목', '원래 설명')
			""");
		// 현재 붙어 있는 대표 이미지 + 아직 안 붙은 업로드 2건(내 것 / 남의 것)
		execute("""
			INSERT INTO product_image (
				product_image_id, product_id, image_url, sort_order, is_thumbnail,
				image_id, owner_member_id, storage_key, content_type, size_bytes
			)
			VALUES
				(9301, 9301, 'https://cdn.test/old.png', 1, TRUE,
					gen_random_uuid(), 9301, 'test/old', 'image/png', 1),
				(9302, NULL, 'https://cdn.test/new.png', 1, FALSE,
					gen_random_uuid(), 9301, 'test/new', 'image/png', 1),
				(9303, NULL, 'https://cdn.test/others.png', 1, FALSE,
					gen_random_uuid(), 9302, 'test/others', 'image/png', 1)
			""");
		entityManager.flush();
	}

	@Test
	void readsCurrentValuesWithRepresentativeImage() {
		ProductSnapshot snapshot = adapter.findSnapshot(9301L).orElseThrow();

		assertThat(snapshot.sellerMemberId()).isEqualTo(9301L);
		assertThat(snapshot.title()).isEqualTo("원래 제목");
		assertThat(snapshot.description()).isEqualTo("원래 설명");
		assertThat(snapshot.categoryCode()).isEqualTo("TEST_UPD_A");
		assertThat(snapshot.imageUrl()).isEqualTo("https://cdn.test/old.png");
	}

	@Test
	void updatesFieldsAndCategory() {
		adapter.updateFields(9301L, "바뀐 제목", "바뀐 설명", "TEST_UPD_B");
		entityManager.flush();
		entityManager.clear();

		ProductSnapshot snapshot = adapter.findSnapshot(9301L).orElseThrow();
		assertThat(snapshot.title()).isEqualTo("바뀐 제목");
		assertThat(snapshot.description()).isEqualTo("바뀐 설명");
		assertThat(snapshot.categoryCode()).isEqualTo("TEST_UPD_B");
	}

	@Test
	void reportsActiveCategoryOnly() {
		assertThat(adapter.existsActiveCategory("TEST_UPD_A")).isTrue();
		assertThat(adapter.existsActiveCategory("TEST_UPD_OFF")).isFalse();
		assertThat(adapter.existsActiveCategory("NOPE")).isFalse();
	}

	/** 떼기 → 붙이기 순서가 지켜지는지. 유니크 제약이 있으니 순서가 틀리면 예외로 드러난다 */
	@Test
	void replacesRepresentativeImageWithOwnedUpload() {
		boolean replaced = adapter.replaceRepresentativeImage(
			9301L,
			9301L,
			"https://cdn.test/new.png"
		);
		entityManager.flush();
		entityManager.clear();

		assertThat(replaced).isTrue();
		assertThat(adapter.findSnapshot(9301L).orElseThrow().imageUrl())
			.isEqualTo("https://cdn.test/new.png");
		// 예전 이미지는 지워지지 않고 "안 붙은 업로드"로 남는다 (되돌릴 수 있는 상태)
		assertThat(attachedProductId(9301L)).isNull();
	}

	@Test
	void refusesUploadOwnedByAnotherMember() {
		boolean replaced = adapter.replaceRepresentativeImage(
			9301L,
			9301L,
			"https://cdn.test/others.png"
		);

		assertThat(replaced).isFalse();
	}

	@Test
	void refusesUnknownImageUrl() {
		assertThat(adapter.replaceRepresentativeImage(9301L, 9301L, "https://cdn.test/none.png"))
			.isFalse();
	}

	@Test
	void returnsEmptyForMissingProduct() {
		assertThat(adapter.findSnapshot(999_999L)).isEmpty();
	}

	private Long attachedProductId(long productImageId) {
		Object value = entityManager.createNativeQuery("""
			SELECT product_id FROM product_image WHERE product_image_id = :id
			""")
			.setParameter("id", productImageId)
			.getSingleResult();
		return value == null ? null : ((Number) value).longValue();
	}

	private void execute(String sql) {
		entityManager.createNativeQuery(sql).executeUpdate();
	}
}
