# 로그아웃

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [로그아웃](https://app.notion.com/fbb776ebfd6883c2ba2f811bcc5d8d7a)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `MEM-01` |
| 저장소 도메인 | `auth` |
| Notion 도메인 | 회원 |
| Method | `POST` |
| Path / Destination | `/api/v1/auth/logout` |
| 상태 | 확정 |
| 인증 | 필요 |
| 공개 범위 | User |
| 담당자 | 신희원 |
| 구현 | ✅ **구현됨** — `LogoutController` (`2026-07-30` 확인) |
| 우선순위 | High |
| 연결 구분 | Front↔BE, BE 내부 |
| 의존 대상 | Front, Redis |
| 마지막 수정 | 2026-07-27 02:08:10Z |

## 요약

Refresh Token을 폐기하고 인증 쿠키를 삭제해 로그인 세션을 종료한다.

## 비고

Access Token은 짧은 만료 시간을 사용

## 개요
현재 로그인 세션의 Refresh Token을 폐기하고 브라우저의 인증 쿠키를 삭제한다.
## Request
```javascript
POST /api/v1/auth/logout
Authorization: Bearer {accessToken}
Cookie: refreshToken={refreshToken}
```
### Header / Cookie
<table>
<tr>
<td>이름</td>
<td>타입</td>
<td>필수</td>
<td>설명</td>
</tr>
<tr>
<td>`Authorization`</td>
<td>String</td>
<td>선택</td>
<td>유효한 Access Token이 있으면 회원 식별에 사용</td>
</tr>
<tr>
<td>`refreshToken`</td>
<td>String</td>
<td>필수</td>
<td>폐기할 현재 로그인 세션의 Refresh Token 쿠키</td>
</tr>
</table>
### Request Body
요청 본문 없음.
## Response
### 204 No Content
응답 본문 없음.
```javascript
Set-Cookie: refreshToken=; Max-Age=0; HttpOnly; Secure; SameSite=Lax; Path=/api/v1/auth
```
## 검증·처리 규칙
- Refresh Token 또는 세션 식별자를 기준으로 서버 저장 정보를 제거한다.
- Access Token이 만료됐더라도 Refresh Token 쿠키 삭제와 세션 폐기는 처리할 수 있다.
- 이미 만료되거나 폐기된 세션에 대한 요청도 멱등하게 `204`로 처리할 수 있다.
- 프론트는 응답 후 보관 중인 Access Token을 제거한다.
## 오류 응답
<table>
<tr>
<td>HTTP</td>
<td>코드</td>
<td>설명</td>
</tr>
<tr>
<td>400</td>
<td>`INVALID_LOGOUT_REQUEST`</td>
<td>쿠키 또는 요청 형식 오류</td>
</tr>
<tr>
<td>500</td>
<td>`LOGOUT_FAILED`</td>
<td>세션 폐기 처리 중 서버 오류</td>
</tr>
</table>
## 처리 흐름
```plain text
Refresh Token 쿠키 수신
→ 서버 세션 조회
→ 세션 또는 토큰 정보 삭제
→ Refresh Token 쿠키 만료
→ 204 No Content 반환
```

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [로그아웃 원본](https://app.notion.com/fbb776ebfd6883c2ba2f811bcc5d8d7a)

