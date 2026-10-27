package com.ssafy.bidlive.bid.infrastructure.redis;

import java.util.Objects;

import com.ssafy.bidlive.auction.domain.AuctionCanceledEvent;
import com.ssafy.bidlive.common.support.redis.RealtimeAuctionRedisKey;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 강제 종료(DEAL-09)가 확정되면 실시간 경매 상태 Hash의 `status`를 닫아 입찰·예측 접수를 막는다.
 *
 * <p>**이걸 안 하면 강제 종료해도 입찰이 계속 받아진다.** `bid-process.lua`와
 * `prediction-participation-process.lua`는 Postgres를 보지 않고 이 Hash의 `status`가 `LIVE`인지만
 * 본다. 그래서 방이 `ENDED`로 커밋돼도 Redis가 `LIVE`면 원래 `endAt`까지 접수가 계속되고,
 * 그 입찰이 새로 잡은 홀드는 **이미 홀드를 다 풀고 나간** 강제 종료가 풀어 줄 수 없다 —
 * 회원 잔액이 그대로 묶인다(S15P11A704-537).
 *
 * <p>{@link RedisAuctionStateInitializer}와 대칭이다. 경매 도메인은 사실(이벤트)만 발행하고
 * 자기 Key는 입찰 도메인이 건드린다 — 경매 쪽이 스크립트의 필드 이름·값을 알게 되면 스크립트가
 * 바뀔 때 조용히 갈린다.
 *
 * <p><b>`status`만 바꾸고 `endAt`은 그대로 둔다.</b> 두 스크립트가 `status`를 `endAt`보다 먼저
 * 보므로 `endAt`을 당겨도 도달하지 않는 죽은 수정이고, `endAt`은 실시간 화면·집계가 읽는
 * <b>예정</b> 마감이라 지우면 종료 이후 화면이 원래 마감 시각을 잃는다.
 *
 * <p><b>`SETTLING`이 아니라 `ENDED`다.</b> BID-07 비크리 엔진(`docs/api/auction/bid-07-...md`)이
 * `SETTLING`을 「정산을 선점했다」는 뜻으로 `LIVE`에서만 원자 전환해 쓰기로 돼 있다. 강제 종료는
 * 정산이 아니라 취소이고 환급까지 이미 끝냈으므로, 같은 명세가 「이미 `SETTLING` 또는 `ENDED`면
 * 중복 차감하지 않는다」로 정한 종착 값을 쓴다. 유찰·강제 종료 구분은 방 상태가 아니라
 * `auction_result.resultType`이 맡는다(같은 명세).
 *
 * <h2>왜 {@code BEFORE_COMMIT}인가 — 초기화와 페이즈가 다른 이유</h2>
 *
 * <p>스프링 6.2.19 소스로 확인한 두 가지 때문이다.
 * <ol>
 *   <li><b>{@code AFTER_COMMIT}의 예외는 삼켜진다.</b>
 *       `TransactionalApplicationListenerSynchronization`은 `AFTER_COMMIT`을 `afterCommit()`이
 *       아니라 `afterCompletion(status)`에서 처리하는데,
 *       `TransactionSynchronizationUtils.invokeAfterCompletion`이 동기화를 <b>하나씩 try/catch로
 *       감싸 Throwable을 잡고 로그만 남긴다.</b> 그 페이즈였다면 Redis가 끊긴 채 종료해도 운영자는
 *       200을 받고, 브로드캐스트도 예외 격리로 그대로 나가고, Redis만 `LIVE`로 남아 537이
 *       <b>조용히</b> 재현된다 — 운영자가 복구를 시작할 계기가 없다</li>
 *   <li><b>{@code BEFORE_COMMIT}의 예외는 롤백시키고 호출자에게 전파된다.</b>
 *       `TransactionSynchronizationUtils.triggerBeforeCommit`은 감싸지 않고,
 *       `AbstractPlatformTransactionManager.processCommit`의 `catch (RuntimeException | Error)`가
 *       `doRollbackOnCommitException` 후 다시 던진다. 그래서 운영자에게 500이 가고 상태 전이·홀드
 *       해제·예측 환급이 <b>전부 함께 되돌아간다</b>(둘 다 `REQUIRED`라 같은 트랜잭션이다)</li>
 * </ol>
 *
 * <p>롤백 후 남는 상태는 「DB는 아직 `LIVE`인데 Redis만 `ENDED`」다. <b>지금은</b> 안전한 방향이다 —
 * 입찰이 거절될 뿐 새는 홀드가 없고, 방이 아직 `WAITING`·`LIVE`라 <b>force-end를 그대로 다시
 * 부르면 복구된다</b>(409가 아니다). 반대 방향(DB만 `ENDED`)이 바로 537이다.
 *
 * <p><b>⚠️ 그 「안전하다」에는 유효기간이 있다 — BID-07 비크리 엔진이 붙으면 뒤집힌다.</b>
 * 그 명세의 「중복 정산 방지」가 `Redis 상태 LIVE → Lua로 SETTLING 선점`이고
 * 「이미 `SETTLING` 또는 `ENDED` 상태라면 중복 차감하지 않는다」이다. 롤백으로 Redis만 `ENDED`가
 * 된 방은 마감 시각이 와도 <b>`LIVE → SETTLING` 선점에 실패해 정산이 통째로 건너뛰어진다.</b>
 * 롤백이 DB 홀드 해제까지 되돌린 뒤라 결과는 <b>홀드가 묶인 채 아무도 풀지 않는 상태</b> —
 * 537이 고치려던 그 증상이 롤백 경로로 재현된다.
 *
 * <p>그래서 <b>정산 엔진이 붙은 뒤에는 이 잔여 상태를 방치하면 안 된다.</b> force-end 재호출로
 * 반드시 정리해야 하는데, 그건 <b>운영자가 500을 보고 다시 누를 때만</b> 일어난다 — 안 누르면
 * 조용히 남는다. 엔진을 붙이는 사람은 이 조합을 감지하거나(예: DB `LIVE`인데 Redis가 `LIVE`가
 * 아닌 방) 잔여 상태 자체가 생기지 않게 하는 쪽을 함께 정해야 한다(MR !266 리뷰).
 *
 * <p><b>{@link RedisAuctionStateInitializer}가 `AFTER_COMMIT`인 것과 비대칭인데, 근거가 있다.</b>
 * 시작은 「안 열리는 쪽」이, 종료는 「닫히는 쪽」이 안전하다. 시작을 `BEFORE_COMMIT`으로 하면
 * 커밋이 실패했을 때 Redis만 `LIVE`로 남아 <b>시작되지도 않은 방에 입찰과 홀드가 쌓인다.</b>
 * 페이즈를 이렇게 나눠 두면 두 리스너 모두 「실패하면 입력이 닫힌 쪽으로 기운다」가 된다.
 *
 * <p><b>{@code @Order}는 쓰지 않는다.</b> 같은 이벤트를 듣는
 * `StompAuctionCanceledEventPublisher`(`AFTER_COMMIT`)와는 <b>페이즈가 순서를 보장한다</b> —
 * `processCommit`이 `triggerBeforeCommit`을 커밋 전에, `afterCompletion`을 커밋 후에 호출한다.
 * 애초에 클래스에 붙인 `@Order`로는 순서를 잡을 수 없었다: `ApplicationListenerMethodAdapter`가
 * `resolveOrder(Method)`로 <b>메서드 애노테이션만</b> 읽는다.
 */
@Component
public class RedisAuctionStateCloser {

	private static final String STATUS_FIELD = "status";
	private static final String ENDED_STATUS = "ENDED";

	private final StringRedisTemplate redisTemplate;

	public RedisAuctionStateCloser(StringRedisTemplate redisTemplate) {
		this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
	}

	@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
	public void onAuctionCanceled(AuctionCanceledEvent event) {
		// 시작 전(`WAITING`) 방이면 Hash가 아직 없어 `status` 하나짜리 Hash가 생긴다. 그래도
		// 두 스크립트는 `AUCTION_NOT_LIVE`로 막으므로 결과가 같고, 없는 것을 확인하는 왕복을
		// 더할 값이 없다.
		redisTemplate.opsForHash().put(
			RealtimeAuctionRedisKey.auctionStateKey(event.auctionId()),
			STATUS_FIELD,
			ENDED_STATUS
		);
	}
}
