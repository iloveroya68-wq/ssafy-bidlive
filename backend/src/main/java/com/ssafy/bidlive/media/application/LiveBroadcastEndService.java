package com.ssafy.bidlive.media.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.ssafy.bidlive.media.application.port.out.LiveSessionGateway;
import com.ssafy.bidlive.media.application.port.out.LiveSessionGatewayException;
import com.ssafy.bidlive.media.domain.LiveBroadcast;
import com.ssafy.bidlive.media.domain.LiveBroadcastEndFailureCode;
import com.ssafy.bidlive.media.domain.LiveBroadcastEndedEvent;
import com.ssafy.bidlive.media.domain.LiveBroadcastRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 방송 개시자의 요청으로 방송 상태와 미디어 세션을 함께 정리하는 유스케이스입니다.
 *
 * <p>순서는 조건부 상태 갱신 → 미디어 세션 종료로 고정한다. 미디어 서버 호출이 실패하면
 * 예외가 트랜잭션을 롤백해 방송이 LIVE로 남으므로 같은 요청을 다시 시도할 수 있고,
 * 반대 순서에서 생기는 "미디어는 끊겼는데 상태는 LIVE"인 보상 불가 상태를 만들지 않는다.
 *
 * <p>시청자 브로드캐스트는 이벤트로 분리해 커밋 이후에만 나가도록 한다. 롤백된 종료가
 * 시청자에게 먼저 전달되는 것을 막기 위한 전제이다.
 */
@Service
public class LiveBroadcastEndService {

	/** 방송 종료 시각도 LIVE-01 발급 시각과 같은 서비스 기준 오프셋을 사용한다. */
	private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

	private final LiveBroadcastRepository liveBroadcastRepository;
	private final LiveSessionGateway liveSessionGateway;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	@Autowired
	public LiveBroadcastEndService(
		LiveBroadcastRepository liveBroadcastRepository,
		LiveSessionGateway liveSessionGateway,
		ApplicationEventPublisher eventPublisher
	) {
		this(liveBroadcastRepository, liveSessionGateway, eventPublisher, Clock.system(SERVICE_ZONE));
	}

	/** 종료 시각을 고정해야 하는 테스트에서만 사용한다. */
	LiveBroadcastEndService(
		LiveBroadcastRepository liveBroadcastRepository,
		LiveSessionGateway liveSessionGateway,
		ApplicationEventPublisher eventPublisher,
		Clock clock
	) {
		this.liveBroadcastRepository = liveBroadcastRepository;
		this.liveSessionGateway = liveSessionGateway;
		this.eventPublisher = eventPublisher;
		this.clock = clock;
	}

	@Transactional
	public void endBroadcast(String roomId, Long requesterId) {
		if (roomId == null || roomId.isBlank()) {
			throw new IllegalArgumentException("roomId must not be blank");
		}
		if (requesterId == null) {
			throw new IllegalArgumentException("requesterId must not be null");
		}

		LiveBroadcast broadcast = liveBroadcastRepository.findByRoomId(roomId)
			.orElseThrow(() -> rejected(LiveBroadcastEndFailureCode.LIVE_NOT_FOUND));

		if (!broadcast.isStartedBy(requesterId)) {
			throw rejected(LiveBroadcastEndFailureCode.NOT_BROADCAST_OWNER);
		}
		if (!broadcast.isLive()) {
			throw rejected(LiveBroadcastEndFailureCode.LIVE_NOT_ACTIVE);
		}

		OffsetDateTime endedAt = OffsetDateTime.now(clock);
		if (!liveBroadcastRepository.endBroadcast(roomId, endedAt)) {
			throw rejected(LiveBroadcastEndFailureCode.LIVE_NOT_ACTIVE);
		}

		closeMediaSession(roomId);

		eventPublisher.publishEvent(new LiveBroadcastEndedEvent(roomId, endedAt));
	}

	/** LIVE-01이 roomId를 customSessionId로 사용하므로 종료 대상 세션 식별자도 roomId와 같다. */
	private void closeMediaSession(String roomId) {
		try {
			liveSessionGateway.closeSession(roomId);
		} catch (LiveSessionGatewayException exception) {
			throw new LiveBroadcastEndRejectedException(
				LiveBroadcastEndFailureCode.MEDIA_SERVER_ERROR, exception);
		}
	}

	private LiveBroadcastEndRejectedException rejected(LiveBroadcastEndFailureCode failureCode) {
		return new LiveBroadcastEndRejectedException(failureCode);
	}
}
