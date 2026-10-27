// DEAL 거래 상태 표시 문구 — 마이페이지의 경매 목록·지금 할 일 두 화면이 공유한다.
//
// 값 집합의 근거는 「내 거래 할 일 조회」(MEM-02, 확정)의 **할 일 판정 규칙**이다:
//
//   - WAITING_ADDRESS  + 구매자 → SUBMIT_ADDRESS
//   - WAITING_SHIPMENT + 판매자 → CONFIRM_SHIPMENT
//   - WAITING_RECEIPT  + 구매자 → CONFIRM_RECEIPT
//   - COMPLETED, AUTO_COMPLETED, CANCELLED는 (할 일에서) 제외한다.
//
// ⚠️ S15P11A704-335에서는 「낙찰 받은 경매 조회」 응답 예시에 등장한 값만 보고
//    "값 목록이 명세에 없다"고 판단해 `SHIPPED`라는 **없는 값**을 지어냈다.
//    한 문서에 값이 없으면 같은 도메인의 다른 문서를 봐야 한다(S15P11A704-336에서 정정).

/** 명세가 열거한 거래 상태. 서버가 새 값을 추가할 수 있으므로 조회는 `dealStatusLabel()`로 한다 */
export const DEAL_STATUS_LABEL: Record<string, string> = {
  WAITING_ADDRESS: '배송지 입력 대기',
  WAITING_SHIPMENT: '발송 대기',
  WAITING_RECEIPT: '수령 확인 대기',
  COMPLETED: '거래 완료',
  /** 구매자가 확인하지 않아 기간 만료로 자동 확정된 건 */
  AUTO_COMPLETED: '자동 확정 완료',
  CANCELLED: '거래 취소',
}

/**
 * 표시 문구를 찾는다. **모르는 코드는 빈 문자열이다** —
 * 화면이 `COMPLETED` 같은 원시 코드를 그대로 노출하지 않게 하려는 것이고,
 * 호출부는 빈 값이면 그 자리를 비운다.
 */
export function dealStatusLabel(status: string | null | undefined): string {
  if (status === null || status === undefined) return ''
  return DEAL_STATUS_LABEL[status] ?? ''
}
