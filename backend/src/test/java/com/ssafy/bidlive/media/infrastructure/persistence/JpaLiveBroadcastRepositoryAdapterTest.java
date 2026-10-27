package com.ssafy.bidlive.media.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.Optional;

import com.ssafy.bidlive.media.domain.LiveBroadcast;
import com.ssafy.bidlive.media.domain.LiveBroadcastRepository;
import com.ssafy.bidlive.media.domain.LiveBroadcastStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

/** 방마다 방송 기록이 하나로 유지되고, 종료는 LIVE 상태에서만 성립하는 조건부 갱신인지 검증합니다. */
@DataJpaTest
@Import(JpaLiveBroadcastRepositoryAdapter.class)
class JpaLiveBroadcastRepositoryAdapterTest {

	private static final String ROOM_ID = "room-123";
	private static final OffsetDateTime STARTED_AT =
		OffsetDateTime.parse("2026-07-20T21:00:00+09:00");
	private static final OffsetDateTime ENDED_AT =
		OffsetDateTime.parse("2026-07-20T21:40:00+09:00");

	@Autowired
	private SpringDataLiveBroadcastRepository springDataRepository;

	@Autowired
	private LiveBroadcastRepository liveBroadcastRepository;

	@Autowired
	private EntityManager entityManager;

	private Long broadcasterId;
	private Long otherMemberId;

	@BeforeEach
	void insertMembers() {
		broadcasterId = insertMember("판매자");
		otherMemberId = insertMember("다른회원");
	}

	@Test
	void recordsLiveBroadcastOnFirstStart() {
		liveBroadcastRepository.startBroadcast(ROOM_ID, broadcasterId, STARTED_AT);
		flushAndClear();

		Optional<LiveBroadcast> broadcast = liveBroadcastRepository.findByRoomId(ROOM_ID);

		assertThat(broadcast).isPresent();
		assertThat(broadcast.get().broadcasterId()).isEqualTo(broadcasterId);
		assertThat(broadcast.get().status()).isEqualTo(LiveBroadcastStatus.LIVE);
		assertThat(broadcast.get().startedAt()).isEqualTo(STARTED_AT);
		assertThat(broadcast.get().endedAt()).isNull();
	}

	/** 같은 방의 반복 발급이 새 행을 만들거나 개시자를 바꾸면 종료 권한이 넘어가므로 유지해야 한다. */
	@Test
	void keepsFirstBroadcasterWhileBroadcastIsLive() {
		liveBroadcastRepository.startBroadcast(ROOM_ID, broadcasterId, STARTED_AT);
		flushAndClear();

		liveBroadcastRepository.startBroadcast(ROOM_ID, otherMemberId, ENDED_AT);
		flushAndClear();

		assertThat(springDataRepository.count()).isEqualTo(1);
		assertThat(liveBroadcastRepository.findByRoomId(ROOM_ID).orElseThrow().broadcasterId())
			.isEqualTo(broadcasterId);
	}

	@Test
	void endsLiveBroadcastAndStoresEndedAt() {
		liveBroadcastRepository.startBroadcast(ROOM_ID, broadcasterId, STARTED_AT);
		flushAndClear();

		assertThat(liveBroadcastRepository.endBroadcast(ROOM_ID, ENDED_AT)).isTrue();
		flushAndClear();

		LiveBroadcast broadcast = liveBroadcastRepository.findByRoomId(ROOM_ID).orElseThrow();
		assertThat(broadcast.status()).isEqualTo(LiveBroadcastStatus.ENDED);
		assertThat(broadcast.endedAt()).isEqualTo(ENDED_AT);
	}

	/** 두 번째 종료 요청은 갱신 대상이 없으므로 false가 되어 409로 이어진다. */
	@Test
	void reportsNoChangeWhenBroadcastIsAlreadyEnded() {
		liveBroadcastRepository.startBroadcast(ROOM_ID, broadcasterId, STARTED_AT);
		flushAndClear();
		liveBroadcastRepository.endBroadcast(ROOM_ID, ENDED_AT);
		flushAndClear();

		assertThat(liveBroadcastRepository.endBroadcast(ROOM_ID, ENDED_AT)).isFalse();
	}

	@Test
	void reportsNoChangeForUnknownRoom() {
		assertThat(liveBroadcastRepository.endBroadcast("room-unknown", ENDED_AT)).isFalse();
	}

	@Test
	void restartsEndedBroadcastWithNewBroadcaster() {
		liveBroadcastRepository.startBroadcast(ROOM_ID, broadcasterId, STARTED_AT);
		flushAndClear();
		liveBroadcastRepository.endBroadcast(ROOM_ID, ENDED_AT);
		flushAndClear();

		OffsetDateTime restartedAt = OffsetDateTime.parse("2026-07-20T22:00:00+09:00");
		liveBroadcastRepository.startBroadcast(ROOM_ID, otherMemberId, restartedAt);
		flushAndClear();

		LiveBroadcast broadcast = liveBroadcastRepository.findByRoomId(ROOM_ID).orElseThrow();
		assertThat(springDataRepository.count()).isEqualTo(1);
		assertThat(broadcast.broadcasterId()).isEqualTo(otherMemberId);
		assertThat(broadcast.status()).isEqualTo(LiveBroadcastStatus.LIVE);
		assertThat(broadcast.startedAt()).isEqualTo(restartedAt);
		assertThat(broadcast.endedAt()).isNull();
	}

	@Test
	void returnsEmptyForRoomWithoutBroadcast() {
		assertThat(liveBroadcastRepository.findByRoomId(ROOM_ID)).isEmpty();
	}

	private Long insertMember(String nickname) {
		return (Long) entityManager
			.createNativeQuery("""
				insert into member (nickname, role, status)
				values (:nickname, 'USER', 'ACTIVE')
				returning member_id
				""")
			.setParameter("nickname", nickname)
			.getSingleResult();
	}

	private void flushAndClear() {
		entityManager.flush();
		entityManager.clear();
	}
}
