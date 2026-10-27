package com.ssafy.bidlive.common.api;

import java.security.Principal;
import java.util.Map;

import com.ssafy.bidlive.common.api.dto.StompTokenRenewalRequest;
import com.ssafy.bidlive.common.security.SecurityPrincipal;
import com.ssafy.bidlive.common.security.StompTokenRenewalErrorCode;
import com.ssafy.bidlive.common.security.StompTokenRenewalException;
import com.ssafy.bidlive.common.security.StompTokenRenewalResult;
import com.ssafy.bidlive.common.security.StompTokenRenewalService;
import com.ssafy.bidlive.common.websocket.StompErrorContext;
import com.ssafy.bidlive.common.websocket.StompErrorContextResolver;
import com.ssafy.bidlive.common.websocket.StompReceiptPublisher;
import com.ssafy.bidlive.common.websocket.StompSessionTokenExpirationUpdater;
import com.ssafy.bidlive.common.websocket.UserWebSocketErrorPublisher;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Controller;

/** STOMP 재인증 요청을 검증하고 세션 만료 시각 갱신과 개인 오류 발행을 조율합니다. */
@Controller
public class StompTokenRenewalController {

	/** 재인증 경로를 만료 검사에서 제외해야 해서 application destination prefix 없이 공개합니다. */
	public static final String RENEW_MAPPING = "/auth/renew";

	private final StompTokenRenewalService renewalService;
	private final StompSessionTokenExpirationUpdater expirationUpdater;
	private final StompErrorContextResolver errorContextResolver;
	private final UserWebSocketErrorPublisher errorPublisher;
	private final StompReceiptPublisher receiptPublisher;

	public StompTokenRenewalController(
		StompTokenRenewalService renewalService,
		StompSessionTokenExpirationUpdater expirationUpdater,
		StompErrorContextResolver errorContextResolver,
		UserWebSocketErrorPublisher errorPublisher,
		StompReceiptPublisher receiptPublisher
	) {
		this.renewalService = renewalService;
		this.expirationUpdater = expirationUpdater;
		this.errorContextResolver = errorContextResolver;
		this.errorPublisher = errorPublisher;
		this.receiptPublisher = receiptPublisher;
	}

	@MessageMapping(RENEW_MAPPING)
	public void renew(
		@Payload StompTokenRenewalRequest request,
		Message<?> message
	) {
		StompErrorContext errorContext = errorContextResolver.resolve(message);

		try {
			SecurityPrincipal principal = requireSecurityPrincipal(
				errorContext.principal()
			);
			Map<String, Object> sessionAttributes = requireSessionAttributes(message);
			String accessToken = request == null ? null : request.accessToken();
			StompTokenRenewalResult result = renewalService.renew(
				principal,
				accessToken
			);

			expirationUpdater.update(
				sessionAttributes,
				result.tokenExpiresAt()
			);
			publishReceipt(errorContext);
		} catch (StompTokenRenewalException exception) {
			errorPublisher.publish(errorContext, exception.errorCode());
		}
	}

	private void publishReceipt(StompErrorContext errorContext) {
		String receiptId = errorContext.correlationId();
		if (receiptId != null) {
			receiptPublisher.publish(errorContext.sessionId(), receiptId);
		}
	}

	private SecurityPrincipal requireSecurityPrincipal(Principal principal) {
		if (principal instanceof SecurityPrincipal securityPrincipal) {
			return securityPrincipal;
		}

		throw invalidRenewal();
	}

	private Map<String, Object> requireSessionAttributes(Message<?> message) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
			message,
			StompHeaderAccessor.class
		);
		if (accessor == null || accessor.getSessionAttributes() == null) {
			throw invalidRenewal();
		}
		return accessor.getSessionAttributes();
	}

	private StompTokenRenewalException invalidRenewal() {
		return new StompTokenRenewalException(
			StompTokenRenewalErrorCode.AUTH_RENEW_INVALID
		);
	}
}
