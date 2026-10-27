# 판매자 발송 확인

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [판매자 발송 확인](https://app.notion.com/255776ebfd688308bd1f01e2bb3efa96)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `DEAL-05` |
| 저장소 도메인 | `deal` |
| Notion 도메인 | 경매 |
| Method | `POST` |
| Path / Destination | `/api/v1/deals/{dealId}/shipment-confirmation` |
| 상태 | 확정 |
| 인증 | 필요 |
| 공개 범위 | Seller |
| 담당자 | 신희원 |
| 구현 | ✅ **구현됨** — `DealEscrowController` (`2026-07-30` 확인) |
| 우선순위 | High |
| 연결 구분 | Front↔BE, BE 내부 |
| 의존 대상 | Front, PostgreSQL, 경매 |
| 마지막 수정 | 2026-07-27 02:08:12Z |

## 요약

판매자가 상품 발송을 확인하고 거래 상태를 수령 확인 대기로 전환한다.

## 비고

택배사 API·송장 추적은 범위 밖

## Request
요청 본문 없음.
## Response
```json
{
  "dealId": 300,
  "status": "WAITING_RECEIPT",
  "shippedAt": "2026-07-22T10:00:00+09:00",
  "autoConfirmAt": "2026-07-29T10:00:00+09:00"
}
```
- 판매자만 `WAITING_SHIPMENT` 상태에서 호출할 수 있다.
- 발송 확인 시 자동 수령 확정 예정 시각을 설정한다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [판매자 발송 확인 원본](https://app.notion.com/255776ebfd688308bd1f01e2bb3efa96)

