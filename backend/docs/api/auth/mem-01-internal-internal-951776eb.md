# OAuth 회원 자동 가입 처리

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [OAuth 회원 자동 가입 처리](https://app.notion.com/951776ebfd68830da05781921f85fdae)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `MEM-01` |
| 저장소 도메인 | `auth` |
| Notion 도메인 | 회원 |
| Method | `INTERNAL` |
| Path / Destination | 내부 처리 |
| 상태 | 확정 |
| 인증 | 불필요 |
| 공개 범위 | Internal |
| 담당자 | 신희원 |
| 구현 | ✅ **구현됨** — `KakaoOAuth2AuthenticationSuccessHandler` — OAuth 콜백에서 신규 회원 자동 가입 (`2026-07-30` 확인) |
| 우선순위 | High |
| 연결 구분 | BE 내부 |
| 의존 대상 | PostgreSQL |
| 마지막 수정 | 2026-07-27 02:08:11Z |

## 요약

카카오 소셜 계정이 처음 확인되면 Member, SocialAccount, 거래·베팅 포인트 지갑을 자동 생성한다.

## 비고

카카오 Callback 내부 처리. 프론트 직접 호출 없음

## 개요
카카오 OAuth Callback 내부에서 신규 사용자를 자동 가입 처리한다. 프론트가 직접 호출하는 API가 아니라 서비스 계층의 내부 처리다.
## 호출 주체
```plain text
카카오 OAuth Callback
```
## 입력
<table>
<tr>
<td>필드</td>
<td>타입</td>
<td>필수</td>
<td>설명</td>
</tr>
<tr>
<td>`provider`</td>
<td>Enum</td>
<td>필수</td>
<td>OAuth 제공자. 현재 `KAKAO`</td>
</tr>
<tr>
<td>`providerUserId`</td>
<td>String</td>
<td>필수</td>
<td>카카오 앱 기준 고유 사용자 ID</td>
</tr>
<tr>
<td>`email`</td>
<td>String</td>
<td>선택</td>
<td>사용자가 제공에 동의한 경우에만 저장</td>
</tr>
<tr>
<td>`nickname`</td>
<td>String</td>
<td>선택</td>
<td>카카오 프로필 닉네임</td>
</tr>
<tr>
<td>`profileImageUrl`</td>
<td>String</td>
<td>선택</td>
<td>카카오 프로필 이미지 URL</td>
</tr>
</table>
## 출력
```json
{
  "memberId": 25,
  "isNewMember": true,
  "onboardingRequired": true
}
```
## 검증 규칙
- `provider + providerUserId` 조합은 유일해야 한다. (같은 sns 계정이면 providerUserId는 유일하다)
- 이메일은 회원 식별 키로 사용하지 않는다.
- Member, SocialAccount, 거래 포인트 지갑, 베팅 포인트 지갑은 하나의 DB 트랜잭션으로 생성한다.
- 신규 회원은 우리 서비스 닉네임을 직접 설정하기 전까지 `nickname=null`로 생성할 수 있다.
- `onboardingRequired`는 별도 저장값이 아니라 `nickname` 존재 여부로 계산한다.
- 카카오 닉네임은 프로필 설정 화면의 기본 입력값으로만 사용할 수 있으며, 우리 서비스 닉네임으로 자동 확정하지 않는다.
- 신규 회원은 가입 보너스 정책에 따라 베팅 포인트 1,000점을 지급하고 지갑 생성과 함께 GRANT 원장을 기록한다.
## 오류 응답
<table>
<tr>
<td>코드</td>
<td>설명</td>
</tr>
<tr>
<td>`SOCIAL_ACCOUNT_ALREADY_EXISTS`</td>
<td>동일 소셜 계정이 이미 다른 처리에서 생성됨</td>
</tr>
<tr>
<td>`OAUTH_PROFILE_INSUFFICIENT`</td>
<td>필수 OAuth 식별 정보가 부족함</td>
</tr>
<tr>
<td>`MEMBER_CREATION_FAILED`</td>
<td>회원 또는 연관 데이터 생성 실패</td>
</tr>
</table>
## 처리 흐름
```plain text
SocialAccount 조회 결과 없음
→ Member 생성
→ SocialAccount 생성 및 Member 연결
→ TRADE 지갑 생성
→ BETTING 지갑 생성
→ 가입 보너스 1,000점 지급 및 GRANT 원장 생성
→ 트랜잭션 커밋
→ memberId와 onboardingRequired 반환
```
## 정합성 원칙
중간 단계에서 하나라도 실패하면 전체 생성 작업을 롤백한다. 동시 최초 로그인으로 중복 회원이 생성되지 않도록 DB 유니크 제약을 최종 방어선으로 사용한다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [OAuth 회원 자동 가입 처리 원본](https://app.notion.com/951776ebfd68830da05781921f85fdae)

