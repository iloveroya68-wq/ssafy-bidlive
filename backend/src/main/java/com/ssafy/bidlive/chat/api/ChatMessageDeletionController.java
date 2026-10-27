package com.ssafy.bidlive.chat.api;

import static com.ssafy.bidlive.chat.application.ChatMessageDeletionErrorCode.INVALID_AUCTION_ID;
import static com.ssafy.bidlive.chat.application.ChatMessageDeletionErrorCode.INVALID_MESSAGE_ID;
import static com.ssafy.bidlive.chat.application.ChatMessageDeletionErrorCode.UNAUTHORIZED;

import com.ssafy.bidlive.chat.application.ChatMessageDeletionException;
import com.ssafy.bidlive.chat.application.ChatMessageDeletionService;
import com.ssafy.bidlive.chat.domain.ChatMessageId;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 판매자와 운영자의 채팅 메시지 삭제 REST 요청을 검증하고 유스케이스에 전달합니다. */
@RestController
@RequestMapping("/api/v1/auctions/{auctionId}/chats/{messageId}")
public class ChatMessageDeletionController {

	private final ChatMessageDeletionService deletionService;

	public ChatMessageDeletionController(
		ChatMessageDeletionService deletionService
	) {
		this.deletionService = deletionService;
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(
		@PathVariable String auctionId,
		@PathVariable String messageId,
		Authentication authentication
	) {
		deletionService.deleteByMember(
			parseAuctionId(auctionId),
			authenticatedMemberId(authentication),
			parseMessageId(messageId)
		);
	}

	private static long parseAuctionId(String value) {
		try {
			long parsed = Long.parseLong(value);
			if (parsed <= 0) {
				throw new NumberFormatException("auctionId must be positive");
			}
			return parsed;
		} catch (NumberFormatException exception) {
			throw new ChatMessageDeletionException(
				INVALID_AUCTION_ID,
				exception
			);
		}
	}

	private static ChatMessageId parseMessageId(String value) {
		try {
			return new ChatMessageId(value);
		} catch (IllegalArgumentException exception) {
			throw new ChatMessageDeletionException(
				INVALID_MESSAGE_ID,
				exception
			);
		}
	}

	private static Long authenticatedMemberId(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			throw new ChatMessageDeletionException(UNAUTHORIZED);
		}
		try {
			long memberId = Long.parseLong(authentication.getName());
			if (memberId <= 0) {
				throw new NumberFormatException("memberId must be positive");
			}
			return memberId;
		} catch (IllegalArgumentException exception) {
			throw new ChatMessageDeletionException(UNAUTHORIZED, exception);
		}
	}
}
