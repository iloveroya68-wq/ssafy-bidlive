package com.ssafy.bidlive.media.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.Objects;

import com.ssafy.bidlive.media.domain.LiveBroadcast;
import com.ssafy.bidlive.media.domain.LiveBroadcastStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "live_broadcast")
public class LiveBroadcastJpaEntity {

	private static final int MAX_ROOM_ID_LENGTH = 100;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "live_broadcast_id")
	private Long id;

	@Column(name = "room_id", nullable = false, updatable = false, length = MAX_ROOM_ID_LENGTH)
	private String roomId;

	@Column(name = "broadcaster_id", nullable = false)
	private Long broadcasterId;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private LiveBroadcastStatus status;

	@Column(name = "started_at", nullable = false)
	private OffsetDateTime startedAt;

	@Column(name = "ended_at")
	private OffsetDateTime endedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected LiveBroadcastJpaEntity() {
	}

	private LiveBroadcastJpaEntity(String roomId, Long broadcasterId, OffsetDateTime startedAt) {
		String normalizedRoomId = Objects.requireNonNull(roomId, "roomId").strip();
		if (normalizedRoomId.isBlank()) {
			throw new IllegalArgumentException("roomId는 비어 있을 수 없습니다.");
		}
		if (normalizedRoomId.length() > MAX_ROOM_ID_LENGTH) {
			throw new IllegalArgumentException("roomId는 100자를 초과할 수 없습니다.");
		}

		this.roomId = normalizedRoomId;
		this.broadcasterId = Objects.requireNonNull(broadcasterId, "broadcasterId");
		this.status = LiveBroadcastStatus.LIVE;
		this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
	}

	static LiveBroadcastJpaEntity live(String roomId, Long broadcasterId, OffsetDateTime startedAt) {
		return new LiveBroadcastJpaEntity(roomId, broadcasterId, startedAt);
	}

	/** 종료된 방송이 있는 방에서 새 방송이 시작되면 같은 행을 재사용해 개시자와 시작 시각을 바꾼다. */
	void restart(Long broadcasterId, OffsetDateTime startedAt) {
		this.broadcasterId = Objects.requireNonNull(broadcasterId, "broadcasterId");
		this.status = LiveBroadcastStatus.LIVE;
		this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
		this.endedAt = null;
	}

	LiveBroadcast toDomain() {
		return new LiveBroadcast(roomId, broadcasterId, status, startedAt, endedAt);
	}

	LiveBroadcastStatus status() {
		return status;
	}

	@PrePersist
	void onCreate() {
		OffsetDateTime now = OffsetDateTime.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = OffsetDateTime.now();
	}
}
