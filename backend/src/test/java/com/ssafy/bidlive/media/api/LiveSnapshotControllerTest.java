package com.ssafy.bidlive.media.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.ssafy.bidlive.media.application.LiveSnapshotCommand;
import com.ssafy.bidlive.media.application.LiveSnapshotRejectedException;
import com.ssafy.bidlive.media.application.LiveSnapshotService;
import com.ssafy.bidlive.media.domain.LiveSnapshot;
import com.ssafy.bidlive.media.domain.LiveSnapshotFailureCode;
import com.ssafy.bidlive.media.domain.LiveSnapshotImage;
import com.ssafy.bidlive.media.domain.SnapshotImageType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * FE 214와 맞춘 multipart 파트 이름을 받아 201과 스냅샷 URL을 반환하고, 이미지 형식·크기와
 * 인증 실패를 사유별 상태 코드로 돌려주는지 검증합니다.
 */
class LiveSnapshotControllerTest {

	private static final String PATH = "/api/v1/rooms/room-123/live/snapshot";
	private static final OffsetDateTime CAPTURED_AT =
		OffsetDateTime.parse("2026-07-20T21:10:00+09:00");

	private final LiveSnapshotService liveSnapshotService = mock(LiveSnapshotService.class);

	/**
	 * standaloneSetup은 Spring Boot의 Jackson 자동 설정을 쓰지 않아 시각이 epoch 숫자로
	 * 직렬화된다. WRITE_DATES_AS_TIMESTAMPS 비활성은 Boot의 JacksonAutoConfiguration이 넣는
	 * 기본값이므로, 여기서 같은 값을 명시해 실제 응답과 같은 ISO-8601 형식을 검증한다.
	 */
	private final MockMvc mockMvc = MockMvcBuilders
		.standaloneSetup(new LiveSnapshotController(liveSnapshotService))
		.setControllerAdvice(new LiveSnapshotExceptionHandler())
		.setMessageConverters(new MappingJackson2HttpMessageConverter(
			Jackson2ObjectMapperBuilder.json()
				.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
				.build()))
		.build();

