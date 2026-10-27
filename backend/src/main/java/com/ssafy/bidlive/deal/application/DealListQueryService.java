package com.ssafy.bidlive.deal.application;

import java.util.List;

import com.ssafy.bidlive.deal.domain.DealListCursor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DEAL-01 내 거래 목록 조회.
 *
 * <p>다음 장 존재는 <b>size + 1건을 읽어</b> 판정한다 — COUNT를 따로 치면 두 쿼리가 서로 다른
 * 스냅샷을 보게 되어 "다음 장이 있다는데 비어 있는" 경계가 생긴다.
 */
@Service
public class DealListQueryService {

	private static final int DEFAULT_SIZE = 20;
	private static final int MAX_SIZE = 100;

	private final DealListQuery dealListQuery;

	public DealListQueryService(DealListQuery dealListQuery) {
		this.dealListQuery = dealListQuery;
	}

	@Transactional(readOnly = true)
	public DealListPage getDeals(DealListCriteria criteria) {
		int size = normalizeSize(criteria.size());
		List<DealListItem> fetched = dealListQuery.findDeals(
			new DealListCriteria(
				criteria.memberId(),
				criteria.role(),
				criteria.status(),
				criteria.cursor(),
				size
			)
		);

		boolean hasNext = fetched.size() > size;
		List<DealListItem> items = hasNext ? fetched.subList(0, size) : fetched;
		String nextCursor = hasNext ? nextCursorOf(items) : null;
		return new DealListPage(List.copyOf(items), nextCursor, hasNext);
	}

	/** 명세 「기본 20, 최대 100」. 0 이하는 기본값으로 되돌린다 */
	private static int normalizeSize(int size) {
		if (size <= 0) {
			return DEFAULT_SIZE;
		}
		return Math.min(size, MAX_SIZE);
	}

	private static String nextCursorOf(List<DealListItem> items) {
		DealListItem last = items.get(items.size() - 1);
		return new DealListCursor(last.sortedAt(), last.dealId()).encode();
	}
}
