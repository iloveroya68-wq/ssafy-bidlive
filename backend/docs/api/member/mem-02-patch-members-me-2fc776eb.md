# 내 프로필 수정

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [내 프로필 수정](https://app.notion.com/2fc776ebfd68837881d4019d605f20d7)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `MEM-02` |
| 저장소 도메인 | `member` |
| Notion 도메인 | 회원 |
| Method | `PATCH` |
| Path / Destination | `/api/v1/members/me` |
| 상태 | 확정 |
| 인증 | 필요 |
| 공개 범위 | User |
| 담당자 | 신희원 |
| 구현 | ✅ **구현됨** — `ProfileUpdateController` (`2026-07-30` 확인) |
| 우선순위 | Medium |
| 연결 구분 | Front↔BE |
| 의존 대상 | Front, PostgreSQL |
| 마지막 수정 | 2026-07-27 02:08:10Z |

## 요약

로그인 사용자의 서비스 닉네임을 수정한다.

## 개요
로그인 사용자의 서비스 닉네임을 수정한다. 신규 OAuth 회원의 최초 프로필 설정에도 동일 API를 재사용한다.
## Request
```javascript
PATCH /api/v1/members/me
Authorization: Bearer {accessToken}
Content-Type: application/json
```
```json
{
  "nickname": "새닉네임"
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
<td>`nickname`</td>
<td>String</td>
<td>필수</td>
<td>변경할 서비스 닉네임. 앞뒤 공백을 제거한 값을 저장한다.</td>
</tr>
</table>
## Response
### 200 OK
```json
{
  "memberId": 25,
  "nickname": "새닉네임",
  "onboardingRequired": false,
  "updatedAt": "2026-07-20T21:30:00+09:00"
}
```
## 검증 규칙
- 요청 Body와 `nickname`이 없으면 `EMPTY_UPDATE_REQUEST`를 반환한다.
- 앞뒤 공백을 제거한 닉네임은 1자 이상 30자 이하이며 한글·영문·숫자만 사용할 수 있다.
- 활성 금칙어가 대소문자 구분 없이 닉네임에 포함되면 저장할 수 없다.
- 다른 회원이 사용하는 닉네임은 영문 대소문자를 구분하지 않고 중복으로 판단한다.
- 현재 회원이 이미 사용하는 본인 닉네임은 다시 저장할 수 있다.
- 닉네임 중복 확인 API를 먼저 호출했더라도 저장 시 중복 검사를 다시 수행한다.
- 앞뒤 공백 제거 후 소문자로 변환한 정규화 닉네임을 별도 저장하고 DB 유니크 제약을 적용한다.
- 서로 다른 회원이 동시에 같은 닉네임을 저장하면 DB 유니크 제약으로 최종 차단한다.
- Access Token의 `memberId`를 기준으로 본인 프로필만 수정한다.
- `onboardingRequired=false`는 닉네임이 설정되고 현재 적용 중인 필수 온보딩 약관에 모두 동의한 경우에만 반환한다.
- 닉네임을 저장했더라도 필수 약관 동의가 완료되지 않았으면 `onboardingRequired=true`를 반환한다.
## 오류 응답
<table>
<tr>
<td>HTTP</td>
<td>코드</td>
<td>설명</td>
</tr>
<tr>
<td>400</td>
<td>`EMPTY_UPDATE_REQUEST`</td>
<td>수정할 필드가 없음</td>
</tr>
<tr>
<td>400</td>
<td>`INVALID_NICKNAME`</td>
<td>닉네임 형식 또는 금칙어 오류</td>
</tr>
<tr>
<td>401</td>
<td>`UNAUTHORIZED`</td>
<td>인증 실패</td>
</tr>
<tr>
<td>404</td>
<td>`MEMBER_NOT_FOUND`</td>
<td>회원이 존재하지 않음</td>
</tr>
<tr>
<td>409</td>
<td>`NICKNAME_ALREADY_EXISTS`</td>
<td>이미 사용 중인 닉네임</td>
</tr>
</table>
## 처리 흐름
```plain text
인증 사용자 확인
→ Request Body 검증
→ 닉네임 정규화·중복·금칙어 검사
→ Member 프로필 수정
→ 정규화 닉네임 DB 유니크 제약 검증
→ 현재 필수 약관 동의 여부 조회
→ 닉네임과 필수 약관 기준으로 onboardingRequired 재계산
→ 수정된 프로필 반환
```

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [내 프로필 수정 원본](https://app.notion.com/2fc776ebfd68837881d4019d605f20d7)

