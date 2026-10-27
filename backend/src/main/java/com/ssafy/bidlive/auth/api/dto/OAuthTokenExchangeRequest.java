package com.ssafy.bidlive.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

public record OAuthTokenExchangeRequest(
	@NotBlank String loginCode
) {
}
