package com.ssafy.bidlive.chat.application;

/** 소프트 삭제의 변경 여부와 연계 상태를 Application 계층에 반환한다. */
public record ChatMessageDeletionResult(
	Status status,
	boolean pinned,
	boolean question
) {

	public ChatMessageDeletionResult {
		if (status != Status.DELETED && (pinned || question)) {
			throw new IllegalArgumentException(
				"unchanged deletion cannot have changed relations"
			);
		}
	}

	public static ChatMessageDeletionResult deleted(
		boolean pinned,
		boolean question
	) {
		return new ChatMessageDeletionResult(Status.DELETED, pinned, question);
	}

	public static ChatMessageDeletionResult alreadyDeleted() {
		return new ChatMessageDeletionResult(
			Status.ALREADY_DELETED,
			false,
			false
		);
	}

	public static ChatMessageDeletionResult notFound() {
		return new ChatMessageDeletionResult(Status.NOT_FOUND, false, false);
	}

	public boolean deleted() {
		return status == Status.DELETED;
	}

	public enum Status {
		DELETED,
		ALREADY_DELETED,
		NOT_FOUND
	}
}
