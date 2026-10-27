package com.ssafy.bidlive.product.application;

/**
 * 상품 수정 요청이 조건을 충족하지 못했음을 나타냅니다.
 *
 * <p>호출 도메인이 사유별로 다른 오류 코드를 내보낼 수 있도록 {@link Reason}을 함께 전달합니다
 * (생성 쪽 {@link ProductRegistrationException}은 사유가 하나뿐이라 구분이 없습니다).
 */
public class ProductUpdateException extends RuntimeException {

	/** 상품 수정이 거절되는 사유입니다. */
	public enum Reason {
		/** 상품이 없다 */
		PRODUCT_NOT_FOUND,
		/** 요청자가 상품의 판매자가 아니다 */
		NOT_PRODUCT_OWNER,
		/** 값의 형식·길이가 어긋나거나 카테고리 코드가 활성 목록에 없다 */
		INVALID_REQUEST,
		/** 지정한 이미지가 이 판매자의 미첨부 업로드가 아니다 */
		IMAGE_NOT_CLAIMABLE
	}

	private final Reason reason;

	public ProductUpdateException(Reason reason) {
		super(reason.name());
		this.reason = reason;
	}

	public Reason getReason() {
		return reason;
	}
}
