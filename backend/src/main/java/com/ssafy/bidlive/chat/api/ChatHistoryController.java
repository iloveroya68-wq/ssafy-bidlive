package com.ssafy.bidlive.chat.api;

import static com.ssafy.bidlive.chat.application.ChatHistoryErrorCode.INVALID_AUCTION_ID;
import static com.ssafy.bidlive.chat.application.ChatHistoryErrorCode.UNAUTHORIZED;

import com.ssafy.bidlive.chat.api.dto.ChatHistoryResponse;
import com.ssafy.bidlive.chat.application.ChatHistoryException;
import com.ssafy.bidlive.chat.application.ChatHistoryQueryService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 인증 회원의 경매별 이전 채팅 이력 조회 요청을 처리한다. */
@RestController
@RequestMapping("/api/v1/auctions/{auctionId}/chats")
public class ChatHistoryController {

	private final ChatHistoryQueryService queryService;

	public ChatHistoryController(ChatHistoryQueryService queryService) {
		this.queryService = queryService;
	}

	@GetMapping
	public ChatHistoryResponse getHistory(
		@PathVariable String auctionId,
		@RequestParam(required = false) String before,
		@RequestParam(required = false) Integer size,
		Authentication authentication
	) {
		return ChatHistoryResponse.from(queryService.getHistory(
			parseAuctionId(auctionId),
			authenticatedMemberId(authentication),
			before,
			size
		));
	}

	private static long parseAuctionId(String value) {
		try {
			long parsed = Long.parseLong(value);
			if (parsed <= 0) {
				throw new NumberFormatException("auctionId must be positive");
			}
			return parsed;
		} catch (NumberFormatException exception) {
			throw new ChatHistoryException(INVALID_AUCTION_ID, exception);
		}
	}

	private static Long authenticatedMemberId(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			throw new ChatHistoryException(UNAUTHORIZED);
		}
		try {
			long memberId = Long.parseLong(authentication.getName());
			if (memberId <= 0) {
				throw new NumberFormatException("memberId must be positive");
			}
			return memberId;
		} catch (IllegalArgumentException exception) {
			throw new ChatHistoryException(UNAUTHORIZED, exception);
		}
	}
}
