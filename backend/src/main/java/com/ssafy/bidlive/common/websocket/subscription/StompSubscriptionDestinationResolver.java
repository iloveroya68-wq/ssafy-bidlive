package com.ssafy.bidlive.common.websocket.subscription;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/** STOMP SUBSCRIBE Destination을 권한 판정 유형과 경매 식별자로 해석합니다. */
@Component
public class StompSubscriptionDestinationResolver {

	private static final String AUCTION_ID_GROUP = "auctionId";
	private static final String AUCTION_ID_PATTERN = "(?<auctionId>[1-9][0-9]*)";
	private static final String SAFE_PATH_SEGMENT = "[A-Za-z0-9][A-Za-z0-9_-]*";

	private static final Pattern AUCTION_QUESTIONS_PATTERN = Pattern.compile(
		"^/topic/auctions/" + AUCTION_ID_PATTERN + "/questions$"
	);

	private static final Pattern AUCTION_PUBLIC_PATTERN = Pattern.compile(
		"^/topic/auctions/" + AUCTION_ID_PATTERN
			+ "(?:/" + SAFE_PATH_SEGMENT + ")?$"
	);

	private static final Pattern USER_QUEUE_PATTERN = Pattern.compile(
		"^/user/queue/" + SAFE_PATH_SEGMENT
			+ "(?:/" + SAFE_PATH_SEGMENT + ")*$"
	);

	public StompSubscriptionDestination resolve(String destination) {
		if (destination == null || destination.isBlank()) {
			return StompSubscriptionDestination.unknown();
		}

		Matcher questionsMatcher = AUCTION_QUESTIONS_PATTERN.matcher(destination);
		if (questionsMatcher.matches()) {
			return resolveAuctionDestination(questionsMatcher, true);
		}

		Matcher publicMatcher = AUCTION_PUBLIC_PATTERN.matcher(destination);
		if (publicMatcher.matches()) {
			return resolveAuctionDestination(publicMatcher, false);
		}

		if (USER_QUEUE_PATTERN.matcher(destination).matches()) {
			return StompSubscriptionDestination.userQueue();
		}

		return StompSubscriptionDestination.unknown();
	}

	private StompSubscriptionDestination resolveAuctionDestination(
		Matcher matcher,
		boolean questions
	) {
		try {
			Long auctionId = Long.valueOf(matcher.group(AUCTION_ID_GROUP));
			if (questions) {
				return StompSubscriptionDestination.auctionQuestions(auctionId);
			}
			return StompSubscriptionDestination.auctionPublic(auctionId);
		} catch (NumberFormatException exception) {
			return StompSubscriptionDestination.unknown();
		}
	}
}
