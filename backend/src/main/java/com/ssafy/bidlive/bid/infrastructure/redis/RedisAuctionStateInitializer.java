package com.ssafy.bidlive.bid.infrastructure.redis;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.ssafy.bidlive.auction.domain.AuctionStartedEvent;
import com.ssafy.bidlive.common.support.redis.RealtimeAuctionRedisKey;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 방송이 시작되면 실시간 경매 상태 Hash를 만든다 (ROOM-05 처리 흐름의 "Redis 실시간 경매 상태 초기화").
 *
 * <p>**이 Hash가 없으면 시작된 방인데도 입찰이 전부 거절된다.** `bid-process.lua`가 첫 줄에서
 * `EXISTS`로 상태 Hash를 확인하고 없으면 `AUCTION_NOT_FOUND`를 돌려주기 때문이다 — Postgres에는
 * 방이 `LIVE`로 있는데 입찰만 "그런 경매 없음"이라고 답하는 상태가 된다.
 *
 * <p>경매 도메인이 Redis Key를 직접 쓰지 않고 **사실(이벤트)만 발행하고 입찰 도메인이 자기 Key를
 * 만드는** 구조다. 경매 쪽이 입찰 스크립트의 필드 이름·타입을 알게 되면, 스크립트가 바뀔 때 조용히
 * 갈린다. 커밋 이후에만 수신하므로 롤백된 시작은 Redis에도 남지 않는다.
 *
 * <p>채우는 필드는 실시간 입찰과 연장 판정이 읽는 것들이다:
 * `status`·`endAt`·`reservePrice`·`sellerId`와 집계·연장 횟수·버전 초기값.
 * `member-bids` 같은 나머지 Key는 스크립트가 필요할 때 만든다.
 */
@Component
public class RedisAuctionStateInitializer {

	/**
	 * `AUCTION_STARTED` 구독자 중 **가장 먼저** 돈다. 브로드캐스트보다 앞이어야 한다.
	 *
	 * <p><b>⚠️ 애노테이션은 반드시 리스너 <i>메서드</i>에 붙는다.</b> 클래스에 붙이면 조용히
	 * 무시된다 — `ApplicationListenerMethodAdapter`가 `resolveOrder(Method)`로 메서드 애노테이션만
	 * 읽고, 못 찾으면 `Ordered.LOWEST_PRECEDENCE`를 돌려준다. 어댑터가 `Ordered`를 구현해
	 * non-null을 주므로 클래스 애노테이션을 보는 `AnnotationAwareOrderComparator`의 폴백은
	 * 호출조차 되지 않는다. **원래 이 클래스에 붙어 있어 처음부터 효과가 없었다**(S15P11A704-537).
	 *
	 * <p>순서 자체가 필요한 이유: 브로드캐스트가 먼저 나가면 전원이 라이브로 넘어간 뒤 상태 Hash가
	 * 아직 없어 **그 사이 입찰이 전부 `AUCTION_NOT_FOUND`로 거절된다.** 초기화가 먼저면 그 창이
	 * 없다. 순서를 명시하지 않으면 빈 등록 순서에 달려 어디로 떨어질지 코드로 알 수 없다
	 * (MR !204 리뷰).
	 *
	 * <p><b>다만 「앞이 던지면 뒤가 안 돈다」는 아니다.</b> 두 리스너 다 `AFTER_COMMIT`이고,
	 * 그 페이즈는 `TransactionSynchronizationUtils.invokeAfterCompletion`이 동기화마다 Throwable을
	 * 잡아 로그만 남긴다. 초기화가 실패해도 브로드캐스트는 그대로 나가고 호출자는 200을 받는다 —
	 * 순서는 창을 좁힐 뿐 실패를 막지 못한다. 막으려면 페이즈를 바꿔야 하는데, 시작은
	 * `BEFORE_COMMIT`으로 옮길 수 없다: 커밋이 실패하면 시작되지도 않은 방이 Redis에서 `LIVE`가 돼
	 * 입찰과 홀드가 쌓인다({@code RedisAuctionStateCloser} 주석의 비대칭 논증).
	 */
	static final int LISTENER_ORDER = 0;

	private static final String LIVE_STATUS = "LIVE";

	private final StringRedisTemplate redisTemplate;

	public RedisAuctionStateInitializer(StringRedisTemplate redisTemplate) {
		this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
	}

	@Order(LISTENER_ORDER)
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onAuctionStarted(AuctionStartedEvent event) {
		Map<String, String> state = new LinkedHashMap<>();
		state.put("status", LIVE_STATUS);
		// 스크립트가 `tonumber`로 읽고 요청 시각과 비교하므로 epoch millis다
		state.put("endAt", String.valueOf(event.endAt().toEpochMilli()));
		state.put("reservePrice", String.valueOf(event.reservePrice()));
		state.put("sellerId", String.valueOf(event.sellerId()));
		state.put("bidCount", "0");
		state.put("participantCount", "0");
		state.put("extensionCount", "0");
		state.put("version", "0");
		state.put("updatedAt", event.startedAt().toString());

		redisTemplate.opsForHash()
			.putAll(RealtimeAuctionRedisKey.auctionStateKey(event.auctionId()), state);
	}
}
