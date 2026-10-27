# 카테고리별 경매방 목록 조회 통합 안내

> **구현 상태:** 🚫 **폐기** — 폐기 (2026-07-30) — ROOM-02가 완전히 포함한다. 새 엔드포인트를 만들지 않는다 (`2026-07-30` 확인)

> 🚫 **폐기 (2026-07-30). 구현하지 않는다. — BE 확인 완료.**
>
> BE에서 같은 결론을 회신했다(2026-07-30): "명세 중복이 있어서 `/api/v1/auctions`는 따로 개발하지
> 않을 예정. 카테고리별 조회는 `GET /api/v1/auction-rooms?status=LIVE&categoryId=1&size=20`으로,
> 전체는 `categoryId`를 빼면 된다. 첫 요청은 `cursor` 생략, 다음 페이지부터 응답의 `nextCursor`를
> 전달. 카테고리 표시명은 `GET /api/v1/auctions/categories`의 `code`를 목록 응답의 `categoryCode`와
> 이어서 쓴다." 아래 근거는 그 회신 전에 정리한 것이고 결론이 같다.
>
> **「경매방 목록 조회」(ROOM-02, `GET /api/v1/auction-rooms`)가 이 계약을 완전히 포함한다.**
> 쿼리 파라미터가 `categoryId`·`status`·`size`·`cursor`로 같고 "카테고리를 선택하지 않으면 전체 조회"도
> 같다. 다른 것은 **경로뿐**(`/auctions` vs `/auction-rooms`)이고, ROOM-02 쪽에는 여기 없는
> `keyword`·`counts`·`serverTime`·`soldPrice`·`endedAt`이 더 있다.
>
> 같은 일을 하는 엔드포인트를 둘 두면 정렬·집계·커서 정책을 두 곳에서 맞춰야 하고, 한쪽만 고치는
> 순간 조용히 갈린다. 프론트도 `shared/api/rooms.ts`에서 `/auction-rooms`만 호출한다.
>
> ROOM-02는 구현 완료(S15P11A704-457, MR !181). **이 문서를 근거로 새 엔드포인트를 만들지 말 것** —
> 카테고리별 조회가 필요하면 `GET /api/v1/auction-rooms?categoryId=...`를 쓴다.
> 원본 Notion 페이지는 이 레포에서 고칠 수 없어 `상태: 확정`으로 남아 있다.

> 아래 내용은 기존 Notion 명세의 별도 엔드포인트를 ROOM-02 경매방 목록 조회로
> 통합한 기록이다.
> 원본: [카테고리별 경매방 목록 조회](https://app.notion.com/293776ebfd6883a2ad218118355b52a0)

## 통합 결정

| 항목 | 값 |
| --- | --- |
| 기존 기능 ID | `ROOM-03` |
| 통합 대상 | `ROOM-02` 경매방 목록 조회 |
| 사용 엔드포인트 | `GET /api/v1/auction-rooms` |
| 기존 엔드포인트 | `GET /api/v1/auctions` |
| 기존 엔드포인트 상태 | 제공하지 않음 |
| 결정일 | 2026-07-30 |

카테고리별 조회는 별도 `GET /api/v1/auctions`를 만들지 않는다. 경매방 목록 조회의
선택 쿼리 파라미터인 `categoryId`를 사용한다.

최종 구현 계약은 [경매방 목록 조회](./room-02-get-auction-rooms-98a776eb.md)를 따른다.

## 요청 예시

카테고리를 선택한 경우:

```text
GET /api/v1/auction-rooms?status=LIVE&categoryId=1&size=20
```

카테고리를 선택하지 않은 경우:

```text
GET /api/v1/auction-rooms?status=LIVE&size=20
```

다음 페이지를 조회하는 경우:

```text
GET /api/v1/auction-rooms?status=LIVE&categoryId=1&size=20&cursor={nextCursor}
```

## 통합 규칙

- `categoryId`를 생략하면 전체 카테고리를 조회한다.
- `categoryId`가 있으면 `items`와 `counts` 모두 해당 카테고리 조건을 적용한다.
- 최초 요청에서는 `cursor`를 생략하고 다음 요청부터 응답의 `nextCursor`를 전달한다.
- `size`는 선택이며 기본값은 8, 최댓값은 20이다.
- 응답 항목은 `category` 객체 대신 표준 `categoryCode`를 반환한다.
- 카테고리 ID·코드·이름 매핑은 `GET /api/v1/auctions/categories` 응답을 사용한다.
- 응답 및 오류 형식은 ROOM-02 계약만을 기준으로 한다.

## 프론트 연동

프론트는 `GET /api/v1/auctions`를 호출하지 않는다. 기존
`getAuctionRooms()`를 사용하고 카테고리 선택 시 `categoryId`만 추가한다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [카테고리별 경매방 목록 조회 원본](https://app.notion.com/293776ebfd6883a2ad218118355b52a0)

