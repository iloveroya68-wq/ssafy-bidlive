# 구매자 수령 확인

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [구매자 수령 확인](https://app.notion.com/f26776ebfd688348903e01b35220a226)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `DEAL-05` |
| 저장소 도메인 | `deal` |
| Notion 도메인 | 경매 |
| Method | `POST` |
| Path / Destination | `/api/v1/deals/{dealId}/receipt-confirmation` |
| 상태 | 확정 |
| 인증 | 필요 |
| 공개 범위 | User |
| 담당자 | 신희원 |
| 구현 | ✅ **구현됨** — `DealEscrowController` (`2026-07-30` 확인) |
| 우선순위 | High |
| 연결 구분 | Front↔BE, BE↔BE, BE 내부 |
| 의존 대상 | Front, PostgreSQL, 포인트, 경매 |
| 마지막 수정 | 2026-07-27 02:08:10Z |

## 요약

구매자가 상품 수령을 확인하고 에스크로 대금을 판매자에게 지급해 거래를 완료한다.

## 비고

완료 시 판매자 지급

## Request
요청 본문 없음.
## Response
```json
{
  "dealId": 300,
  "status": "COMPLETED",
  "sellerPayoutAmount": 74000,
  "completedAt": "2026-07-23T18:00:00+09:00"
}
```
## 처리 흐름
```plain text
구매자 권한·WAITING_RECEIPT 상태 확인
→ 중복 요청 확인
→ 에스크로 금액 판매자 TRADE 지갑 지급
→ 포인트 원장 기록
→ deal COMPLETED 변경
```
- 구매자만 호출할 수 있다.
- 동일 거래는 한 번만 지급한다.
- 자동 확정 배치도 같은 지급 서비스를 사용한다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [구매자 수령 확인 원본](https://app.notion.com/f26776ebfd688348903e01b35220a226)

