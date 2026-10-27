# 내 거래 할 일 조회

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [내 거래 할 일 조회](https://app.notion.com/27e776ebfd6882caa10b81ef2c6344bd)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `MEM-02` |
| 저장소 도메인 | `deal` |
| Notion 도메인 | 경매 |
| Method | `GET` |
| Path / Destination | `/api/v1/members/me/deals/todos` |
| 상태 | 확정 |
| 인증 | 필요 |
| 공개 범위 | User |
| 담당자 | 신희원 |
| 구현 | ✅ **구현됨** — `DealTodoController` (`2026-07-30` 확인) |
| 우선순위 | Medium |
| 연결 구분 | Front↔BE |
| 의존 대상 | Front, PostgreSQL, 경매 |
| 마지막 수정 | 2026-07-27 02:08:13Z |

## 요약

배송지 입력·발송 확인·수령 확인 등 현재 로그인 사용자가 처리해야 하는 거래만 조회한다.

## 비고

마이페이지 지금 할 일

## 개요
마이페이지의 `지금 할 일` 영역에서 현재 로그인 사용자가 직접 처리해야 하는 거래를 모두 조회한다. 별도의 할 일 테이블을 사용하지 않고 `deal.status`와 사용자의 거래 역할을 기준으로 할 일을 계산한다.
## Request
```javascript
GET /api/v1/members/me/deals/todos
Authorization: Bearer {accessToken}
```
요청 본문과 쿼리 파라미터는 없다.
## Response
### 200 OK
```json
{
  "totalCount": 3,
  "items": [
    {
      "dealId": 300,
      "auctionId": 100,
      "productTitle": "키크론 Q1 Pro",
      "imageUrl": "https://cdn.example.com/products/abc.jpg",
      "role": "BUYER",
      "dealStatus": "WAITING_ADDRESS",
      "finalPrice": 74000,
      "actionCode": "SUBMIT_ADDRESS",
      "actionLabel": "배송지 입력",
      "actionDescription": "상품을 받을 배송지를 입력해 주세요.",
      "actionPath": "/deals/300/address",
      "endedAt": "2026-07-21T20:17:13+09:00",
      "updatedAt": "2026-07-21T20:18:00+09:00",
      "autoConfirmAt": null
    },
    {
      "dealId": 301,
      "auctionId": 101,
      "productTitle": "한정판 피규어",
      "imageUrl": "https://cdn.example.com/products/figure.jpg",
      "role": "SELLER",
      "dealStatus": "WAITING_SHIPMENT",
      "finalPrice": 120000,
      "actionCode": "CONFIRM_SHIPMENT",
      "actionLabel": "발송 확인",
      "actionDescription": "상품을 발송한 뒤 발송 확인을 완료해 주세요.",
      "actionPath": "/deals/301/shipment",
      "endedAt": "2026-07-20T21:10:00+09:00",
      "updatedAt": "2026-07-21T09:30:00+09:00",
      "autoConfirmAt": null
    },
    {
      "dealId": 302,
      "auctionId": 102,
      "productTitle": "빈티지 카메라",
      "imageUrl": "https://cdn.example.com/products/camera.jpg",
      "role": "BUYER",
      "dealStatus": "WAITING_RECEIPT",
      "finalPrice": 210000,
      "actionCode": "CONFIRM_RECEIPT",
      "actionLabel": "수령 확인",
      "actionDescription": "상품을 확인한 뒤 수령 확인을 완료해 주세요.",
      "actionPath": "/deals/302/receipt",
      "endedAt": "2026-07-18T19:30:00+09:00",
      "updatedAt": "2026-07-20T10:00:00+09:00",
      "autoConfirmAt": "2026-07-27T10:00:00+09:00"
    }
  ]
}
```
## 응답 필드
<table>
<tr>
<td>필드</td>
<td>타입</td>
<td>설명</td>
</tr>
<tr>
<td>`totalCount`</td>
<td>Integer</td>
<td>현재 사용자가 처리해야 하는 전체 거래 수</td>
</tr>
<tr>
<td>`items`</td>
<td>Array</td>
<td>할 일 목록. 할 일이 없으면 빈 배열</td>
</tr>
<tr>
<td>`dealId`</td>
<td>Long</td>
<td>거래 ID</td>
</tr>
<tr>
<td>`auctionId`</td>
<td>Long</td>
<td>경매 ID</td>
</tr>
<tr>
<td>`productTitle`</td>
<td>String</td>
<td>상품명</td>
</tr>
<tr>
<td>`imageUrl`</td>
<td>String</td>
<td>상품 대표 이미지 URL</td>
</tr>
<tr>
<td>`role`</td>
<td>Enum</td>
<td>현재 사용자의 거래 역할. `BUYER` 또는 `SELLER`</td>
</tr>
<tr>
<td>`dealStatus`</td>
<td>Enum</td>
<td>현재 거래 상태</td>
</tr>
<tr>
<td>`finalPrice`</td>
<td>Long</td>
<td>최종 낙찰가</td>
</tr>
<tr>
<td>`actionCode`</td>
<td>Enum</td>
<td>`SUBMIT_ADDRESS`, `CONFIRM_SHIPMENT`, `CONFIRM_RECEIPT`</td>
</tr>
<tr>
<td>`actionLabel`</td>
<td>String</td>
<td>화면 버튼 문구</td>
</tr>
<tr>
<td>`actionDescription`</td>
<td>String</td>
<td>사용자가 수행할 작업 안내</td>
</tr>
<tr>
<td>`actionPath`</td>
<td>String</td>
<td>프론트 이동 경로 또는 액션 화면 식별 경로</td>
</tr>
<tr>
<td>`endedAt`</td>
<td>DateTime</td>
<td>경매 종료 시각</td>
</tr>
<tr>
<td>`updatedAt`</td>
<td>DateTime</td>
<td>거래 상태가 마지막으로 변경된 시각</td>
</tr>
<tr>
<td>`autoConfirmAt`</td>
<td>DateTime\|null</td>
<td>자동 구매 확정 예정 시각. 수령 대기 상태가 아니면 `null`</td>
</tr>
</table>
## 할 일 판정 규칙
- `WAITING_ADDRESS` + 구매자 → `SUBMIT_ADDRESS`
- `WAITING_SHIPMENT` + 판매자 → `CONFIRM_SHIPMENT`
- `WAITING_RECEIPT` + 구매자 → `CONFIRM_RECEIPT`
- `WAITING_RECEIPT` + 판매자 → `AWAIT_RECEIPT` **(처리할 일이 없는 표시 전용 항목)**
- `COMPLETED`, `AUTO_COMPLETED`, `CANCELLED`는 제외한다.

### `AWAIT_RECEIPT` — 계약이 넓어진 지점 (S15P11A704-694)

이 목록은 원래 「처리해야 하는 거래」만 담았고, 그래서 **판매자는 발송 확인을 누르는 순간 목록에서
사라졌다.** 자기가 무엇을 어디로 보냈는지 다시 볼 데가 없었다.

`AWAIT_RECEIPT`는 그 자리를 메우는 **버튼 없는 항목**이다. 구매자가 수령을 확인할 때까지 판매자에게
남아 배송지·송장을 계속 볼 수 있게 한다(배송 정보는 [배송 정보 조회](./deal-05-get-deals-by-dealid-shipping.md)로 받는다).

- `actionLabel`: `수령 대기 중`
- `actionDescription`: `구매자가 수령을 확인하면 정산이 완료돼요.`
- 클라이언트는 `actionCode`로 액션 있는 항목과 가른다 — `AWAIT_RECEIPT`에는 버튼을 그리지 않는다

⚠️ 액션 판정이 **상태만으로 결정되지 않게 됐다.** `WAITING_RECEIPT` 하나가 역할에 따라 갈린다.
- 오래된 할 일이 먼저 보이도록 `updatedAt` 오름차순으로 정렬한다.
- 할 일이 없으면 `totalCount`는 `0`, `items`는 `[]`를 반환한다.
## 오류 응답
<table>
<tr>
<td>HTTP</td>
<td>코드</td>
<td>설명</td>
</tr>
<tr>
<td>401</td>
<td>`UNAUTHORIZED`</td>
<td>인증 실패</td>
</tr>
</table>
## 처리 흐름
```plain text
인증 사용자 확인
→ 구매자·판매자로 연결된 진행 중 거래 조회
→ deal.status와 사용자 역할로 액션 주체 판정
→ 액션 코드·문구·이동 경로 생성
→ updatedAt 오름차순 정렬
→ totalCount와 items 배열 반환
```

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [내 거래 할 일 조회 원본](https://app.notion.com/27e776ebfd6882caa10b81ef2c6344bd)

