# 낙찰 받은 경매 조회

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [낙찰 받은 경매 조회](https://app.notion.com/a2d776ebfd6883c081d38150111bbc34)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `MEM-02` |
| 저장소 도메인 | `member` |
| Notion 도메인 | 경매 |
| Method | `GET` |
| Path / Destination | `/api/v1/members/me/auctions/won` |
| 상태 | 확정 |
| 인증 | 필요 |
| 공개 범위 | User |
| 담당자 | 신희원 |
| 구현 | ✅ **구현됨** — `WonAuctionQueryController` (`2026-07-30` 확인) |
| 우선순위 | Medium |
| 연결 구분 | Front↔BE |
| 의존 대상 | Front, PostgreSQL, 경매 |
| 마지막 수정 | 2026-07-27 02:08:14Z |

## 요약

로그인 사용자가 낙찰자로 확정된 경매 이력을 커서 기반으로 조회한다.

## 비고

마이페이지 낙찰 받은 탭

## 개요
로그인 사용자가 낙찰자로 확정된 경매 이력을 커서 기반으로 조회한다.
## Request
```javascript
GET /api/v1/members/me/auctions/won?cursor={cursor}&size=8
Authorization: Bearer {accessToken}
```
## Query Parameter
<table>
<tr>
<td>필드</td>
<td>타입</td>
<td>필수</td>
<td>설명</td>
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
<td>조회 개수. 기본 8, 최대 20</td>
</tr>
</table>
## Response
### 200 OK
```json
{
  "items": [
    {
      "auctionId": 100,
      "dealId": 300,
      "productTitle": "키크론 Q1 Pro",
      "imageUrl": "https://cdn.example.com/products/abc.jpg",
      "sellerNickname": "서연***",
      "finalPrice": 74000,
      "myFinalBid": 82000,
      "dealStatus": "WAITING_ADDRESS",
      "actionRequired": true,
      "actionCode": "SUBMIT_ADDRESS",
      "actionLabel": "배송지 입력",
      "snapshotUrl": "https://cdn.example.com/snapshots/100.jpg",
      "endedAt": "2026-07-21T20:17:13+09:00"
    },
    {
      "auctionId": 90,
      "dealId": 280,
      "productTitle": "한정판 피규어",
      "imageUrl": "https://cdn.example.com/products/figure.jpg",
      "sellerNickname": "지훈***",
      "finalPrice": 120000,
      "myFinalBid": 145000,
      "dealStatus": "COMPLETED",
      "actionRequired": false,
      "actionCode": null,
      "actionLabel": null,
      "snapshotUrl": "https://cdn.example.com/snapshots/90.jpg",
      "endedAt": "2026-07-19T21:10:00+09:00"
    }
  ],
  "nextCursor": "opaque-cursor",
  "hasNext": true
}
```
## 응답 필드
- `items`: 낙찰 받은 경매 목록. 결과가 없으면 빈 배열
- `sellerNickname`: 판매자 마스킹 닉네임
- `finalPrice`: 실제 지불 대상이 된 최종 낙찰가
- `myFinalBid`: 사용자의 최종 최고 입찰가
- `dealStatus`: 배송·거래 진행 상태
- `actionRequired`: 현재 사용자가 수행할 액션이 있는지 여부
- `actionCode`: `SUBMIT_ADDRESS`, `CONFIRM_RECEIPT` 또는 `null`
- `snapshotUrl`: 낙찰 순간 스냅샷 URL
- `nextCursor`, `hasNext`: 무한 스크롤용 커서 정보
## 규칙
- `auction_result.winnerId`가 현재 사용자인 경매만 포함한다.
- 최신 종료순으로 조회한다.
- 낙찰자 본인의 정보 외 상대방 닉네임은 마스킹한다.
- 거래가 완료된 경매도 이력에 포함한다.
- `WAITING_ADDRESS`, `WAITING_RECEIPT`처럼 구매자 액션이 필요한 경우 액션 정보를 함께 반환한다.
## 오류 응답
<table>
<tr>
<td>HTTP</td>
<td>코드</td>
<td>설명</td>
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

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [낙찰 받은 경매 조회 원본](https://app.notion.com/a2d776ebfd6883c081d38150111bbc34)

