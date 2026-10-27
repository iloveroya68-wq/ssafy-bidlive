package com.ssafy.bidlive.chat.application;

/** 채팅 메시지 소프트 삭제와 연계 상태 변경을 원자적으로 수행한다. */
public interface ChatMessageDeletionPersistence {

	ChatMessageDeletionResult delete(ChatMessageDeletionCommand command);
}
