package com.ssafy.bidlive.auction.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;

import com.ssafy.bidlive.bid.infrastructure.redis.RedisAuctionStateCloser;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * `AUCTION_CANCELED` 리스너 둘의 **페이즈**를 잠근다 (S15P11A704-537).
 *
 * <p>순서를 `@Order`로 잡지 않고 페이즈로 잡는다. 스프링은 `triggerBeforeCommit`을 커밋 전에,
 * `afterCompletion`을 커밋 후에 호출하므로 `BEFORE_COMMIT`은 `AFTER_COMMIT`보다 항상 먼저다.
 * 반대로 `@Order`는 <b>클래스에 붙이면 무시된다</b> — `ApplicationListenerMethodAdapter`가
 * `resolveOrder(Method)`로 메서드만 읽는다. 그래서 상수 비교로는 아무것도 잠기지 않는다.
 *
 * <p>차단이 먼저여야 하는 이유는 두 가지다. 알린 뒤에 닫으면 그 사이 들어온 입찰의 홀드를 아무도
 * 풀지 않고(537), `AFTER_COMMIT`이었다면 Redis 실패가
 * `TransactionSynchronizationUtils.invokeAfterCompletion`에 삼켜져 운영자가 200을 받는다.
 */
class AuctionCanceledListenerPhaseTest {

	@Test
	void closesBidInputBeforeCommitSoFailureRollsBack() {
		assertThat(phaseOf(RedisAuctionStateCloser.class))
			.as("차단이 BEFORE_COMMIT이어야 브로드캐스트보다 먼저 돌고 실패가 롤백된다")
			.isEqualTo(TransactionPhase.BEFORE_COMMIT);
	}

	@Test
	void broadcastsOnlyAfterCommit() {
		assertThat(phaseOf(StompAuctionCanceledEventPublisher.class))
			.as("커밋 전에 쏘면 롤백된 종료를 화면이 확정으로 그린다")
			.isEqualTo(TransactionPhase.AFTER_COMMIT);
	}

	private static TransactionPhase phaseOf(Class<?> listener) {
		Method method = Arrays.stream(listener.getDeclaredMethods())
			.filter(candidate -> AnnotatedElementUtils.findMergedAnnotation(
				candidate, TransactionalEventListener.class
			) != null)
			.findFirst()
			.orElseThrow(() -> new AssertionError(
				listener.getSimpleName() + "에 @TransactionalEventListener 메서드가 없다"
			));
		return AnnotatedElementUtils
			.findMergedAnnotation(method, TransactionalEventListener.class)
			.phase();
	}
}
