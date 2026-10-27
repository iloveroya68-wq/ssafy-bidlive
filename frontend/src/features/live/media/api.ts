// LIVE-01·02 미디어 세션 토큰 (S15P11A704-209) — OpenVidu 발행/수신 토큰 발급 + 방송 종료.
//
// 명세: 「스트리밍 방 세션 생성 및 발행 토큰 발급」(LIVE-01, Seller)
//       「스트리밍 시청 토큰 발급」(LIVE-01, User) · 「라이브 스트리밍 방송 종료」(LIVE-02, Seller).
//
// ⚠️ 경로 리소스명 주의: 미디어는 `/rooms/{roomId}/live/*` 다 —
//    입찰(`/auctions/{id}`)·방 CRUD(`/auction-rooms/{id}`)와 리소스명이 다르므로 섞지 않는다.
//    **경로 자체는 BE와 일치한다**(`LiveTokenController` 대조, S15P11A704-439). 다만 BE는 `roomId`를
//    DB에서 찾지 않고 OpenVidu 세션 이름으로 그대로 쓰므로 **없는 id를 넣어도 404가 아니라 빈 세션**이
//    만들어진다 — 「토큰은 받았는데 영상이 없다」로 보인다. publisher·subscriber가 같은 값을 쓰는 한
//    auctionId를 넣는 현재 방식은 안전하다.
//
// 이 모듈의 범위는 **토큰 발급·세션 종료까지**다. 실제 OpenVidu SDK 연결·스트림 바인딩(#video 슬롯)은
// 후속(시청자 수신 뷰·송출 프리뷰) 티켓이 이 토큰을 받아 수행한다.

import { API_ERROR_CODE, ApiError } from '@/shared/api/errors'
import { http } from '@/shared/api/http'
import { isMockApi, mockDelay } from '@/shared/api/mock'
import type { IsoDateTime } from '@/shared/api/types'

export interface CatchUpAudioChunk {
  sequence: number
  startedAt: IsoDateTime
  endedAt: IsoDateTime
  audio: Blob
}

/**
 * 호출부 계약 — `LiveStage`·`PublisherPreview`가 `const { token } = await ...`로 받는 모양이다.
 * 서버 응답이 봉투로 바뀌어도 **이 계약은 유지**한다(S15P11A704-521).
 */
export interface LiveMediaToken {
  token: string
}

/** 봉투 `type` — 발급된 토큰의 종류. FE는 분기에 쓰지 않고 계약 변화를 눈에 보이게만 한다. */
export type LiveTokenEventType = 'LIVE_PUBLISHER_TOKEN_ISSUED' | 'LIVE_SUBSCRIBER_TOKEN_ISSUED'

/** OpenVidu Connection role. BE `LiveSessionRole`이 그대로 직렬화된다. */
export type LiveSessionRole = 'PUBLISHER' | 'SUBSCRIBER'

/**
 * 봉투 `data`. **`roomId`는 숫자가 아니라 문자열이다** — BE가 PathVariable을 그대로 되돌려 주며
 * "확장성을 고려해 String으로 확정"했다고 DTO 주석에 적혀 있다(`LiveTokenResponse`).
 *
 * `sessionId`·`role`·`createdAt`은 **FE가 소비하지 않는다.** 타입에만 남겨 계약이 바뀌면
 * 컴파일 단계에서 드러나게 한다.
 */
export interface LiveTokenData {
  roomId: string
  sessionId: string
  token: string
  role: LiveSessionRole
  createdAt: IsoDateTime
}

/**
 * ⚠️ **미디어 토큰 응답은 프로젝트 공통 `{ type, data }` 봉투다** (S15P11A704-521).
 * 소켓 이벤트와 같은 규격(AGENTS.md 규칙 9)이지만 이쪽은 REST 응답이다.
 *
 * 봉투를 벗기는 공용 인터셉터는 없다 — `shared/api/http.ts`의 응답 인터셉터는 401 재발급만 하고,
 * 봉투를 쓰는 다른 도메인도 각자 `*Envelope` 타입을 명시해 직접 벗긴다
 * (`shared/api/rooms.ts`의 카테고리, `shared/api/wallets.ts`의 정산 계좌).
 */
export interface LiveTokenEnvelope {
  type: LiveTokenEventType
  data: LiveTokenData
}

/** 미디어 경로 베이스 — `/api/v1` 기준 상대경로(shared/api 규약과 동일, `/v1` 프리픽스 없음) */
const liveBasePath = (roomId: number): string => `/rooms/${roomId}/live`

