package com.ssafy.bidlive.deal.application;

import java.util.List;

/** 커서 페이지. `nextCursor`는 다음 장이 있을 때만 채운다 */
public record DealListPage(List<DealListItem> items, String nextCursor, boolean hasNext) {
}
