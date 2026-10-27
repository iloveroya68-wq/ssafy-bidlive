# 질문 큐 항목 제거

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [질문 큐 항목 제거](https://app.notion.com/12a776ebfd68827da94a81bbc24803f9)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `CHAT-06` |
| 저장소 도메인 | `chat` |
| Notion 도메인 | 채팅 |
| Method | `DELETE` |
| Path / Destination | `/api/v1/auctions/{auctionId}/questions/{questionId}` |
| 상태 | 확정 |
| 인증 | 불필요 |
| 공개 범위 | 미지정 |
| 담당자 | 김경민 |
| 우선순위 | Medium |
| 연결 구분 | 미지정 |
| 의존 대상 | 미지정 |
| 마지막 수정 | 2026-07-27 02:08:10Z |

## 비고

RESOLVED 전이; 이미 처리된 질문은 204 멱등 성공

> **REST 상태 전이 명세** — 호스트가 질문을 답변 완료 처리한다.
## 호출 위치
```plain text
DELETE /api/v1/auctions/{auctionId}/questions/{questionId}
```
## 처리 규칙
- 해당 경매 호스트만 가능
- 같은 경매의 `PENDING` 질문을 `RESOLVED`로 전이
- 이미 `RESOLVED` 또는 `FILTERED`인 질문은 `204 No Content` 멱등 성공
- 존재하지 않거나 다른 경매의 질문은 `QUESTION_NOT_FOUND`
- 실제 상태가 바뀐 경우에만 `QUESTION_REMOVED(reason=RESOLVED)` 송출
## 성공 응답
```plain text
204 No Content
```
## 실패 코드
<table>
<tr>
<td>코드</td>
<td>설명</td>
</tr>
<tr>
<td>`NOT_AUCTION_HOST`</td>
<td>호스트가 아님</td>
</tr>
<tr>
<td>`QUESTION_NOT_FOUND`</td>
<td>없거나 다른 경매의 질문</td>
</tr>
</table>
## 필수 테스트
- PENDING→RESOLVED
- 반복 DELETE 204 및 이벤트 중복 없음
- FILTERED 질문 DELETE 204
- 비호스트·타 경매 질문 차단
- 동시 해결 요청 한 번만 상태 변경

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [질문 큐 항목 제거 원본](https://app.notion.com/12a776ebfd68827da94a81bbc24803f9)

