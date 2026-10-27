package com.ssafy.bidlive.common.websocket.send;

import com.ssafy.bidlive.common.config.WebSocketProperties;
import com.ssafy.bidlive.common.security.SecurityPrincipal;
import com.ssafy.bidlive.common.websocket.StompErrorContextResolver;
import com.ssafy.bidlive.common.websocket.UserWebSocketErrorPublisher;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

/**
 * 애플리케이션 destination 밖으로 향하는 STOMP SEND frame을 폐기합니다.
 *
 * <p>simple broker는 클라이언트가 보낸 SEND라도 broker destination이면 구독자에게 그대로
 * 중계합니다. 막지 않으면 아무 로그인 사용자나 {@code /topic/auctions/{id}/chat}이나
 * {@code /user/{다른 회원}/queue/moderation}으로 직접 SEND해서 서버가 발행한 이벤트와
 * 구분되지 않는 낙찰·검열 통지를 위조할 수 있고, 채팅도 뮤트·슬로우 모드·금칙어·비공개방
 * 판정과 저장을 담당하는 {@code ChatSendService}를 거치지 않고 방 전체에 뿌려집니다.
 *
 * <p>Bean 등록은 {@code WebSocketConfig}가 담당합니다. destination prefix를 소유한 설정에서
 * 직접 생성해야 broker 설정과 같은 값을 쓴다는 것이 한곳에서 드러납니다.
 */
public class StompSendDestinationInterceptor implements ChannelInterceptor {

	private final String applicationDestinationPrefix;
	private final StompErrorContextResolver errorContextResolver;
	private final UserWebSocketErrorPublisher errorPublisher;

	public StompSendDestinationInterceptor(
		WebSocketProperties properties,
		StompErrorContextResolver errorContextResolver,
		UserWebSocketErrorPublisher errorPublisher
	) {
		this.applicationDestinationPrefix = withTrailingSlash(
			properties.getApplicationDestinationPrefix()
		);
		this.errorContextResolver = errorContextResolver;
		this.errorPublisher = errorPublisher;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
			message,
			StompHeaderAccessor.class
		);
		if (accessor == null || accessor.getCommand() != StompCommand.SEND) {
			return message;
		}

		String destination = accessor.getDestination();
		if (destination != null
			&& destination.startsWith(applicationDestinationPrefix)) {
			return message;
		}

		publishDenialIfPossible(message, accessor);
		return null;
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
			StompSendErrorCode.SEND_DENIED
		);
	}

	private String withTrailingSlash(String prefix) {
		return prefix.endsWith("/") ? prefix : prefix + "/";
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
