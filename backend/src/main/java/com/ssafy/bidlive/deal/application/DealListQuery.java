package com.ssafy.bidlive.deal.application;

import java.util.List;

/** 내 거래 목록 읽기 포트 */
public interface DealListQuery {

	/** 다음 장 존재 판정을 위해 <b>size + 1</b>건을 읽어 온다 */
	List<DealListItem> findDeals(DealListCriteria criteria);
}
