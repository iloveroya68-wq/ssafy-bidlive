package com.ssafy.bidlive.product.domain;

/** 검열 요청이 거부되는 사유이며 이름이 그대로 오류 응답의 `code`가 됩니다. */
public enum ProductInspectionFailureCode {

	/** 검열할 텍스트와 이미지가 모두 없거나 이미지 개수 한도를 넘겼다. */
	INVALID_INSPECTION_REQUEST,

	/** Credit이 소진되어 검열을 수행할 수 없다. */
	AI_CREDIT_EXHAUSTED
}
