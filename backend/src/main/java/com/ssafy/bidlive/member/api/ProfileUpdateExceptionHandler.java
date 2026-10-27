package com.ssafy.bidlive.member.api;

import com.ssafy.bidlive.member.api.dto.ProfileUpdateErrorResponse;
import com.ssafy.bidlive.member.application.ProfileUpdateException;
import com.ssafy.bidlive.member.domain.ProfileUpdateErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 프로필 수정 실패를 명세의 HTTP 상태와 오류 응답으로 변환한다. */
@RestControllerAdvice(assignableTypes = ProfileUpdateController.class)
public class ProfileUpdateExceptionHandler {

	@ExceptionHandler(ProfileUpdateException.class)
	ResponseEntity<ProfileUpdateErrorResponse> handle(
		ProfileUpdateException exception
	) {
		ProfileUpdateErrorCode errorCode = exception.getErrorCode();
		return ResponseEntity.status(httpStatus(errorCode))
			.body(new ProfileUpdateErrorResponse(
				errorCode.name(),
				message(errorCode)
			));
	}

	private HttpStatus httpStatus(ProfileUpdateErrorCode errorCode) {
		return switch (errorCode) {
			case EMPTY_UPDATE_REQUEST, INVALID_NICKNAME -> HttpStatus.BAD_REQUEST;
			case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
			case MEMBER_NOT_FOUND -> HttpStatus.NOT_FOUND;
			case NICKNAME_ALREADY_EXISTS -> HttpStatus.CONFLICT;
		};
	}

	private String message(ProfileUpdateErrorCode errorCode) {
		return switch (errorCode) {
			case EMPTY_UPDATE_REQUEST -> "수정할 프로필 정보를 입력해 주세요.";
			case INVALID_NICKNAME -> "닉네임 형식 또는 금칙어 정책을 확인해 주세요.";
			case UNAUTHORIZED -> "인증이 필요합니다.";
			case MEMBER_NOT_FOUND -> "인증 정보에 해당하는 회원이 존재하지 않습니다.";
			case NICKNAME_ALREADY_EXISTS -> "이미 사용 중인 닉네임입니다.";
		};
	}
}
