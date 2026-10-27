package com.ssafy.bidlive.deal.application;

import java.util.Optional;

/** 거래 배송 정보 조회 포트 (S15P11A704-694). */
public interface DealShippingQuery {

	/**
	 * 그 거래의 <b>당사자에게만</b> 배송 정보를 준다.
	 *
	 * <p>회원 판정을 서비스가 아니라 조회 조건에 두는 이유: 남의 거래는 애초에 행이 돌아오지 않아야
	 * 「조회해 놓고 버리는」 경로가 생기지 않는다. 구매자의 실명·전화번호·주소가 실리는 응답이라
	 * 판정이 한 곳이어야 한다.
	 *
	 * @return 당사자가 아니거나 없는 거래면 빈 값 — 호출부는 둘을 구분하지 않고 404로 답한다
	 */
	Optional<DealShippingView> findForParticipant(long dealId, long memberId);
}