	@Test
	void storesSnapshotFromImageAndAuctionIdParts() throws Exception {
		when(liveSnapshotService.storeSnapshot(any())).thenReturn(new LiveSnapshot(
			"room-123", 102L, "https://s3.test/snapshots/room-room-123/auction-102.jpg", CAPTURED_AT
		));

		mockMvc.perform(multipart(PATH)
				.file(jpegImage())
				.param("auctionId", "102")
				.principal(authenticated("25")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.type").value("LIVE_SNAPSHOT_CAPTURED"))
			.andExpect(jsonPath("$.data.roomId").value("room-123"))
			.andExpect(jsonPath("$.data.auctionId").value(102))
			.andExpect(jsonPath("$.data.snapshotUrl")
				.value("https://s3.test/snapshots/room-room-123/auction-102.jpg"))
			.andExpect(jsonPath("$.data.capturedAt").value("2026-07-20T21:10:00+09:00"));
	}

	/** 이미지와 경매의 연결은 인증 주체와 경로 변수로 서버가 구성해야 한다. */
	@Test
	void buildsCommandFromPathVariableAndAuthenticatedMember() throws Exception {
		when(liveSnapshotService.storeSnapshot(any())).thenReturn(new LiveSnapshot(
			"room-123", 102L, "https://s3.test/snapshot.jpg", CAPTURED_AT
		));

		mockMvc.perform(multipart(PATH)
				.file(jpegImage())
				.param("auctionId", "102")
				.principal(authenticated("25")))
			.andExpect(status().isCreated());

		ArgumentCaptor<LiveSnapshotCommand> command =
			ArgumentCaptor.forClass(LiveSnapshotCommand.class);
		verify(liveSnapshotService).storeSnapshot(command.capture());

		assertThat(command.getValue().roomId()).isEqualTo("room-123");
		assertThat(command.getValue().auctionId()).isEqualTo(102L);
		assertThat(command.getValue().requesterId()).isEqualTo(25L);
		assertThat(command.getValue().image().type()).isEqualTo(SnapshotImageType.JPEG);
		assertThat(command.getValue().image().content()).containsExactly(1, 2, 3);
	}

	@Test
	void rejectsRequestWithoutAuthentication() throws Exception {
		mockMvc.perform(multipart(PATH).file(jpegImage()).param("auctionId", "102"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

		verify(liveSnapshotService, never()).storeSnapshot(any());
	}

	@Test
	void rejectsMissingImagePart() throws Exception {
		mockMvc.perform(multipart(PATH)
				.param("auctionId", "102")
				.principal(authenticated("25")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_SNAPSHOT_REQUEST"));

		verify(liveSnapshotService, never()).storeSnapshot(any());
	}

	@Test
	void rejectsMissingAuctionId() throws Exception {
		mockMvc.perform(multipart(PATH)
				.file(jpegImage())
				.principal(authenticated("25")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_SNAPSHOT_REQUEST"));

		verify(liveSnapshotService, never()).storeSnapshot(any());
	}

	@Test
	void rejectsEmptyImage() throws Exception {
		MockMultipartFile empty =
			new MockMultipartFile("image", "frame.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[0]);

		mockMvc.perform(multipart(PATH)
				.file(empty)
				.param("auctionId", "102")
				.principal(authenticated("25")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("IMAGE_REQUIRED"));

		verify(liveSnapshotService, never()).storeSnapshot(any());
	}

	@Test
	void rejectsUnsupportedImageType() throws Exception {
		MockMultipartFile gif = new MockMultipartFile(
			"image", "frame.gif", MediaType.IMAGE_GIF_VALUE, new byte[] {1, 2, 3});

		mockMvc.perform(multipart(PATH)
				.file(gif)
				.param("auctionId", "102")
				.principal(authenticated("25")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE_TYPE"));

		verify(liveSnapshotService, never()).storeSnapshot(any());
	}

	@Test
	void rejectsImageLargerThanPolicy() throws Exception {
		MockMultipartFile tooLarge = new MockMultipartFile(
			"image",
			"frame.jpg",
			MediaType.IMAGE_JPEG_VALUE,
			new byte[(int) LiveSnapshotImage.MAX_SIZE_BYTES + 1]
		);

		mockMvc.perform(multipart(PATH)
				.file(tooLarge)
				.param("auctionId", "102")
				.principal(authenticated("25")))
			.andExpect(status().isPayloadTooLarge())
			.andExpect(jsonPath("$.code").value("IMAGE_TOO_LARGE"));

		verify(liveSnapshotService, never()).storeSnapshot(any());
	}

	@ParameterizedTest
	@MethodSource("failureCodeStatuses")
	void mapsFailureCodeToSpecifiedHttpStatus(
		LiveSnapshotFailureCode failureCode,
		HttpStatus expectedStatus
	) throws Exception {
		when(liveSnapshotService.storeSnapshot(any()))
			.thenThrow(new LiveSnapshotRejectedException(failureCode));

		mockMvc.perform(multipart(PATH)
				.file(jpegImage())
				.param("auctionId", "102")
				.principal(authenticated("25")))
			.andExpect(status().is(expectedStatus.value()))
			.andExpect(jsonPath("$.code").value(failureCode.name()))
			.andExpect(jsonPath("$.message").isNotEmpty());
	}

	private static Stream<Arguments> failureCodeStatuses() {
		return Stream.of(
			Arguments.of(LiveSnapshotFailureCode.AUCTION_NOT_FOUND, HttpStatus.NOT_FOUND),
			Arguments.of(LiveSnapshotFailureCode.NOT_SNAPSHOT_UPLOADER, HttpStatus.FORBIDDEN),
			Arguments.of(LiveSnapshotFailureCode.AUCTION_NOT_SETTLED, HttpStatus.CONFLICT),
			Arguments.of(LiveSnapshotFailureCode.SNAPSHOT_STORAGE_ERROR, HttpStatus.BAD_GATEWAY)
		);
	}

	private MockMultipartFile jpegImage() {
		return new MockMultipartFile(
			"image", "frame.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[] {1, 2, 3});
	}

	private Authentication authenticated(String name) {
		return UsernamePasswordAuthenticationToken.authenticated(name, null, List.of());
	}
}
