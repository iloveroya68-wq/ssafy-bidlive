package com.ssafy.bidlive.wallet.application;

import java.util.List;

public record PointLedgerQueryPage(
	List<PointLedgerQueryRow> rows,
	String nextCursor,
	boolean hasNext
) {
}
