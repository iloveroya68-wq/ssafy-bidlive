package com.ssafy.bidlive.deal.application;

import java.time.Instant;

import com.ssafy.bidlive.deal.domain.DealRole;

/**
 * 거래 목록 한 줄.
 *
 * <p>{@code counterpartyNickname}은 <b>마스킹된 값</b>이다 — 상대방 원문 닉네임을 화면까지
 * 보내면 개발자 도구로 걷어낼 수 있다(AGENTS.md 규칙 6).
 *
 * <p>{@code settledAt}은 완료된 거래에만 있다. 진행 중이면 null이다.
 *
 * <p>⚠️ {@code status}는 <b>enum이 아니라 문자열</b>이다. {@code deal.deal_status}에는 DB CHECK가
 * 없어서 {@code DealStatus}에 없는 값이 들어 있을 수 있고, {@code valueOf}로 받으면 그 한 행 때문에
 * 목록 전체가 500이 된다({@code DealStatus} javadoc이 경고하는 그대로다). 응답도 결국 문자열이라
 * enum 왕복이 얻는 것 없이 위험만 만든다.
 */
public record DealListItem(
	long dealId,
	long auctionId,
	DealRole role,
	String productName,
	String thumbnailUrl,
	long finalPrice,
	String status,
	String counterpartyNickname,
	Instant settledAt,
	Instant sortedAt
) {
}
