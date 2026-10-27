# 고정 공지 해제

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [고정 공지 해제](https://app.notion.com/b6b776ebfd6882e78f228196441e9f9b)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `CHAT-02` |
| 저장소 도메인 | `chat` |
| Notion 도메인 | 채팅 |
| Method | `DELETE` |
| Path / Destination | `/api/v1/auctions/{auctionId}/chat/pin` |
| 상태 | 확정 |
| 인증 | 필요 — Access Token |
| 공개 범위 | 미지정 |
| 담당자 | 김경민 |
| 우선순위 | Medium |
| 연결 구분 | 미지정 |
| 의존 대상 | 미지정 |
| 마지막 수정 | 2026-07-27 02:08:13Z |

## 비고

현재 공지가 없어도 멱등 성공

> **REST 명세** — 호스트가 현재 고정 공지를 해제한다.
## 호출 위치
```plain text
DELETE /api/v1/auctions/{auctionId}/chat/pin
```
## 처리 규칙
- 요청자와 경매 `hostId` 일치
- 현재 공지가 없더라도 `204 No Content` 멱등 성공
- 공지가 있으면 현재 `chat_message.is_pinned=true` 행을 같은 트랜잭션에서 해제
- 경매별 PostgreSQL transaction advisory lock으로 등록·교체·해제를 직렬화
- 실제 변경이 커밋된 경우에만 `NOTICE_UNPINNED` 송출
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
<td>`AUCTION_NOT_FOUND`</td>
<td>경매 없음</td>
</tr>
</table>
## 필수 테스트
- 정상 해제
- 공지가 없는 상태의 반복 해제 204
- 비호스트 차단
- 실제 변경 한 번에 이벤트 한 번만 송출
- WS-03 pinnedNotice=null 반영

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [고정 공지 해제 원본](https://app.notion.com/b6b776ebfd6882e78f228196441e9f9b)

