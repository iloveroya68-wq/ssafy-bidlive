# 운영자 신고 목록 조회

> ⚠️ **Notion 원본에 없는 로컬 신설 계약이다.** 운영자 콘솔(S15P11A704-525)에 필요해 이 레포에서
> 정의했다. Notion에 반영되면 이 문구를 지운다.

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `ADMIN-01` |
| 저장소 도메인 | `report` |
| Method | `GET` |
| Path | `/api/v1/admin/reports` |
| 상태 | 확정 (로컬) |
| 인증 | 필요 |
| 공개 범위 | Admin |
| 구현 | ✅ **구현됨** — `AdminReportController` (S15P11A704-525) |

## 요약

신고가 접수된 경매를 **경매별로 묶어** 최신순으로 돌려준다. 운영자가 판단하는 단위가 신고 1건이
아니라 방이기 때문이다.

## 요청

| 파라미터 | 타입 | 기본 | 설명 |
| --- | --- | --- | --- |
| `includeEnded` | boolean | `false` | `true`면 종료된 방까지 포함 |
| `cursor` | string | — | 이전 응답의 `nextCursor` |
| `size` | int | 20 | 최대 100 |

## 성공 응답

```json
{
  "items": [{
    "auctionId": 9203,
    "title": "빈티지 카메라",
    "thumbnailUrl": "https://...",
    "sellerNickname": "서연***",
    "status": "LIVE",
    "reportCount": 8,
    "reasonCounts": [
      { "reasonCode": "PROHIBITED_ITEM", "count": 5 },
      { "reasonCode": "FALSE_LISTING", "count": 3 }
    ],
    "latestReportedAt": "2026-07-31T10:31:00+09:00"
  }],
  "nextCursor": null,
  "hasNext": false
}
```

## 처리 규칙

- 삭제된 방(`deleted_at IS NOT NULL`)은 제외한다
- 기본은 방 상태가 `WAITING`·`LIVE`인 것만. `includeEnded=true`면 `ENDED`까지
- 정렬은 `latestReportedAt DESC, auctionId DESC`. 시각만으로 정렬하면 같은 시각 행에서 페이지가
  겹치거나 빠져 `auctionId`를 타이브레이커로 함께 싣는다
- `reasonCounts`는 건수 내림차순, **동수면 `reasonCode` 사전순**으로 고정한다. 순서가 흔들리면
  화면의 「금지 품목 5 · 허위 3」이 새로고침마다 뒤바뀐다
- `sellerNickname`은 서버가 마스킹해 보낸다 (`SellerNicknameMask`)
- `thumbnailUrl`은 **null일 수 있다.** 이미지가 없거나 전부 `BLOCKED`인 상품이면 그대로 `null`이
  나간다 — 프론트가 대체 이미지를 그려야 한다
- `report.status`는 **읽지도 쓰지도 않는다.** 접수 시 항상 `RECEIVED`라 필터로서 의미가 없다

## 오류 응답

| 상태 | 코드 | 조건 |
| --- | --- | --- |
| 400 | `INVALID_CURSOR` | 커서 형식이 깨졌다 |
| 400 | `INVALID_SIZE` | `size`가 숫자가 아니다 |
| 400 | `INVALID_INCLUDE_ENDED` | `includeEnded`가 `true`·`false`가 아니다 |
| 401 | — | 비로그인 (Security가 낸다) |
| 403 | — | `ADMIN`이 아니다 (Security가 낸다) |

## 구현 메모

**`ADMIN` 계정은 `ADMIN_MEMBER_IDS` 환경변수로 만든다.** 카카오 OAuth가 유일한 로그인 경로라
시드로 계정을 만들 수 없다 — 이미 가입한 회원을 승격하는 것만 가능하다.

절차: 카카오로 로그인 → 본인 `member_id` 확인 → `ADMIN_MEMBER_IDS=42`를 env에 → 재기동 →
**재로그인(또는 토큰 갱신)**. `AdminMemberRoleInitializer`가 부팅 시 1회 올린다. 멱등이다.

⚠️ **마지막 단계를 빠뜨리면 DB는 `ADMIN`인데 화면은 계속 403이다.** 권한 판정은 JWT의 `role`
클레임에서 나오고(`JwtAuthenticationFilter`), 승격 전에 발급된 액세스 토큰에는 `USER`가 박혀 있다.
재로그인하거나 `POST /api/v1/auth/refresh`로 갱신해야 DB의 `Member`로 토큰이 다시 발급된다
(`AccessTokenRefreshService`).
