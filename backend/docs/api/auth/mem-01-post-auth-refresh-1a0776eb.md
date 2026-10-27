# Access Token 재발급

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [Access Token 재발급](https://app.notion.com/1a0776ebfd68822893d2819e05291e6f)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `MEM-01` |
| 저장소 도메인 | `auth` |
| Notion 도메인 | 회원 |
| Method | `POST` |
| Path / Destination | `/api/v1/auth/refresh` |
| 상태 | 확정 |
| 인증 | 불필요 |
| 공개 범위 | Public |
| 담당자 | 신희원 |
| 구현 | ✅ **구현됨** — `AccessTokenRefreshController` (`2026-07-30` 확인) |
| 우선순위 | High |
| 연결 구분 | Front↔BE, BE 내부 |
| 의존 대상 | Front, Redis, PostgreSQL |
| 마지막 수정 | 2026-07-27 02:08:10Z |

## 요약

HttpOnly 쿠키의 Refresh Token을 검증하고 새로운 Access Token을 발급한다.

## 비고

Request Body에 Refresh Token을 받지 않음

## 개요
브라우저가 자동 전송한 HttpOnly Refresh Token 쿠키를 검증하고 새로운 Access Token을 발급한다.
## Request
```javascript
POST /api/v1/auth/refresh
Cookie: refreshToken={refreshToken}
```
### Cookie
<table>
<tr>
<td>이름</td>
<td>타입</td>
<td>필수</td>
<td>설명</td>
</tr>
<tr>
<td>`refreshToken`</td>
<td>String</td>
<td>필수</td>
<td>로그인 시 HttpOnly 쿠키로 발급된 Refresh Token</td>
</tr>
</table>
### Request Body
요청 본문 없음. Refresh Token을 JSON Body나 Query Parameter로 전달하지 않는다.
## Response
### 200 OK
```json
{
  "tokenType": "Bearer",
  "accessToken": "eyJ...",
  "expiresIn": 1800
}
```
본 구현은 토큰 회전 정책을 적용하므로 새 Refresh Token 쿠키도 함께 발급한다.
## 검증 규칙
- Refresh Token의 서명과 만료 시간을 검증한다.
- 서버에 저장된 세션 또는 토큰 정보와 일치해야 한다.
- 토큰에 연결된 Member가 존재하고 사용 가능한 상태여야 한다.
- 토큰 회전 시 기존 Refresh Token은 폐기한다.

### 회전 유예 창 (S15P11A704-693)

회전은 원자적이라 **같은 Refresh Token을 든 요청이 겹치면 한쪽만 통과한다.** 진 쪽의 실패는
세션이 끊긴 것이 아니라 이긴 쪽이 정상적으로 회전시킨 상태인데, 그대로 401을 주면 클라이언트가
그것을 로그아웃으로 확정한다. 브라우저 탭이 둘 이상이면 실제로 겹친다 — 프론트의 재발급은
탭 안에서만 single-flight이고 탭 사이에는 조율이 없다.

그래서 회전 직후 **10초 동안**은 방금 폐기된 토큰으로 들어온 요청도 받는다.

- 그때 발급했던 **같은 Refresh Token**을 그대로 돌려준다 (새로 회전시키지 않는다 — 회전 사슬이
  둘로 갈라지면 어느 쪽 쿠키가 살아남는지가 응답 도착 순서에 달린다)
- Access Token만 새로 발급한다
- 회원 상태 검증(`MEMBER_UNAVAILABLE`)은 그대로 적용한다
- 유예 창 밖이면 종전대로 `REFRESH_TOKEN_INVALID`다

⚠️ 유예 창 동안에는 이미 회전된 토큰이 다시 통하므로, 탈취된 토큰도 그 몇 초 안에는 재사용된다.
창을 짧게 두고 새 토큰을 발급하지 않는 것으로 범위를 제한한다.

## 오류 응답
<table>
<tr>
<td>HTTP</td>
<td>코드</td>
<td>설명</td>
</tr>
<tr>
<td>401</td>
<td>`REFRESH_TOKEN_MISSING`</td>
<td>Refresh Token 쿠키가 없음</td>
</tr>
<tr>
<td>401</td>
<td>`REFRESH_TOKEN_INVALID`</td>
<td>서명 또는 서버 저장값 검증 실패</td>
</tr>
<tr>
<td>401</td>
<td>`REFRESH_TOKEN_EXPIRED`</td>
<td>Refresh Token 만료</td>
</tr>
<tr>
<td>403</td>
<td>`MEMBER_UNAVAILABLE`</td>
<td>정지 또는 탈퇴 등 사용할 수 없는 회원</td>
</tr>
</table>
## 처리 흐름
```plain text
Refresh Token 쿠키 수신
→ 토큰 서명·만료 검증
→ 서버 세션 조회
→ 회원 상태 확인
→ 새 Access Token 발급
→ 기존 Refresh Token 폐기 및 새 Refresh Token 회전
→ 토큰 응답 반환
```

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [Access Token 재발급 원본](https://app.notion.com/1a0776ebfd68822893d2819e05291e6f)

