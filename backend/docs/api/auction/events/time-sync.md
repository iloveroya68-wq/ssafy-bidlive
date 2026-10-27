# TIME_SYNC

> Notion WebSocket 이벤트 명세에서 동기화한 로컬 이벤트 계약이다.  
> 원본: [TIME_SYNC](https://app.notion.com/bfd776ebfd6883b38b8301492822139b)

> **2026-08-01 로컬 확정.** 프론트 실연동 타입과 동일하게 `data`를
> `serverTime`, `endAt` 두 필드로 고정했다.

## 메타데이터

| 항목 | 값 |
| --- | --- |
| Event Type | `TIME_SYNC` |
| 저장소 도메인 | `auction` |
| 연관 기능 ID | `BID-05` |
| Destination | `/topic/auctions/{auctionId}` |
| 수신 대상 | 경매방 전체 |
| 상태 | 확정 |
| 우선순위 | High |
| 발생 시점 | LIVE 구독·재연결, 시작·연장 직후 또는 30초 주기 보정 시 |
| data 필드 | serverTime, endAt |

## 설명

서버 기준 시각과 마감 시각을 전달해 클라이언트 타이머 오차를 보정한다.

## 메시지

```json
{
  "type": "TIME_SYNC",
  "data": {
    "serverTime": "2026-07-20T21:12:44+09:00",
    "endAt": "2026-07-20T21:20:00+09:00"
  }
}
```

## 처리 규칙

- 두 필드는 UTC offset을 포함한 ISO 8601 DateTime이며 `null`을 허용하지 않는다.
- `status`, `stateVersion`, `auctionId`를 `data`에 포함하지 않는다.
- `WAITING`, `ENDED` 경매에는 발행하지 않는다.
- 동일 이벤트의 중복 수신을 허용하며 클라이언트는 최신 수신 값으로 덮어쓴다.
- `TIME_SYNC`는 서버 권위 상태를 변경하지 않는 `stateVersion` 비대상 이벤트다.
- 상세한 발생 조건, 계산식과 장애 처리는 [BID-05 서버 타이머 동기화](../bid-05-ws-topic-auctions-by-auctionid-4ef776eb.md)를 따른다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [TIME_SYNC 원본](https://app.notion.com/bfd776ebfd6883b38b8301492822139b)
