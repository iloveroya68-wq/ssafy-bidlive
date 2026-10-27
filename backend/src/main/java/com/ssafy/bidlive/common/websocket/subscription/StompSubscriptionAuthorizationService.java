package com.ssafy.bidlive.common.websocket.subscription;

import java.security.Principal;

import com.ssafy.bidlive.auction.application.AuctionOwnershipReader;
import com.ssafy.bidlive.auction.application.CatchUpRoomAccessReader;
import com.ssafy.bidlive.auction.domain.AuctionRoomVisibility;
import com.ssafy.bidlive.common.security.SecurityPrincipal;
import org.springframework.stereotype.Service;

/** 세션 Principal과 Destination을 기준으로 STOMP 구독 권한을 판정합니다. */
@Service
public class StompSubscriptionAuthorizationService {

	private final StompSubscriptionDestinationResolver destinationResolver;
	private final AuctionOwnershipReader auctionOwnershipReader;
	private final CatchUpRoomAccessReader roomAccessReader;

	public StompSubscriptionAuthorizationService(
		StompSubscriptionDestinationResolver destinationResolver,
		AuctionOwnershipReader auctionOwnershipReader,
		CatchUpRoomAccessReader roomAccessReader
	) {
		this.destinationResolver = destinationResolver;
		this.auctionOwnershipReader = auctionOwnershipReader;
		this.roomAccessReader = roomAccessReader;
	}

	public boolean canSubscribe(
		Principal principal,
		String destination
	) {
		if (!(principal instanceof SecurityPrincipal securityPrincipal)) {
			return false;
		}

		StompSubscriptionDestination resolved = destinationResolver.resolve(
			destination
		);
		return switch (resolved.type()) {
			case AUCTION_PUBLIC -> canSubscribeRoom(
				resolved.auctionId(),
				securityPrincipal.memberId()
			);
			case USER_QUEUE -> true;
			case AUCTION_QUESTIONS -> auctionOwnershipReader.isSeller(
				resolved.auctionId(),
				securityPrincipal.memberId()
			);
			case UNKNOWN -> false;
		};
	}

	/**
	 * `AUCTION_PUBLIC`은 Destination 모양일 뿐 방이 공개라는 뜻이 아니다. 방의 공개 여부를
	 * 보지 않으면 순번 auctionId를 훑는 것만으로 비공개 방의 실시간 채팅과 `AUCTION_SETTLED`
	 * 낙찰 정보가 그대로 흘러나가, REST가 막는 것을 구독이 내주게 된다.
	 *
	 * <p>판정 규칙은 ROOM-07 입장·채팅 이력과 같다. 비공개 방은 비판매자에게 존재 자체를 알리지
	 * 않아야 하므로 없는 방과 같은 거절로 되돌린다(S15P11A704-482).
	 */
	private boolean canSubscribeRoom(Long auctionId, Long memberId) {
		return roomAccessReader.findAccess(auctionId)
			.filter(room -> !room.deleted())
			.filter(room -> AuctionRoomVisibility.isPublic(room.visibility())
				|| room.sellerId() == memberId)
			.isPresent();
	}
}
