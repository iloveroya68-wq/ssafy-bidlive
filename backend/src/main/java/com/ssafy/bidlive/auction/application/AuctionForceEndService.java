package com.ssafy.bidlive.auction.application;

import static com.ssafy.bidlive.auction.domain.AuctionForceEndErrorCode.AUCTION_NOT_ENDABLE;
import static com.ssafy.bidlive.auction.domain.AuctionForceEndErrorCode.AUCTION_NOT_FOUND;
import static com.ssafy.bidlive.auction.domain.AuctionForceEndErrorCode.INVALID_AUCTION_ID;
import static com.ssafy.bidlive.auction.domain.AuctionForceEndErrorCode.INVALID_REASON;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.ssafy.bidlive.auction.application.AuctionForceEndPersistence.OutstandingBidHold;
import com.ssafy.bidlive.auction.application.AuctionForceEndPersistence.ResultCounts;
import com.ssafy.bidlive.auction.domain.AuctionCanceledEvent;
import com.ssafy.bidlive.auction.domain.AuctionSellerEndErrorCode;
import com.ssafy.bidlive.auction.domain.AuctionStatus;
import com.ssafy.bidlive.auction.domain.AuctionTerminationReason;
import com.ssafy.bidlive.prediction.application.PredictionSettlementCommand;
import com.ssafy.bidlive.prediction.application.PredictionSettlementResult;
import com.ssafy.bidlive.prediction.application.PredictionSettlementService;
import com.ssafy.bidlive.wallet.domain.PointLedgerReferenceType;
import com.ssafy.bidlive.wallet.application.TradePointOperationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DEAL-09 운영 강제 종료.
 *
 * <p>명세(`docs/api/auction/deal-09-...md`)의 처리 규칙을 이 순서로 이행한다.
 * <ol>
 *   <li>`WAITING`·`LIVE`만 대상 — 조건부 UPDATE가 판정과 전이 사이의 경쟁을 막는다</li>
 *   <li>결과 행 생성(`CANCELLED`/`FORCED`)</li>
 *   <li>입찰 보류금 해제 · 예측 포인트 환급</li>
 *   <li>`AUCTION_CANCELED` 발행 — 시청자 브로드캐스트는 커밋 후에만(이벤트 명세 「발생 시점」)</li>
 * </ol>
 *
 * <p><b>입력 차단은 여기서 끝나지 않는다.</b> 입찰·예측 접수는 Postgres를 보지 않고 Redis 상태
 * Hash의 `status`만 본다(`bid-process.lua`·`prediction-participation-process.lua`). 그래서 이
 * 서비스의 `ENDED` 전이만으로는 차단되지 않고, `AUCTION_CANCELED`를 받은
 * `RedisAuctionStateCloser`가 그 Hash를 닫아야 실제로 막힌다 — 그 리스너가 안 돌면 원래 `endAt`까지
 * 입찰이 계속 접수되고 그 홀드는 여기서 이미 다 풀고 지나간 뒤라 아무도 해제하지 않는다
 * (S15P11A704-537). Redis Key는 입찰 도메인 소유라 이 서비스는 사실만 발행한다.
 * <b>채팅에는 아직 그런 게이트가 없다</b> — CHAT-01 전송 핸들러 자체가 구현되지 않았다.
 *
 * <p>그 리스너는 <b>`BEFORE_COMMIT`</b>이라 <b>이 트랜잭션 안에서</b> 돈다. Redis 차단이 실패하면
 * 여기까지 전부 롤백되고 호출자는 500을 받는다 — 「DB만 `ENDED`」로 갈리지 않는다. 반대로
 * `AFTER_COMMIT`이면 예외가 삼켜져 200이 나가고 아무도 모른다(그쪽 주석에 소스 근거).
 *
 * <p><b>멱등성</b>은 「두 번째 호출이 아무것도 안 한다」로 만든다. 상태 전이가 0행이면 곧바로
 * `AUCTION_NOT_ENDABLE`로 끊어서 환급 단계에 도달하지 않는다. 환급 자체도 각각
 * 재실행에 안전하지만(홀드 잔액 0 제외 · `saveIfAbsent`), 앞에서 막는 쪽이 근거가 명확하다.
 */
