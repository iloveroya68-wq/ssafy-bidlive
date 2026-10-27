package com.ssafy.bidlive.chat.api;

import static com.ssafy.bidlive.chat.domain.PinnedNoticeErrorCode.INVALID_AUCTION_ID;
import static com.ssafy.bidlive.chat.domain.PinnedNoticeErrorCode.INVALID_MESSAGE_ID;
import static com.ssafy.bidlive.chat.domain.PinnedNoticeErrorCode.UNAUTHORIZED;

import com.ssafy.bidlive.chat.api.dto.PinNoticeRequest;
import com.ssafy.bidlive.chat.api.dto.PinnedNoticeResponse;
import com.ssafy.bidlive.chat.application.PinnedNoticeCommandService;
import com.ssafy.bidlive.chat.application.PinnedNoticeException;
import com.ssafy.bidlive.chat.domain.ChatMessageId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 판매자의 고정 공지 등록·교체·해제 REST 요청을 처리합니다. */
@RestController
@RequestMapping("/api/v1/auctions/{auctionId}/chat/pin")
public class PinnedNoticeController {

	private final PinnedNoticeCommandService commandService;

	public PinnedNoticeController(PinnedNoticeCommandService commandService) {
		this.commandService = commandService;
	}

	@PostMapping
	public PinnedNoticeResponse pin(
		@PathVariable String auctionId,
		@Valid @RequestBody PinNoticeRequest request,
		Authentication authentication
	) {
		return PinnedNoticeResponse.from(commandService.pin(
			parseAuctionId(auctionId),
			authenticatedMemberId(authentication),
			parseMessageId(request.messageId())
		));
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void unpin(
		@PathVariable String auctionId,
		Authentication authentication
	) {
		commandService.unpin(
			parseAuctionId(auctionId),
			authenticatedMemberId(authentication)
		);
	}

	private static long parseAuctionId(String value) {
		try {
			long parsed = Long.parseLong(value);
			if (parsed <= 0) {
				throw new NumberFormatException("id must be positive");
			}
			return parsed;
		} catch (NumberFormatException exception) {
			throw new PinnedNoticeException(INVALID_AUCTION_ID, exception);
		}
	}

	private static ChatMessageId parseMessageId(String value) {
		try {
			return new ChatMessageId(value);
		} catch (IllegalArgumentException exception) {
			throw new PinnedNoticeException(INVALID_MESSAGE_ID, exception);
		}
	}

	private static Long authenticatedMemberId(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			throw new PinnedNoticeException(UNAUTHORIZED);
		}
		try {
			long memberId = Long.parseLong(authentication.getName());
			if (memberId <= 0) {
				throw new NumberFormatException("memberId must be positive");
			}
			return memberId;
		} catch (IllegalArgumentException exception) {
			throw new PinnedNoticeException(UNAUTHORIZED, exception);
		}
	}
}
