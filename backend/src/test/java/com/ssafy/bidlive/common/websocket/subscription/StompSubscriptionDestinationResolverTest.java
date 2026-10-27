package com.ssafy.bidlive.common.websocket.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** STOMP 구독 Destination의 허용 유형과 기본 거부 해석을 검증합니다. */
class StompSubscriptionDestinationResolverTest {

	private final StompSubscriptionDestinationResolver resolver =
		new StompSubscriptionDestinationResolver();

	@ParameterizedTest
	@MethodSource("auctionPublicDestinations")
	void resolvesAuctionPublicDestination(String destination, Long auctionId) {
		StompSubscriptionDestination result = resolver.resolve(destination);

		assertThat(result.type())
			.isEqualTo(StompSubscriptionDestinationType.AUCTION_PUBLIC);
		assertThat(result.auctionId()).isEqualTo(auctionId);
	}

	@Test
	void resolvesQuestionsBeforeGeneralAuctionSubtopic() {
		StompSubscriptionDestination result = resolver.resolve(
			"/topic/auctions/42/questions"
		);

		assertThat(result.type())
			.isEqualTo(StompSubscriptionDestinationType.AUCTION_QUESTIONS);
		assertThat(result.auctionId()).isEqualTo(42L);
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"/user/queue/errors",
		"/user/queue/moderation",
		"/user/queue/custom/events"
	})
	void resolvesCurrentUserQueue(String destination) {
		StompSubscriptionDestination result = resolver.resolve(destination);

		assertThat(result.type())
			.isEqualTo(StompSubscriptionDestinationType.USER_QUEUE);
		assertThat(result.auctionId()).isNull();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {" ", "\t"})
	void resolvesMissingDestinationAsUnknown(String destination) {
		assertUnknown(destination);
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"/topic/auctions/0",
		"/topic/auctions/-1",
		"/topic/auctions/01",
		"/topic/auctions/not-a-number",
		"/topic/auctions/9223372036854775808",
		"/topic/auctions/42/",
		"/topic/auctions/42/questions/extra",
		"/topic/auctions/42/chat?memberId=999",
		"/topic/auctions/42/%71uestions",
		"/user/queue/",
		"/user/42/queue/errors",
		"/queue/errors",
		"/topic/private"
	})
	void resolvesMalformedOrUnregisteredDestinationAsUnknown(String destination) {
		assertUnknown(destination);
	}

	private void assertUnknown(String destination) {
		StompSubscriptionDestination result = resolver.resolve(destination);

		assertThat(result.type())
			.isEqualTo(StompSubscriptionDestinationType.UNKNOWN);
		assertThat(result.auctionId()).isNull();
	}

	private static Stream<Arguments> auctionPublicDestinations() {
		return Stream.of(
			Arguments.of("/topic/auctions/1", 1L),
			Arguments.of("/topic/auctions/42/chat", 42L),
			Arguments.of("/topic/auctions/100/viewers", 100L),
			Arguments.of("/topic/auctions/200/bids", 200L),
			Arguments.of(
				"/topic/auctions/9223372036854775807",
				Long.MAX_VALUE
			)
		);
	}
}
