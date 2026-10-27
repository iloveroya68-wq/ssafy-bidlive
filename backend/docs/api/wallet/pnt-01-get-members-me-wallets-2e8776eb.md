# 거래 포인트 지갑 조회

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [거래 포인트 지갑 조회](https://app.notion.com/2e8776ebfd688317a25a81395457b01b)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `PNT-01` |
| 저장소 도메인 | `wallet` |
| Notion 도메인 | 포인트 |
| Method | `GET` |
| Path / Destination | `/api/v1/members/me/wallets` |
| 상태 | 확정 |
| 인증 | 필요 |
| 공개 범위 | User |
| 담당자 | 신희원 |
| 구현 | ✅ **구현됨** — `WalletQueryController` (`2026-07-30` 확인) |
| 우선순위 | High |
| 연결 구분 | Front↔BE |
| 의존 대상 | Front, PostgreSQL, 포인트 |
| 마지막 수정 | 2026-07-27 02:08:12Z |

## 요약

입찰·낙찰 결제 전용 거래 포인트의 잔액·가용액·홀드액을 조회한다.

## 비고

베팅 포인트는 PNT-05 별도 재화

## 개요
로그인 사용자의 거래 포인트와 베팅 포인트 지갑 상태를 함께 조회한다.
## Request
```javascript
GET /api/v1/members/me/wallets
Authorization: Bearer {accessToken}
```
### Request Body
요청 본문 없음.
## Response
### 200 OK
```json
{
  "tradeWallet": {
    "balance": 200000,
    "heldAmount": 82000,
    "availableAmount": 118000
  },
  "bettingWallet": {
    "balance": 5000,
    "heldAmount": 500,
    "availableAmount": 4500
  }
}
```
### Response Field
<table>
<tr>
<td>필드</td>
<td>타입</td>
<td>설명</td>
</tr>
<tr>
<td>`tradeWallet`</td>
<td>Object</td>
<td>입찰·낙찰 결제에 사용하는 거래 포인트 지갑</td>
</tr>
<tr>
<td>`bettingWallet`</td>
<td>Object</td>
<td>최종가 예측에만 사용하는 무료 베팅 포인트 지갑</td>
</tr>
<tr>
<td>`balance`</td>
<td>Long</td>
<td>지갑의 총 보유액</td>
</tr>
<tr>
<td>`heldAmount`</td>
<td>Long</td>
<td>입찰 또는 베팅으로 사용이 제한된 금액</td>
</tr>
<tr>
<td>`availableAmount`</td>
<td>Long</td>
<td>현재 새 요청에 사용할 수 있는 금액</td>
</tr>
</table>
## 계산·검증 규칙
- `availableAmount = balance - heldAmount`로 계산한다.
- DB의 `available_balance`는 `availableAmount`, `held_balance`는
  `heldAmount`로 반환하며, `balance = available_balance + held_balance`로
  계산한다.
- 잔액과 홀드 금액은 음수가 될 수 없다.
- 거래 포인트와 베팅 포인트는 서로 전환할 수 없다.
- 회원 생성 시 두 지갑이 모두 생성되어 있어야 한다.
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
<tr>
<td>404</td>
<td>`MEMBER_NOT_FOUND`</td>
<td>회원이 존재하지 않음</td>
</tr>
<tr>
<td>404</td>
<td>`WALLET_NOT_FOUND`</td>
<td>필수 포인트 지갑이 존재하지 않음</td>
</tr>
</table>
## 처리 흐름
```plain text
인증 사용자 확인
→ TRADE 지갑 조회
→ BETTING 지갑 조회
→ 각 지갑의 가용 금액 계산
→ 이중 지갑 상태 반환
```

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [거래 포인트 지갑 조회 원본](https://app.notion.com/2e8776ebfd688317a25a81395457b01b)

