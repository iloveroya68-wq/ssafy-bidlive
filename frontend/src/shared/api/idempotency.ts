// 멱등 요청 키 (S15P11A704-59)
//
// 명세상 클라이언트가 키를 만들어 보내는 엔드포인트는 셋이다.
// - BID-06 입찰: `Idempotency-Key: {uuid}` **헤더**
// - PNT-01 충전·환불: `Idempotency-Key: {uuid}` **헤더**
// - PRED-01 예측 베팅: `clientRequestId` **본문 필드** (헤더가 아니다 — 명세 그대로 유지한다)
//
// 재시도할 때는 **같은 키를 재사용**해야 중복 처리가 막힌다. 그래서 키 생성은 제출 버튼을 누르는
// 시점에 한 번만 하고, 재시도 경로에는 만들어 둔 키를 그대로 넘긴다.

import type { AxiosRequestConfig } from 'axios'

export const IDEMPOTENCY_KEY_HEADER = 'Idempotency-Key'

function fallbackUuid(): string {
  // `crypto.randomUUID`는 보안 컨텍스트(https·localhost)에서만 제공된다.
  // 사내망 http 접속 데모에서 죽지 않도록 폴백을 둔다 — 암호학적 강도는 필요 없다(중복 방지용).
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (ch) => {
    const random = Math.floor(Math.random() * 16)
    const value = ch === 'x' ? random : (random & 0x3) | 0x8
    return value.toString(16)
  })
}

/** 요청 고유값(UUID v4) 생성 */
export function newRequestId(): string {
  const webCrypto = globalThis.crypto
  if (webCrypto && typeof webCrypto.randomUUID === 'function') return webCrypto.randomUUID()
  return fallbackUuid()
}

/** `Idempotency-Key` 헤더를 실은 요청 설정. 키를 생략하면 새로 만든다. */
export function idempotent(key: string = newRequestId()): AxiosRequestConfig {
  return { headers: { [IDEMPOTENCY_KEY_HEADER]: key } }
}