@Service
public class AuctionForceEndService {

	private static final String RELEASE_IDEMPOTENCY_PREFIX = "force-end:bid:";

	/**
	 * 판매자 종료의 사유 문구. 운영 종료와 달리 사람이 적어 넣는 값이 아니라 고정이다 —
	 * 「왜 끝냈는가」는 판매자의 사정이고 참여자에게 물어 전할 계약이 없다.
	 * 결과 화면의 문구는 `termination_reason=HOST_LEFT`를 보고 서버가 따로 만든다.
	 */
	private static final String HOST_LEFT_REASON = "판매자가 방송을 종료했어요";

	private final AuctionForceEndPersistence persistence;
	private final TradePointOperationService tradePointOperationService;
	private final PredictionSettlementService predictionSettlementService;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	/**
	 * 생성자가 둘이라 스프링이 고를 것을 명시한다. 없으면 컨텍스트가
	 * `NoSuchMethodException: <init>()`로 죽는다 — `BidCommandService`가 같은 이유로 붙여 뒀다.
	 */
	@Autowired
	public AuctionForceEndService(
		AuctionForceEndPersistence persistence,
		TradePointOperationService tradePointOperationService,
		PredictionSettlementService predictionSettlementService,
		ApplicationEventPublisher eventPublisher
	) {
		this(
			persistence,
			tradePointOperationService,
			predictionSettlementService,
			eventPublisher,
			Clock.systemUTC()
		);
	}

	public AuctionForceEndService(
		AuctionForceEndPersistence persistence,
		TradePointOperationService tradePointOperationService,
		PredictionSettlementService predictionSettlementService,
		ApplicationEventPublisher eventPublisher,
		Clock clock
	) {
		this.persistence = persistence;
		this.tradePointOperationService = tradePointOperationService;
		this.predictionSettlementService = predictionSettlementService;
		this.eventPublisher = eventPublisher;
		this.clock = clock;
	}

	@Transactional
	public AuctionForceEndResult forceEnd(long auctionId, String reason) {
		if (auctionId <= 0) {
			throw new AuctionForceEndException(INVALID_AUCTION_ID);
		}
		if (reason == null || reason.isBlank()) {
			throw new AuctionForceEndException(INVALID_REASON);
		}

		AuctionForceEndPersistence.ForceEndTarget target = persistence
			.findTarget(auctionId)
			.orElseThrow(() -> new AuctionForceEndException(AUCTION_NOT_FOUND));
		// 삭제된 방은 존재를 감추지 않는다 — 운영자만 부르는 API라 404로 숨길 이유가 없고,
		// 「종료할 수 없는 상태」라는 사실이 그대로 쓸모 있다.
		if (target.deleted() || !endable(target.status())) {
			throw new AuctionForceEndException(AUCTION_NOT_ENDABLE);
		}

		return endWithoutSale(auctionId, AuctionTerminationReason.FORCED, reason);
	}

