# 약관 동의 API

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [약관 동의 API](https://app.notion.com/662776ebfd6882a6883a816672a757bd)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `MEM-02` |
| 저장소 도메인 | `member` |
| Notion 도메인 | 회원 |
| Method | `POST` |
| Path / Destination | `/api/v1/members/me/terms-agreements` |
| 상태 | 확정 |
| 인증 | 필요 |
| 공개 범위 | User |
| 담당자 | 신희원 |
| 구현 | ✅ **구현됨** — `TermsAgreementController` (`2026-07-30` 확인) |
| 우선순위 | High |
| 연결 구분 | Front↔BE |
| 의존 대상 | Front, PostgreSQL |
| 마지막 수정 | 2026-07-27 02:08:12Z |

## API
```plain text
POST /api/v1/members/me/terms-agreements
Authorization: Bearer {accessToken}
Content-Type: application/json
```
## Request
```plain text
{
  "agreements": [
    {
      "termId":1,
      "agreed":true
    },
    {
      "termId":2,
      "agreed":true
    },
    {
      "termId":3,
      "agreed":false
    }
  ]
}
```
## Response
```plain text
{
  "onboardingCompleted":true,
  "agreedAt":"2026-07-23T14:30:00+09:00"
}
```
## 검증 규칙
```plain text
- 인증된 사용자만 동의할 수 있다.
- 존재하고 현재 유효한 약관만 처리한다.
- 모든 필수 약관에 `agreed=true`가 제출되어야 한다.
- 필수 약관 거부 시 온보딩을 완료하지 않는다.
- 같은 사용자와 같은 약관 버전의 중복 동의는 한 번만 저장한다.
- 약관 동의 저장과 온보딩 완료 상태 변경은 하나의 트랜잭션으로 처리한다.
```
## 오류 응답
<table>
<tr>
<td>HTTP</td>
<td>코드</td>
<td>설명</td>
</tr>
<tr>
<td>400</td>
<td>`INVALID_TERMS_REQUEST`</td>
<td>동의 목록이 없거나 요청 형식이 잘못됨</td>
</tr>
<tr>
<td>400</td>
<td>`DUPLICATE_TERM_ID`</td>
<td>동일한 약관 ID가 요청에 중복 포함됨</td>
</tr>
<tr>
<td>401</td>
<td>`UNAUTHORIZED`</td>
<td>인증 실패</td>
</tr>
<tr>
<td>404</td>
<td>`MEMBER_NOT_FOUND`</td>
<td>인증된 회원 정보가 존재하지 않음</td>
</tr>
<tr>
<td>404</td>
<td>`TERM_NOT_FOUND`</td>
<td>요청한 약관이 존재하지 않음</td>
</tr>
<tr>
<td>409</td>
<td>`REQUIRED_TERMS_NOT_AGREED`</td>
<td>필수 약관에 동의하지 않음</td>
</tr>
<tr>
<td>409</td>
<td>`TERMS_VERSION_OUTDATED`</td>
<td>제출한 약관이 현재 적용 중인 버전이 아님</td>
</tr>
<tr>
<td>409</td>
<td>`TERM_NOT_ACTIVE`</td>
<td>아직 시행 전이거나 만료된 약관에 동의를 요청함</td>
</tr>
<tr>
<td>503</td>
<td>`TERMS_AGREEMENT_UNAVAILABLE`</td>
<td>약관 동의 저장 또는 온보딩 상태 변경 실패</td>
</tr>
</table>

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [약관 동의 API 원본](https://app.notion.com/662776ebfd6882a6883a816672a757bd)

