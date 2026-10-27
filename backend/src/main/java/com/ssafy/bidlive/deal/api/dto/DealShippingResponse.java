package com.ssafy.bidlive.deal.api.dto;

import java.time.Instant;

import com.ssafy.bidlive.deal.application.DealShippingView;

/**
 * DEAL-05 배송 정보 조회 응답 (S15P11A704-694).
 *
 * <p>수취인 필드는 <b>마스킹하지 않는다.</b> 거래 상대에게 물건을 보내려면 실명·전화번호·주소가
 * 그대로 필요하고, 조회 자체가 그 거래 당사자로 이미 좁혀져 있다. 다른 화면의 마스킹(`판**`)은
 * 제3자에게 보이는 목록이라 기준이 다르다.
 *
 * <p>`addressSubmitted`를 따로 두는 이유: 필드가 전부 null인 것과 「입력 전」을 화면이 구분하려면
 * 판정을 서버가 해 주는 편이 낫다. 프론트가 null 조합을 다시 해석하지 않게 한다.
 */
public record DealShippingResponse(
	long dealId,
	String role,
	String dealStatus,
	boolean addressSubmitted,
	String recipientName,
	String recipientPhone,
	String postalCode,
	String address,
	String addressDetail,
	String carrier,
	String trackingNumber,
	Instant addressSubmittedAt,
	Instant shippedAt,
	Instant receivedConfirmedAt,
	Instant autoConfirmAt
) {

	public static DealShippingResponse from(DealShippingView view) {
		return new DealShippingResponse(
			view.dealId(),
			view.viewerRole().name(),
			view.dealStatus().name(),
			view.hasAddress(),
			view.recipientName(),
			view.recipientPhone(),
			view.postalCode(),
			view.address(),
			view.addressDetail(),
			view.carrier(),
			view.trackingNumber(),
			view.addressSubmittedAt(),
			view.shippedAt(),
			view.receivedConfirmedAt(),
			view.autoConfirmAt()
		);
	}
}
