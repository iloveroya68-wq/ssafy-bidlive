package com.ssafy.bidlive.media.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.ssafy.bidlive.media.application.ViewerCountService;
import com.ssafy.bidlive.media.application.port.out.ViewerCountPublisher;
import com.ssafy.bidlive.media.application.port.out.ViewerSessionStore;
import org.junit.jupiter.api.Test;

/** 접속 세션 설정이 TTL 정책을 가진 활성 수 집계 서비스를 생성하는지 검증합니다. */
class ViewerSessionConfigurationTest {

	@Test
	void createsViewerCountService() {
		ViewerSessionConfiguration configuration =
			new ViewerSessionConfiguration();
		ViewerSessionProperties properties = new ViewerSessionProperties();

		ViewerCountService service = configuration.viewerCountService(
			mock(ViewerSessionStore.class),
			mock(ViewerCountPublisher.class),
			properties
		);

		assertThat(service).isNotNull();
	}
}
