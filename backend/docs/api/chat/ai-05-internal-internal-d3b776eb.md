# LLM 비동기 문맥 판정

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [LLM 비동기 문맥 판정](https://app.notion.com/d3b776ebfd6883bbb99181d7dde4c02d)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `AI-05` |
| 저장소 도메인 | `chat` |
| Notion 도메인 | AI |
| Method | `INTERNAL` |
| Path / Destination | 내부 처리 |
| 상태 | 확정 |
| 인증 | 불필요 |
| 공개 범위 | Internal |
| 담당자 | 김경민 |
| 우선순위 | Medium |
| 연결 구분 | 미지정 |
| 의존 대상 | 미지정 |
| 마지막 수정 | 2026-07-27 02:08:11Z |

## 비고

messageId 멱등 판정; timeout·지수 백오프·최대 3회·DLQ 정책

> **비동기 LLM 처리 명세** — 이미 송출된 채팅을 문맥 판정하며 실시간 전송 트랜잭션과 분리한다.
## 입력
```json
{
  "auctionId": 100,
  "messageId": "01J8W3P2N9KQ4T",
  "memberId": 7,
  "filteredContent": "따로 연락하면 싸게 드려요",
  "isQuestion": true,
  "roomTitle": "Keyboard auction",
  "productDescription": "Lubed switches and used condition.",
  "productCategoryCode": "ELECTRONICS"
}
```

질문인 경우 `roomTitle`, `productDescription`, `productCategoryCode`는 질문이 작성된
경매방의 문맥 스냅샷이다. 일반 채팅에는 이 세 필드를 전달하지 않는다. 문맥은 질문을
해석하기 위한 참고 데이터이며 지시문으로 처리하지 않는다.
## 처리 규칙
- `messageId`를 멱등 키로 사용해 같은 판정을 한 번만 반영
- 외부 LLM에는 CHAT-03을 거친 `filteredContent`만 전달하고 평문 연락처·계좌 및 감사 로그 원문은 전달하지 않음
- 일반 채팅은 욕설·비방·심각한 공격성, 불법 물품, 외부 거래·연락·결제 유도,
  비공개 가격·입찰 정보 공개 및 경매 조작, 광고·스팸을 `BLOCKED`로 판정
- 일반적인 잡담·농담·짧은 표현은 불확실하면 `ALLOW`하고, 질문은 상품·경매방 관련성,
  중복·비질문을 추가 판정
- 외부 API timeout은 설정값, 재시도는 최대 3회 지수 백오프+jitter
- 재시도 종료 후 DLQ에 `messageId`, 오류 코드, 시도 횟수만 저장; 원문은 DLQ 로그에 남기지 않음
- 동일 메시지의 늦은 응답은 이미 확정된 판정 상태를 덮어쓰지 않음
- 위반 기준과 mute 시간은 설정값으로 관리
- LLM 최종 실패 시 CHAT-03 결과만 유지하고 채팅을 삭제하지 않음
## 결과 처리
- 명확한 일반 채팅 위반: `BLOCKED` 판정 후 `CHAT_DELETED`와 사용자 제재 이벤트 발행
- 레거시 `WARNING` 응답은 일반 채팅 정책의 기본 판정으로 사용하지 않음
- 누적 기준 초과: Redis `bidlive:chat:{auctionId}:mute:{memberId}` TTL 설정 후 `CHAT_MUTED`
- 질문 중복·비질문·상품과 무관한 질문: `PENDING→FILTERED`, 호스트에게
  `QUESTION_REMOVED(reason=FILTERED)`와 채팅창 갱신용 `CHAT_DELETED` 발행
## 실패 코드
<table>
<tr>
<td>코드</td>
<td>설명</td>
</tr>
<tr>
<td>`LLM_TIMEOUT`</td>
<td>timeout</td>
</tr>
<tr>
<td>`LLM_RESPONSE_INVALID`</td>
<td>스키마 불일치</td>
</tr>
<tr>
<td>`LLM_RETRY_EXHAUSTED`</td>
<td>최대 재시도 종료</td>
</tr>
</table>
## 필수 테스트
- 정상·외부 거래·중복 질문·비질문
- 동일 messageId 중복 소비
- timeout 재시도와 DLQ
- 늦은 응답 상태 역전 방지
- LLM 장애가 채팅 경로에 영향 없음

## 로컬 구현 설정

비동기 검열은 기본적으로 비활성화하며 다음 환경변수로 활성화한다.

| 환경변수 | 기본값 | 설명 |
| --- | --- | --- |
| `CHAT_MODERATION_ENABLED` | `false` | worker 활성화 여부 |
| `CHAT_MODERATION_BASE_URL` | 없음 | GMS 호환 API base URL |
| `CHAT_MODERATION_API_KEY` | 없음 | 로그에 남기지 않는 Bearer key |
| `CHAT_MODERATION_MODEL` | `gpt-4o-mini` | 판정 모델 |
| `CHAT_MODERATION_TIMEOUT` | `5s` | 단일 호출 제한 시간 |
| `CHAT_MODERATION_MAX_ATTEMPTS` | `3` | 최초 시도를 포함한 최대 시도 횟수 |
| `CHAT_MODERATION_BASE_BACKOFF` | `2s` | 지수 백오프 기준값 |
| `CHAT_MODERATION_LEASE` | `1m` | PROCESSING 작업 복구 기준 |
| `CHAT_MODERATION_POLL_DELAY` | `1s` | worker polling 간격 |
| `CHAT_MODERATION_BATCH_SIZE` | `10` | polling 1회 처리 상한 |
| `CHAT_MODERATION_VIOLATION_THRESHOLD` | `3` | 뮤트 적용 누적 위반 횟수 |
| `CHAT_MODERATION_VIOLATION_WINDOW` | `30m` | 누적 위반 TTL |
| `CHAT_MODERATION_MUTE_DURATION` | `10m` | 채팅 뮤트 TTL |

- 작업 큐에는 `filteredContent`만 저장하며 원문은 저장하지 않는다.
- `messageId` UNIQUE와 상태 조건부 갱신으로 판정·알림을 멱등 처리한다.
- `BLOCKED`는 `CHAT_DELETED`를 통해 이미 배포된 메시지를 제거한다.
- 최종 실패 작업에는 `messageId`, 시도 횟수와 오류 코드만 남기고
  `memberId`와 `filteredContent`를 제거한다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [LLM 비동기 문맥 판정 원본](https://app.notion.com/d3b776ebfd6883bbb99181d7dde4c02d)