/**
 * 봉투에서 토큰만 꺼낸다.
 *
 * 옛 모양(최상위 `token`)을 받으면 **조용히 통과시키지 않고 던진다.** 이 버그가 실서버에서만
 * 터졌던 이유가 정확히 그것이다 — 타입만 `{ token }`으로 붙어 있어 컴파일은 통과하고
 * `session.connect(undefined)`에서야 죽었다. 여기서 막으면 「영상 연결에 실패했어요」로 표면화된다.
 *
 * ⚠️ `payload?.data?.token`의 옵셔널 체이닝은 **타입상 불필요해 보여도 지우지 않는다** —
 * 서버가 `data: null`을 주면 타입은 거짓말이 되고 런타임만 남는다. 스펙이 그 모양을 잠갔다.
 */
function unwrapToken(payload: LiveTokenEnvelope): LiveMediaToken {
  const token = payload?.data?.token
  if (typeof token !== 'string' || token.length === 0) {
    throw new ApiError('영상 토큰 응답에 data.token이 없어요', {
      code: API_ERROR_CODE.MEDIA_TOKEN_SHAPE_UNEXPECTED,
      details: payload,
    })
  }
  return { token }
}

/** LIVE-01 판매자 발행 토큰 (Seller) — 방송 시작 시 OpenVidu 세션·송출 토큰을 발급받는다 */
export async function issuePublisherToken(roomId: number): Promise<LiveMediaToken> {
  if (isMockApi()) {
    await mockDelay(300)
    return { token: `mock-publisher-token-${roomId}` }
  }
  const { data } = await http.post<LiveTokenEnvelope>(`${liveBasePath(roomId)}/publisher`)
  return unwrapToken(data)
}

/** LIVE-01 시청자 수신 토큰 (User) — 입장 시 영상 수신용 토큰을 발급받는다 */
export async function issueSubscriberToken(roomId: number): Promise<LiveMediaToken> {
  if (isMockApi()) {
    await mockDelay(300)
    return { token: `mock-subscriber-token-${roomId}` }
  }
  const { data } = await http.post<LiveTokenEnvelope>(`${liveBasePath(roomId)}/subscriber`)
  return unwrapToken(data)
}

/** LIVE-02 방송 종료 (Seller) — 미디어 세션·방 상태를 정리한다 */
export async function endLiveStream(roomId: number): Promise<void> {
  if (isMockApi()) {
    await mockDelay(200)
    return
  }
  await http.post<void>(`${liveBasePath(roomId)}/end`)
}

/**
 * AI-03 판매자 음성 청크 접수. 서버는 202만 반환하고 STT는 별도 실행기에서 처리한다.
 * 브라우저가 만든 완결된 Blob만 보내며 원본 음성을 DB에 영구 저장하지 않는다.
 */
export async function uploadCatchUpAudioChunk(
  auctionId: number,
  chunk: CatchUpAudioChunk,
): Promise<void> {
  const form = new FormData()
  form.append('sequence', String(chunk.sequence))
  form.append('startedAt', chunk.startedAt)
  form.append('endedAt', chunk.endedAt)
  form.append('audio', chunk.audio, `chunk-${chunk.sequence}.webm`)
  await http.post<void>(`/auction-rooms/${auctionId}/stt/chunks`, form)
}

/**
 * LIVE-06 낙찰 스냅샷 업로드 (Seller) — **캡처 주체는 프론트다**(명세 A안 확정).
 * 판매자 브라우저가 송출 중인 `<video>` 프레임을 canvas로 떠서 올리고, 서버는 이미지 검증 →
 * 낙찰 맥락 검증 → S3 저장 → 메타 기록만 한다. 캡처·트리거는 `PublisherPreview`가 한다.
 *
 * ⚠️ 예전 주석은 「서버가 OpenVidu에서 직접 캡처하니 FE는 이 엔드포인트를 부르지 않는다」였다 —
 *    그건 body `{ dealId }`였던 **구명세** 기준이고 A안 확정으로 뒤집혔다. 되살린 것이 이 함수다.
 *
 * ⚠️ 경로의 `roomId`와 폼 필드 `auctionId`는 명세상 별개 값이지만 **이 레포에선 같은 값**이라
 *    인자가 하나다 — 콘솔이 라우트의 경매 id를 그대로 `live.connect()`에 넣고, 토큰 발급·방송 종료도
 *    같은 값을 방 id 자리에 쓴다(이 파일 상단 주석). 둘이 갈라지면 여기부터 인자를 나눈다.
 *
 * 파일명은 서버가 쓰지 않는다(S3 key를 직접 만든다) — 로그에서 알아보기 좋은 값만 붙인다.
 */
export async function uploadLiveSnapshot(auctionId: number, image: Blob): Promise<void> {
  const form = new FormData()
  form.append('image', image, `auction-${auctionId}.jpg`)
  form.append('auctionId', String(auctionId))
  await http.post<void>(`${liveBasePath(auctionId)}/snapshot`, form)
}
