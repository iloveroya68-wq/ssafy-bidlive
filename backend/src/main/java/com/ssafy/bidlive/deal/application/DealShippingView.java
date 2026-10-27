package com.ssafy.bidlive.deal.application;

import java.time.Instant;

import com.ssafy.bidlive.deal.domain.DealRole;
import com.ssafy.bidlive.deal.domain.DealStatus;

/**
 * 거래 한 건의 배송 정보다 (S15P11A704-694).
 *
 * <p>이 값이 필요한 이유는 단순하다 — <b>판매자가 어디로 보낼지 알 방법이 없었다.</b> 배송지는
 * 구매자가 `PATCH /deals/{id}/shipping-address`로 넣으면 저장만 되고 되읽는 API가 없어서,
 * 판매자는 「발송 확인」 버튼만 받았다.
 *
 * <p>배송지 입력 전에는 수취인 필드가 전부 null이다 — 그 자체가 「아직 안 받았다」는 답이라
 * 404로 감추지 않는다. 발송 확인 전에는 송장 필드가 null이다.
 */
public record DealShippingView(
	long dealId,
	/** 조회한 사람의 역할. 화면이 「보낼 곳」과 「받을 곳」 중 무엇으로 읽을지 가른다 */
	DealRole viewerRole,
	DealStatus dealStatus,
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
	/** 수령 대기 중일 때의 자동 구매 확정 예정 시각 */
	Instant autoConfirmAt
) {

	/** 배송지가 들어왔는가 — 판매자 화면이 「구매자가 아직 입력하지 않았어요」를 띄우는 근거 */
	public boolean hasAddress() {
		return addressSubmittedAt != null;
	}
}
