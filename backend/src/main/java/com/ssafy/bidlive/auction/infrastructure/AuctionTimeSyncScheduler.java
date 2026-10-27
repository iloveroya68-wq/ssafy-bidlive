package com.ssafy.bidlive.auction.infrastructure;

import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ssafy.bidlive.auction.application.AuctionTimeSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.user.SimpSubscription;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 실제 메인 토픽 구독자가 있는 LIVE 경매의 시간을 30초마다 다시 동기화한다. */
@Component
public class AuctionTimeSyncScheduler {

	private static final Logger log = LoggerFactory.getLogger(
		AuctionTimeSyncScheduler.class
	);
	private static final Pattern MAIN_TOPIC_PATTERN = Pattern.compile(
		"^/topic/auctions/([1-9]\\d*)$"
	);

	private final SimpUserRegistry userRegistry;
	private final AuctionTimeSyncService timeSyncService;

	public AuctionTimeSyncScheduler(
		SimpUserRegistry userRegistry,
		AuctionTimeSyncService timeSyncService
	) {
		this.userRegistry = Objects.requireNonNull(
			userRegistry,
			"userRegistry"
		);
		this.timeSyncService = Objects.requireNonNull(
			timeSyncService,
			"timeSyncService"
		);
	}

	@Scheduled(fixedRate = 30, timeUnit = TimeUnit.SECONDS)
	void synchronizeSubscribedAuctions() {
		for (Long auctionId : subscribedAuctionIds()) {
			try {
				timeSyncService.synchronizeIfLive(auctionId);
			} catch (RuntimeException exception) {
				log.warn(
					"Scheduled auction TIME_SYNC failed: auctionId={}, failureType={}",
					auctionId,
					exception.getClass().getSimpleName()
				);
			}
		}
	}

	private Set<Long> subscribedAuctionIds() {
		Set<Long> auctionIds = new TreeSet<>();
		userRegistry.getUsers().forEach(user ->
			user.getSessions().forEach(session ->
				session.getSubscriptions().stream()
					.map(SimpSubscription::getDestination)
					.map(AuctionTimeSyncScheduler::auctionId)
					.filter(Objects::nonNull)
					.forEach(auctionIds::add)
			)
		);
		return auctionIds;
	}

	private static Long auctionId(String destination) {
		if (destination == null) {
			return null;
		}
		Matcher matcher = MAIN_TOPIC_PATTERN.matcher(destination);
		if (!matcher.matches()) {
			return null;
		}
		try {
			return Long.parseLong(matcher.group(1));
		} catch (NumberFormatException exception) {
			return null;
		}
	}
}
