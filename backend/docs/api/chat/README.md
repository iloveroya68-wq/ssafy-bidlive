# chat API

`src/main/java/com/ssafy/bidlive/chat/` 도메인이 담당하는 로컬 API 계약이다.

## API 및 내부 계약

| 기능 ID | Method | Path / Destination | 상태 | 명세 |
| --- | --- | --- | --- | --- |
| `AI-05` | `INTERNAL` | 내부 처리 | 확정 | [LLM 비동기 문맥 판정](./ai-05-internal-internal-d3b776eb.md) |
| `AI-05` | `WS` | `/user/queue/moderation` | 확정 | [검열 경고·뮤트 통지](./ai-05-ws-user-queue-moderation-5bb776eb.md) |
| `CHAT-01` | `WS` | `/topic/auctions/{auctionId}/chat` | 확정 | [채팅 수신](./chat-01-ws-topic-auctions-by-auctionid-chat-533776eb.md) |
| `CHAT-01` | `WS` | `/app/auctions/{auctionId}/chat` | 확정 | [채팅 전송](./chat-01-ws-app-auctions-by-auctionid-chat-1eb776eb.md) |
| `CHAT-01` | `GET` | `/api/v1/auctions/{auctionId}/chats` | 확정 | [채팅 이력 조회](./chat-01-get-auctions-by-auctionid-chats.md) |
| `CHAT-01` | `DELETE` | `/api/v1/auctions/{auctionId}/chats/{messageId}` | 확정 | [채팅 메시지 삭제](./chat-01-delete-auctions-by-auctionid-chats-by-messageid.md) |
| `CHAT-02` | `POST` | `/api/v1/auctions/{auctionId}/chat/pin` | 확정 | [고정 공지 등록·교체](./chat-02-post-auctions-by-auctionid-chat-pin-516776eb.md) |
| `CHAT-02` | `DELETE` | `/api/v1/auctions/{auctionId}/chat/pin` | 확정 | [고정 공지 해제](./chat-02-delete-auctions-by-auctionid-chat-pin-b6b776eb.md) |
| `CHAT-03` | `INTERNAL` | 내부 처리 | 확정 | [룰 기반 채팅 필터](./chat-03-internal-internal-127776eb.md) |
| `CHAT-04` | `INTERNAL` | 내부 처리 | 확정 | [슬로우 모드](./chat-04-internal-internal-b00776eb.md) |
| `CHAT-06` | `WS` | `/app/auctions/{auctionId}/chat` | 확정 | [질문 채팅 전송](./chat-06-ws-app-auctions-by-auctionid-chat-781776eb.md) |
| `CHAT-06` | `WS` | `/topic/auctions/{auctionId}/questions` | 확정 | [질문 큐 수신](./chat-06-ws-topic-auctions-by-auctionid-questions-5ec776eb.md) |
| `CHAT-06` | `GET` | `/api/v1/auctions/{auctionId}/questions` | 확정 | [질문 큐 조회](./chat-06-get-auctions-by-auctionid-questions-e0c776eb.md) |
| `CHAT-06` | `DELETE` | `/api/v1/auctions/{auctionId}/questions/{questionId}` | 확정 | [질문 큐 항목 제거](./chat-06-delete-auctions-by-auctionid-questions-by-questionid-12a776eb.md) |

## 검증 문서

- [실시간 채팅 민감정보 노출 점검](./realtime-sensitive-data-security-audit.md)

## 출처

- [Notion 최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)

