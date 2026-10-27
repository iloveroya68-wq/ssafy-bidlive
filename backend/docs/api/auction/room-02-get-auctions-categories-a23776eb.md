# 경매방 카테고리 목록 조회

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [경매방 카테고리 목록 조회](https://app.notion.com/a23776ebfd6882ae9d2601b3bbf9ff7c)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `ROOM-02` |
| 저장소 도메인 | `auction` |
| Notion 도메인 | 경매 |
| Method | `GET` |
| Path / Destination | `/api/v1/auctions/categories` |
| 상태 | 확정 |
| 인증 | 불필요 |
| 공개 범위 | 미지정 |
| 담당자 | 신희원 |
| 구현 | ✅ **구현됨** — `AuctionCategoryController` (`2026-07-30` 확인) |
| 우선순위 | High |
| 연결 구분 | Front↔BE |
| 의존 대상 | Front, PostgreSQL |
| 마지막 수정 | 2026-07-27 02:08:15Z |

## 비고

경매방 상품 카테고리 검색한다.

## API
```plain text
GET /api/v1/auctions/categories
```
## Response
```plain text
{
  "items": [
    {
      "categoryId":1,
      "code":"ELECTRONICS",
      "name":"전자기기",
      "displayOrder":1
    },
    {
      "categoryId":2,
      "code":"FASHION",
      "name":"패션",
      "displayOrder":2
    }
  ]
}
```
## 주요 규칙
- 활성화된 카테고리만 반환한다.
- `displayOrder` 오름차순으로 반환한다.
- `categoryId`, `code`, `name`을 함께 제공한다.
- 프론트의 하드코딩된 카테고리 목록 대신 이 API 결과를 사용한다.
- 목록 변경이 잦지 않으므로 프론트에서 캐시할 수 있다.
## 오류 응답
<table>
<tr>
<td>HTTP</td>
<td>코드</td>
<td>설명</td>
</tr>
<tr>
<td>503</td>
<td>`CATEGORY_SERVICE_UNAVAILABLE`</td>
<td>카테고리 목록 조회 실패</td>
</tr>
</table>

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [경매방 카테고리 목록 조회 원본](https://app.notion.com/a23776ebfd6882ae9d2601b3bbf9ff7c)

