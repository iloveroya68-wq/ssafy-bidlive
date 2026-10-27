# 스트리밍 방 세션 생성 및 발행 토큰 발급

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [스트리밍 방 세션 생성 및 발행 토큰 발급](https://app.notion.com/404776ebfd68837aa9bd0174af3377e7)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `LIVE-01` |
| 저장소 도메인 | `media` |
| Notion 도메인 | 방송 |
| Method | `POST` |
| Path / Destination | `/api/v1/rooms/{roomId}/live/publisher` |
| 상태 | 확정 |
| 인증 | 필요 |
| 공개 범위 | Seller |
| 담당자 | 이연주 |
| 우선순위 | High |
| 연결 구분 | Front↔BE, BE↔BE |
| 의존 대상 | Front, 경매 |
| 마지막 수정 | 2026-07-27 02:08:13Z |

## 요약

판매자가 라이브 방송을 시작할 때 OpenVidu 세션과 송출 토큰을 발급한다.

## 비고

OpenVidu Publisher Token 생성 (P0)

## 개요
판매자가 라이브 방송을 시작할 때 OpenVidu 미디어 세션을 생성하거나 기존 세션을 확인하고 송출용 Publisher 토큰을 발급한다.
## Request
```javascript
POST /api/v1/rooms/{roomId}/live/publisher
Authorization: Bearer {accessToken}
```
## Response
### 200 OK
```json
{
  "type": "LIVE_PUBLISHER_TOKEN_ISSUED",
  "data": {
    "roomId": 102,
    "sessionId": "ses_BidCast_Room_102",
    "token": "wss://dev-media.bidcast.site?sessionId=ses_BidCast_Room_102&token=tok_pub_abc123456",
    "role": "PUBLISHER",
    "createdAt": "2026-07-20T21:05:00+09:00"
  }
}
```
## 검증 규칙
- 인증 사용자가 해당 방의 판매자여야 한다.
- 방송을 시작할 수 있는 경매방 상태여야 한다.
- 동일 방의 OpenVidu 세션은 중복 생성하지 않는다.
## 처리 흐름
```plain text
판매자 권한 및 방 상태 확인
→ OpenVidu 세션 생성 또는 조회
→ Publisher 토큰 생성
→ type·data 공통 응답 반환
```

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [스트리밍 방 세션 생성 및 발행 토큰 발급 원본](https://app.notion.com/404776ebfd68837aa9bd0174af3377e7)

