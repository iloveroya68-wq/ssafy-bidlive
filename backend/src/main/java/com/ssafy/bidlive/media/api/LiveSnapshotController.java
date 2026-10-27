package com.ssafy.bidlive.media.api;

import java.io.IOException;

import com.ssafy.bidlive.media.api.dto.LiveSnapshotResponse;
import com.ssafy.bidlive.media.application.LiveSnapshotCommand;
import com.ssafy.bidlive.media.application.LiveSnapshotRejectedException;
import com.ssafy.bidlive.media.application.LiveSnapshotService;
import com.ssafy.bidlive.media.domain.LiveSnapshot;
import com.ssafy.bidlive.media.domain.LiveSnapshotFailureCode;
import com.ssafy.bidlive.media.domain.LiveSnapshotImage;
import com.ssafy.bidlive.media.domain.SnapshotImageType;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 프론트가 캡처한 낙찰 스냅샷 이미지를 multipart로 받아 저장 요청으로 넘기는 진입점입니다.
 *
 * <p>파트 이름은 프론트 계약과 맞춘 {@code image}, {@code auctionId}이며, 이미지의 형식과 크기는
 * 도메인 값 객체 생성 시점에 검증한다. 파일명은 저장 key에 쓰지 않으므로 받지 않는다.
 */
@RestController
@RequestMapping("/api/v1/rooms/{roomId}/live")
public class LiveSnapshotController {

	private final LiveSnapshotService liveSnapshotService;

	public LiveSnapshotController(LiveSnapshotService liveSnapshotService) {
		this.liveSnapshotService = liveSnapshotService;
	}

	@PostMapping(path = "/snapshot", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<LiveSnapshotResponse> storeSnapshot(
		@PathVariable String roomId,
		@RequestParam("auctionId") Long auctionId,
		@RequestParam("image") MultipartFile image,
		Authentication authentication
	) {
		LiveSnapshot snapshot = liveSnapshotService.storeSnapshot(new LiveSnapshotCommand(
			roomId,
			auctionId,
			LiveRequestAuthentication.memberId(authentication),
			snapshotImage(image)
		));

		return ResponseEntity.status(HttpStatus.CREATED)
			.body(LiveSnapshotResponse.from(snapshot));
	}

	/** 선언된 Content-Type만으로 형식을 확정하고, 크기 정책은 값 객체가 강제한다. */
	private LiveSnapshotImage snapshotImage(MultipartFile image) {
		if (image == null || image.isEmpty()) {
			throw new LiveSnapshotRejectedException(LiveSnapshotFailureCode.IMAGE_REQUIRED);
		}

		SnapshotImageType type = SnapshotImageType.fromContentType(image.getContentType())
			.orElseThrow(() -> new LiveSnapshotRejectedException(
				LiveSnapshotFailureCode.UNSUPPORTED_IMAGE_TYPE));

		if (image.getSize() > LiveSnapshotImage.MAX_SIZE_BYTES) {
			throw new LiveSnapshotRejectedException(LiveSnapshotFailureCode.IMAGE_TOO_LARGE);
		}

		try {
			return new LiveSnapshotImage(image.getBytes(), type);
		} catch (IOException exception) {
			throw new LiveSnapshotRejectedException(
				LiveSnapshotFailureCode.IMAGE_REQUIRED, exception);
		}
	}
}
