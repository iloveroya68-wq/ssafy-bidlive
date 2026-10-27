# AI 상품 자동 완성

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [AI 상품 자동 완성](https://app.notion.com/e36776ebfd68837b9afe0164e4214d46)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `AI-01` |
| 저장소 도메인 | `product` |
| Notion 도메인 | AI |
| Method | `POST` |
| Path / Destination | `/api/v1/ai/products/analyze` |
| 상태 | 확정 |
| 인증 | 필요 |
| 공개 범위 | Seller |
| 담당자 | 김택림 |
| 우선순위 | Medium |
| 연결 구분 | Front↔BE |
| 의존 대상 | Front, AI |
| 마지막 수정 | 2026-07-27 02:08:10Z |

## 개요
판매자가 업로드한 상품 사진을 Vision 모델로 분석해 상품 등록 폼의 초안을 생성한다. AI 결과는 자동 확정하지 않고 판매자가 확인·수정한 뒤 저장한다.
## Request
```javascript
POST /api/v1/ai/products/analyze
Authorization: Bearer {accessToken}
Content-Type: multipart/form-data
```
### Form Data
<table>
<tr>
<td>필드</td>
<td>타입</td>
<td>필수</td>
<td>설명</td>
</tr>
<tr>
<td>`images`</td>
<td>File\[\]</td>
<td>선택</td>
<td>상품 사진 목록</td>
</tr>
<tr>
<td>`productNameHint`</td>
<td>String</td>
<td>선택</td>
<td>사진 식별 실패 시 참고할 모델명·상품명</td>
</tr>
</table>
`images`와 `productNameHint` 중 하나 이상은 필수다.
## Response
### 200 OK
```json
{
  "analysisId": "f08d93c0-43fb-4c85-bfb7-980625c8b354",
  "productName": "Keychron Q1 Pro",
  "brand": "Keychron",
  "modelName": "Q1 Pro",
  "category": "KEYBOARD",
  "titleDraft": "키크론 Q1 Pro 기계식 키보드",
  "descriptionDraft": "사용감이 있는 Q1 Pro 키보드입니다. 외관과 구성품을 확인해 주세요.",
  "conditionChecklist": [
    {
      "item": "외관 스크래치",
      "detected": true,
      "confidence": 0.78
    }
  ],
  "confidence": 0.86,
  "requiresReview": true
}
```
## 검증 규칙
- 요청자는 인증된 판매자여야 한다.
- 허용된 이미지 형식과 최대 파일 크기를 검증한다.
- 외부 AI 응답을 내부 카테고리 enum과 구조에 맞게 검증한다.
- 신뢰도가 낮거나 필수 필드를 식별하지 못하면 `requiresReview`를 `true`로 반환한다.
## 오류 응답
<table>
<tr>
<td>HTTP</td>
<td>코드</td>
<td>설명</td>
</tr>
<tr>
<td>400</td>
<td>`AI_INPUT_REQUIRED`</td>
<td>사진과 텍스트 힌트가 모두 없음</td>
</tr>
<tr>
<td>400</td>
<td>`UNSUPPORTED_IMAGE_TYPE`</td>
<td>지원하지 않는 이미지 형식</td>
</tr>
<tr>
<td>413</td>
<td>`IMAGE_SIZE_EXCEEDED`</td>
<td>이미지 용량 초과</td>
</tr>
<tr>
<td>422</td>
<td>`PRODUCT_NOT_IDENTIFIED`</td>
<td>상품을 식별하지 못함</td>
</tr>
<tr>
<td>503</td>
<td>`AI_SERVICE_UNAVAILABLE`</td>
<td>외부 AI 호출 실패</td>
</tr>
</table>
## 처리 흐름
```plain text
사진 또는 텍스트 힌트 입력
→ 파일 검증 및 저장
→ Vision 모델 호출
→ 구조화된 결과 검증
→ 등록 폼 초안 반환
→ 판매자가 수정·확정
```
## 폴백
AI 호출이 실패해도 방 개설을 막지 않는다. 프론트는 수동 입력 폼을 그대로 제공한다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [AI 상품 자동 완성 원본](https://app.notion.com/e36776ebfd68837b9afe0164e4214d46)

