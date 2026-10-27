package com.ssafy.bidlive.media.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.ssafy.bidlive.media.application.port.out.LiveSessionGateway;
import com.ssafy.bidlive.media.application.port.out.LiveSessionGatewayException;
import com.ssafy.bidlive.media.domain.LiveBroadcast;
import com.ssafy.bidlive.media.domain.LiveBroadcastEndFailureCode;
import com.ssafy.bidlive.media.domain.LiveBroadcastEndedEvent;
import com.ssafy.bidlive.media.domain.LiveBroadcastRepository;
import com.ssafy.bidlive.media.domain.LiveBroadcastStatus;
import com.ssafy.bidlive.media.domain.LiveSessionRole;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 방송 개시자만 종료할 수 있고, 상태 갱신이 미디어 세션 종료보다 먼저 일어나며,
 * 미디어 서버 실패는 예외로 올려 트랜잭션을 롤백시키는 정책을 검증합니다.
 */
class LiveBroadcastEndServiceTest {

	private static final String ROOM_ID = "room-123";
	private static final Long BROADCASTER_ID = 25L;
	private static final Clock FIXED_CLOCK =
		Clock.fixed(Instant.parse("2026-07-20T12:40:00Z"), ZoneId.of("Asia/Seoul"));
	private static final OffsetDateTime EXPECTED_ENDED_AT =
		OffsetDateTime.parse("2026-07-20T21:40:00+09:00");

	private final RecordingLiveBroadcastRepository repository =
		new RecordingLiveBroadcastRepository();
	private final RecordingLiveSessionGateway gateway = new RecordingLiveSessionGateway();
	private final RecordingEventPublisher eventPublisher = new RecordingEventPublisher();
	private final LiveBroadcastEndService liveBroadcastEndService =
		new LiveBroadcastEndService(repository, gateway, eventPublisher, FIXED_CLOCK);

	@Test
	void endsBroadcastBeforeClosingMediaSessionAndPublishesEndedEvent() {
		repository.store(liveBroadcast());

		liveBroadcastEndService.endBroadcast(ROOM_ID, BROADCASTER_ID);

		assertThat(repository.calls).containsExactly(
			"findByRoomId:room-123",
			"endBroadcast:room-123:2026-07-20T21:40+09:00"
		);
		assertThat(gateway.closedSessions).containsExactly(ROOM_ID);
		assertThat(eventPublisher.events)
			.containsExactly(new LiveBroadcastEndedEvent(ROOM_ID, EXPECTED_ENDED_AT));
	}

	@Test
	void rejectsUnknownRoomBeforeTouchingMediaServer() {
		assertThatThrownBy(() -> liveBroadcastEndService.endBroadcast(ROOM_ID, BROADCASTER_ID))
			.isInstanceOf(LiveBroadcastEndRejectedException.class)
			.extracting(exception -> ((LiveBroadcastEndRejectedException) exception).getFailureCode())
			.isEqualTo(LiveBroadcastEndFailureCode.LIVE_NOT_FOUND);

		assertThat(gateway.closedSessions).isEmpty();
		assertThat(eventPublisher.events).isEmpty();
	}

	@Test
	void rejectsMemberWhoDidNotStartBroadcast() {
		repository.store(liveBroadcast());

		assertThatThrownBy(() -> liveBroadcastEndService.endBroadcast(ROOM_ID, 99L))
			.isInstanceOf(LiveBroadcastEndRejectedException.class)
			.extracting(exception -> ((LiveBroadcastEndRejectedException) exception).getFailureCode())
			.isEqualTo(LiveBroadcastEndFailureCode.NOT_BROADCAST_OWNER);

		assertThat(repository.calls).containsExactly("findByRoomId:room-123");
		assertThat(gateway.closedSessions).isEmpty();
	}

	@Test
	void rejectsAlreadyEndedBroadcast() {
		repository.store(new LiveBroadcast(
			ROOM_ID,
			BROADCASTER_ID,
			LiveBroadcastStatus.ENDED,
			OffsetDateTime.parse("2026-07-20T21:00:00+09:00"),
			OffsetDateTime.parse("2026-07-20T21:30:00+09:00")
		));

		assertThatThrownBy(() -> liveBroadcastEndService.endBroadcast(ROOM_ID, BROADCASTER_ID))
			.isInstanceOf(LiveBroadcastEndRejectedException.class)
			.extracting(exception -> ((LiveBroadcastEndRejectedException) exception).getFailureCode())
			.isEqualTo(LiveBroadcastEndFailureCode.LIVE_NOT_ACTIVE);

		assertThat(gateway.closedSessions).isEmpty();
	}

