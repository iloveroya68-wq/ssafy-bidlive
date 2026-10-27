// API 계약 레이어 공통 스칼라·페이지 타입 (S15P11A704-59)
//
// 여기 있는 별칭은 "백엔드 확인이 끝나면 한 줄만 고치면 되는 지점"을 만들기 위한 것이다.
// 근거: docs/04-백엔드-스키마-메모.md §6.3 (데이터 형식 미확정 항목)

/**
 * BIGINT PK의 JSON 직렬화 형식이 확정되지 않았다 (docs/04 §6.3).
 * 현재 FE는 전부 `number`를 쓰고 있어 그대로 맞춘다.
 * 서버가 문자열로 내려주는 것이 확인되면 **이 한 줄만** `string`으로 바꾼다.
 */
export type Id = number

/**
 * `TIMESTAMPTZ` 컬럼의 전송 형식. BID-06 응답의 `submittedAt`이 문자열인 것으로 보아
 * ISO 8601 문자열로 가정한다 (docs/03 §6-9의 201 응답 예시).
 * 스토어(`live.endsAt` 등)는 epoch ms를 쓰므로 **변환은 이 레이어에서** 한다 (docs/04 §6.3).
 */
export type IsoDateTime = string

/** ISO 문자열 → epoch ms. 값이 없거나 파싱 불가면 null (서버 시각 필드는 대부분 nullable이다). */
export function toEpochMs(value: IsoDateTime | null | undefined): number | null {
  if (value === null || value === undefined || value === '') return null
  const ms = Date.parse(value)
  return Number.isNaN(ms) ? null : ms
}

/** epoch ms → ISO 문자열. 목 어댑터가 목 데이터(epoch)를 서버 형식으로 되돌릴 때 쓴다. */
export function toIsoDateTime(epochMs: number): IsoDateTime {
  return new Date(epochMs).toISOString()
}

/**
 * 커서 페이지네이션 응답.
 * 방 목록·원장 모두 무한 스크롤이고(docs/02 라우트 표), 원장은 두 테이블이 분리돼 있어
 * offset 페이지네이션으로 병합할 수 없다(docs/04 §3.5). 그래서 커서 방식을 기본으로 둔다.
 * ⚠️ 서버가 실제로 커서를 주는지는 미확인 — 오프셋이면 이 타입만 바꾼다.
 */
export interface CursorPage<T> {
  items: T[]
  /** 다음 페이지 요청에 그대로 실어 보내는 불투명 문자열. 마지막 페이지면 null */
  nextCursor: string | null
  hasNext: boolean
}

/** 커서 페이지 요청 쿼리 공통부 */
export interface CursorPageQuery {
  cursor?: string | null
  size?: number
}

/** `product.moderation_status` / `product_image.moderation_status` (docs/04 §3.4) */
export type ModerationStatus = 'PENDING' | 'PASSED' | 'FLAGGED' | 'BLOCKED'
