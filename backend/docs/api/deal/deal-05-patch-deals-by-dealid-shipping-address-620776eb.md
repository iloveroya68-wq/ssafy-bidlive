# 배송지 입력

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [배송지 입력](https://app.notion.com/620776ebfd68830ab67501014028e8d4)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `DEAL-05` |
| 저장소 도메인 | `deal` |
| Notion 도메인 | 경매 |
| Method | `PATCH` |
| Path / Destination | `/api/v1/deals/{dealId}/shipping-address` |
| 상태 | 확정 |
| 인증 | 필요 |
| 공개 범위 | User |
| 담당자 | 신희원 |
| 구현 | ✅ **구현됨** — `DealEscrowController` (`2026-07-30` 확인) |
| 우선순위 | High |
| 연결 구분 | Front↔BE |
| 의존 대상 | Front, PostgreSQL, 경매 |
| 마지막 수정 | 2026-07-27 02:08:12Z |

## 요약

낙찰자가 택배 수령에 필요한 배송지를 입력하고 거래 상태를 발송 대기로 전환한다.

## 비고

택배 단일, 직접 수령 없음

## Request
```json
{
  "receiverName": "김서연",
  "contact": "010-1234-5678",
  "postalCode": "06236",
  "address": "서울시 강남구 ...",
  "detailAddress": "101동 202호",
  "memo": "문 앞에 놓아 주세요"
}
```
## Response
```json
{
  "dealId": 300,
  "status": "WAITING_SHIPMENT",
  "updatedAt": "2026-07-21T21:00:00+09:00"
}
```
- 구매자만 `WAITING_ADDRESS` 상태에서 입력할 수 있다.
- 연락처와 주소는 거래 당사자에게만 공개한다.
- 배송 방법 선택 필드는 두지 않는다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [배송지 입력 원본](https://app.notion.com/620776ebfd68830ab67501014028e8d4)

