package com.ssafy.bidlive.auction.api;

import java.io.IOException;
import java.time.Instant;

import com.ssafy.bidlive.auction.application.CatchUpAudioChunkService;
import com.ssafy.bidlive.auction.application.CatchUpSummaryException;
import com.ssafy.bidlive.auction.domain.CatchUpSummaryErrorCode;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 판매자 브라우저가 만든 완결된 짧은 음성 파일을 비동기 STT 작업으로 접수한다. */
@RestController
@RequestMapping("/api/v1/auction-rooms")
public class CatchUpAudioChunkController {

	private final CatchUpAudioChunkService service;

	public CatchUpAudioChunkController(CatchUpAudioChunkService service) {
		this.service = service;
	}

	@PostMapping(
		value = "/{auctionId}/stt/chunks",
		consumes = MediaType.MULTIPART_FORM_DATA_VALUE
	)
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void upload(
		@PathVariable long auctionId,
		@RequestParam long sequence,
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startedAt,
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endedAt,
		@RequestPart("audio") MultipartFile audio,
		Authentication authentication
	) {
		try {
			service.submit(
				auctionId,
				CatchUpSummaryController.memberId(authentication),
				sequence,
				startedAt,
				endedAt,
				audio.getContentType(),
				audio.getBytes()
			);
		} catch (IOException exception) {
			throw new CatchUpSummaryException(
				CatchUpSummaryErrorCode.INVALID_AUDIO_CHUNK,
				exception
			);
		}
	}
}
