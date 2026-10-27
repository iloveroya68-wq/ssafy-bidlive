package com.ssafy.bidlive.common.websocket.expiry;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import com.ssafy.bidlive.common.config.WebSocketProperties;
import com.ssafy.bidlive.common.security.SecurityPrincipal;
import com.ssafy.bidlive.common.security.StompAuthenticationErrorCode;
import com.ssafy.bidlive.common.security.WebSocketSessionAttributes;
import com.ssafy.bidlive.common.websocket.StompErrorContextResolver;
import com.ssafy.bidlive.common.websocket.UserWebSocketErrorPublisher;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

/**
 * Access Token이 만료된 세션의 SEND·SUBSCRIBE frame을 폐기하고 {@code AUTH_EXPIRED}를 알립니다.
 *
 * <p>인증은 CONNECT에서 한 번만 일어나고 그 뒤의 frame은 아무 검사도 거치지 않았습니다. 그래서
 * 30분짜리 Access Token으로 한 번 연결해 두면 토큰이 만료돼도, 로그아웃해도, 계정이 정지되거나
 * 권한이 강등돼도 그 세션은 입찰과 채팅을 계속 보낼 수 있었습니다. 회원 상태와 역할은 CONNECT
 * 시점의 스냅샷이라, 사용자 화면에서는 이미 로그아웃했는데도 열어둔 탭 하나가 며칠 뒤까지
 * 그 사람 이름으로 방에 글을 쓰는 것으로 보입니다.
 *
 * <p>세션에 저장된 {@code tokenExpiresAt}에 유예 시간을 더한 시각이 지나면 frame을 폐기하고 개인
 * 오류 큐로 {@code AUTH_EXPIRED}를 보냅니다. 연결은 끊지 않습니다. 클라이언트가 새 토큰으로
 * 재인증해 같은 세션을 그대로 복구하는 것이 WS-01이 정한 회복 경로이고, 연결을 끊으면 이미
 * 받아둔 구독과 회복 경로가 함께 사라져 방 화면 전체를 다시 그려야 합니다.
 *
 * <p>재인증 destination만 예외로 통과시킵니다. 재인증도 만료된 세션이 보내는 SEND라서 함께
 * 막으면 클라이언트가 빠져나올 유일한 frame이 빠져나오려는 검사에 막힙니다. 새로고침 말고는
 * 방법이 없는 영구 잠금이 되므로 이 예외는 선택이 아닙니다.
 *
 * <p>{@code tokenExpiresAt}을 읽을 수 없으면 통과시킵니다. 이 속성은 CONNECT 인증이 성공해야만
 * 기록되므로 값이 없는 인증 세션은 정상 경로에서 나올 수 없고, 반대로 만료로 취급하면 같은
 * 속성을 읽는 재인증까지 실패해 역시 회복 불가능한 잠금이 됩니다.
 *
 * <p>Bean 등록은 {@code WebSocketConfig}가 담당합니다. destination prefix와 유예 시간을 가진
 * 설정에서 직접 생성해야 broker 설정과 같은 값을 쓴다는 것이 한곳에서 드러납니다.
 */
public class StompSessionExpiryInterceptor implements ChannelInterceptor {

	private final String renewDestination;
	private final Duration grace;
	private final StompErrorContextResolver errorContextResolver;
	private final UserWebSocketErrorPublisher errorPublisher;
	private final Clock clock;

	public StompSessionExpiryInterceptor(
		WebSocketProperties properties,
		String renewMapping,
		StompErrorContextResolver errorContextResolver,
		UserWebSocketErrorPublisher errorPublisher
	) {
		this(
			properties,
			renewMapping,
			errorContextResolver,
			errorPublisher,
			Clock.systemUTC()
		);
	}

	StompSessionExpiryInterceptor(
		WebSocketProperties properties,
		String renewMapping,
		StompErrorContextResolver errorContextResolver,
		UserWebSocketErrorPublisher errorPublisher,
		Clock clock
	) {
		this.renewDestination = applicationDestination(
			properties.getApplicationDestinationPrefix(),
			renewMapping
		);
		this.grace = properties.getTokenExpiryGrace();
		this.errorContextResolver = errorContextResolver;
		this.errorPublisher = errorPublisher;
		this.clock = clock;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
			message,
			StompHeaderAccessor.class
		);
		if (accessor == null || !isGuarded(accessor.getCommand())) {
			return message;
		}
		if (renewDestination.equals(accessor.getDestination())) {
			return message;
		}
		if (!isExpired(accessor.getSessionAttributes())) {
			return message;
		}

		publishDenialIfPossible(message, accessor);
		return null;
	}

	/**
	 * SEND와 SUBSCRIBE만 검사합니다. UNSUBSCRIBE·DISCONNECT·ACK과 heartbeat은 아무 권한도
	 * 행사하지 않으면서 세션을 정리하는 데 필요하므로, 막으면 만료된 세션이 조용히 끊지도
	 * 못하고 남습니다.
	 */
	private boolean isGuarded(StompCommand command) {
		return command == StompCommand.SEND
			|| command == StompCommand.SUBSCRIBE;
	}

	private boolean isExpired(Map<String, Object> sessionAttributes) {
		if (sessionAttributes == null) {
			return false;
		}

		Object value = sessionAttributes.get(
			WebSocketSessionAttributes.TOKEN_EXPIRES_AT
		);
		if (!(value instanceof Instant expiresAt)) {
			return false;
		}
		return expiresAt.plus(grace).isBefore(clock.instant());
	}

	private void publishDenialIfPossible(
		Message<?> message,
		StompHeaderAccessor accessor
	) {
		if (!(accessor.getUser() instanceof SecurityPrincipal)) {
			return;
		}
		if (!hasText(accessor.getSessionId())
			|| !hasText(accessor.getDestination())) {
			return;
		}

		errorPublisher.publish(
			errorContextResolver.resolve(message),
			StompAuthenticationErrorCode.AUTH_EXPIRED
		);
	}

	private String applicationDestination(String prefix, String mapping) {
		String normalizedPrefix = prefix.endsWith("/")
			? prefix.substring(0, prefix.length() - 1)
			: prefix;
		String normalizedMapping = mapping.startsWith("/")
			? mapping
			: "/" + mapping;
		return normalizedPrefix + normalizedMapping;
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
