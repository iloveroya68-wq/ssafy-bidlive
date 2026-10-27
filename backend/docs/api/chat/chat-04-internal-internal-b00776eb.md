# 슬로우 모드

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [슬로우 모드](https://app.notion.com/b00776ebfd688219afa90143fa2829c0)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `CHAT-04` |
| 저장소 도메인 | `chat` |
| Notion 도메인 | 채팅 |
| Method | `INTERNAL` |
| Path / Destination | 내부 처리 |
| 상태 | 확정 |
| 인증 | 불필요 |
| 공개 범위 | Internal |
| 담당자 | 김경민 |
| 우선순위 | Low |
| 연결 구분 | 미지정 |
| 의존 대상 | 미지정 |
| 마지막 수정 | 2026-07-27 02:08:10Z |

## 비고

경매·회원별 전송 제한 카운터를 원자적으로 관리

> **내부 처리 명세** — 채팅 전송 직전에 경매·사용자별 전송 간격을 검사하는 도배 방지 로직이다.
## 목적
한 사용자의 연속 전송이 채팅 품질과 WebSocket 처리량을 떨어뜨리지 않도록 제한한다.
## 호출 위치
```java
ChatSlowModeResult checkAndConsume(
    long auctionId,
    long memberId,
    Instant requestedAt
)
```
## 입력
```json
{
  "auctionId": 100,
  "memberId": 25,
  "requestedAt": "2026-07-20T21:03:12+09:00"
}
```
## 처리 규칙
- 키: `bidlive:chat:{auctionId}:slow-mode:{memberId}`
- 정책 시간 내 첫 요청만 `SET NX PX`로 허용
- 기본 제한 시간은 3초이며 `app.chat.slow-mode.interval` 설정으로 관리
- 환경변수로 재정의할 때는 `APP_CHAT_SLOW_MODE_INTERVAL=3s` 형식을 사용
- 남은 대기 시간은 Redis `PTTL`을 기준으로 초 단위 올림하여 반환
- 제한 검사는 메시지 저장·브로드캐스트보다 먼저 실행
## 결과 예시
```json
{ "allowed": true, "retryAfterSeconds": 0 }
```
제한 시 `allowed=false`와 남은 대기 시간을 반환한다.
## 실패 코드
<table>
<tr>
<td>코드</td>
<td>설명</td>
</tr>
<tr>
<td>`SLOW_MODE_LIMIT`</td>
<td>허용 간격 안에 다시 전송함</td>
</tr>
</table>
## 후속 처리
실패 시 `/user/queue/errors`에 개인 오류를 회신하고 메시지를 저장·브로드캐스트하지 않는다.
## 필수 테스트
- 첫 전송 허용·연속 전송 차단
- 제한 시간 후 재허용
- 사용자·경매별 카운터 격리
- 동시 요청 한 건만 허용

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [슬로우 모드 원본](https://app.notion.com/b00776ebfd688219afa90143fa2829c0)

