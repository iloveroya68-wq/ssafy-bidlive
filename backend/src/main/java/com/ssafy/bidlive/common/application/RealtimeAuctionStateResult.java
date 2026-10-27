package com.ssafy.bidlive.common.application;

import java.time.OffsetDateTime;

/** 공개 경매 상태와 선택적 회원별 상태를 API 계층에 전달하는 조회 결과입니다. */
public record RealtimeAuctionStateResult(
	long stateVersion,
	RealtimeAuctionStateSnapshot.Status auctionStatus,
	OffsetDateTime endAt,
	long remainingSeconds,
	int extendCount,
	int remainingExtensions,
	long bidCount,
	long bidParticipantCount,
	long viewerCount,
	Long myBidAmount,
	Long myPredictedPrice,
	Long myStakeAmount,
	ParticipationType myParticipationType,
	PinnedNotice pinnedNotice,
	boolean amIMuted,
	OffsetDateTime mutedUntil
) {

	/** 회원의 상호 배타적인 실시간 경매 참여 유형입니다. */
	public enum ParticipationType {
		NONE,
		BID,
		PREDICTION
	}

	/** 현재 고정된 채팅 공지의 공개 값입니다. */
	public record PinnedNotice(String messageId, String content) {
	}
}
