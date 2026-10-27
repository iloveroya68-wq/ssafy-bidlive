package com.ssafy.bidlive.common.websocket.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.time.Instant;
import java.util.Optional;

import com.ssafy.bidlive.auction.application.AuctionOwnershipReader;
import com.ssafy.bidlive.auction.application.CatchUpRoomAccess;
import com.ssafy.bidlive.auction.application.CatchUpRoomAccessReader;
import com.ssafy.bidlive.common.security.SecurityPrincipal;
import com.ssafy.bidlive.member.domain.MemberRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** STOMP 구독 유형별 인증 및 경매 판매자 권한 판정 규칙을 검증합니다. */
@ExtendWith(MockitoExtension.class)
class StompSubscriptionAuthorizationServiceTest {

	private static final Long MEMBER_ID = 25L;
	private static final Long SELLER_ID = 88L;
	private static final Long AUCTION_ID = 42L;

	@Mock
	private AuctionOwnershipReader auctionOwnershipReader;
	@Mock
	private CatchUpRoomAccessReader roomAccessReader;

	private StompSubscriptionAuthorizationService authorizationService;
	private SecurityPrincipal principal;

	@BeforeEach
	void setUp() {
		authorizationService = new StompSubscriptionAuthorizationService(
			new StompSubscriptionDestinationResolver(),
			auctionOwnershipReader,
			roomAccessReader
		);
		principal = new SecurityPrincipal(MEMBER_ID, MemberRole.USER);
	}

	@Test
	void allowsAuthenticatedMemberToSubscribeAuctionPublicTopic() {
		stubRoom(SELLER_ID, "PUBLIC", false);

		boolean allowed = authorizationService.canSubscribe(
			principal,
			"/topic/auctions/42/chat"
		);

		assertThat(allowed).isTrue();
		verifyNoInteractions(auctionOwnershipReader);
	}

	@Test
	void deniesNonSellerFromPrivateRoomTopic() {
		stubRoom(SELLER_ID, "PRIVATE", false);

		boolean allowed = authorizationService.canSubscribe(
			principal,
			"/topic/auctions/42/chat"
		);

		assertThat(allowed).isFalse();
	}

	@Test
	void allowsSellerToSubscribePrivateRoomTopic() {
		stubRoom(MEMBER_ID, "PRIVATE", false);

		boolean allowed = authorizationService.canSubscribe(
			principal,
			"/topic/auctions/42"
		);

		assertThat(allowed).isTrue();
	}

	@Test
	void deniesSellerFromDeletedRoomTopic() {
		stubRoom(MEMBER_ID, "PUBLIC", true);

		boolean allowed = authorizationService.canSubscribe(
			principal,
			"/topic/auctions/42/chat"
		);

		assertThat(allowed).isFalse();
	}

	@Test
	void deniesRoomTopicThatDoesNotExist() {
		when(roomAccessReader.findAccess(AUCTION_ID)).thenReturn(Optional.empty());

		boolean allowed = authorizationService.canSubscribe(
			principal,
			"/topic/auctions/42/chat"
		);

		assertThat(allowed).isFalse();
	}

	@Test
	void allowsSellerToSubscribeAuctionQuestionsTopic() {
		when(auctionOwnershipReader.isSeller(AUCTION_ID, MEMBER_ID))
			.thenReturn(true);

		boolean allowed = authorizationService.canSubscribe(
			principal,
			"/topic/auctions/42/questions"
		);

		assertThat(allowed).isTrue();
		verify(auctionOwnershipReader).isSeller(AUCTION_ID, MEMBER_ID);
	}

	@Test
	void deniesNonSellerFromAuctionQuestionsTopic() {
		when(auctionOwnershipReader.isSeller(AUCTION_ID, MEMBER_ID))
			.thenReturn(false);

		boolean allowed = authorizationService.canSubscribe(
			principal,
			"/topic/auctions/42/questions"
		);

		assertThat(allowed).isFalse();
		verify(auctionOwnershipReader).isSeller(AUCTION_ID, MEMBER_ID);
	}

	@Test
	void allowsAuthenticatedMemberToSubscribeOwnUserQueue() {
		boolean allowed = authorizationService.canSubscribe(
			principal,
			"/user/queue/errors"
		);

		assertThat(allowed).isTrue();
		verifyNoInteractions(auctionOwnershipReader);
	}

	@Test
	void deniesUnregisteredDestination() {
		boolean allowed = authorizationService.canSubscribe(
			principal,
			"/topic/private"
		);

		assertThat(allowed).isFalse();
		verifyNoInteractions(auctionOwnershipReader);
	}

	@Test
	void deniesMissingPrincipal() {
		boolean allowed = authorizationService.canSubscribe(
			null,
			"/topic/auctions/42"
		);

		assertThat(allowed).isFalse();
		verifyNoInteractions(auctionOwnershipReader);
	}

	@Test
	void deniesPrincipalNotCreatedByStompAuthentication() {
		Principal unverifiedPrincipal = () -> MEMBER_ID.toString();

		boolean allowed = authorizationService.canSubscribe(
			unverifiedPrincipal,
			"/user/queue/errors"
		);

		assertThat(allowed).isFalse();
		verifyNoInteractions(auctionOwnershipReader);
	}

	private void stubRoom(Long sellerId, String visibility, boolean deleted) {
		when(roomAccessReader.findAccess(AUCTION_ID)).thenReturn(Optional.of(
			new CatchUpRoomAccess(
				AUCTION_ID,
				sellerId,
				"LIVE",
				visibility,
				deleted,
				Instant.parse("2026-08-06T12:00:00Z")
			)
		));
	}
}
