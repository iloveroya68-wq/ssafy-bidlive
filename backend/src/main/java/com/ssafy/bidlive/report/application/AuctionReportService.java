package com.ssafy.bidlive.report.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;

import com.ssafy.bidlive.report.domain.AuctionReportErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대기방·라이브 경매 신고를 접수한다(DEAL-04).
 *
 * <p>접수만 한다 — 거래 상태 변경·정산 보류·강제 종료는 하지 않는다. 명세가 그 셋을 운영자 검토 후
 * DEAL-09로 넘겼다.
 *
 * <p>판정 순서가 계약이다: 없음·삭제됨(404) → 본인 경매(403) → 신고 불가 상태(409) → 중복(409).
 * 본인 여부를 상태보다 먼저 보는 이유는, 자기 방을 신고하려는 요청에 "지금은 신고할 수 없는
 * 상태"라고 답하면 다시 시도할 수 있다고 읽히기 때문이다.
 */
@Service
public class AuctionReportService {

	/** 접수 시각도 다른 도메인과 같은 서비스 기준 오프셋을 쓴다. */
	private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

	/** 신고할 수 있는 경매 상태. 종료된 방은 신고 대상이 아니다(거래 분쟁은 다른 창구다). */
	private static final Set<String> REPORTABLE_STATUSES = Set.of("WAITING", "LIVE");

	/**
	 * 공개된 방만 신고 대상이다. `auction_room.visibility`를 문자열로 비교하는 이유는 status와 같다 —
	 * 값 하나 때문에 500을 내지 않는다. 알 수 없는 값은 비공개로 보수적으로 취급한다.
	 */
	private static final String PUBLIC_VISIBILITY = "PUBLIC";

	private final AuctionReportPersistence reportPersistence;
	private final Clock clock;

	@Autowired
	public AuctionReportService(AuctionReportPersistence reportPersistence) {
		this(reportPersistence, Clock.system(SERVICE_ZONE));
	}

	/** 접수 시각을 고정해야 하는 테스트에서만 사용한다. */
	AuctionReportService(AuctionReportPersistence reportPersistence, Clock clock) {
		this.reportPersistence = reportPersistence;
		this.clock = clock;
	}

	@Transactional
	public AuctionReportResult report(long auctionId, Long reporterId, AuctionReportCommand command) {
		if (reporterId == null) {
			throw new AuctionReportException(AuctionReportErrorCode.UNAUTHORIZED);
		}
		if (command == null) {
			throw new AuctionReportException(AuctionReportErrorCode.INVALID_REPORT_REQUEST);
		}

		AuctionReportTarget target = reportPersistence.findReportTarget(auctionId)
			.filter(found -> !found.deleted())
			.orElseThrow(() -> new AuctionReportException(
				AuctionReportErrorCode.AUCTION_NOT_FOUND
			));

		// 비공개 방은 판매자 외에 존재 자체를 숨긴다 — 상세(ROOM-03)·입장(ROOM-07)·수정·삭제·시작과
		// 같은 규칙이다. 여기서 201을 주면 그 응답만으로 "그 id의 방이 있고 진행 중이다"가 드러난다.
		// 신고자는 정의상 판매자가 아니고, 입장 자체가 404로 막히므로 볼 수 없는 방을 신고할 일도 없다.
		if (!reporterId.equals(target.sellerId())
			&& !PUBLIC_VISIBILITY.equals(target.visibility())) {
			throw new AuctionReportException(AuctionReportErrorCode.AUCTION_NOT_FOUND);
		}
		if (reporterId.equals(target.sellerId())) {
			throw new AuctionReportException(
				AuctionReportErrorCode.OWN_AUCTION_REPORT_NOT_ALLOWED
			);
		}
		if (!REPORTABLE_STATUSES.contains(target.status())) {
			throw new AuctionReportException(AuctionReportErrorCode.AUCTION_NOT_REPORTABLE);
		}
		// 명세는 중복 정책을 "적용한다"까지만 정했다 — 같은 회원이 같은 경매에 두 번 접수하는 것을
		// 막는 쪽으로 뒀다. 사유가 달라도 운영자가 보는 건 같은 방이라 행을 늘릴 이유가 없다.
		if (reportPersistence.existsReport(auctionId, reporterId)) {
			throw new AuctionReportException(AuctionReportErrorCode.DUPLICATE_AUCTION_REPORT);
		}

		Instant receivedAt = clock.instant();
		// 위 EXISTS는 빠른 경로일 뿐이다. 그 검사와 INSERT 사이에 끼어든 동시 요청은 유니크 인덱스가
		// 잡고, 그때 저장은 빈 값을 돌려준다(V16). 보장은 DB가 진다.
		return reportPersistence.save(auctionId, reporterId, command, receivedAt)
			.orElseThrow(() -> new AuctionReportException(
				AuctionReportErrorCode.DUPLICATE_AUCTION_REPORT
			));
	}
}
