# member API

`src/main/java/com/ssafy/bidlive/member/` 도메인이 담당하는 로컬 API 계약이다.

## API 및 내부 계약

| 기능 ID | Method | Path / Destination | 상태 | 명세 |
| --- | --- | --- | --- | --- |
| `MEM-03` | `GET` | `/api/v1/members/me/settlement-account` | 확정 | [정산 계좌 조회](./mem-03-get-members-me-settlement-account.md) |
| `MEM-03` | `PUT` | `/api/v1/members/me/settlement-account` | 확정 | [정산 계좌 등록·변경](./mem-03-put-members-me-settlement-account.md) |
| `MEM-02` | `GET` | `/api/v1/members/me/auctions/hosted` | 확정 | [개최한 경매 조회](./mem-02-get-members-me-auctions-hosted-bdc776eb.md) |
| `MEM-02` | `GET` | `/api/v1/members/me/auctions/won` | 확정 | [낙찰 받은 경매 조회](./mem-02-get-members-me-auctions-won-a2d776eb.md) |
| `MEM-02` | `PATCH` | `/api/v1/members/me` | 확정 | [내 프로필 수정](./mem-02-patch-members-me-2fc776eb.md) |
| `MEM-02` | `GET` | `/api/v1/members/nickname-availability` | 확정 | [닉네임 중복 확인](./mem-02-get-members-nickname-availability-0de776eb.md) |
| `MEM-02` | `POST` | `/api/v1/members/me/terms-agreements` | 확정 | [약관 동의 API](./mem-02-post-members-me-terms-agreements-662776eb.md) |
| `MEM-02` | `GET` | `/api/v1/terms?purpose=ONBOARDING` | 확정 | [약관 조회 API](./mem-02-get-terms-purpose-onboarding-3af776eb.md) |
| `MEM-02` | `GET` | `/api/v1/members/me/auctions/participated` | 확정 | [참여한 경매 조회](./mem-02-get-members-me-auctions-participated-87d776eb.md) |
| `MEM-02` | `GET` | `/api/v1/members/me` | 확정 | [프로필 조회](./mem-02-get-members-me-58a776eb.md) |

## 출처

- [Notion 최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)

