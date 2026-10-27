package com.ssafy.bidlive.common.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import com.ssafy.bidlive.chat.application.ChatModerationPenaltyPort;
import com.ssafy.bidlive.chat.application.ChatSlowModeChecker;
import com.ssafy.bidlive.chat.application.ChatSlowModeResult;
import com.ssafy.bidlive.chat.infrastructure.ChatAsyncModerationProperties;
import com.ssafy.bidlive.common.security.JwtProperties;
import com.ssafy.bidlive.common.support.redis.RealtimeAuctionRedisKey;
import com.ssafy.bidlive.media.application.ViewerCountQueryService;
import com.ssafy.bidlive.media.application.ViewerSessionTrackingService;
import com.ssafy.bidlive.media.application.port.out.ViewerSessionStore;
import com.ssafy.bidlive.member.domain.MemberRole;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** 세션 TTL·슬로우 모드와 PostgreSQL·Redis 재접속 상태의 정합성을 회귀 검증합니다. */
@SpringBootTest
@AutoConfigureMockMvc
class RealtimeStateConsistencyIntegrationTest
	extends RealtimeIntegrationTestEnvironment {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Autowired
	private ViewerSessionStore viewerSessionStore;

	@Autowired
	private ViewerSessionTrackingService viewerSessionTrackingService;

	@Autowired
	private ViewerCountQueryService viewerCountQueryService;

	@Autowired
	private ChatSlowModeChecker chatSlowModeChecker;

	@Autowired
	private ChatModerationPenaltyPort chatModerationPenaltyPort;

	@Autowired
	private ChatAsyncModerationProperties moderationProperties;

	@Autowired
	private JwtProperties jwtProperties;

	@Autowired
	private MockMvc mockMvc;

	private RealtimeTestFixture fixture;
	private RealtimeTestFixture.AuctionFixture auction;

	@BeforeEach
	void setUp() {
		fixture = new RealtimeTestFixture(jdbcTemplate, redisTemplate);
		fixture.reset();
		auction = fixture.createLiveAuction();
	}

	@Test
	void keepsViewerCountConsistentAcrossTtlAndMultipleSubscriptions() {
		long auctionId = auction.auctionId();
		Instant now = Instant.now();
		viewerSessionStore.record(auctionId, "stale-session", now.minusSeconds(31));
		viewerSessionStore.record(auctionId, "fresh-session", now.minusSeconds(5));

		assertThat(viewerSessionStore.removeExpiredAndCount(
			auctionId,
			now.minusSeconds(30)
		)).isEqualTo(1L);

		viewerSessionTrackingService.subscribe(
			"multi-subscription-session",
			"chat-subscription",
			auctionId
		);
		viewerSessionTrackingService.subscribe(
			"multi-subscription-session",
			"viewer-subscription",
			auctionId
		);
		assertThat(viewerCountQueryService.activeViewerCount(auctionId))
			.isEqualTo(2L);

		viewerSessionTrackingService.unsubscribe(
			"multi-subscription-session",
			"chat-subscription"
		);
		assertThat(viewerCountQueryService.activeViewerCount(auctionId))
			.isEqualTo(2L);

		viewerSessionTrackingService.unsubscribe(
			"multi-subscription-session",
			"viewer-subscription"
		);
		assertThat(viewerCountQueryService.activeViewerCount(auctionId))
			.isEqualTo(1L);
	}

	@Test
	void isolatesSlowModeByAuctionAndMemberWithRedisTtl() {
		Instant requestedAt = Instant.now();

		ChatSlowModeResult first = chatSlowModeChecker.checkAndConsume(
			auction.auctionId(),
			auction.viewerId(),
			requestedAt
		);
		ChatSlowModeResult duplicate = chatSlowModeChecker.checkAndConsume(
			auction.auctionId(),
			auction.viewerId(),
			requestedAt
		);
		ChatSlowModeResult otherMember = chatSlowModeChecker.checkAndConsume(
			auction.auctionId(),
			auction.sellerId(),
			requestedAt
		);

		assertThat(first.allowed()).isTrue();
		assertThat(duplicate.allowed()).isFalse();
		assertThat(duplicate.retryAfterSeconds()).isPositive();
		assertThat(otherMember.allowed()).isTrue();
		assertThat(redisTemplate.keys("bidlive:chat:*:slow-mode:*")).hasSize(2);
	}

	@Test
	void restoresPinnedNoticeAfterRealtimeStateIsRecreatedWithNewVersion()
		throws Exception {
		fixture.insertChatMessages(auction);
		jdbcTemplate.update("""
			UPDATE chat_message
			SET is_pinned = TRUE
			WHERE message_id = ?
			""", RealtimeTestFixture.CHAT_MESSAGE_ID);
		viewerSessionStore.record(
			auction.auctionId(),
			"snapshot-viewer",
			Instant.now()
		);
		fixture.seedRealtimeState(auction, 81L);

		assertSnapshot(81L);

		redisTemplate.delete(
			RealtimeAuctionRedisKey.auctionStateKey(auction.auctionId())
		);
		mockMvc.perform(get(
				"/api/v1/auctions/{auctionId}/realtime-state",
				auction.auctionId()
			)
				.header(HttpHeaders.AUTHORIZATION, bearer(auction.viewerId())))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("AUCTION_STATE_UNAVAILABLE"));

		fixture.seedRealtimeState(auction, 82L);
		assertSnapshot(82L);
	}

	@Test
	void restoresRemainingExtensionsAndMuteExpirationAfterReconnect()
		throws Exception {
		fixture.seedRealtimeState(auction, 91L);
		Instant violationTime = Instant.now();
		for (
			int count = 0;
			count < moderationProperties.getViolationThreshold();
			count++
		) {
			chatModerationPenaltyPort.recordViolation(
				auction.auctionId(),
				auction.viewerId(),
				violationTime
			);
		}

		mockMvc.perform(get(
				"/api/v1/auctions/{auctionId}/realtime-state",
				auction.auctionId()
			)
				.header(HttpHeaders.AUTHORIZATION, bearer(auction.viewerId())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.remainingExtensions").value(2))
			.andExpect(jsonPath("$.amIMuted").value(true))
			.andExpect(jsonPath("$.mutedUntil").isNotEmpty());

		redisTemplate.delete(redisTemplate.keys("bidlive:chat:*:mute:*"));

		mockMvc.perform(get(
				"/api/v1/auctions/{auctionId}/realtime-state",
				auction.auctionId()
			)
				.header(HttpHeaders.AUTHORIZATION, bearer(auction.viewerId())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.remainingExtensions").value(2))
			.andExpect(jsonPath("$.amIMuted").value(false))
			.andExpect(jsonPath("$.mutedUntil").doesNotExist());
	}

	private void assertSnapshot(long expectedStateVersion) throws Exception {
		mockMvc.perform(get(
				"/api/v1/auctions/{auctionId}/realtime-state",
				auction.auctionId()
			)
				.header(HttpHeaders.AUTHORIZATION, bearer(auction.viewerId())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.stateVersion").value(expectedStateVersion))
			.andExpect(jsonPath("$.auctionStatus").value("LIVE"))
			.andExpect(jsonPath("$.remainingExtensions").value(2))
			.andExpect(jsonPath("$.bidCount").value(7L))
			.andExpect(jsonPath("$.bidParticipantCount").value(1L))
			.andExpect(jsonPath("$.viewerCount").value(1L))
			.andExpect(jsonPath("$.amIMuted").value(false))
			.andExpect(jsonPath("$.mutedUntil").doesNotExist())
			.andExpect(jsonPath("$.pinnedNotice.messageId")
				.value(RealtimeTestFixture.CHAT_MESSAGE_ID));
	}

	private String bearer(long memberId) {
		Instant now = Instant.now();
		String accessToken = Jwts.builder()
			.issuer(jwtProperties.issuer())
			.subject(Long.toString(memberId))
			.issuedAt(Date.from(now))
			.expiration(Date.from(now.plus(Duration.ofMinutes(30))))
			.claim("tokenType", "ACCESS")
			.claim("role", MemberRole.USER.name())
			.signWith(jwtProperties.signingKey(), Jwts.SIG.HS256)
			.compact();
		return "Bearer " + accessToken;
	}
}