	/** 조회 이후 다른 요청이 먼저 종료를 확정한 경우 조건부 갱신이 0건이 되어 종료를 거부한다. */
	@Test
	void rejectsConcurrentEndWhenConditionalUpdateChangesNothing() {
		repository.store(liveBroadcast());
		repository.endBroadcastResult = false;

		assertThatThrownBy(() -> liveBroadcastEndService.endBroadcast(ROOM_ID, BROADCASTER_ID))
			.isInstanceOf(LiveBroadcastEndRejectedException.class)
			.extracting(exception -> ((LiveBroadcastEndRejectedException) exception).getFailureCode())
			.isEqualTo(LiveBroadcastEndFailureCode.LIVE_NOT_ACTIVE);

		assertThat(gateway.closedSessions).isEmpty();
		assertThat(eventPublisher.events).isEmpty();
	}

	/** 미디어 서버 실패는 502로 변환되고 예외가 올라가야 상태 갱신이 함께 롤백된다. */
	@Test
	void reportsMediaServerErrorWithoutPublishingEndedEvent() {
		repository.store(liveBroadcast());
		gateway.closeFailure =
			new LiveSessionGatewayException("Failed to close OpenVidu session");

		assertThatThrownBy(() -> liveBroadcastEndService.endBroadcast(ROOM_ID, BROADCASTER_ID))
			.isInstanceOf(LiveBroadcastEndRejectedException.class)
			.hasCauseInstanceOf(LiveSessionGatewayException.class)
			.extracting(exception -> ((LiveBroadcastEndRejectedException) exception).getFailureCode())
			.isEqualTo(LiveBroadcastEndFailureCode.MEDIA_SERVER_ERROR);

		assertThat(eventPublisher.events).isEmpty();
	}

	@Test
	void rejectsBlankRoomIdAndMissingRequester() {
		assertThatThrownBy(() -> liveBroadcastEndService.endBroadcast(" ", BROADCASTER_ID))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> liveBroadcastEndService.endBroadcast(ROOM_ID, null))
			.isInstanceOf(IllegalArgumentException.class);

		assertThat(repository.calls).isEmpty();
	}

	private LiveBroadcast liveBroadcast() {
		return new LiveBroadcast(
			ROOM_ID,
			BROADCASTER_ID,
			LiveBroadcastStatus.LIVE,
			OffsetDateTime.parse("2026-07-20T21:00:00+09:00"),
			null
		);
	}

	private static final class RecordingLiveBroadcastRepository implements LiveBroadcastRepository {

		private final List<String> calls = new ArrayList<>();
		private LiveBroadcast stored;
		private boolean endBroadcastResult = true;

		private void store(LiveBroadcast broadcast) {
			this.stored = broadcast;
		}

		@Override
		public Optional<LiveBroadcast> findByRoomId(String roomId) {
			calls.add("findByRoomId:" + roomId);
			return Optional.ofNullable(stored);
		}

		@Override
		public void startBroadcast(String roomId, Long broadcasterId, OffsetDateTime startedAt) {
			calls.add("startBroadcast:" + roomId + ":" + broadcasterId);
		}

		@Override
		public boolean endBroadcast(String roomId, OffsetDateTime endedAt) {
			calls.add("endBroadcast:" + roomId + ":" + endedAt);
			return endBroadcastResult;
		}
	}

	private static final class RecordingLiveSessionGateway implements LiveSessionGateway {

		private final List<String> closedSessions = new ArrayList<>();
		private LiveSessionGatewayException closeFailure;

		@Override
		public String ensureSession(String roomId) {
			return roomId;
		}

		@Override
		public String issueToken(String sessionId, LiveSessionRole role) {
			return "token-" + role;
		}

		@Override
		public void closeSession(String sessionId) {
			if (closeFailure != null) {
				throw closeFailure;
			}
			closedSessions.add(sessionId);
		}
	}

	private static final class RecordingEventPublisher implements ApplicationEventPublisher {

		private final List<Object> events = new ArrayList<>();

		@Override
		public void publishEvent(Object event) {
			events.add(event);
		}
	}
}
