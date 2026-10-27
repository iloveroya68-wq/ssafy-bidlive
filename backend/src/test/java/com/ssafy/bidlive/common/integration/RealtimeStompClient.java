package com.ssafy.bidlive.common.integration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/** 실제 WebSocket에서 STOMP frame을 송수신하는 통합 테스트 전용 클라이언트입니다. */
final class RealtimeStompClient implements AutoCloseable {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

	private final WebSocket webSocket;
	private final FrameListener listener;

	private RealtimeStompClient(
		WebSocket webSocket,
		FrameListener listener
	) {
		this.webSocket = webSocket;
		this.listener = listener;
	}

	static RealtimeStompClient connect(
		URI uri,
		String origin,
		String accessToken
	) throws Exception {
		FrameListener listener = new FrameListener();
		WebSocket webSocket = HttpClient.newHttpClient()
			.newWebSocketBuilder()
			.header("Origin", origin)
			.subprotocols("v12.stomp")
			.buildAsync(uri, listener)
			.get(DEFAULT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
		RealtimeStompClient client = new RealtimeStompClient(webSocket, listener);
		client.sendFrame("""
			CONNECT
			accept-version:1.2
			heart-beat:0,0
			Authorization:Bearer %s

			""".formatted(accessToken));
		client.await("CONNECTED", frame -> true, DEFAULT_TIMEOUT);
		return client;
	}

	void subscribe(String id, String destination) {
		sendFrame("""
			SUBSCRIBE
			id:%s
			destination:%s
			ack:auto

			""".formatted(id, destination));
	}

	void sendJson(String destination, String body) {
		sendFrame("""
			SEND
			destination:%s
			content-type:application/json

			%s""".formatted(destination, body));
	}

	String awaitMessageContaining(String expectedText) throws Exception {
		return await(
			"MESSAGE",
			frame -> frame.contains(expectedText),
			DEFAULT_TIMEOUT
		);
	}

	boolean hasMessageContaining(String expectedText, Duration timeout)
		throws Exception {
		try {
			await("MESSAGE", frame -> frame.contains(expectedText), timeout);
			return true;
		} catch (IllegalStateException exception) {
			return false;
		}
	}

	static String bodyOf(String frame) {
		int delimiter = frame.indexOf("\n\n");
		if (delimiter < 0) {
			throw new IllegalArgumentException("STOMP frame body delimiter is missing");
		}
		return frame.substring(delimiter + 2);
	}

	private String await(
		String command,
		Predicate<String> predicate,
		Duration timeout
	) throws Exception {
		long deadline = System.nanoTime() + timeout.toNanos();
		while (System.nanoTime() < deadline) {
			long remaining = deadline - System.nanoTime();
			String frame = listener.poll(remaining, TimeUnit.NANOSECONDS);
			if (frame == null) {
				break;
			}
			String normalized = frame.stripLeading();
			if (normalized.startsWith(command + "\n")
				&& predicate.test(normalized)) {
				return normalized;
			}
		}
		throw new IllegalStateException(
			"STOMP " + command + " frame was not received within " + timeout
		);
	}

	private void sendFrame(String frame) {
		webSocket.sendText(frame + '\0', true).join();
	}

	@Override
	public void close() {
		try {
			sendFrame("DISCONNECT\n\n");
		} finally {
			webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "test-complete").join();
		}
	}

	/** WebSocket 조각을 NUL 기준의 완성된 STOMP frame으로 조립합니다. */
	private static final class FrameListener implements WebSocket.Listener {

		private final StringBuilder buffer = new StringBuilder();
		private final LinkedBlockingQueue<String> frames = new LinkedBlockingQueue<>();

		@Override
		public void onOpen(WebSocket webSocket) {
			webSocket.request(1);
		}

		@Override
		public CompletionStage<?> onText(
			WebSocket webSocket,
			CharSequence data,
			boolean last
		) {
			buffer.append(data);
			int delimiter;
			while ((delimiter = buffer.indexOf("\0")) >= 0) {
				String frame = buffer.substring(0, delimiter);
				buffer.delete(0, delimiter + 1);
				if (!frame.isBlank()) {
					frames.offer(frame);
				}
			}
			webSocket.request(1);
			return null;
		}

		@Override
		public CompletionStage<?> onBinary(
			WebSocket webSocket,
			ByteBuffer data,
			boolean last
		) {
			webSocket.request(1);
			return null;
		}

		@Override
		public void onError(WebSocket webSocket, Throwable error) {
			frames.offer("ERROR\n\n" + Objects.toString(error.getMessage(), "WebSocket error"));
		}

		String poll(long timeout, TimeUnit timeUnit) throws InterruptedException {
			return frames.poll(timeout, timeUnit);
		}
	}
}
