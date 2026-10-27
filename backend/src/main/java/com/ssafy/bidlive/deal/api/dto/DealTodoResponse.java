package com.ssafy.bidlive.deal.api.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.ssafy.bidlive.deal.application.DealTodoItem;
import com.ssafy.bidlive.deal.domain.DealRole;
import com.ssafy.bidlive.deal.domain.DealStatus;
import com.ssafy.bidlive.deal.domain.DealTodoAction;

/** 「지금 할 일」 한 건의 응답이다. 버튼 문구와 이동 경로까지 서버가 채운다. */
public record DealTodoResponse(
	Long dealId,
	Long auctionId,
	String productTitle,
	String imageUrl,
	DealRole role,
	DealStatus dealStatus,
	long finalPrice,
	DealTodoAction actionCode,
	String actionLabel,
	String actionDescription,
	String actionPath,
	OffsetDateTime endedAt,
	OffsetDateTime updatedAt,
	OffsetDateTime autoConfirmAt
) {

	private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

	public static DealTodoResponse from(DealTodoItem item) {
		DealTodoAction action = item.action();
		return new DealTodoResponse(
			item.dealId(),
			item.auctionId(),
			item.productTitle(),
			item.imageUrl(),
			item.role(),
			item.status(),
			item.finalPrice(),
			action,
			action.label(),
			action.description(),
			action.pathFor(item.dealId()),
			atKorea(item.endedAt()),
			atKorea(item.updatedAt()),
			atKorea(item.autoConfirmAt())
		);
	}

	static OffsetDateTime atKorea(Instant instant) {
		return instant == null ? null : instant.atZone(KOREA_ZONE).toOffsetDateTime();
	}
}
