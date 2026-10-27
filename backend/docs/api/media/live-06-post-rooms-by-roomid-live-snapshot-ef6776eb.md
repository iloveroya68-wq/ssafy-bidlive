# 낙찰 순간 방송 스냅샷 캡처 및 저장

> Notion 최종 종합 명세에서 동기화한 뒤 캡처 주체 결정(A안)에 따라 갱신한 로컬 구현 계약이다.  
> 원본: [낙찰 순간 방송 스냅샷 캡처 및 저장](https://app.notion.com/ef6776ebfd6883b99aa301cafcc1edd2)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `LIVE-06` |
| 저장소 도메인 | `media` |
| Notion 도메인 | 방송 |
| Method | `POST` |
| Path / Destination | `/api/v1/rooms/{roomId}/live/snapshot` |
| 상태 | 확정 |
| 인증 | 필요 |
| 공개 범위 | Seller·낙찰 관계자 (자격 판정은 경매 도메인) |
| 담당자 | 이연주 |
| 우선순위 | Medium |
| 연결 구분 | Front↔BE |
| 의존 대상 | Front, 경매, PostgreSQL, S3 |
| 마지막 수정 | 2026-07-29 |

## 요약

프론트가 캡처한 낙찰 시점 방송 프레임을 받아 S3에 저장하고 증빙 URL을 반환한다.

## 개요

캡처 주체는 **프론트**다(A안). 프론트가 `<video>` 프레임을 canvas로 캡처해 이미지 파일로 업로드하고,
서버는 이미지 검증 → 낙찰 맥락 검증 → S3 저장 → 메타 저장을 담당한다. 서버가 OpenVidu에서 직접
프레임을 캡처하지 않는다.

## Request

```javascript
POST /api/v1/rooms/{roomId}/live/snapshot
Authorization: Bearer {accessToken}
Content-Type: multipart/form-data
```

| 파트 | 종류 | 필수 | 설명 |
| --- | --- | --- | --- |
| `image` | file | 필수 | 캡처 이미지. `image/jpeg` 또는 `image/png`, 최대 5MB |
| `auctionId` | form field | 필수 | 스냅샷을 연결할 경매 식별자(number) |

- 파일명은 저장 경로에 사용하지 않으므로 어떤 값이든 무관하다.
- `roomId`는 경로 변수에서만 받는다.

## Response

### 201 Created

```json
{
  "type": "LIVE_SNAPSHOT_CAPTURED",
  "data": {
    "roomId": "102",
    "auctionId": 9982,
    "snapshotUrl": "https://bidcast-bucket.s3.ap-northeast-2.amazonaws.com/snapshots/room-102/auction-9982.jpg",
    "capturedAt": "2026-07-20T21:10:00+09:00"
  }
}
```

- `roomId`는 LIVE-01·LIVE-02와 같은 String 계약을 유지한다.
- `capturedAt`은 클라이언트 값이 아니라 서버 시각(KST 오프셋)이다.

### 오류

| HTTP | `code` | 발생 조건 |
| --- | --- | --- |
| 400 | `INVALID_SNAPSHOT_REQUEST` | `image` 파트 또는 `auctionId` 값이 없거나 `auctionId` 형식이 잘못됐다. |
| 400 | `IMAGE_REQUIRED` | `image` 파트가 비어 있다. |
| 400 | `UNSUPPORTED_IMAGE_TYPE` | `image/jpeg`·`image/png` 이외의 형식이다. |
| 401 | `UNAUTHORIZED` | Access Token이 없거나 인증 회원 식별자를 얻을 수 없다. |
| 403 | `NOT_SNAPSHOT_UPLOADER` | 요청자에게 이 경매의 스냅샷 업로드 자격이 없다. |
| 404 | `AUCTION_NOT_FOUND` | 방 또는 경매를 찾을 수 없다. |
| 409 | `AUCTION_NOT_SETTLED` | 낙찰이 확정된 경매가 아니다. |
| 413 | `IMAGE_TOO_LARGE` | 이미지가 5MB를 초과했다. |
| 415 | — | `Content-Type`이 `multipart/form-data`가 아니다. |
| 502 | `SNAPSHOT_STORAGE_ERROR` | S3 업로드가 실패했다. |

오류 본문은 `{"code": "...", "message": "..."}` 형식이다.

## 처리 흐름

```plain text
인증 확인
→ 이미지 유무·형식·크기 검증
→ 낙찰 맥락 검증(방·경매 존재, 낙찰 확정, 요청자 자격)
→ S3 업로드
→ 스냅샷 메타 저장
→ 201 반환
```

## 무결성 규칙

- 이미지와 경매의 연결은 요청 값이 아니라 서버 검증을 통과한 뒤에만 성립한다. 낙찰 확정 여부와
  요청자 자격은 경매 정보를 소유한 도메인이 판정한다.
- S3 key는 `snapshots/room-{roomId}/auction-{auctionId}.{jpg|png}` 로 서버가 만든다. 클라이언트가
  보낸 파일명은 사용하지 않는다.
- 저장 확장자와 `Content-Type`은 검증을 통과한 이미지 형식에서만 가져온다.
- 경매당 스냅샷은 하나다. 같은 경매를 다시 올리면 같은 S3 key를 덮어쓰고 메타의 URL과
  `capturedAt`을 갱신한다.
- 검증 실패 시 이미지를 저장소에 올리지 않는다. S3 업로드가 실패하면 예외로 트랜잭션을 롤백해
  URL 없는 메타가 남지 않는다.

## 확인 필요

- 요청자 자격 정책(판매자 전용인지 낙찰자도 허용인지)은 경매 도메인이 확정한다. 현재 media는
  `AuctionSnapshotVerifier` 포트로 판정만 받는다.
- auction 도메인 검증 구현이 없는 동안 업로드는 기본 차단(409)이며,
  `app.media.snapshot.skip-auction-verification=true` 인 개발 환경에서만 통과한다. 운영에서 켜면 안 된다.
- 스냅샷 메타는 `deal.snapshot_image_url`이 아니라 media 도메인의 `live_snapshot` 테이블에 보관한다.
  이 API 계약이 `dealId`가 아닌 `auctionId` 기준이고 deal 도메인 구현이 없어서이며, deal 구현 후
  소유 위치를 정리해야 한다.
- 캡처 실패가 낙찰 정산을 중단시키는지는 정산 로직이 생긴 뒤 결정한다. 현재 이 API는 정산 흐름과
  분리된 프론트 업로드 경로다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [낙찰 순간 방송 스냅샷 캡처 및 저장 원본](https://app.notion.com/ef6776ebfd6883b99aa301cafcc1edd2)
