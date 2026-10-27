# 포인트 원장 내역 조회

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [포인트 원장 내역 조회](https://app.notion.com/499776ebfd68838aa92081deb56666a8)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `PNT-02` |
| 저장소 도메인 | `wallet` |
| Notion 도메인 | 포인트 |
| Method | `GET` |
| Path / Destination | `/api/v1/members/me/point-ledgers` |
| 상태 | 확정 |
| 인증 | 필요 |
| 공개 범위 | User |
| 담당자 | 신희원 |
| 구현 | ✅ **구현됨** — `PointLedgerQueryController` (`2026-07-30` 확인) |
| 우선순위 | High |
| 연결 구분 | Front↔BE |
| 의존 대상 | Front, PostgreSQL, 포인트 |
| 마지막 수정 | 2026-07-30 |

## 개요

로그인 사용자의 거래·베팅 포인트 원장 이력을 최신순으로 조회한다.
커서 기반 페이징을 사용하며 원장 행은 수정하지 않고 정정 거래를 새 행으로 기록한다.

## Request

```http
GET /api/v1/members/me/point-ledgers?walletType=TRADE&transactionType=HOLD&cursor={cursor}&size=20
Authorization: Bearer {accessToken}
```

### Query Parameter

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `walletType` | Enum | 선택 | `TRADE` 또는 `BETTING`. 생략하면 전체 지갑 조회 |
| `transactionType` | Enum | 선택 | `CHARGE`, `HOLD`, `RELEASE`, `SETTLEMENT`, `REFUND`, `GRANT`, `BET`, `PAYOUT` |
| `cursor` | String | 선택 | 이전 응답의 `nextCursor`. 최초 조회 시 생략 |
| `size` | Integer | 선택 | 조회 개수. 기본 20, 최대 100 |

## Response

### 200 OK

```json
{
  "items": [
    {
      "ledgerId": 1001,
      "walletType": "TRADE",
      "transactionType": "HOLD",
      "availableChange": -82000,
      "heldChange": 82000,
      "availableBalanceAfter": 118000,
      "heldBalanceAfter": 82000,
      "description": "경매 입찰 포인트 보류",
      "referenceType": "BID",
      "referenceId": 351,
      "createdAt": "2026-07-20T21:12:44+09:00"
    }
  ],
  "nextCursor": null,
  "hasNext": false
}
```

### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `items` | Array | 조회된 포인트 원장 목록 |
| `items[].availableChange` | Long | 가용 포인트 증감. 증가는 양수, 감소는 음수, 변동 없음은 0 |
| `items[].heldChange` | Long | 보류 포인트 증감. 증가는 양수, 감소는 음수, 변동 없음은 0 |
| `items[].availableBalanceAfter` | Long | 해당 원장 처리 직후 가용 포인트 잔액 |
| `items[].heldBalanceAfter` | Long | 해당 원장 처리 직후 보류 포인트 잔액 |
| `items[].description` | String | 프론트 원장 행에 표시할 서버 생성 설명 문구 |
| `items[].referenceType` | Enum | 증감 원인 도메인. 아래 전체 값 목록 참조 |
| `items[].referenceId` | Long / null | 원인 도메인의 식별자. 직접 지급 등 연결 대상이 없으면 `null` |
| `nextCursor` | String / null | 다음 조회 시 전달할 불투명한 커서. 다음 페이지가 없으면 `null` |
| `hasNext` | Boolean | 다음 원장 내역 존재 여부 |

### referenceType 전체 값

| 값 | 설명 |
| --- | --- |
| `BID` | 입찰 금액 홀드·추가 홀드·해제 |
| `AUCTION` | 낙찰 확정·유찰·판매자 이탈·강제 종료에 따른 경매 정산 |
| `DEAL` | 구매 확정·판매자 지급·거래 취소에 따른 거래 정산 |
| `PREDICTION` | 낙찰가 예측 베팅·배당·환불 |
| `PAYMENT` | 거래 포인트 충전·결제 취소·환불 |
| `REWARD` | 가입·출석·시청 등 베팅 포인트 보상 |
| `ADMIN` | 운영자 수동 조정 또는 데이터 정정 |

## 조회·표시 규칙

- `createdAt DESC, ledgerId DESC` 순서로 조회한다.
- 거래·베팅 원장의 `createdAt`과 `ledgerId`가 모두 같으면 `TRADE`, `BETTING` 순서로 정렬한다.
- `availableChange`와 `heldChange`는 부호가 있는 값이며 한쪽이 변하지 않으면 0을 반환한다.
- 입찰 홀드는 `availableChange < 0`, `heldChange > 0`으로 같은 행에 표현한다.
- 보류 해제는 `availableChange > 0`, `heldChange < 0`으로 같은 행에 표현한다.
- `description`은 서버가 `transactionType`과 `referenceType`을 기준으로 생성한다.
- 위 표에 없는 `referenceType`을 추가하면 이 명세와 프론트 표시 정책을 함께 갱신한다.
- 커서에는 마지막 항목의 `createdAt`, `ledgerId`, `walletType`을 포함한다.
- 프론트는 커서 내부를 해석하지 않고 다음 요청에 그대로 전달한다.
- `hasNext=true`이면 `nextCursor`는 반드시 값이 있다.
- `hasNext=false`이면 `nextCursor=null`이다.
- `walletType`을 생략하면 회원에게 `TRADE`, `BETTING` 지갑이 모두 있어야 한다.
- `walletType`을 지정하면 해당 유형의 지갑이 있어야 한다.

## 설명 문구 정책

| transactionType | referenceType | description |
| --- | --- | --- |
| `CHARGE` | `PAYMENT` | 거래 포인트 충전 |
| `HOLD` | `BID` | 경매 입찰 포인트 보류 |
| `RELEASE` | `BID` | 경매 입찰 포인트 보류 해제 |
| `SETTLEMENT` | `AUCTION` | 경매 낙찰 포인트 정산 |
| `SETTLEMENT` | `DEAL` | 거래 정산 포인트 지급 |
| `REFUND` | `PREDICTION` | 예측 베팅 포인트 환불 |
| `REFUND` | `PAYMENT` | 거래 포인트 환불 |
| `REFUND` | `DEAL` | 거래 취소 포인트 환불 |
| `REFUND` | `AUCTION` | 경매 종료 포인트 환불 |
| `GRANT` | `REWARD` | 베팅 포인트 보상 지급 |
| `BET` | `PREDICTION` | 낙찰가 예측 베팅 |
| `PAYOUT` | `PREDICTION` | 낙찰가 예측 배당 |

## 오류 응답

| HTTP | 코드 | 설명 |
| --- | --- | --- |
| 400 | `INVALID_CURSOR` | 커서 형식이 잘못됨 |
| 400 | `INVALID_LEDGER_FILTER` | 지원하지 않는 지갑·거래 유형 또는 조회 크기 |
| 401 | `UNAUTHORIZED` | 인증 실패 |
| 404 | `WALLET_NOT_FOUND` | 회원의 필수 포인트 지갑이 존재하지 않음 |

## 처리 흐름

```text
인증 사용자 확인
→ Query Parameter 검증
→ 요청 범위의 필수 지갑 존재 확인
→ cursor 디코딩
→ 거래·베팅 원장을 통합하여 조건에 맞는 size+1건 조회
→ 다음 페이지 존재 여부 판정
→ 마지막 항목으로 nextCursor 생성
→ referenceType과 description을 포함한 원장 목록 반환
```

## 원장 원칙

- 원장 행은 생성 후 수정·삭제하지 않는다.
- 오류 정정이 필요하면 반대 방향의 정정 원장 행을 추가한다.
- `referenceType`과 `referenceId`로 입찰·거래·예측 등 발생 원인을 추적한다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [포인트 원장 내역 조회 원본](https://app.notion.com/499776ebfd68838aa92081deb56666a8)
