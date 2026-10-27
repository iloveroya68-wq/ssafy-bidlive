# 구독 권한 검증

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [구독 권한 검증](https://app.notion.com/146776ebfd68832297c1812f96949080)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `WS-02` |
| 저장소 도메인 | `common` |
| Notion 도메인 | 공통 |
| Method | `INTERNAL` |
| Path / Destination | 내부 처리 |
| 상태 | 확정 |
| 인증 | 불필요 |
| 공개 범위 | Internal |
| 담당자 | 김경민 |
| 우선순위 | High |
| 연결 구분 | 미지정 |
| 의존 대상 | 미지정 |
| 마지막 수정 | 2026-07-27 02:08:15Z |

## 비고

STOMP SUBSCRIBE 권한 검증만 담당; 이벤트 payload 계약은 생성 도메인 명세 참조

> **내부 권한 명세** — STOMP `SUBSCRIBE` 프레임의 Destination과 세션 Principal만 검증한다.
## 목적
호스트 전용 질문 큐와 사용자 개인 큐를 구독 단계에서 보호한다. 이벤트 생성·payload 구성은 각 도메인 명세의 책임이며 이 기능에서 재계산하거나 변환하지 않는다.
## 호출 위치
```java
boolean canSubscribe(Principal principal, String destination)
```
## 허용 규칙
<table>
<tr>
<td>Destination</td>
<td>허용 대상</td>
</tr>
<tr>
<td>`/topic/auctions/{auctionId}` 및 일반 하위 토픽</td>
<td>인증 사용자</td>
</tr>
<tr>
<td>`/topic/auctions/{auctionId}/questions`</td>
<td>해당 경매 호스트</td>
</tr>
<tr>
<td>`/user/queue/**`</td>
<td>현재 Principal의 사용자 큐</td>
</tr>
</table>
- `hostId` 조회는 `AuctionQueryPort`에 위임
- Path의 `auctionId`와 조회한 경매를 기준으로 권한 판별
- 허용 목록에 없는 Destination은 기본 거부
- 권한 결과를 세션 전체에 영구 캐시하지 않으며, 짧은 캐시를 쓰면 경매 단위 키와 TTL을 적용
- 브로커는 각 도메인의 `{type, data}` payload를 변경하지 않음
## 실패
`SUBSCRIPTION_DENIED`를 `/user/queue/errors`로 보내고 해당 SUBSCRIBE 프레임을 무시한다.
## 필수 테스트
- 호스트 questions 구독 성공·비호스트 거부
- 자기 경매와 타 경매 분리
- `/user/queue/**` 타인 구독 차단
- 미등록 Destination 기본 거부
- 거부 후 연결 유지

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [구독 권한 검증 원본](https://app.notion.com/146776ebfd68832297c1812f96949080)

