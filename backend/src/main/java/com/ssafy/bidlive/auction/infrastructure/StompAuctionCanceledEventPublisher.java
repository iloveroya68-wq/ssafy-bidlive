package com.ssafy.bidlive.auction.infrastructure;

import java.time.Instant;

import com.ssafy.bidlive.auction.domain.AuctionCanceledEvent;
import com.ssafy.bidlive.auction.domain.AuctionTerminationReason;
import com.ssafy.bidlive.common.event.WsEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 커밋된 강제 종료만 경매방 전체에 `AUCTION_CANCELED`로 알린다.
 *
 * <p>이벤트 명세의 「발생 시점」이 <b>상태 반영과 포인트 환급 완료 후</b>라 `AFTER_COMMIT`이다.
 * 커밋 전에 쏘면 롤백된 종료를 화면이 확정으로 그린다.
 *
 * <p>실시간 입력 차단(`RedisAuctionStateCloser`)은 `BEFORE_COMMIT`이라 <b>항상 이보다 먼저</b>
 * 돈다 — 순서는 페이즈가 보장하므로 `@Order`를 두지 않는다(클래스에 붙인 `@Order`는
 * `ApplicationListenerMethodAdapter.resolveOrder(Method)`가 메서드만 읽어 무시된다). 차단이
 * 먼저여야 하는 이유는 그쪽 주석에 있다: 알린 뒤에 닫으면 그 사이 들어온 입찰의 홀드를 아무도
 * 풀지 않는다(S15P11A704-537).
 */
@Component
public class StompAuctionCanceledEventPublisher {

	static final String DESTINATION_PATTERN = "/topic/auctions/%d";
	static final String EVENT_TYPE = "AUCTION_CANCELED";
	static final String NO_SALE_EVENT_TYPE = "AUCTION_NO_SALE";
	static final String RESULT_PATH_PATTERN = "/rooms/%d/nosale";

	private final SimpMessagingTemplate messagingTemplate;

	public StompAuctionCanceledEventPublisher(
		SimpMessagingTemplate messagingTemplate
	) {
		this.messagingTemplate = messagingTemplate;
	}

	/**
	 * 사유에 따라 <b>다른 이벤트 타입</b>으로 나간다 (S15P11A704-687).
	 *
	 * <p>내부적으로는 같은 사실(낙찰 없이 닫혔다)이지만 참여자에게는 다른 일이다 —
	 * `AUCTION_CANCELED`는 프론트에서 「운영팀이 이 방송을 종료했어요」로 그려지므로, 판매자가
	 * 끝낸 방에 그걸 띄우면 화면이 거짓말을 한다. 판매자 종료는 `AUCTION_NO_SALE`로 나간다 —
	 * 그 이벤트의 명세 「발생 시점」이 <b>"유효 입찰이 없거나 낙찰 없이 경매가 종료되었을 때"</b>라
	 * 이 경우를 이미 포함하고, `reason` 필드가 `HOST_LEFT`를 그대로 실어 나른다.
	 *
	 * <p>두 이벤트의 data가 다르다 — `AUCTION_CANCELED`만 `resultPath`를 준다. 유찰 이벤트 명세의
	 * data는 `reason`·`endedAt` 둘뿐이라 거기에 없는 필드를 얹지 않는다.
	 */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onCanceled(AuctionCanceledEvent event) {
		messagingTemplate.convertAndSend(
			DESTINATION_PATTERN.formatted(event.auctionId()),
			event.terminationReason() == AuctionTerminationReason.HOST_LEFT
				? new WsEvent<>(
					NO_SALE_EVENT_TYPE,
					new AuctionNoSaleData(
						event.terminationReason().terminationReason(),
						event.endedAt()
					)
				)
				: new WsEvent<>(
					EVENT_TYPE,
					new AuctionCanceledData(
						event.reason(),
						event.endedAt(),
						RESULT_PATH_PATTERN.formatted(event.auctionId())
					)
				)
		);
	}

	/**
	 * 이벤트 명세의 data 3필드.
	 *
	 * <p>`resultPath`가 낙찰 결과(`/rooms/{id}/result`)가 아니라 유찰 안내(`/nosale`)인 이유:
	 * 강제 종료는 낙찰자가 없어 역할 분기가 성립하지 않는다. 프론트도 두 화면을 별도 라우트로
	 * 나눠 두었고 서로의 결과를 받으면 상대 라우트로 replace한다.
	 */
	record AuctionCanceledData(
		String reason,
		Instant endedAt,
		String resultPath
	) {
	}

	/**
	 * `AUCTION_NO_SALE` 명세의 data 2필드. 정산 엔진이 마감 유찰에 쓰는 것과 같은 모양이라
	 * 프론트는 두 경로를 구분하지 않고 `reason`으로만 사유를 가른다.
	 */
	record AuctionNoSaleData(
		String reason,
		Instant endedAt
	) {
	}
}
