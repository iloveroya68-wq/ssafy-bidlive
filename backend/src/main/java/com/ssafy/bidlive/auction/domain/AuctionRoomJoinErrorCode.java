package com.ssafy.bidlive.auction.domain;

/**
 * 대기방 입장 오류 코드다.
 *
 * <p>{@code ROOM_ACCESS_DENIED}와 {@code AUCTION_ROOM_NOT_FOUND}의 쓰임이 갈린다 —
 * 앞은 **회원 상태**(정지·탈퇴)로 막는 경우고, 비공개 방을 남이 열려 할 때는 방의 존재를 감춰야
 * 하므로 뒤를 쓴다(S15P11A704-482).
 */
public enum AuctionRoomJoinErrorCode {
	UNAUTHORIZED,
	ROOM_ACCESS_DENIED,
	AUCTION_ROOM_NOT_FOUND,
	AUCTION_ALREADY_ENDED
}
