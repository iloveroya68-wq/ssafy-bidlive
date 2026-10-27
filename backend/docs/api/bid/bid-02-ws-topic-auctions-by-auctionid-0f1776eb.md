# 입찰 상태 브로드캐스트

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [입찰 상태 브로드캐스트](https://app.notion.com/0f1776ebfd6883e6a717819de7fcc71f)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `BID-02` |
| 저장소 도메인 | `bid` |
| Notion 도메인 | 입찰 |
| Method | `WS` |
| Path / Destination | `/topic/auctions/{auctionId}` |
| 상태 | 확정 |
| 구현 | ✅ **구현됨** — `BidAcceptedWebSocketPublisher` (`2026-07-31` 확인) |
| 인증 | 필요 |
| 공개 범위 | User |
| 담당자 | 김택림 |
| 우선순위 | High |
| 연결 구분 | Front↔BE |
| 의존 대상 | Front, 경매 |
| 마지막 수정 | 2026-07-27 02:08:14Z |

## 개요
입찰이 성공적으로 처리된 뒤 해당 경매방 구독자에게 최신 공개 상태를 전파한다.
- **Destination**: `/topic/auctions/{auctionId}`
- **Event Type**: `BID_ACCEPTED`
- **수신 대상**: 해당 경매방 전체 구독자
## 메시지 예시
```json
{
  "type": "BID_ACCEPTED",
  "data": {
    "bidCount": 13,
    "participantCount": 9,
    "vibeMessage": "누군가 베팅을 진행 중입니다!"
  }
}
```
## data 필드
<table>
<tr>
<td>필드</td>
<td>타입</td>
<td>설명</td>
</tr>
<tr>
<td>`bidCount`</td>
<td>Long</td>
<td>해당 경매의 누적 입찰 건수</td>
</tr>
<tr>
<td>`participantCount`</td>
<td>Long</td>
<td>한 번 이상 입찰한 고유 참여자 수</td>
</tr>
<tr>
<td>`vibeMessage`</td>
<td>String</td>
<td>입찰 기세 연출에 사용할 문구</td>
</tr>
</table>
## 공개 금지 정보
- 입찰자 ID 및 닉네임
- 입찰 금액
- 현재 최고가 및 2등가
- 현재 순위
- 개인 포인트 및 홀드 금액
## 발생 흐름
```plain text
POST /api/v1/auctions/{auctionId}/bids
→ Redis Lua 원자적 입찰 처리 성공
→ 입찰 이력 저장
→ /topic/auctions/{auctionId} 로 BID_ACCEPTED 발행
```
## 프론트 처리
- 입찰 건수 갱신
- 참여자 수 갱신
- Vibe 이펙트 표시
- 본인의 정확한 입찰 결과는 REST 응답을 사용한다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [입찰 상태 브로드캐스트 원본](https://app.notion.com/0f1776ebfd6883e6a717819de7fcc71f)

