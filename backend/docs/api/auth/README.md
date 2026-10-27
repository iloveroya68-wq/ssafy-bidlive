# auth API

`src/main/java/com/ssafy/bidlive/auth/` 도메인이 담당하는 로컬 API 계약이다.

## API 및 내부 계약

| 기능 ID | Method | Path / Destination | 상태 | 명세 |
| --- | --- | --- | --- | --- |
| `MEM-01` | `POST` | `/api/v1/auth/refresh` | 확정 | [Access Token 재발급](./mem-01-post-auth-refresh-1a0776eb.md) |
| `MEM-01` | `POST` | `/api/v1/auth/oauth2/token` | 확정 | [OAuth 로그인 코드 교환](./mem-01-post-auth-oauth2-token-769776eb.md) |
| `MEM-01` | `INTERNAL` | 내부 처리 | 확정 | [OAuth 회원 자동 가입 처리](./mem-01-internal-internal-951776eb.md) |
| `MEM-01` | `POST` | `/api/v1/auth/logout` | 확정 | [로그아웃](./mem-01-post-auth-logout-fbb776eb.md) |
| `MEM-01` | `GET` | `/login/oauth2/code/kakao` | 확정 | [카카오 OAuth Callback](./mem-01-get-login-oauth2-code-kakao-aa6776eb.md) |
| `MEM-01` | `GET` | `/oauth2/authorization/kakao` | 확정 | [카카오 OAuth 로그인 시작](./mem-01-get-oauth2-authorization-kakao-203776eb.md) |

## 출처

- [Notion 최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)

