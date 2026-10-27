package com.ssafy.bidlive.chat.application;

import java.util.Optional;

import com.ssafy.bidlive.chat.domain.ChatMessageId;
import com.ssafy.bidlive.chat.domain.PinnedNotice;

/** 방별 고정 공지의 잠금·조회·교체·해제를 제공하는 영속화 계약입니다. */
public interface PinnedNoticePersistence {

	void lockAuction(long auctionId);

	Optional<PinnedNotice> findMessageForPin(
		long auctionId,
		ChatMessageId messageId
	);

	Optional<PinnedNotice> findPinned(long auctionId);

	boolean replacePinned(long auctionId, ChatMessageId messageId);

	boolean clearPinned(long auctionId);
}
