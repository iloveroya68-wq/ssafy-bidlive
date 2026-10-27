package com.ssafy.bidlive.media.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ssafy.bidlive.media.application.port.out.ViewerSessionStore;
import org.junit.jupiter.api.Test;

/** 접속자 수 조회와 Redis 장애 시 공개 API의 안전한 0 대체를 검증합니다. */
class ViewerCountQueryServiceTest {

	private final ViewerSessionStore sessionStore = mock(ViewerSessionStore.class);
	private final ViewerCountQueryService service =
		new ViewerCountQueryService(sessionStore);

	@Test
	void returnsViewerSessionCount() {
		when(sessionStore.count(42L)).thenReturn(18L);

		assertThat(service.activeViewerCount(42L)).isEqualTo(18L);
	}

	@Test
	void fallsBackToZeroWhenRedisIsUnavailable() {
		when(sessionStore.count(42L)).thenThrow(new IllegalStateException("redis down"));

		assertThat(service.activeViewerCount(42L)).isZero();
	}

	@Test
	void rejectsInvalidAuctionId() {
		assertThatThrownBy(() -> service.activeViewerCount(0L))
			.isInstanceOf(IllegalArgumentException.class);
	}
}
