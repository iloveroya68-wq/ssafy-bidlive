# OAuth 로그인 코드 교환

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [OAuth 로그인 코드 교환](https://app.notion.com/769776ebfd6882ecb49f010700d14bb4)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `MEM-01` |
| 저장소 도메인 | `auth` |
| Notion 도메인 | 회원 |
| Method | `POST` |
| Path / Destination | `/api/v1/auth/oauth2/token` |
| 상태 | 확정 |
| 인증 | 불필요 |
| 공개 범위 | Public |
| 담당자 | 신희원 |
| 구현 | ✅ **구현됨** — `OAuthTokenController` (`2026-07-30` 확인) |
| 우선순위 | High |
| 연결 구분 | Front↔BE, BE 내부 |
| 의존 대상 | Front, Redis, PostgreSQL |
| 마지막 수정 | 2026-07-27 02:08:10Z |

## 요약

OAuth Callback에서 발급한 일회용 로그인 코드를 자체 JWT Access Token으로 교환한다.

## 비고

Refresh Token은 HttpOnly 쿠키로 발급

## 개요
OAuth Callback에서 발급된 일회용 로그인 코드를 우리 서비스의 JWT로 교환한다. Access Token은 응답 본문으로 반환하고 Refresh Token은 HttpOnly 쿠키로 발급한다.
## Request
```javascript
POST /api/v1/auth/oauth2/token
Content-Type: application/json
```
```json
{
  "loginCode": "one-time-code"
}
```
### Request Body
<table>
<tr>
<td>필드</td>
<td>타입</td>
<td>필수</td>
<td>설명</td>
</tr>
<tr>
<td>`loginCode`</td>
<td>String</td>
<td>필수</td>
<td>OAuth Callback 이후 발급된 일회용 로그인 코드</td>
</tr>
</table>
## Response
### 200 OK
```json
{
  "tokenType": "Bearer",
  "accessToken": "eyJ...",
  "expiresIn": 1800,
  "member": {
    "memberId": 25,
    "nickname": null,
    "onboardingRequired": true
  }
}
```
```javascript
Set-Cookie: refreshToken=...; HttpOnly; Secure; SameSite=Lax; Path=/api/v1/auth
```
## 검증 규칙
- `loginCode`는 Redis에 존재하고 만료되지 않은 코드여야 한다.
- 로그인 코드에 연결된 Member가 존재하고 사용 가능한 상태여야 한다.
- 코드 교환 성공 시 해당 코드를 즉시 삭제해 재사용을 막는다.
- 신규 회원의 `nickname`이 비어 있으면 `onboardingRequired=true`를 반환한다.
- 기존 회원 또는 닉네임 설정이 완료된 회원은 `onboardingRequired=false`를 반환한다.
- `onboardingRequired`는 별도 DB 컬럼이 아니라 현재 회원 정보로 계산할 수 있다.
## 오류 응답
<table>
<tr>
<td>HTTP</td>
<td>코드</td>
<td>설명</td>
</tr>
<tr>
<td>400</td>
<td>`INVALID_LOGIN_CODE`</td>
<td>로그인 코드 형식이 잘못됨</td>
</tr>
<tr>
<td>404</td>
<td>`MEMBER_NOT_FOUND`</td>
<td>코드에 연결된 회원이 존재하지 않음</td>
</tr>
<tr>
<td>409</td>
<td>`LOGIN_CODE_ALREADY_USED`</td>
<td>이미 사용된 로그인 코드</td>
</tr>
<tr>
<td>410</td>
<td>`LOGIN_CODE_EXPIRED`</td>
<td>로그인 코드가 만료됨</td>
</tr>
</table>
## 처리 흐름
```plain text
loginCode 수신
→ Redis에서 로그인 코드 조회
→ 연결된 memberId와 회원 상태 확인
→ 로그인 코드 즉시 삭제
→ Access Token과 Refresh Token 생성
→ Refresh Token 쿠키 설정
→ nickname 존재 여부로 onboardingRequired 계산
→ Access Token과 회원 요약 정보 반환
```
## 보안 원칙
- Access Token과 Refresh Token을 리다이렉트 URL에 포함하지 않는다.
- 프론트는 `loginCode`를 해석하지 않고 교환 API에 그대로 전달한다.
- 로그인 코드는 짧은 TTL과 1회 사용 정책을 적용한다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [OAuth 로그인 코드 교환 원본](https://app.notion.com/769776ebfd6882ecb49f010700d14bb4)

