package com.ssafy.bidlive.auction.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;

import com.ssafy.bidlive.auction.domain.AuctionCanceledEvent;
import com.ssafy.bidlive.auction.domain.AuctionTerminationReason;
import com.ssafy.bidlive.common.event.WsEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * 낙찰 없는 종료 하나가 **사유에 따라 다른 이벤트 타입**으로 나가는지 검증한다 (S15P11A704-687).
 *
 * <p>여기가 깨지면 화면이 거짓말을 한다. 판매자가 끝낸 방에 `AUCTION_CANCELED`가 가면 프론트는
 * 「운영팀이 이 방송을 종료했어요」를 띄우고, 반대로 운영 종료가 `AUCTION_NO_SALE`로 가면
 * 제재 검토 중인 방에서 재등록 버튼이 열린다. 둘 다 조용히 틀린다 — 이벤트는 흘러가고 로그도 안 남는다.
 */
class StompAuctionCanceledEventPublisherTest {

	private static final long AUCTION_ID = 100L;
	private static final Instant ENDED_AT = Instant.parse("2026-08-03T06:00:00Z");
	private static final String DESTINATION = "/topic/auctions/100";

	@Test
	void sendsAuctionCanceledForOperatorTermination() {
		SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
		StompAuctionCanceledEventPublisher publisher =
			new StompAuctionCanceledEventPublisher(messagingTemplate);

		publisher.onCanceled(new AuctionCanceledEvent(
			AUCTION_ID,
			AuctionTerminationReason.FORCED,
			"POLICY_VIOLATION",
			ENDED_AT
		));

		WsEvent<?> event = captureSent(messagingTemplate);
		assertThat(event.type()).isEqualTo("AUCTION_CANCELED");
		assertThat(event.data())
			.isEqualTo(new StompAuctionCanceledEventPublisher.AuctionCanceledData(
				"POLICY_VIOLATION",
				ENDED_AT,
				"/rooms/100/nosale"
			));
	}

	@Test
	void sendsAuctionNoSaleForSellerTermination() {
		SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
		StompAuctionCanceledEventPublisher publisher =
			new StompAuctionCanceledEventPublisher(messagingTemplate);

		publisher.onCanceled(new AuctionCanceledEvent(
			AUCTION_ID,
			AuctionTerminationReason.HOST_LEFT,
			"판매자가 방송을 종료했어요",
			ENDED_AT
		));

		WsEvent<?> event = captureSent(messagingTemplate);
		assertThat(event.type()).isEqualTo("AUCTION_NO_SALE");
		// 유찰 이벤트 명세의 data는 `reason`·`endedAt` 둘뿐이다 — `resultPath`를 얹지 않는다.
		assertThat(event.data())
			.isEqualTo(new StompAuctionCanceledEventPublisher.AuctionNoSaleData(
				"HOST_LEFT",
				ENDED_AT
			));
	}

	/**
	 * `reason`은 사람이 읽는 문구라 프론트의 분기 근거가 아니다 — 유찰 사유는 `HOST_LEFT`처럼
	 * 코드로 나가야 화면이 문구 변경에 흔들리지 않는다.
	 */
	@Test
	void sellerTerminationCarriesReasonCodeNotProse() {
		SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
		StompAuctionCanceledEventPublisher publisher =
			new StompAuctionCanceledEventPublisher(messagingTemplate);

		publisher.onCanceled(new AuctionCanceledEvent(
			AUCTION_ID,
			AuctionTerminationReason.HOST_LEFT,
			"판매자가 방송을 종료했어요",
			ENDED_AT
		));

		WsEvent<?> event = captureSent(messagingTemplate);
		assertThat(event.data())
			.hasFieldOrPropertyWithValue("reason", "HOST_LEFT");
	}

	private static WsEvent<?> captureSent(SimpMessagingTemplate messagingTemplate) {
		ArgumentCaptor<WsEvent<?>> captor = ArgumentCaptor.forClass(WsEvent.class);
		verify(messagingTemplate).convertAndSend(eq(DESTINATION), captor.capture());
		return captor.getValue();
	}
}
