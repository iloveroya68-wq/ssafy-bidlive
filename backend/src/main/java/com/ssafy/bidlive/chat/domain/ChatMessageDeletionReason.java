package com.ssafy.bidlive.chat.domain;

/** 채팅 메시지 소프트 삭제의 내부 감사 사유를 구분한다. */
public enum ChatMessageDeletionReason {
	SELLER_DELETED,
	ADMIN_DELETED,
	MODERATION_BLOCKED,
	QUESTION_FILTERED
}
