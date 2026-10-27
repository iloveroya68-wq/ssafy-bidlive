# 카카오 OAuth 로그인 시작

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [카카오 OAuth 로그인 시작](https://app.notion.com/203776ebfd6882cdac0a81ed609e65d0)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `MEM-01` |
| 저장소 도메인 | `auth` |
| Notion 도메인 | 회원 |
| Method | `GET` |
| Path / Destination | `/oauth2/authorization/kakao` |
| 상태 | 확정 |
| 인증 | 불필요 |
| 공개 범위 | Public |
| 담당자 | 신희원 |
| 구현 | ✅ **구현됨** — Spring Security `oauth2Login` (`SecurityConfig`) — 프레임워크 제공 (`2026-07-30` 확인) |
| 우선순위 | High |
| 연결 구분 | Front↔BE |
| 의존 대상 | Front |
| 마지막 수정 | 2026-07-27 02:08:12Z |

## 요약

사용자를 카카오 인증·동의 화면으로 리다이렉트한다. 이메일·비밀번호를 직접 받지 않는다.

## 비고

Spring Security OAuth2 Client 로그인 진입점

## 개요
카카오 OAuth 로그인을 시작한다. 프론트는 이메일과 비밀번호를 직접 전송하지 않고 이 경로로 브라우저를 이동시킨다.
→ 우리 api에 요청하면 내부적으로 OAuth필터를 거쳐 카카오 인증 URL을 생성하고 client를 카카오 로그인 화면으로 redirect 시킴
## Request
```javascript
GET /oauth2/authorization/kakao
```
### Path Variable
없음.
### Query Parameter
없음. OAuth 요청의 `state`와 redirect URI는 Spring Security OAuth2 Client가 생성·관리한다.
### Request Body
요청 본문 없음.
## Response
### 302 Found
카카오 인증·동의 화면으로 리다이렉트한다.
## 검증 규칙
- 카카오 OAuth Client 설정이 활성화되어 있어야 한다.
- 등록된 redirect URI와 서버 설정이 일치해야 한다.
- 이메일·비밀번호 기반 로그인 요청은 받지 않는다.
## 오류 응답
<table>
<tr>
<td>HTTP</td>
<td>코드</td>
<td>설명</td>
</tr>
<tr>
<td>400</td>
<td>`OAUTH_REQUEST_INVALID`</td>
<td>OAuth 요청 생성에 필요한 설정이 올바르지 않음</td>
</tr>
<tr>
<td>503</td>
<td>`OAUTH_PROVIDER_UNAVAILABLE`</td>
<td>카카오 인증 서버에 연결할 수 없음</td>
</tr>
</table>
## 처리 흐름
```plain text
프론트 로그인 버튼 클릭
→ 백엔드 OAuth 진입점 요청
→ state 및 카카오 인증 URL 생성
→ 카카오 인증·동의 화면으로 302 Redirect
→ 인증 완료 후 카카오 OAuth Callback 호출
```
## 공개 원칙
이 API는 JSON 응답 API가 아니라 브라우저 리다이렉트 진입점이다. 자체 이메일·비밀번호 회원가입 API는 사용하지 않는다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [카카오 OAuth 로그인 시작 원본](https://app.notion.com/203776ebfd6882cdac0a81ed609e65d0)

