# 닉네임 중복 확인

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [닉네임 중복 확인](https://app.notion.com/0de776ebfd6883dfb51c017b886bbd43)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `MEM-02` |
| 저장소 도메인 | `member` |
| Notion 도메인 | 회원 |
| Method | `GET` |
| Path / Destination | `/api/v1/members/nickname-availability` |
| 상태 | 확정 |
| 인증 | 필요 |
| 공개 범위 | User |
| 담당자 | 신희원 |
| 구현 | ✅ **구현됨** — `NicknameAvailabilityController` (`2026-07-30` 확인) |
| 우선순위 | Medium |
| 연결 구분 | Front↔BE |
| 의존 대상 | Front, PostgreSQL |
| 마지막 수정 | 2026-07-27 02:08:10Z |

## 요약

입력한 닉네임의 형식과 중복 여부를 확인한다.

## 비고

사전 확인용 API이며 최종 저장 시 DB UNIQUE 제약으로 다시 검증

## 개요
프로필 최초 설정 또는 닉네임 변경 전에 입력한 닉네임의 사용 가능 여부를 확인한다.
> 이 API의 결과는 사용자 편의를 위한 사전 확인이다. 실제 중복 방지는 프로필 저장 시 DB 유니크 제약으로 최종 검증한다.
## Request
```javascript
GET /api/v1/members/nickname-availability?nickname={nickname}
Authorization: Bearer {accessToken}
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
<td>`nickname`</td>
<td>String</td>
<td>필수</td>
<td>사용 가능 여부를 확인할 닉네임</td>
</tr>
</table>
## Response
### 200 OK — 사용 가능
```json
{
  "nickname": "택림",
  "available": true,
  "reason": null
}
```
### 200 OK — 사용 불가
```json
{
  "nickname": "희원",
  "available": false,
  "reason": "ALREADY_EXISTS"
}
```
### Response Field
<table>
<tr>
<td>필드</td>
<td>타입</td>
<td>설명</td>
</tr>
<tr>
<td>`nickname`</td>
<td>String</td>
<td>검사한 닉네임</td>
</tr>
<tr>
<td>`available`</td>
<td>Boolean</td>
<td>닉네임 사용 가능 여부</td>
</tr>
<tr>
<td>`reason`</td>
<td>String / null</td>
<td>사용 불가 사유. `ALREADY_EXISTS`, `INVALID_FORMAT`, `PROHIBITED_WORD`</td>
</tr>
</table>
## 검증 규칙
- 앞뒤 공백을 제거한 값을 기준으로 검사한다.
- 닉네임은 1자 이상 30자 이하이며 한글·영문·숫자만 사용할 수 있다.
- 활성 금칙어가 대소문자 구분 없이 닉네임에 포함되면 사용할 수 없다.
- 대소문자를 구분하지 않고 중복 여부를 판단한다.
- 현재 사용자가 이미 사용 중인 본인 닉네임은 사용 가능으로 처리할 수 있다.
- 사전 확인 후 다른 사용자가 같은 닉네임을 먼저 저장할 수 있으므로 저장 API에서도 중복 검사를 반복한다.
## 오류 응답
<table>
<tr>
<td>HTTP</td>
<td>코드</td>
<td>설명</td>
</tr>
<tr>
<td>400</td>
<td>`NICKNAME_REQUIRED`</td>
<td>닉네임이 누락되거나 공백만 입력됨</td>
</tr>
<tr>
<td>401</td>
<td>`UNAUTHORIZED`</td>
<td>인증 실패</td>
</tr>
</table>
## 처리 흐름
```plain text
인증 사용자 확인
→ nickname 정규화
→ 형식·금칙어 검사
→ 정규화 닉네임으로 Member 중복 조회
→ available과 reason 반환
```
## 정합성 원칙
프로필 저장 API에서 동일 검증을 다시 수행하고, 정규화 닉네임에 DB 유니크 인덱스를 적용한다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [닉네임 중복 확인 원본](https://app.notion.com/0de776ebfd6883dfb51c017b886bbd43)

