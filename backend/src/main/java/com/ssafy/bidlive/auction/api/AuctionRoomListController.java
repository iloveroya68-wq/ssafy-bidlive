package com.ssafy.bidlive.auction.api;

import com.ssafy.bidlive.auction.api.dto.AuctionRoomListResponse;
import com.ssafy.bidlive.auction.application.AuctionRoomListQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 비로그인도 볼 수 있는 공개 경매방 목록을 상태·카테고리·검색어 조건으로 반환한다. */
@RestController
@RequestMapping("/api/v1/auction-rooms")
public class AuctionRoomListController {

	private final AuctionRoomListQueryService listQueryService;

	public AuctionRoomListController(AuctionRoomListQueryService listQueryService) {
		this.listQueryService = listQueryService;
	}

	@GetMapping
	public AuctionRoomListResponse getRooms(
		@RequestParam(required = false) String status,
		@RequestParam(required = false) Long categoryId,
		@RequestParam(required = false) String keyword,
		@RequestParam(required = false) String cursor,
		@RequestParam(required = false) Integer size
	) {
		return AuctionRoomListResponse.from(
			listQueryService.getRooms(status, categoryId, keyword, cursor, size)
		);
	}
}
