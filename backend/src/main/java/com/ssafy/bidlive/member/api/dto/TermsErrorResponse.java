package com.ssafy.bidlive.member.api.dto;

/** 약관 조회 API의 오류 코드와 사용자 메시지를 반환한다. */
public record TermsErrorResponse(String code, String message) {
}
