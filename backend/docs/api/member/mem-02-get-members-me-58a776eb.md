# 프로필 조회

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [프로필 조회](https://app.notion.com/58a776ebfd688371a21d81c0af53442c)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `MEM-02` |
| 저장소 도메인 | `member` |
| Notion 도메인 | 회원 |
| Method | `GET` |
| Path / Destination | `/api/v1/members/me` |
| 상태 | 확정 |
| 인증 | 필요 |
| 공개 범위 | User |
| 담당자 | 신희원 |
| 구현 | ✅ **구현됨** — `ProfileQueryController` (`2026-07-30` 확인) |
| 우선순위 | High |
| 연결 구분 | Front↔BE |
| 의존 대상 | Front, PostgreSQL |
| 마지막 수정 | 2026-07-28 |

## 개요

로그인 사용자의 마이페이지 상단에 표시할 프로필 요약 정보를 조회한다.

닉네임, 회원 상태·권한, 완료된 판매·구매 건수와 낙찰가 예측 적중률을 반환한다.
포인트 지갑과 거래 목록은 각각 전용 API에서 별도로 조회한다.

## Request

```http
GET /api/v1/members/me
Authorization: Bearer {accessToken}
```

요청 본문 없음.

## Response

### 200 OK

```json
{
  "memberId": 25,
  "nickname": "준호",
  "status": "ACTIVE",
  "role": "USER",
  "activitySummary": {
    "salesCount": 3,
    "purchaseCount": 7,
    "predictionParticipationCount": 13,
    "predictionHitCount": 8,
    "predictionHitRate": 61.54
  }
}
```

### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `memberId` | Long | 우리 서비스 내부 회원 ID |
| `nickname` | String | 입찰과 채팅에 표시되는 서비스 닉네임 |
| `status` | Enum | 회원 상태. `ACTIVE`, `SUSPENDED` |
| `role` | Enum | 회원 권한. `USER`, `ADMIN` |
| `activitySummary` | Object | 마이페이지 상단 활동 요약 |
| `activitySummary.salesCount` | Long | 판매자로서 거래 완료된 건수 |
| `activitySummary.purchaseCount` | Long | 구매자로서 거래 완료된 건수 |
| `activitySummary.predictionParticipationCount` | Long | 정산이 완료된 낙찰가 예측 참여 건수 |
| `activitySummary.predictionHitCount` | Long | 실제 낙찰가 대비 오차율 20% 이내 적중 건수 |
| `activitySummary.predictionHitRate` | Double | 예측 적중률(%). 소수점 둘째 자리까지 반환 |

## 회원 상태·권한 정책

| 상태 | 프로필 조회 | 처리 |
| --- | --- | --- |
| `ACTIVE` | 허용 | 모든 인가된 기능을 정상적으로 사용할 수 있다. |
| `SUSPENDED` | 허용 | 프로필을 반환하되 입찰·베팅·채팅 등 제한 기능은 사용할 수 없다. |
| `WITHDRAWN` | 거부 | `MEMBER_NOT_AVAILABLE` 오류를 반환한다. |

- 프론트는 `status=SUSPENDED`이면 제한 기능을 잠그고 회원 정지 안내를 표시한다.
- UI 잠금과 별개로 백엔드에서도 각 API 요청 시 현재 회원 상태를 검사해야 한다.
- `role=ADMIN`은 관리자 화면 노출에 사용할 수 있지만 실제 관리자 API 권한은 백엔드에서 검증한다.
- `role`은 `USER`, `ADMIN`으로 반환하며 Spring Security의 `ROLE_USER`,
  `ROLE_ADMIN` 표현과 구분한다.

## 집계 규칙

- `salesCount`는 사용자가 판매자인 거래 중 `COMPLETED` 상태의 건수다.
- `purchaseCount`는 사용자가 구매자인 거래 중 `COMPLETED` 상태의 건수다.
- 예측 적중은 정산 완료된 예측 중 실제 낙찰가 대비 오차율이 5% 이하인 경우다.
- 유찰·강제 종료로 전액 환불된 예측은 참여 건수와 적중률 계산에서 제외한다.
- `predictionHitRate = predictionHitCount / predictionParticipationCount × 100`으로
  계산하고 소수점 셋째 자리에서 반올림한다.
- 정산 완료된 예측 참여가 0건이면 `predictionHitRate=0.0`을 반환한다.
- 본인 프로필의 닉네임은 원문으로 반환하고 다른 사용자에게 공개할 때만 서버가
  마스킹한다.

## API 범위 분리

| 마이페이지 영역 | 사용 API |
| --- | --- |
| 닉네임·상태·권한·활동 요약 | `GET /api/v1/members/me` |
| 거래·베팅 포인트 지갑 | `GET /api/v1/members/me/wallets` |
| 지금 할 일·낙찰 받은·개최한 거래 목록 | `GET /api/v1/members/me/deals` 계열 |
| 참여했지만 낙찰되지 않은 경매·예측 이력 | 참여한 경매 조회 API |

지갑과 거래 목록은 프로필 응답에 포함하지 않는다. 각 영역의 갱신 주기와 데이터
크기가 다르므로 프론트가 병렬로 호출한다.

## 검증 규칙

- Access Token의 `memberId`를 기준으로 본인 정보만 조회한다.
- `status`, `role`은 요청 당시 DB에 저장된 최신 회원 정보를 기준으로 반환한다.
- 프론트가 전달한 회원 상태나 권한 정보는 신뢰하지 않는다.
- `ACTIVE`, `SUSPENDED` 회원은 프로필을 조회할 수 있다.
- `WITHDRAWN` 회원은 프로필을 조회할 수 없다.
- 프로필 조회 시 닉네임 설정과 최신 필수 약관 동의가 모두 완료되어야 한다.
- 모든 집계는 현재 회원이 당사자인 데이터만 대상으로 한다.
- 통계 조회 실패 시 임의의 기본값으로 응답하지 않고 서버 오류로 처리한다.

## 오류 응답

| HTTP | 코드 | 설명 |
| --- | --- | --- |
| 401 | `UNAUTHORIZED` | Access Token이 없거나 유효하지 않음 |
| 403 | `MEMBER_NOT_AVAILABLE` | 탈퇴한 회원이어서 프로필을 조회할 수 없음 |
| 404 | `MEMBER_NOT_FOUND` | 인증 정보에 해당하는 회원이 존재하지 않음 |
| 409 | `ONBOARDING_NOT_COMPLETED` | 최초 프로필 설정 또는 필수 약관 동의가 완료되지 않음 |
| 500 | `PROFILE_SUMMARY_QUERY_FAILED` | 프로필 활동 요약 집계 실패 |

## 처리 흐름

```text
Access Token 검증
→ memberId 추출
→ DB에서 최신 Member 조회
→ 회원 status와 role 확인
→ WITHDRAWN 회원 조회 거부
→ 닉네임과 최신 필수 약관을 기준으로 온보딩 완료 여부 확인
→ 완료된 판매·구매 거래 건수 집계
→ 정산 완료 예측 참여·적중 건수 집계
→ 예측 적중률 계산
→ status와 role을 포함한 프로필 요약 반환
```

## 화면 매핑

```text
준호
판매 3 · 구매 7 · 예측 적중률 62%
```

- `status=SUSPENDED`이면 주요 기능을 비활성화하고 정지 안내를 표시한다.
- 프로필 원형 아바타의 글자는 프론트가 `nickname`으로 생성한다.
- 포인트 카드는 지갑 조회 API 응답으로 표시한다.
- `지금 할 일`과 거래 탭은 거래 조회 API 응답으로 표시한다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [프로필 조회 원본](https://app.notion.com/58a776ebfd688371a21d81c0af53442c)
