package com.ssafy.bidlive.common.api.dto;

import java.time.OffsetDateTime;
import java.util.Objects;

import com.ssafy.bidlive.common.application.RealtimeAuctionStateResult;

/** 재접속 시 공개 상태와 요청 회원의 참여 상태를 함께 복원하는 API 응답입니다. */
public record RealtimeAuctionStateResponse(
	long stateVersion,
	RealtimeAuctionStatus auctionStatus,
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
	RealtimeParticipationType myParticipationType,
	PinnedNoticeResponse pinnedNotice,
	boolean amIMuted,
	OffsetDateTime mutedUntil
) {

	public RealtimeAuctionStateResponse {
		requireNonNegative(stateVersion, "stateVersion");
		Objects.requireNonNull(auctionStatus, "auctionStatus");
		Objects.requireNonNull(endAt, "endAt");
		requireNonNegative(remainingSeconds, "remainingSeconds");
		requireNonNegative(extendCount, "extendCount");
		requireNonNegative(remainingExtensions, "remainingExtensions");
		requireNonNegative(bidCount, "bidCount");
		requireNonNegative(bidParticipantCount, "bidParticipantCount");
		requireNonNegative(viewerCount, "viewerCount");
		Objects.requireNonNull(myParticipationType, "myParticipationType");
		if (amIMuted != (mutedUntil != null)) {
			throw new IllegalArgumentException(
				"amIMuted and mutedUntil must describe the same mute state"
			);
		}
	}

	/** application 조회 결과를 외부 realtime-state 응답 계약으로 변환합니다. */
	public static RealtimeAuctionStateResponse from(
		RealtimeAuctionStateResult result
	) {
		Objects.requireNonNull(result, "result");
		return new RealtimeAuctionStateResponse(
			result.stateVersion(),
			RealtimeAuctionStatus.valueOf(result.auctionStatus().name()),
			result.endAt(),
			result.remainingSeconds(),
			result.extendCount(),
			result.remainingExtensions(),
			result.bidCount(),
			result.bidParticipantCount(),
			result.viewerCount(),
			result.myBidAmount(),
			result.myPredictedPrice(),
			result.myStakeAmount(),
			RealtimeParticipationType.valueOf(
				result.myParticipationType().name()
			),
			result.pinnedNotice() == null
				? null
				: new PinnedNoticeResponse(
					result.pinnedNotice().messageId(),
					result.pinnedNotice().content()
				),
			result.amIMuted(),
			result.mutedUntil()
		);
	}

	private static void requireNonNegative(long value, String fieldName) {
		if (value < 0) {
			throw new IllegalArgumentException(fieldName + "은 0 이상이어야 합니다.");
		}
	}

	/** 현재 고정된 공지의 외부 공개 필드입니다. */
	public record PinnedNoticeResponse(
		String messageId,
		String content
	) {

		public PinnedNoticeResponse {
			messageId = requireText(messageId, "messageId");
			content = requireText(content, "content");
		}

		private static String requireText(String value, String fieldName) {
			Objects.requireNonNull(value, fieldName);
			if (value.isBlank()) {
				throw new IllegalArgumentException(fieldName + "은 비어 있을 수 없습니다.");
			}
			return value;
		}
	}
}
