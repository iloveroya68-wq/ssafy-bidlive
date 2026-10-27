package com.ssafy.bidlive.deal.application;

import com.ssafy.bidlive.deal.domain.DealErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 거래 배송 정보 조회 (S15P11A704-694).
 *
 * <p>없는 거래와 남의 거래를 <b>같은 404로</b> 답한다. 구분해 주면 거래 번호를 훑어 「존재하는
 * 거래」를 알아낼 수 있고, 그 자체가 남의 거래 정보다. 조회 조건이 둘을 이미 합쳐 놓아서
 * 여기서는 나눌 수도 없다.
 */
@Service
public class DealShippingQueryService {

	private final DealShippingQuery shippingQuery;

	public DealShippingQueryService(DealShippingQuery shippingQuery) {
		this.shippingQuery = shippingQuery;
	}

	@Transactional(readOnly = true)
	public DealShippingView findShipping(long dealId, long memberId) {
		if (dealId <= 0) {
			throw new DealException(DealErrorCode.DEAL_NOT_FOUND);
		}
		return shippingQuery.findForParticipant(dealId, memberId)
			.orElseThrow(() -> new DealException(DealErrorCode.DEAL_NOT_FOUND));
	}
}
