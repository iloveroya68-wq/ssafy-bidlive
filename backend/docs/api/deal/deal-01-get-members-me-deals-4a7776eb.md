# 내 거래 목록 조회

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [내 거래 목록 조회](https://app.notion.com/4a7776ebfd688318a81701f40b373e56)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `DEAL-01` |
| 저장소 도메인 | `deal` |
| Notion 도메인 | 경매 |
| Method | `GET` |
| Path / Destination | `/api/v1/members/me/deals` |
| 상태 | 확정 |
| 인증 | 필요 |
| 공개 범위 | Internal |
| 담당자 | 신희원 |
| 구현 | ✅ **구현됨** — `MyDealListController` (S15P11A704-295 후속). 아래 「구현 메모」 참조 (`2026-07-30` 확인) |
| 우선순위 | High |
| 연결 구분 | Front↔BE |
| 의존 대상 | Front, PostgreSQL, 경매 |
| 마지막 수정 | 2026-07-27 02:08:12Z |

## 요약

기존 통합 거래 목록은 마이페이지 용도에 따라 지금 할 일·낙찰 받은·개최한·참여한 경매 조회 API로 분리되었다.

## 개요
로그인 사용자가 구매자 또는 판매자로 참여한 거래 목록을 커서 기반으로 조회한다.
## Request
```javascript
GET /api/v1/members/me/deals?role=BUYER&status=COMPLETED&cursor={cursor}&size=20
Authorization: Bearer {accessToken}
```
### Query Parameter
<table>
<tr>
<td>필드</td>
<td>타입</td>
<td>필수</td>
<td>설명</td>
</tr>
<tr>
<td>`role`</td>
<td>Enum</td>
<td>선택</td>
<td>`BUYER` 또는 `SELLER`. 생략하면 전체 거래 조회</td>
</tr>
<tr>
<td>`status`</td>
<td>Enum</td>
<td>선택</td>
<td>거래 상태 필터</td>
</tr>
<tr>
<td>`cursor`</td>
<td>String</td>
<td>선택</td>
<td>이전 응답의 `nextCursor`. 최초 조회 시 생략</td>
</tr>
<tr>
<td>`size`</td>
<td>Integer</td>
<td>선택</td>
<td>조회 개수. 기본 20, 최대 100</td>
</tr>
</table>
## Response
### 200 OK
```json
{
  "items": [
    {
      "dealId": 300,
      "auctionId": 100,
      "role": "BUYER",
      "productName": "키크론 Q1 Pro",
      "thumbnailUrl": "https://cdn.example.com/products/501.png",
      "finalPrice": 74000,
      "status": "COMPLETED",
      "counterpartyNickname": "판**",
      "settledAt": "2026-07-20T22:00:00+09:00"
    }
  ],
  "nextCursor": null,
  "hasNext": false
}
```
## 검증 규칙
- Access Token의 `memberId`가 구매자 또는 판매자로 포함된 거래만 조회한다.
- 지원하는 역할과 거래 상태만 필터로 사용할 수 있다.
- 거래 상태는 `PAYMENT_PENDING`, `PAID`, `SHIPPING`, `DELIVERED`, `COMPLETED`, `DISPUTED`, `CANCELED` 등을 사용한다.
- 거래 상대방 정보는 정책에 따라 마스킹한다.
## 오류 응답
<table>
<tr>
<td>HTTP</td>
<td>코드</td>
<td>설명</td>
</tr>
<tr>
<td>400</td>
<td>`INVALID_DEAL_FILTER`</td>
<td>지원하지 않는 역할 또는 상태 필터</td>
</tr>
<tr>
<td>400</td>
<td>`INVALID_CURSOR`</td>
<td>커서 형식 오류</td>
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
→ 역할·상태·cursor 검증
→ 사용자 관련 거래 size+1건 조회
→ 상대방 공개 정보 마스킹
→ nextCursor와 hasNext 계산
→ 거래 목록 반환
```

## 구현 메모 (2026-07-30)

### 구현하며 정한 것

- **거래 상태는 enum이 아니라 문자열로 다룬다.** `deal_status`에 DB CHECK가 없어 `DealStatus`에 없는
  값이 들어 있을 수 있고, `valueOf`로 받으면 **그 한 행 때문에 목록 전체가 500**이 된다.
  응답도 결국 문자열이라 enum 왕복은 얻는 것 없이 위험만 만든다.
  (필터 파라미터는 `DealStatus`로 **검증만** 하고 값은 그대로 넘긴다.)
- **정렬 키는 `COALESCE(completed_at, cancelled_at, created_at)` 내림차순**이다.
  `deal`에는 갱신 시각 컬럼이 없어 완료·취소·생성 순으로 떨어뜨린다.
  타이브레이커로 `deal_id`를 함께 싣는다 — 시각 하나만 쓰면 같은 시각의 행에서 페이지가 겹치거나 빠진다.
- **다음 장 존재는 `size + 1`건을 읽어 판정한다.** COUNT를 따로 치면 두 쿼리가 서로 다른 스냅샷을 보게 되어
  "다음 장이 있다는데 눌러 보면 비어 있는" 경계가 생긴다.
- 오류는 도메인 공통 체계를 쓴다 — `INVALID_DEAL_FILTER`(역할·상태·size) · `INVALID_CURSOR`(커서).
  형제 엔드포인트와 같은 `DealErrorResponse` 봉투다. `size=abc`도 같은 경로로 400이 되도록
  문자열로 받아 직접 파싱한다(int로 받으면 Spring 타입 변환이 먼저 터져 봉투가 갈린다).
- `role`·`status`에 알아볼 수 없는 값이 오면 **400**이다. 조용히 무시하면 필터를 건 사용자가
  전체 목록을 보게 된다.
- `counterpartyNickname`은 **서버가 마스킹**한다. 규칙은 `SellerNicknameMask`를 그대로 쓴다 —
  두 벌이 되면 같은 사람이 화면마다 다르게 가려진다.
### ⚠️ 이 문서의 거래 상태 어휘는 구현과 다르다

「검증 규칙」의 상태 값(`PAYMENT_PENDING`·`PAID`·`SHIPPING`·`DELIVERED`·`COMPLETED`·`DISPUTED`·`CANCELED`)은
실제 스키마와 **어휘가 통째로 다르다.** `deal.deal_status`가 쓰는 값은 에스크로 흐름 그대로다:

`WAITING_ADDRESS` → `WAITING_SHIPMENT` → `WAITING_RECEIPT` → `COMPLETED` (+ `AUTO_COMPLETED`·`CANCELLED`)

겹치는 `COMPLETED` 하나를 빼면 대응이 없고, 취소는 **스펠링까지 다르다**(`CANCELED` vs `CANCELLED`).
구현은 **스키마·`DealStatus` 값을 정본으로** 삼았다 — 「지금 할 일」(MEM-02)과 에스크로 전이 API가
이미 그 값으로 돌아가고 있어서, 여기만 문서 어휘를 따르면 같은 거래가 화면마다 다른 상태로 보인다.

⚠️ **그래서 `deal_status`에는 「enum에 없는 값」이 들어올 여지가 문서에 남아 있는 셈이다.**
DB CHECK도 없다. 구현이 상태를 문자열로 다루는 이유가 이것이다(위 「구현하며 정한 것」 참조).

### 그 밖에

- ⚠️ **경로가 「지금 할 일」과 한 겹 차이다.** `/members/me/deals`(이 계약)와
  `/members/me/deals/todos`(MEM-02)는 다른 API다. `SecurityConfig`도 **둘을 따로 등록**해야 한다 —
  하나만 적으면 나머지가 `anyRequest().permitAll()`로 새어 공개 조회가 된다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [내 거래 목록 조회 원본](https://app.notion.com/4a7776ebfd688318a81701f40b373e56)

