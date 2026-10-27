package com.ssafy.bidlive.chat.application;

import java.util.Optional;

import com.ssafy.bidlive.chat.domain.PinnedNotice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 현재 고정 공지를 다른 유스케이스가 재접속 상태에 포함할 수 있도록 공개합니다. */
@Service
public class PinnedNoticeQueryService {

	private final PinnedNoticePersistence persistence;

	public PinnedNoticeQueryService(PinnedNoticePersistence persistence) {
		this.persistence = persistence;
	}

	@Transactional(readOnly = true)
	public Optional<PinnedNotice> findPinned(long auctionId) {
		if (auctionId <= 0) {
			throw new IllegalArgumentException("auctionId must be positive");
		}
		return persistence.findPinned(auctionId);
	}
}
