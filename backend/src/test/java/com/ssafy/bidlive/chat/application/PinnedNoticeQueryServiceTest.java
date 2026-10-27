package com.ssafy.bidlive.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.ssafy.bidlive.chat.domain.PinnedNotice;
import org.junit.jupiter.api.Test;

/** WS-03이 사용할 현재 고정 공지 공개 조회 계약을 검증합니다. */
class PinnedNoticeQueryServiceTest {

	private final PinnedNoticePersistence persistence =
		mock(PinnedNoticePersistence.class);
	private final PinnedNoticeQueryService service =
		new PinnedNoticeQueryService(persistence);

	@Test
	void returnsCurrentPinnedNotice() {
		PinnedNotice notice = new PinnedNotice("101", "배송비 포함입니다.");
		when(persistence.findPinned(42L)).thenReturn(Optional.of(notice));

		assertThat(service.findPinned(42L)).contains(notice);
	}

	@Test
	void rejectsInvalidAuctionId() {
		assertThatThrownBy(() -> service.findPinned(0L))
			.isInstanceOf(IllegalArgumentException.class);
	}
}
