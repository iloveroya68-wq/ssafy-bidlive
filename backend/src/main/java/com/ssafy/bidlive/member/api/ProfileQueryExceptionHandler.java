package com.ssafy.bidlive.member.api;

import com.ssafy.bidlive.member.api.dto.ProfileQueryErrorResponse;
import com.ssafy.bidlive.member.application.ProfileQueryException;
import com.ssafy.bidlive.member.domain.ProfileQueryErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 프로필 조회 실패를 명세의 HTTP 상태와 오류 응답으로 변환한다. */
@RestControllerAdvice(assignableTypes = ProfileQueryController.class)
public class ProfileQueryExceptionHandler {

	@ExceptionHandler(ProfileQueryException.class)
	ResponseEntity<ProfileQueryErrorResponse> handle(
		ProfileQueryException exception
	) {
		ProfileQueryErrorCode errorCode = exception.getErrorCode();
		return ResponseEntity.status(httpStatus(errorCode))
			.body(new ProfileQueryErrorResponse(
				errorCode.name(),
				message(errorCode)
			));
	}

	private HttpStatus httpStatus(ProfileQueryErrorCode errorCode) {
		return switch (errorCode) {
			case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
			case MEMBER_NOT_AVAILABLE -> HttpStatus.FORBIDDEN;
			case MEMBER_NOT_FOUND -> HttpStatus.NOT_FOUND;
			case ONBOARDING_NOT_COMPLETED -> HttpStatus.CONFLICT;
			case PROFILE_SUMMARY_QUERY_FAILED ->
				HttpStatus.INTERNAL_SERVER_ERROR;
		};
	}

	private String message(ProfileQueryErrorCode errorCode) {
		return switch (errorCode) {
			case UNAUTHORIZED -> "인증이 필요합니다.";
			case MEMBER_NOT_AVAILABLE -> "탈퇴한 회원은 프로필을 조회할 수 없습니다.";
			case MEMBER_NOT_FOUND -> "인증 정보에 해당하는 회원이 존재하지 않습니다.";
			case ONBOARDING_NOT_COMPLETED ->
				"닉네임 설정과 필수 약관 동의를 완료해 주세요.";
			case PROFILE_SUMMARY_QUERY_FAILED ->
				"프로필 활동 요약을 조회하지 못했습니다.";
		};
	}
}
