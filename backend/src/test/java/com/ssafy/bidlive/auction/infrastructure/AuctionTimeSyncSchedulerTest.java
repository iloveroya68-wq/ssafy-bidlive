package com.ssafy.bidlive.auction.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.ssafy.bidlive.auction.application.AuctionTimeSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpSubscription;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 구독자가 있는 메인 토픽만 30초 TIME_SYNC 대상으로 수집하고 실패를 격리하는지 검증한다. */
class AuctionTimeSyncSchedulerTest {

	private SimpUserRegistry userRegistry;
	private AuctionTimeSyncService timeSyncService;
	private AuctionTimeSyncScheduler scheduler;

	@BeforeEach
	void setUp() {
		userRegistry = mock(SimpUserRegistry.class);
		timeSyncService = mock(AuctionTimeSyncService.class);
		scheduler = new AuctionTimeSyncScheduler(
			userRegistry,
			timeSyncService
		);
	}

	@Test
	void synchronizesEachSubscribedMainTopicOnce() {
		registerDestinations(
			"/topic/auctions/101",
			"/topic/auctions/100",
			"/topic/auctions/100",
			"/topic/auctions/200/viewers",
			"/topic/auctions/201/chat"
		);

		scheduler.synchronizeSubscribedAuctions();

		verify(timeSyncService).synchronizeIfLive(100L);
		verify(timeSyncService).synchronizeIfLive(101L);
		verifyNoMoreInteractions(timeSyncService);
	}

	@Test
	void continuesWithOtherAuctionsWhenOneSynchronizationFails() {
		registerDestinations(
			"/topic/auctions/100",
			"/topic/auctions/101"
		);
		when(timeSyncService.synchronizeIfLive(100L))
			.thenThrow(new IllegalStateException("redis unavailable"));

		scheduler.synchronizeSubscribedAuctions();

		verify(timeSyncService).synchronizeIfLive(100L);
		verify(timeSyncService).synchronizeIfLive(101L);
	}

	@Test
	void doesNothingWithoutSubscriptions() {
		when(userRegistry.getUsers()).thenReturn(Set.of());

		scheduler.synchronizeSubscribedAuctions();

		verifyNoInteractions(timeSyncService);
	}

	@Test
	void enablesSchedulingInAuctionInfrastructure() {
		assertThat(AuctionTimeSyncSchedulingConfiguration.class
			.getAnnotation(EnableScheduling.class))
			.isNotNull();
	}

	private void registerDestinations(String... destinations) {
		SimpUser user = mock(SimpUser.class);
		SimpSession session = mock(SimpSession.class);
		Set<SimpSubscription> subscriptions = new LinkedHashSet<>();
		for (String destination : List.of(destinations)) {
			SimpSubscription subscription = mock(SimpSubscription.class);
			when(subscription.getDestination()).thenReturn(destination);
			subscriptions.add(subscription);
		}
		when(session.getSubscriptions()).thenReturn(subscriptions);
		when(user.getSessions()).thenReturn(Set.of(session));
		when(userRegistry.getUsers()).thenReturn(Set.of(user));
	}
}
