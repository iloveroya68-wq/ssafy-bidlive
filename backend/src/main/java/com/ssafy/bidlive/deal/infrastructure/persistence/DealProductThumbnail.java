package com.ssafy.bidlive.deal.infrastructure.persistence;

/**
 * 거래 화면들이 쓰는 대표 이미지 선택 규칙. 「지금 할 일」과 「내 거래 목록」이 같은 SQL 조각을
 * 문자 단위로 복제하고 있었다 — 한쪽만 고치면 두 화면의 썸네일이 갈린다.
 */
final class DealProductThumbnail {

	/** 상관 서브쿼리. 바깥 쿼리에 별칭 {@code p}(product)가 있어야 한다 */
	static final String SELECT = """
		(
			SELECT thumbnail.image_url
			FROM product_image thumbnail
			WHERE thumbnail.product_id = p.product_id
				AND thumbnail.moderation_status <> 'BLOCKED'
			ORDER BY thumbnail.is_thumbnail DESC, thumbnail.sort_order ASC
			LIMIT 1
		)""";

	private DealProductThumbnail() {
	}
}
