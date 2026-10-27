# AI 기반 상품 이미지·텍스트 검열

> Notion 최종 종합 명세에서 동기화한 뒤 검열 입력 방식과 AI 연동 대상 결정에 따라 갱신한 로컬 구현 계약이다.  
> 원본: [AI 기반 상품 이미지·텍스트 검열](https://app.notion.com/d62776ebfd6883b8846981bc0547fad7)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `AI-04` |
| 저장소 도메인 | `product` |
| Notion 도메인 | AI |
| Method | `POST` |
| Path / Destination | `/api/v1/ai/products/inspect` |
| 상태 | 확정 |
| 인증 | 필요 |
| 공개 범위 | 인증 회원 |
| 담당자 | 이연주 |
| 우선순위 | Medium |
| 연결 구분 | Front↔BE, BE↔AI(GMS) |
| 의존 대상 | Front, SSAFY GMS, PostgreSQL |
| 마지막 수정 | 2026-07-29 |

## 요약

상품 등록 전 제목·설명·이미지를 AI로 검열해 판정과 위험 카테고리를 반환한다.

## 개요

**등록 전 사전 검열**이다. `productId`가 아니라 등록하려는 원본 입력을 그대로 받는다. 이 판정은
상품 등록을 직접 막지 않고, 등록 흐름이 참조하는 결과와 감사 이력만 남긴다.

AI는 **SSAFY GMS**(OpenAI 호환 게이트웨이)를 통해 호출한다. GMS에 별도 Moderation 모델이 없으므로
Vision을 지원하는 멀티모달 텍스트 모델에 검열 프롬프트를 주고, 응답은 `response_format`의 JSON
스키마로 강제한다.

## Request

```javascript
POST /api/v1/ai/products/inspect
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "title": "빈티지 가죽 자켓",
  "description": "3년 사용한 정품입니다.",
  "imageUrls": ["https://cdn.example.com/p/1.jpg"]
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `title` | String(≤100) | 조건부 | 상품 제목 |
| `description` | String(≤5000) | 조건부 | 상품 설명 |
| `imageUrls` | String[](≤20, 각 ≤2048) | 조건부 | 검열할 이미지 URL |

- 세 필드 중 **최소 하나에 검열할 내용이 있어야 한다.** 공백만 있는 값은 없는 것으로 본다.
- 검열 대상 이미지 개수 상한은 `ai.gms.max-image-count`(기본 10)이며 초과 시 400이다.
- 제목과 설명은 문맥이 끊기지 않도록 하나의 텍스트로 합쳐 한 번에 검열한다.

## Response

### 200 OK

```json
{
  "type": "AI_INSPECTION_RESULT",
  "data": {
    "verdict": "REJECTED",
    "textResult": {
      "verdict": "PASSED",
      "imageUrl": null,
      "flaggedCategories": [],
      "maxScore": 0.03,
      "reason": "일반적인 중고 의류입니다.",
      "failureReason": null
    },
    "imageResults": [
      {
        "verdict": "REJECTED",
        "imageUrl": "https://cdn.example.com/p/1.jpg",
        "flaggedCategories": ["COUNTERFEIT"],
        "maxScore": 0.93,
        "reason": "정품을 사칭한 로고가 확인됩니다.",
        "failureReason": null
      }
    ],
    "inspectedAt": "2026-07-20T21:00:00+09:00"
  }
}
```

- `verdict`: `PASSED` | `REJECTED` | `NEEDS_REVIEW`
- `textResult`는 검열할 텍스트가 없으면 `null`이다.
- `failureReason`은 AI 판정을 얻지 못해 보류된 경우의 사유(`TIMEOUT`·`TRANSPORT`·`INVALID_RESPONSE`)이며
  정상 판정에서는 `null`이다.
- `inspectedAt`은 서버 시각(KST 오프셋)이다.

### 오류

| HTTP | `code` | 발생 조건 |
| --- | --- | --- |
| 400 | `INVALID_INSPECTION_REQUEST` | 검열할 내용이 하나도 없거나, 이미지 개수·필드 길이 한도를 넘었거나, 본문 형식이 잘못됐다. |
| 401 | `UNAUTHORIZED` | Access Token이 없거나 인증 회원 식별자를 얻을 수 없다. |
| 502 | `AI_CREDIT_EXHAUSTED` | GMS Credit이 소진되어 검열을 수행할 수 없다. |

오류 본문은 `{"code": "...", "message": "..."}` 형식이다.

## 위험 카테고리

`ILLEGAL_GOODS`, `COUNTERFEIT`, `WEAPONS`, `DRUGS`, `ADULT`, `VIOLENCE`, `HATE`,
`PERSONAL_INFO`, `SPAM`

카테고리 목록은 코드 상수로 고정한다. 모델이 목록에 없는 카테고리를 만들어 보내도 집계에서
제외해 판정 기준이 흔들리지 않게 한다.

## 판정 규칙

- 카테고리별 위험도는 0.0~1.0이며 단위 판정은 **최고 점수 하나**로 결정한다.
- `maxScore >= ai.inspection.reject-threshold`(기본 0.8) → `REJECTED`
- `maxScore >= ai.inspection.review-threshold`(기본 0.4) → `NEEDS_REVIEW`
- 그 외 → `PASSED`
- 최종 판정은 텍스트와 모든 이미지의 단위 판정을 합친다. **하나라도 `REJECTED`면 `REJECTED`,
  보류가 하나라도 남아 있으면 `NEEDS_REVIEW`이고, 모두 통과일 때만 `PASSED`다.**
- 검열한 대상이 하나도 없거나 점수가 비어 있으면 `PASSED`가 아니라 `NEEDS_REVIEW`다.

## 처리 흐름

```plain text
인증 확인
→ 입력 검증
→ 콘텐츠 해시로 확정 판정 캐시 조회(있으면 AI 호출 생략)
→ 텍스트 검열(BE↔GMS)
→ 이미지 검열(BE↔GMS, 동시 실행 상한 안에서 병렬)
→ 임계값 기준 단위 판정 및 집계
→ 검열 이력 저장(detail은 JSONB)
→ 200 반환
```

## 실패 정책

- **자동 통과를 만들지 않는다.** GMS 장애, 허용 시간 초과, JSON 스키마 위반은 해당 단위를
  `NEEDS_REVIEW`로 보류하고 `failureReason`에 사유를 남긴다. 보류가 남으면 최종 판정도 `PASSED`가
  되지 않는다.
- 이미지 한 장이 실패해도 나머지 판정은 버리지 않는다.
- **Credit 소진만 예외**로 502를 반환한다. 콘텐츠 판단의 모호함이 아니라 운영자가 개입해야 하는
  상태이므로 보류로 감추지 않는다.
- 보류 판정은 캐시로 재사용하지 않는다. 판정을 얻지 못한 상태일 수 있어 다음 요청에서 다시
  시도해야 한다.

## Credit 절감

- 콘텐츠 해시(SHA-256) 기준으로 확정 판정이 있으면 AI를 호출하지 않는다. 이미지 순서와 중복,
  앞뒤 공백은 정규화하므로 같은 콘텐츠로 인식된다.
- 재시도는 전송 오류에 한해 `ai.gms.max-retries`(기본 1)까지만 한다. 4xx는 다시 보내도 결과가
  같으므로 재시도하지 않는다.
- 이미지 개수 상한과 동시 실행 상한으로 한 요청의 Credit 소모를 제한한다.

## 설정

| 키 | 기본값 | 설명 |
| --- | --- | --- |
| `ai.gms.base-url` | 없음(필수) | GMS 엔드포인트 |
| `ai.gms.api-key` | 없음(필수) | 팀 GMS Key. env·시크릿으로만 주입하며 로그에 남기지 않는다. |
| `ai.gms.inspection-model` | `gpt-4o-mini` | Vision을 지원하는 멀티모달 모델명 |
| `ai.gms.timeout` | `15s` | 호출 한 건의 허용 시간 |
| `ai.gms.max-retries` | `1` | 전송 오류 재시도 횟수 |
| `ai.gms.max-concurrent-image-inspections` | `3` | 이미지 검열 동시 실행 상한 |
| `ai.gms.max-image-count` | `10` | 한 요청의 이미지 개수 상한 |
| `ai.inspection.reject-threshold` | `0.8` | `REJECTED` 임계값 |
| `ai.inspection.review-threshold` | `0.4` | `NEEDS_REVIEW` 임계값 |

## 확인 필요

- GMS 엔드포인트, 팀 Key와 검열 모델명은 배포 환경 값으로 채워야 한다. `inspection-model` 기본값은
  임시이며 GMS가 제공하는 Vision 모델명으로 확정해야 한다.
- 이미지는 `image_url` 파트로 URL을 그대로 전달한다. GMS 게이트웨이가 외부 URL을 가져오지 못하면
  BE가 내려받아 base64로 인라인하는 방식으로 어댑터만 교체해야 한다.
- `SELLER` 역할이 없어 "Seller 전용"을 강제할 근거가 없다. 이 도메인에서는 인증 회원이면 누구나
  상품을 등록할 수 있으므로 인증까지만 요구한다. 판매자 자격 개념이 생기면 403을 추가한다.
- 검열 결과와 `product.moderation_status` 연결은 상품 등록 API가 생길 때 그 쪽에서 정한다.
  `verdict` 3값과 `moderation_status` 4값의 매핑도 그 시점에 확정한다.
- 동기 응답으로 구현했다. 이미지가 많아 응답이 길어지면 비동기 작업·완료 이벤트 분리를 재검토한다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [AI 기반 상품 이미지·텍스트 검열 원본](https://app.notion.com/d62776ebfd6883b8846981bc0547fad7)
