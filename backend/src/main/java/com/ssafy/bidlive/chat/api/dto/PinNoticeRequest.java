package com.ssafy.bidlive.chat.api.dto;

import jakarta.validation.constraints.NotBlank;

/** 고정할 채팅 메시지 식별자를 전달합니다. */
public record PinNoticeRequest(
	@NotBlank String messageId
) {
}