	/**
	 * ROOM-09 판매자 종료. 판매자가 진행 콘솔에서 방송을 끝낼 때 경매도 함께 닫는다.
	 *
	 * <p><b>낙찰이 나지 않는다.</b> 마감 시각까지 간 정상 종료가 아니므로 `HOST_LEFT` 유찰로 닫고
	 * 입찰 홀드를 전액 해제한다 — 입찰이 있었더라도 그렇다. 그 짝(`NO_SALE`/`HOST_LEFT`)은
	 * 명세가 이미 정해 둔 것이고(`auction_result.termination_reason`), 결과 조회·유찰 화면이
	 * 「판매자 이탈로 종료됐어요」로 이미 처리하고 있었다. 낙찰까지 내려면 종료가 아니라
	 * <b>마감을 당기는</b> 별개 동작이라 이 API가 할 일이 아니다.
	 *
	 * <p>판정 순서가 계약이다: 인증(401) → 없음·삭제됨(404) → 판매자 아님(403) → 종료 불가(409).
	 * 삭제된 방을 404로 감추는 것이 운영 종료와 다르다 — 이쪽은 아무 회원이나 부를 수 있는
	 * 경로라 남의 방 존재 여부를 알려 줄 이유가 없다.
	 */
	@Transactional
	public AuctionForceEndResult endBySeller(long auctionId, Long memberId) {
		if (auctionId <= 0) {
			throw new AuctionSellerEndException(AuctionSellerEndErrorCode.INVALID_AUCTION_ID);
		}
		if (memberId == null) {
			throw new AuctionSellerEndException(AuctionSellerEndErrorCode.UNAUTHORIZED);
		}

		AuctionForceEndPersistence.ForceEndTarget target = persistence
			.findTarget(auctionId)
			.filter(found -> !found.deleted())
			.orElseThrow(() -> new AuctionSellerEndException(
				AuctionSellerEndErrorCode.AUCTION_NOT_FOUND
			));
		if (!memberId.equals(target.sellerMemberId())) {
			throw new AuctionSellerEndException(
				AuctionSellerEndErrorCode.NOT_AUCTION_SELLER
			);
		}
		if (!endable(target.status())) {
			throw new AuctionSellerEndException(
				AuctionSellerEndErrorCode.AUCTION_NOT_ENDABLE
			);
		}

		return endWithoutSale(
			auctionId,
			AuctionTerminationReason.HOST_LEFT,
			HOST_LEFT_REASON
		);
	}

	/**
	 * 두 종료가 공유하는 마감 절차다. 대상 판정만 진입 메서드가 각자 하고, 여기부터는 같다 —
	 * 상태 전이·결과 행·홀드 해제·예측 환급·이벤트 발행.
	 *
	 * <p>전이가 0행이면 그 사이 다른 요청이 먼저 끝낸 것이라 환급 단계에 도달하지 않는다.
	 * 예외 타입은 진입 경로마다 달라야 해서 호출부가 넘긴 사유로 갈라 던진다.
	 */
	private AuctionForceEndResult endWithoutSale(
		long auctionId,
		AuctionTerminationReason terminationReason,
		String reason
	) {
		Instant endedAt = clock.instant();
		if (persistence.markEnded(auctionId, endedAt) == 0) {
			throw notEndable(terminationReason);
		}

		ResultCounts counts = persistence.countBids(auctionId);
		long auctionResultId = persistence.createResultIfAbsent(
			auctionId,
			counts,
			terminationReason
		);

		ReleaseSummary released = releaseBidHolds(auctionId);
		// 예측 환급은 두 종료가 같다 — 낙찰가가 없으면 적중 판정 자체가 성립하지 않아 전액 환급이다.
		PredictionSettlementResult refunded = predictionSettlementService.settle(
			new PredictionSettlementCommand(
				auctionId,
				auctionResultId,
				null,
				PredictionSettlementCommand.CompletionType.FORCED
			)
		);

		eventPublisher.publishEvent(
			new AuctionCanceledEvent(auctionId, terminationReason, reason, endedAt)
		);
		return new AuctionForceEndResult(
			auctionId,
			endedAt,
			released.count(),
			released.amount(),
			refunded.settledCount()
		);
	}

	private RuntimeException notEndable(AuctionTerminationReason terminationReason) {
		return terminationReason == AuctionTerminationReason.HOST_LEFT
			? new AuctionSellerEndException(AuctionSellerEndErrorCode.AUCTION_NOT_ENDABLE)
			: new AuctionForceEndException(AUCTION_NOT_ENDABLE);
	}

	private ReleaseSummary releaseBidHolds(long auctionId) {
		List<OutstandingBidHold> holds =
			persistence.findOutstandingBidHolds(auctionId);
		long amount = 0L;
		for (OutstandingBidHold hold : holds) {
			tradePointOperationService.release(
				PointLedgerReferenceType.BID,
				hold.bidId(),
				hold.memberId(),
				hold.remainingHold(),
				RELEASE_IDEMPOTENCY_PREFIX + hold.bidId()
			);
			amount = Math.addExact(amount, hold.remainingHold());
		}
		return new ReleaseSummary(holds.size(), amount);
	}

	private boolean endable(String status) {
		return AuctionStatus.WAITING.name().equals(status)
			|| AuctionStatus.LIVE.name().equals(status);
	}

	private record ReleaseSummary(int count, long amount) {
	}
}
