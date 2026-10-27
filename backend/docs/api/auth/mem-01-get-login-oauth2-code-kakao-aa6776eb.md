# 카카오 OAuth Callback

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [카카오 OAuth Callback](https://app.notion.com/aa6776ebfd6882e3a3d081bcb850f8af)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `MEM-01` |
| 저장소 도메인 | `auth` |
| Notion 도메인 | 회원 |
| Method | `GET` |
| Path / Destination | `/login/oauth2/code/kakao` |
| 상태 | 확정 |
| 인증 | 불필요 |
| 공개 범위 | Public |
| 담당자 | 신희원 |
| 구현 | ✅ **구현됨** — Spring Security `oauth2Login` (`SecurityConfig`) — 프레임워크 제공 (`2026-07-30` 확인) |
| 우선순위 | High |
| 연결 구분 | Front↔BE, BE↔BE, BE↔Redis, BE 내부 |
| 의존 대상 | Front, Redis, PostgreSQL |
| 마지막 수정 | 2026-07-27 02:08:12Z |

## 요약

카카오 인증 후 브라우저가 Callback으로 이동하면 사용자 정보를 확인하고, 기존 회원 로그인 또는 신규 회원 자동 가입 후 일회용 로그인 코드를 발급한다.

## 비고

카카오 인증 후 브라우저가 이동하는 OAuth Callback. 프론트 직접 호출 없음

## 개요
카카오 인증이 끝난 뒤 브라우저가 이동하는 OAuth Callback 엔드포인트다. Spring Security OAuth2 Client가 전달된 Authorization Code를 처리하고 카카오 계정과 우리 서비스의 `Member`를 연결한다.
기존 소셜 계정이면 연결된 회원으로 로그인하고, 처음 확인된 소셜 계정이면 별도의 회원가입 API 없이 `Member`, `SocialAccount`, 거래 포인트 지갑과 베팅 포인트 지갑을 자동 생성한다.
> 이 경로는 프론트가 직접 호출하는 일반 REST API가 아니다. 카카오가 브라우저에 `302 Redirect`를 반환하고, 브라우저가 이 Callback 주소로 `GET` 요청을 보낸다.
이때 front에서는 redirect 된 url에서 code를 바탕으로 `OAuth로그인 코드 교환` api를 통해 accessToken을 발급 받는다.
## Request
### 인증 성공
```javascript
GET /login/oauth2/code/kakao?code={authorizationCode}&state={state}
```
### 인증 실패 또는 사용자 동의 거부
```javascript
GET /login/oauth2/code/kakao?error={error}&error_description={description}&state={state}
```
### Query Parameter
<table>
<tr>
<td>필드</td>
<td>타입</td>
<td>필수</td>
<td>설명</td>
</tr>
<tr>
<td>`code`</td>
<td>String</td>
<td>조건부 필수</td>
<td>카카오 인증 성공 시 전달되는 Authorization Code. `error`와 동시에 전달되지 않는다.</td>
</tr>
<tr>
<td>`state`</td>
<td>String</td>
<td>필수</td>
<td>OAuth 요청 시작 시 생성한 값. 로그인 요청 위조 방지를 위해 서버에 저장된 값과 비교한다.</td>
</tr>
<tr>
<td>`error`</td>
<td>String</td>
<td>조건부 필수</td>
<td>사용자 동의 거부 또는 카카오 인증 실패 시 전달되는 오류 코드</td>
</tr>
<tr>
<td>`error_description`</td>
<td>String</td>
<td>선택</td>
<td>카카오가 전달한 오류 상세 설명</td>
</tr>
</table>
## Response
### 302 Found — 인증 및 회원 처리 성공
```javascript
HTTP/1.1 302 Found
Location: {frontendBaseUrl}/oauth/callback?code={oneTimeLoginCode}
```
- `oneTimeLoginCode`는 우리 서버가 새로 발급한 일회용 코드다.
- 카카오 Authorization Code나 우리 서비스의 Access Token·Refresh Token을 프론트 URL에 직접 포함하지 않는다.
- 프론트는 전달받은 일회용 코드를 `POST /api/v1/auth/oauth2/token`으로 교환한다.
### 302 Found — 인증 또는 내부 처리 실패
```javascript
HTTP/1.1 302 Found
Location: {frontendBaseUrl}/oauth/callback?error={serviceErrorCode}
```
프론트는 `error` 값을 기준으로 로그인 실패 화면 또는 재시도 안내를 표시한다.
## 신규·기존 사용자 판정
카카오는 사용자가 우리 서비스 회원인지 알려주지 않는다. Callback 처리 중 카카오 사용자 정보를 조회한 뒤, 우리 DB의 `SocialAccount` 존재 여부로 판단한다.
```sql
SELECT member_id
FROM social_account
WHERE provider = 'KAKAO'
  AND provider_user_id = :providerUserId;
```
<table>
<tr>
<td>조회 결과</td>
<td>처리</td>
</tr>
<tr>
<td>SocialAccount 존재</td>
<td>연결된 `Member`를 기존 사용자로 로그인 처리</td>
</tr>
<tr>
<td>SocialAccount 없음</td>
<td>`Member`, `SocialAccount`, `TRADE` 지갑, `BETTING` 지갑을 생성한 뒤 로그인 처리</td>
</tr>
</table>
## 검증 규칙
- 전달된 `state`가 OAuth 로그인 시작 시 서버가 생성한 값과 일치해야 한다.
- 성공 요청에는 `code`가 있어야 하며, 실패 요청에는 `error`가 포함될 수 있다.
- Authorization Code는 한 번만 사용하며 재사용할 수 없다.
- 카카오 사용자 식별 기준은 이메일이 아니라 `provider + providerUserId` 조합이다.
- `provider + providerUserId`에는 DB 유니크 제약을 적용한다.
- 신규 회원과 연관 데이터 생성은 하나의 DB 트랜잭션으로 처리한다.
- 일회용 로그인 코드는 Redis에 짧은 TTL로 저장하고 한 번 사용하면 즉시 삭제한다.
## 오류 응답
<table>
<tr>
<td>HTTP</td>
<td>코드</td>
<td>설명</td>
</tr>
<tr>
<td>400</td>
<td>`INVALID_OAUTH_STATE`</td>
<td>`state`가 누락되었거나 서버 저장값과 일치하지 않음</td>
</tr>
<tr>
<td>400</td>
<td>`OAUTH_ACCESS_DENIED`</td>
<td>사용자가 카카오 로그인 또는 필수 정보 제공에 동의하지 않음</td>
</tr>
<tr>
<td>401</td>
<td>`OAUTH_AUTHENTICATION_FAILED`</td>
<td>Authorization Code 교환 또는 카카오 사용자 인증 실패</td>
</tr>
<tr>
<td>409</td>
<td>`SOCIAL_ACCOUNT_CONFLICT`</td>
<td>동일 카카오 계정 연결 정보가 충돌함</td>
</tr>
<tr>
<td>500</td>
<td>`OAUTH_MEMBER_CREATION_FAILED`</td>
<td>신규 Member 또는 연관 지갑 생성 실패</td>
</tr>
<tr>
<td>503</td>
<td>`OAUTH_PROVIDER_UNAVAILABLE`</td>
<td>카카오 인증·사용자 정보 API를 사용할 수 없음</td>
</tr>
</table>
> 브라우저 기반 OAuth 흐름에서는 위 오류를 JSON으로 직접 보여주기보다 프론트 Callback 주소로 `302 Redirect`하면서 서비스 오류 코드를 전달할 수 있다.
## 처리 흐름
```plain text
브라우저가 Callback 요청
→ state 검증
→ Authorization Code를 카카오 Access Token으로 교환
→ 카카오 사용자 정보 조회
→ provider=KAKAO, providerUserId 추출
→ SocialAccount 조회
   ├─ 존재: 연결된 Member 조회
   └─ 없음: Member + SocialAccount + TRADE/BETTING 지갑 생성
→ 우리 서비스용 일회용 loginCode 생성
→ Redis에 짧은 TTL로 저장
→ 프론트 /oauth/callback?code={loginCode}로 302 Redirect
→ 프론트가 POST /api/v1/auth/oauth2/token 호출
```
## 보안·저장 원칙
- 카카오 Access Token은 우리 서비스 API의 인증 토큰으로 사용하지 않는다.
- 로그인 처리에만 카카오 사용자 정보를 조회한다면 카카오 Access Token을 DB에 영구 저장하지 않는다.
- Access Token과 Refresh Token을 URL Query Parameter에 포함하지 않는다.
- 일회용 로그인 코드에는 회원 정보나 JWT 원문을 직접 담지 않는다.
- 신규 회원 생성 중 하나라도 실패하면 `Member`, `SocialAccount`, 지갑 생성을 모두 롤백한다.
- 브라우저가 Callback을 요청하지만, Authorization Code 교환과 카카오 사용자 정보 조회는 우리 백엔드와 카카오 서버 간 통신으로 수행한다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [카카오 OAuth Callback 원본](https://app.notion.com/aa6776ebfd6882e3a3d081bcb850f8af)

