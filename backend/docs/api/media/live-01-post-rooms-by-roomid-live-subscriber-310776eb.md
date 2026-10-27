# 스트리밍 시청 토큰 발급

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [스트리밍 시청 토큰 발급](https://app.notion.com/310776ebfd6883c0b6ca813dfac53f09)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `LIVE-01` |
| 저장소 도메인 | `media` |
| Notion 도메인 | 방송 |
| Method | `POST` |
| Path / Destination | `/api/v1/rooms/{roomId}/live/subscriber` |
| 상태 | 확정 |
| 인증 | 필요 |
| 공개 범위 | User |
| 담당자 | 이연주 |
| 우선순위 | High |
| 연결 구분 | Front↔BE, BE↔BE |
| 의존 대상 | Front, 경매 |
| 마지막 수정 | 2026-07-27 02:08:13Z |

## 요약

사용자가 라이브 방에 입장할 때 영상 수신용 토큰을 발급한다.

## 비고

OpenVidu Subscriber Token 생성 (P0)

## 개요
시청자가 라이브 경매방에 입장할 때 영상 수신 전용 Subscriber 토큰을 발급한다.
## Request
```javascript
POST /api/v1/rooms/{roomId}/live/subscriber
Authorization: Bearer {accessToken}
```
## Response
### 200 OK
```json
{
  "type": "LIVE_SUBSCRIBER_TOKEN_ISSUED",
  "data": {
    "roomId": 102,
    "sessionId": "ses_BidCast_Room_102",
    "token": "wss://dev-media.bidcast.site?sessionId=ses_BidCast_Room_102&token=tok_sub_xyz987654",
    "role": "SUBSCRIBER",
    "createdAt": "2026-07-20T21:05:10+09:00"
  }
}
```
## 검증 규칙
- 인증된 사용자만 발급받을 수 있다.
- 해당 방의 라이브 세션이 존재하고 입장 가능한 상태여야 한다.
- 토큰 권한은 영상 수신 전용 `SUBSCRIBER`로 제한한다.
## 처리 흐름
```plain text
사용자 및 방 상태 확인
→ OpenVidu 세션 확인
→ Subscriber 토큰 생성
→ type·data 공통 응답 반환
```

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [스트리밍 시청 토큰 발급 원본](https://app.notion.com/310776ebfd6883c0b6ca813dfac53f09)

