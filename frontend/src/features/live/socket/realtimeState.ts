// WS-03 경매 상태 스냅샷 — GET /api/v1/auctions/{auctionId}/realtime-state
// 명세: 「최종(종합) 명세서」 > API 명세 테이블 > 경매 상태 스냅샷
//
// 스냅샷에서 복원하는 값(applySnapshot, handlers.ts):
//   - `myParticipationType` → 입찰↔예측 배타 잠금 복원 (S15P11A704-62, docs/03 §4-1)
//   - `myBidAmount`         → 내 입찰가 복원 (본인 값만, 미입찰이면 null)
//   - `myPredictedPrice` / `myStakeAmount` → 내 예측 금액·스테이크 복원 (S15P11A704-388)
//   - `bidCount` / `bidParticipantCount` → 비크리에서 공개 가능한 집계값
//   - `pinnedNotice`·`amIMuted`·`endAt`·`stateVersion` 등 나머지 라이브 상태
//
// 배치: 스냅샷은 소켓 재접속 절차(SUBSCRIBE receipt → 스냅샷 → 버퍼 재생, docs/03 §6-5)의 일부다.
// 실소켓(207)이 붙으며 원래 예고대로 features/bid/ → 여기(features/live/socket/)로 이관했다(S15P11A704-228).
// stateVersion 기반 버퍼 재생은 snapshotReplay.ts + liveSocket.ts가 담당한다.

import { http } from '@/shared/api/http'
import type { ParticipationType } from '@/shared/lib/participation'

/** 고정 공지 (CHAT — 표시는 FE2 ChatPanel 소관, 여기서는 통과만 시킨다) */
export interface PinnedNotice {
  messageId: string
  content: string
}

/** 명세 「경매 상태 스냅샷」의 결과 예시와 필드가 1:1 대응한다 */
export interface RealtimeState {
  /** 경매별 단조 증가 버전 — 이벤트 역전·누락 판정용 */
  stateVersion: number
  /** 경매 상태 문자열. 값 목록이 명세에 없다(예시는 "OPEN") */
  auctionStatus: string
  /** 마감 시각 (ISO 8601 문자열) */
  endAt: string
  remainingSeconds: number
  extendCount: number
  /** 남은 연장 횟수 — **서버 권위 값**(BE !321에서 응답에 추가 확정). FE 상한 상수로 유도하지 않는다 */
  remainingExtensions: number
  /** 누적 입찰 건수 */
  bidCount: number
  /** 입찰한 고유 회원 수 (상향 입찰 중복 제거) */
  bidParticipantCount: number
  viewerCount: number
  /** **본인** 최종 입찰가. 미입찰이면 null (비크리 — 타인 값은 오지 않는다) */
  myBidAmount: number | null
  /**
   * **본인** 예측 금액. 예측 미참여면 null.
   * 재접속 시 "얼마에 걸었는지"를 복원하는 값이다 — 없으면 잠금만 복원되고 금액이 빈칸으로 남는다.
   */
  myPredictedPrice: number | null
  /** **본인** 예측 스테이크. 예측 미참여면 null */
  myStakeAmount: number | null
  /** 배타 잠금 복원 근거값 */
  myParticipationType: ParticipationType
  pinnedNotice: PinnedNotice | null
  amIMuted: boolean
  /**
   * 뮤트 해제 시각 (ISO 8601). 확정 계약 — BE가 `amIMuted`와의 동치를 강제한다
   * (둘이 어긋나면 응답 생성 자체를 거부하므로 뮤트면 반드시 값이, 아니면 반드시 null이 온다).
   * 규칙 5: 시각은 서버값만 — 자체 계산 금지.
   */
  mutedUntil: string | null
}

/**
 * 스냅샷 조회. 인증 불필요(명세: 인증 No)지만 `myBidAmount`·`myParticipationType`은
 * 토큰이 실린 경우에만 본인 값이 채워진다.
 */
export async function fetchRealtimeState(auctionId: number): Promise<RealtimeState> {
  // 경로는 baseURL(`/api/v1`) 기준 상대경로다(shared/api 규약, `/v1` 프리픽스 없음) —
  // 이관 전 `/v1/auctions/...`는 최종 URL이 `/api/v1/v1/...`가 되던 버그였다(실 모드 미검증이라 안 드러남).
  const { data } = await http.get<RealtimeState>(`/auctions/${auctionId}/realtime-state`)
  return data
}
