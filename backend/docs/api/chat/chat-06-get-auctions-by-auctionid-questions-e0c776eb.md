# 질문 큐 조회

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [질문 큐 조회](https://app.notion.com/e0c776ebfd6882d2b13e81195f9dfb98)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `CHAT-06` |
| 저장소 도메인 | `chat` |
| Notion 도메인 | 채팅 |
| Method | `GET` |
| Path / Destination | `/api/v1/auctions/{auctionId}/questions` |
| 상태 | 확정 |
| 인증 | 불필요 |
| 공개 범위 | 미지정 |
| 담당자 | 김경민 |
| 우선순위 | Medium |
| 연결 구분 | 미지정 |
| 의존 대상 | 미지정 |
| 마지막 수정 | 2026-07-27 02:08:10Z |

## 비고

PENDING 질문 cursor pagination 조회; 기본 20·최대 100

> **REST 조회 명세** — 호스트가 PENDING 질문을 cursor pagination으로 복원한다.
## 호출 위치
```plain text
GET /api/v1/auctions/{auctionId}/questions?cursor={questionId}&size=20
```
## 요청 규칙
- 해당 경매 호스트만 조회 가능
- `size` 기본 20, 최소 1, 최대 100
- `cursor`가 없으면 첫 페이지
- `createdAt ASC, questionId ASC`의 안정 정렬
- `question_status=PENDING`만 반환
## 결과 예시
```json
{
  "items": [
    {
      "questionId": "01J8W3P2N9KQ4T",
      "memberId": 7,
      "nickname": "서연",
      "content": "키캡 흠집을 보여주세요",
      "createdAt": "2026-07-20T21:03:12+09:00"
    }
  ],
  "nextCursor": "01J8W3P2N9KQ4T",
  "hasNext": false
}
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
<td>`INVALID_CURSOR`</td>
<td>cursor 형식 또는 범위 오류</td>
</tr>
<tr>
<td>`INVALID_PAGE_SIZE`</td>
<td>size 범위 오류</td>
</tr>
</table>
## 필수 테스트
- 빈 목록·첫 페이지·다음 페이지
- 동일 createdAt에서 안정 정렬
- PENDING만 노출
- 최대 size 제한
- 비호스트 차단

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [질문 큐 조회 원본](https://app.notion.com/e0c776ebfd6882d2b13e81195f9dfb98)

