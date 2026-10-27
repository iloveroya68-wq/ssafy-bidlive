# 중간 입장 요약 조회

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [중간 입장 요약 조회](https://app.notion.com/2ae776ebfd6883fda154016cfe1ad4d7)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `AI-03` |
| 저장소 도메인 | `auction` |
| Notion 도메인 | AI |
| Method | `GET` |
| Path / Destination | `/api/v1/auction-rooms/{auctionId}/catch-up-summary` |
| 상태 | 확정 |
| 인증 | 필요 |
| 공개 범위 | User |
| 담당자 | 신희원 |
| 구현 | ✅ **구현** — RoomEvent 집계, Redis 2분 캐시, 선택적 GMS STT·AI 요약과 프론트 카드 (`S15P11A704-700`) |
| 우선순위 | Medium |
| 연결 구분 | Front↔BE, BE↔AI, BE↔Redis |
| 의존 대상 | Front, PostgreSQL, Redis, 경매, AI |
| 마지막 수정 | 2026-07-27 02:08:10Z |

## 요약

중간 입장자에게 RoomEvent 로그 기반 경매 진행 요약 카드를 제공한다.

## 개요
방송 중간에 입장한 사용자에게 RoomEvent 로그와 캐시된 요약을 기반으로 “지금까지의 방송” 카드를 제공한다. 요약 기능 실패는 방 입장·시청·입찰을 차단하지 않는다.
## Request
```javascript
GET /api/v1/auction-rooms/{auctionId}/catch-up-summary
Authorization: Bearer {accessToken}
```
### Path Variable
<table>
<tr>
<td>이름</td>
<td>타입</td>
<td>필수</td>
<td>설명</td>
</tr>
<tr>
<td>`auctionId`</td>
<td>Long</td>
<td>필수</td>
<td>중간 입장 요약을 조회할 경매방 ID</td>
</tr>
</table>
## Response
### 200 OK
```json
{
  "auctionId": 100,
  "generatedAt": "2026-07-20T21:08:00+09:00",
  "expiresAt": "2026-07-20T21:10:00+09:00",
  "bidSummary": {
    "bidCount": 7,
    "participantCount": 5
  },
  "extensionSummary": {
    "extensionCount": 1,
    "extendedMinutes": 5
  },
  "sellerMentions": [
    "키캡 1개 체결감 하자"
  ],
  "serverTime": "2026-07-20T21:08:12+09:00",
  "endAt": "2026-07-20T21:15:00+09:00",
  "sourceType": "STT_AND_ROOM_EVENT"
}
```
### 204 No Content
캐시와 생성 가능한 이벤트 정보가 모두 없으면 응답 본문 없이 요약 카드를 표시하지 않는다.
## 생성·검증 규칙
- 경매방이 존재하고 방송이 시작된 상태여야 한다.
- 매 입장 요청마다 요약을 새로 생성하지 않는다.
- 방별 최대 2분 주기로 요약을 갱신하고 Redis에 캐시한다.
- 1단계는 입찰 건수 변화, 참여자 수, 연장, 상태 변경, 판매자 메모 등 RoomEvent를 사용한다.
- 2단계에서는 STT 발화 텍스트를 비동기로 포함할 수 있다.
- 판매자 브라우저는 방송 중 마이크를 15초 단위 완결 파일로 만들어 내부 접수 API에 전송한다.
- SSAFY GMS의 OpenAI 호환 `POST /audio/transcriptions`와 `whisper-1`을 사용한다.
- 기본값은 마이크 수집과 GMS Credit 사용을 막기 위해 비활성화하며, 배포 Secret에
  `GMS_API_KEY`를 주입한 뒤 `GMS_STT_ENABLED=true`와
  `VITE_STT_CAPTURE_ENABLED=true`를 함께 적용한다.
- 진행 중인 입찰 금액·순위·회원 식별 정보는 요약에 포함하지 않는다.
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
<td>403</td>
<td>`ROOM_ACCESS_DENIED`</td>
<td>경매방 접근 권한 없음</td>
</tr>
<tr>
<td>404</td>
<td>`AUCTION_ROOM_NOT_FOUND`</td>
<td>경매방이 존재하지 않음</td>
</tr>
<tr>
<td>409</td>
<td>`AUCTION_NOT_STARTED`</td>
<td>아직 방송이 시작되지 않음</td>
</tr>
</table>
## 처리 흐름
```plain text
인증 사용자와 방 접근 권한 확인
→ Redis 요약 캐시 조회
→ 유효한 캐시가 있으면 즉시 반환
→ 캐시가 없으면 RoomEvent 조회
→ 비공개 정보 제거 후 요약 생성
→ Redis에 TTL 캐시 저장
→ 요약 카드 반환
```
## 폴백 원칙
- 캐시가 있으면 기존 캐시를 반환한다.
- AI 또는 STT 처리가 실패하면 RoomEvent 기반 요약으로 폴백한다.
- 생성 결과가 없으면 `204 No Content`를 반환한다.
- 요약 실패가 방 입장이나 핵심 거래 흐름에 영향을 주지 않는다.

## 판매자 음성 접수

```http
POST /api/v1/auction-rooms/{auctionId}/stt/chunks
Authorization: Bearer {accessToken}
Content-Type: multipart/form-data
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `sequence` | Long | 방 안에서 단조 증가하는 청크 순서 |
| `startedAt` | DateTime | 음성 구간 시작 시각 |
| `endedAt` | DateTime | 음성 구간 종료 시각 |
| `audio` | File | `webm`, `ogg`, `wav` 형식의 완결된 짧은 음성 파일 |

- 판매자 본인이면서 경매가 `LIVE`일 때만 `202 Accepted`로 접수한다.
- `(auctionId, sequence)`를 Redis에서 멱등하게 선점하고 별도 STT 실행기에서 처리한다.
- 음성 바이너리는 DB에 저장하지 않으며, 확정 텍스트만 최대 6시간 Redis에 보관한다.
- STT 실패 시 해당 요약 주기는 RoomEvent만 사용하며 방송 송출과 입찰을 중단하지 않는다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [중간 입장 요약 조회 원본](https://app.notion.com/2ae776ebfd6883fda154016cfe1ad4d7)

