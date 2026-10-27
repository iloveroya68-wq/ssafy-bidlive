package com.ssafy.bidlive.media.application;

import com.ssafy.bidlive.media.application.port.out.ViewerSessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 경매별 활성 STOMP 접속 세션 수를 다른 도메인에 공개합니다. */
@Service
public class ViewerCountQueryService {

	private static final Logger log = LoggerFactory.getLogger(
		ViewerCountQueryService.class
	);

	private final ViewerSessionStore sessionStore;

	public ViewerCountQueryService(ViewerSessionStore sessionStore) {
		this.sessionStore = sessionStore;
	}

	public long activeViewerCount(long auctionId) {
		if (auctionId <= 0) {
			throw new IllegalArgumentException("auctionId는 양수여야 합니다.");
		}
		try {
			return sessionStore.count(auctionId);
		} catch (RuntimeException exception) {
			log.warn(
				"Viewer session Redis operation failed: operation=count, failureType={}",
				exception.getClass().getSimpleName()
			);
			return 0L;
		}
	}
}
