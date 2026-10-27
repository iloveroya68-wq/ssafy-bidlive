# 라이브 스트리밍 방송 종료

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [라이브 스트리밍 방송 종료](https://app.notion.com/502776ebfd6883a9a46701a379a2baa8)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `LIVE-02` |
| 저장소 도메인 | `media` |
| Notion 도메인 | 방송 |
| Method | `POST` |
| Path / Destination | `/api/v1/rooms/{roomId}/live/end` |
| 상태 | 확정 |
| 인증 | 필요 |
| 공개 범위 | Seller |
| 담당자 | 이연주 |
| 우선순위 | High |
| 연결 구분 | Front↔BE, BE↔BE |
| 의존 대상 | Front, 경매, PostgreSQL |
| 마지막 수정 | 2026-07-27 02:08:10Z |

## 요약

판매자가 라이브 방송을 종료하고 미디어 세션과 방 상태를 정리한다.

## 비고

세션 종료 및 상태 변경 (P0)

## 개요
판매자가 진행 중인 라이브 방송을 종료하고 OpenVidu 세션 및 경매방의 방송 상태를 정리한다.
## Request
```javascript
POST /api/v1/rooms/{roomId}/live/end
Authorization: Bearer {accessToken}
```
## Response
### 204 No Content
응답 본문 없이 방송 종료 완료를 반환한다.
### 오류
| HTTP | `code` | 발생 조건 |
| --- | --- | --- |
| 401 | `UNAUTHORIZED` | Access Token이 없거나 인증 회원 식별자를 얻을 수 없다. |
| 403 | `NOT_BROADCAST_OWNER` | 인증 회원이 이 방의 방송을 시작한 회원이 아니다. |
| 404 | `LIVE_NOT_FOUND` | 해당 방의 방송 기록이 없다. |
| 409 | `LIVE_NOT_ACTIVE` | 이미 종료된 방송이거나 동시 종료 요청에서 밀렸다. |
| 502 | `MEDIA_SERVER_ERROR` | OpenVidu 세션 종료 호출이 실패했다. |

오류 본문은 `{"code": "...", "message": "..."}` 형식이다.
## 검증 규칙
- 인증 사용자가 해당 방의 방송을 시작한 판매자여야 한다.
- 이미 종료된 방송에 대한 중복 요청은 멱등 처리하지 않고 `409 LIVE_NOT_ACTIVE`로 거부한다.
- 상태 변경은 `LIVE` 상태만 `ENDED`로 바꾸는 조건부 갱신이며, 동시에 들어온 종료 요청 중
  하나만 성공하고 나머지는 `409`가 된다.
## 처리 흐름
```plain text
인증 및 방송 개시자 확인
→ 방송 상태 조건부 변경 및 종료 시각 저장
→ OpenVidu 세션 종료
→ 커밋 후 시청자에게 LIVE_ENDED 브로드캐스트
→ 204 반환
```
## 실패 보상 정책
- OpenVidu 종료 호출 실패는 예외로 올려 상태 변경 트랜잭션을 함께 롤백한다. 방송이 `LIVE`로
  남으므로 같은 요청을 다시 시도할 수 있다.
- 반대 순서(미디어 종료 후 상태 저장)를 쓰지 않는 이유는 저장이 실패할 때 "미디어는 끊겼는데
  상태는 `LIVE`"인 보상 불가 상태가 남기 때문이다.
- 이미 사라진 OpenVidu 세션(404)은 종료 목표가 달성된 상태로 보고 실패로 처리하지 않는다.
- 시청자 브로드캐스트는 커밋 이후에만 나가므로 롤백된 종료가 시청자에게 먼저 전달되지 않는다.
## 확인 필요
- 경매 마감·정산 연동: `auction` 도메인의 공개 Application 계약이 없어 아직 호출하지 않는다.
  계약이 생기면 상태 변경과 같은 트랜잭션 경계에 둘지 결정해야 한다.
- 방송 상태는 `auction_room`이 아닌 `media` 도메인의 `live_broadcast` 테이블에 임시로 보관한다.
  `auction` 도메인 구현 후 소유 위치를 정리해야 한다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [라이브 스트리밍 방송 종료 원본](https://app.notion.com/502776ebfd6883a9a46701a379a2baa8)

