package com.ssafy.bidlive.auction.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;

import com.ssafy.bidlive.bid.infrastructure.redis.RedisAuctionStateInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * `AUCTION_STARTED` 커밋 후 리스너 둘의 실행 순서를 잠근다 (MR !204 리뷰).
 *
 * <p>브로드캐스트가 먼저 나가면 전원이 라이브로 넘어간 뒤 상태 Hash가 아직 없어 그 사이 입찰이
 * `AUCTION_NOT_FOUND`로 거절된다. 순서를 명시하지 않으면 빈 등록 순서에 달려 결정적이지 않다.
 *
 * <p><b>애노테이션이 붙은 자리까지 검증한다</b>(S15P11A704-537). 스프링이 이벤트 리스너의 순서를
 * 읽는 곳은 `ApplicationListenerMethodAdapter.resolveOrder(Method)` 하나뿐이라 **클래스에 붙인
 * `@Order`는 조용히 무시된다** — 어댑터가 `Ordered`로 `LOWEST_PRECEDENCE`를 돌려주므로
 * `AnnotationAwareOrderComparator`의 클래스 애노테이션 폴백도 타지 않는다. 원래 이 테스트는
 * 클래스 애노테이션을 읽어 「순서를 잠갔다」고 말하면서 실제로는 아무것도 잠그지 않았다.
 */
class AuctionStartedListenerOrderTest {

	@Test
	void initializesBidStateBeforeBroadcastingToSubscribers() {
		assertThat(orderOf(RedisAuctionStateInitializer.class))
			.as("Redis 상태 초기화가 브로드캐스트보다 먼저 돌아야 한다")
			.isLessThan(orderOf(StompAuctionStartedEventPublisher.class));
	}

	@Test
	void declaresOrderOnTheListenerMethodNotTheClass() {
		for (Class<?> listener : new Class<?>[] {
			RedisAuctionStateInitializer.class, StompAuctionStartedEventPublisher.class
		}) {
			assertThat(AnnotatedElementUtils.findMergedAnnotation(
				listenerMethod(listener), Order.class
			))
				.as(listener.getSimpleName() + ": 스프링은 메서드의 @Order만 읽는다")
				.isNotNull();
		}
	}

	private static int orderOf(Class<?> listener) {
		Order order = AnnotatedElementUtils.findMergedAnnotation(
			listenerMethod(listener), Order.class
		);
		assertThat(order).as(listener.getSimpleName() + " 리스너 메서드에 @Order가 없다").isNotNull();
		return order.value();
	}

	private static Method listenerMethod(Class<?> listener) {
		return Arrays.stream(listener.getDeclaredMethods())
			.filter(candidate -> AnnotatedElementUtils.findMergedAnnotation(
				candidate, TransactionalEventListener.class
			) != null)
			.findFirst()
			.orElseThrow(() -> new AssertionError(
				listener.getSimpleName() + "에 @TransactionalEventListener 메서드가 없다"
			));
	}
}
