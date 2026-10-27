# 약관 조회 API

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [약관 조회 API](https://app.notion.com/3af776ebfd6882189809014c4765f258)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `MEM-02` |
| 저장소 도메인 | `member` |
| Notion 도메인 | 회원 |
| Method | `GET` |
| Path / Destination | `/api/v1/terms?purpose=ONBOARDING` |
| 상태 | 확정 |
| 인증 | 필요 |
| 공개 범위 | User |
| 담당자 | 신희원 |
| 구현 | ✅ **구현됨** — `TermsController` (`2026-07-30` 확인) |
| 우선순위 | High |
| 연결 구분 | Front↔BE |
| 의존 대상 | Front, PostgreSQL |
| 마지막 수정 | 2026-07-27 02:08:10Z |

## API
```plain text
GET /api/v1/terms?purpose=ONBOARDING
Authorization: Bearer {accessToken}
```
## Response
```plain text
{
  "items": [
    {
      "termId":1,
      "code":"SERVICE_TERMS",
      "title":"서비스 이용약관",
      "content":"약관 본문...",
      "version":"1.0",
      "required":true,
      "agreed":false
    },
    {
      "termId":2,
      "code":"PRIVACY_POLICY",
      "title":"개인정보 수집 및 이용 동의",
      "content":"약관 본문...",
      "version":"1.0",
      "required":true,
      "agreed":false
    },
    {
      "termId":3,
      "code":"MARKETING_CONSENT",
      "title":"마케팅 정보 수신 동의",
      "content":"약관 본문...",
      "version":"1.0",
      "required":false,
      "agreed":false
    }
  ]
}
```
## 주요 규칙
```plain text
- 현재 시행 중인 최신 약관만 반환한다.
- 필수·선택 여부를 함께 반환한다.
- 현재 사용자의 해당 버전 동의 여부를 반환한다.
- 온보딩 미완료 사용자도 호출할 수 있어야 한다.
- 약관이 개정되면 기존 약관을 수정하지 않고 새 버전 또는 새 약관 행으로 관리한다.
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
<td>`INVALID_TERMS_PURPOSE`</td>
<td>지원하지 않는 약관 조회 목적을 전달함</td>
</tr>
<tr>
<td>401</td>
<td>`UNAUTHORIZED`</td>
<td>인증 실패</td>
</tr>
<tr>
<td>404</td>
<td>`ACTIVE_TERMS_NOT_FOUND`</td>
<td>현재 적용 중인 약관이 존재하지 않음</td>
</tr>
<tr>
<td>404</td>
<td>`MEMBER_NOT_FOUND`</td>
<td>인증된 회원 정보가 존재하지 않음</td>
</tr>
<tr>
<td>503</td>
<td>`TERMS_SERVICE_UNAVAILABLE`</td>
<td>약관 정보 조회 실패</td>
</tr>
</table>

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [약관 조회 API 원본](https://app.notion.com/3af776ebfd6882189809014c4765f258)

