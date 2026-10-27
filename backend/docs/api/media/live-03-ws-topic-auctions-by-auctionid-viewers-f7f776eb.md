# 접속 세션 수 브로드캐스트

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [접속 세션 수 브로드캐스트](https://app.notion.com/f7f776ebfd6882a98d44013bd7c2ea6c)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `LIVE-03` |
| 저장소 도메인 | `media` |
| Notion 도메인 | 방송 |
| Method | `WS` |
| Path / Destination | `/topic/auctions/{auctionId}/viewers` |
| 상태 | 확정 |
| 인증 | 불필요 |
| 공개 범위 | 미지정 |
| 담당자 | 김경민 |
| 우선순위 | High |
| 연결 구분 | 미지정 |
| 의존 대상 | 미지정 |
| 마지막 수정 | 2026-07-27 02:08:10Z |

## 비고

VIEWER_COUNT는 고유 사용자 수가 아닌 활성 STOMP 세션 수

> **WS 수신 명세** — 경매방의 활성 STOMP 접속 세션 수를 브로드캐스트한다.
## 구독 위치
```plain text
SUB /topic/auctions/{auctionId}/viewers
```
## 결과
```json
{
  "type": "VIEWER_COUNT",
  "data": {
    "viewerCount": 168,
    "countedBy": "STOMP_SESSION",
    "measuredAt": "2026-07-20T21:03:12+09:00"
  }
}
```
## 처리 규칙
- 만료 member 정리 후 `ZCARD`가 직전 값과 달라졌을 때 송출
- `viewerCount`는 회원 수가 아니라 활성 STOMP 세션 수
- 다중 탭은 각각 포함하며 OpenVidu 미디어 연결 여부와 무관
- Redis 장애 시 추정값을 새 값처럼 송출하지 않고 마지막 화면 값을 유지
- 신규 입장·재접속의 기준값은 WS-03 스냅샷에서 복원
## 필수 테스트
- 입장·이탈 시 증감
- TTL 만료 정리 후 감소 이벤트
- 동일 값 중복 송출 방지
- 다중 탭 표시
- Redis 장애 시 잘못된 0 송출 방지
- WS-03 viewerCount 일치

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [접속 세션 수 브로드캐스트 원본](https://app.notion.com/f7f776ebfd6882a98d44013bd7c2ea6c)

