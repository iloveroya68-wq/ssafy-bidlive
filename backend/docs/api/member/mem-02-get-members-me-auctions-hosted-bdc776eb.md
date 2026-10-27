# 개최한 경매 조회

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [개최한 경매 조회](https://app.notion.com/bdc776ebfd6883fa830581f6d097dd17)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `MEM-02` |
| 저장소 도메인 | `member` |
| Notion 도메인 | 경매 |
| Method | `GET` |
| Path / Destination | `/api/v1/members/me/auctions/hosted` |
| 상태 | 확정 |
| 인증 | 필요 |
| 공개 범위 | User |
| 담당자 | 신희원 |
| 구현 | ✅ **구현됨** — `HostedAuctionQueryController` (`2026-07-30` 확인) |
| 우선순위 | Medium |
| 연결 구분 | Front↔BE |
| 의존 대상 | Front, PostgreSQL, 경매 |
| 마지막 수정 | 2026-07-27 02:08:13Z |

## 요약

로그인 사용자가 판매자로 개설한 경매 이력을 커서 기반으로 조회한다.

## 비고

마이페이지 개최한 탭

## 개요
로그인 사용자가 판매자로 개설한 경매 이력을 커서 기반으로 조회한다. 대기·라이브·종료 상태를 모두 포함한다.
## Request
```javascript
GET /api/v1/members/me/auctions/hosted?cursor={cursor}&size=8
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
      "roomStatus": "ENDED",
      "resultType": "SOLD",
      "finalPrice": 74000,
      "winnerNickname": "준호***",
      "dealStatus": "WAITING_SHIPMENT",
      "actionRequired": true,
      "actionCode": "CONFIRM_SHIPMENT",
      "actionLabel": "발송 확인",
      "bidCount": 12,
      "participantCount": 9,
      "predictionParticipantCount": 32,
      "scheduledAt": "2026-07-21T20:00:00+09:00",
      "startedAt": "2026-07-21T20:02:13+09:00",
      "endedAt": "2026-07-21T20:17:13+09:00"
    },
    {
      "auctionId": 101,
      "dealId": null,
      "productTitle": "빈티지 카메라",
      "imageUrl": "https://cdn.example.com/products/camera.jpg",
      "roomStatus": "WAITING",
      "resultType": null,
      "finalPrice": null,
      "winnerNickname": null,
      "dealStatus": null,
      "actionRequired": false,
      "actionCode": null,
      "actionLabel": null,
      "bidCount": 0,
      "participantCount": 0,
      "predictionParticipantCount": 0,
      "scheduledAt": "2026-07-23T20:00:00+09:00",
      "startedAt": null,
      "endedAt": null
    }
  ],
  "nextCursor": "opaque-cursor",
  "hasNext": true
}
```
## 응답 필드
- `items`: 개최한 경매 목록. 결과가 없으면 빈 배열
- `dealId`: 낙찰 거래가 생성된 경우의 거래 ID. 대기·라이브·유찰은 `null`
- `roomStatus`: `WAITING`, `LIVE`, `SETTLING`, `ENDED`, `CANCELLED`
- `resultType`: `SOLD`, `NO_BID`, `HOST_LEFT`, `FORCED` 또는 결과 확정 전 `null`
- `winnerNickname`: 낙찰자 마스킹 닉네임. 낙찰자가 없으면 `null`
- `dealStatus`: 낙찰 후 거래 상태. 거래가 없으면 `null`
- `actionRequired`: 현재 판매자가 처리할 액션이 있는지 여부
- `actionCode`: 현재는 `CONFIRM_SHIPMENT` 또는 `null`
- `bidCount`: 누적 입찰 요청 건수
- `participantCount`: 고유 입찰 참여자 수
- `predictionParticipantCount`: 고유 예측 베팅 참여자 수
- `nextCursor`, `hasNext`: 무한 스크롤용 커서 정보
## 규칙
- `room.sellerId`가 현재 사용자인 경매만 포함한다.
- 대기·라이브·집계 중·종료 상태를 모두 포함할 수 있다.
- 기본 정렬은 생성 시각 또는 예정 시각 기준 최신순으로 한다.
- 종료된 방은 `resultType`과 `finalPrice`를 함께 반환한다.
- `WAITING_SHIPMENT` 상태에서는 판매자에게 발송 확인 액션을 제공한다.
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
- [개최한 경매 조회 원본](https://app.notion.com/bdc776ebfd6883fa830581f6d097dd17)

