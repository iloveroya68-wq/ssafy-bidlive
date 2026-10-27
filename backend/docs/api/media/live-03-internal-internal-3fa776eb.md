# 접속 세션 수 집계

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [접속 세션 수 집계](https://app.notion.com/3fa776ebfd6883f6b387811b18872204)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `LIVE-03` |
| 저장소 도메인 | `media` |
| Notion 도메인 | 방송 |
| Method | `INTERNAL` |
| Path / Destination | 내부 처리 |
| 상태 | 확정 |
| 인증 | 불필요 |
| 공개 범위 | Internal |
| 담당자 | 김경민 |
| 우선순위 | High |
| 연결 구분 | 미지정 |
| 의존 대상 | 미지정 |
| 마지막 수정 | 2026-07-27 02:08:10Z |

## 비고

stompSessionId heartbeat·TTL·정리 작업으로 유령 세션 제거

> **내부 접속 세션 집계 명세** — 활성 STOMP 세션을 heartbeat 시각이 포함된 Redis ZSET으로 관리한다.
## 목적
서버 비정상 종료나 Disconnect 이벤트 유실 뒤에도 유령 세션이 자동 제거되는 접속 세션 수를 제공한다. 이 값은 고유 사용자 수가 아니라 다중 탭을 포함한 활성 STOMP 세션 수다.
## 저장 구조
```plain text
Key: bidlive:media:{auctionId}:viewer-sessions
Type: ZSET
Member: {stompSessionId}
Score: lastSeenEpochMillis
```
## 처리 규칙
- 최초 경매 토픽 SUBSCRIBE: `ZADD`로 세션과 현재 시각 기록
- STOMP heartbeat 또는 세션 활동: 해당 member의 score 갱신
- UNSUBSCRIBE·DISCONNECT: `ZREM`
- 조회·브로드캐스트 전 `viewer.ttl-seconds`보다 오래된 member를 `ZREMRANGEBYSCORE`로 제거
- 정리 스케줄러도 `viewer.cleanup-interval-seconds` 주기로 실행
- `ZCARD`로 활성 세션 수 계산
- 다중 탭은 서로 다른 `stompSessionId`로 각각 집계
- OpenVidu `mediaSessionId`와 토큰 발급은 집계 기준이 아님
## 장애 처리
Redis 갱신 실패가 구독을 종료하지는 않는다. 운영 로그와 메트릭을 남기고 다음 heartbeat·정리 작업에서 회복한다.
## 후속 처리
활성 수가 달라진 경우 LIVE-03 브로드캐스트, WS-03 viewerCount 조회에 사용한다.
## 필수 테스트
- SUBSCRIBE·UNSUBSCRIBE·정상 DISCONNECT
- 프로세스 강제 종료 후 TTL 경과 자동 제거
- heartbeat 갱신 중 세션 유지
- 중복 구독과 다중 탭
- OpenVidu ID 혼용 방지
- 동시 100세션과 정리 작업 경쟁

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [접속 세션 수 집계 원본](https://app.notion.com/3fa776ebfd6883f6b387811b18872204)

