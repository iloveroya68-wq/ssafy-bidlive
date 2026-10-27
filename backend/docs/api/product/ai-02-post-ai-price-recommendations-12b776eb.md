# 하한가 추천

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [하한가 추천](https://app.notion.com/12b776ebfd6882cf81e6815d7900e93d)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `AI-02` |
| 저장소 도메인 | `product` |
| Notion 도메인 | AI |
| Method | `POST` |
| Path / Destination | `/api/v1/ai/price-recommendations` |
| 상태 | 확정 |
| 인증 | 필요 |
| 공개 범위 | Seller |
| 담당자 | 김택림 |
| 우선순위 | Low |
| 연결 구분 | Front↔BE |
| 의존 대상 | Front, AI |
| 마지막 수정 | 2026-07-27 02:08:10Z |

## 개요
AI-01에서 식별한 상품 정보와 판매자가 입력한 상태 정보를 바탕으로 추천 하한가와 예상 낙찰 범위를 근거와 함께 제공한다. 추천값은 참고용이며 판매자가 최종 하한가를 직접 확정한다.
## Request
```javascript
POST /api/v1/ai/price-recommendations
Authorization: Bearer {accessToken}
Content-Type: application/json
```
```json
{
  "analysisId": "f08d93c0-43fb-4c85-bfb7-980625c8b354",
  "productName": "Keychron Q1 Pro",
  "brand": "Keychron",
  "modelName": "Q1 Pro",
  "category": "KEYBOARD",
  "condition": "USED_GOOD",
  "usageMonths": 10,
  "defects": ["하판 미세 스크래치"],
  "includedItems": ["본체", "케이블", "박스"]
}
```
## Response
### 200 OK
```json
{
  "recommendationId": "78bfc7ec-63fa-4666-8d64-28aa7632d894",
  "recommendedReservePrice": 25000,
  "expectedWinningPrice": {
    "min": 35000,
    "max": 45000
  },
  "confidence": 0.74,
  "reasons": [
    "동일 모델의 신품 가격",
    "사용 기간 10개월",
    "경미한 외관 흠집",
    "박스 및 기본 구성품 포함"
  ],
  "similarTransactionCount": 0,
  "sourceType": "LLM_ESTIMATION"
}
```
## sourceType
- `LLM_ESTIMATION`: 자체 거래 이력이 부족해 외부 모델 추론 중심
- `HISTORICAL_HYBRID`: 자체 낙찰 이력과 LLM 추론을 함께 사용
## 검증 규칙
- 추천 하한가는 0보다 큰 정수여야 한다.
- 예상 최저가는 예상 최고가 이하이어야 한다.
- 응답 카테고리와 상태 값은 서버 enum으로 검증한다.
- 신뢰도가 낮아도 추천을 강제 적용하지 않고 참고 정보로 반환한다.
## 오류 응답
<table>
<tr>
<td>HTTP</td>
<td>코드</td>
<td>설명</td>
</tr>
<tr>
<td>400</td>
<td>`INVALID_PRODUCT_INFORMATION`</td>
<td>추천에 필요한 상품 정보 부족 또는 형식 오류</td>
</tr>
<tr>
<td>422</td>
<td>`PRICE_RECOMMENDATION_UNAVAILABLE`</td>
<td>가격 범위를 추론하지 못함</td>
</tr>
<tr>
<td>503</td>
<td>`AI_SERVICE_UNAVAILABLE`</td>
<td>외부 AI 호출 실패</td>
</tr>
</table>
## 처리 흐름
```plain text
AI-01 결과 또는 판매자 입력
→ 유사 자체 낙찰 이력 조회
→ LLM 가격 추론
→ 결과 범위 및 근거 검증
→ 추천 하한가·예상 낙찰 범위 반환
→ 판매자가 수정 후 방 개설 API에 최종값 제출
```
## 저장 권장 항목
추천값의 정확도를 평가할 수 있도록 `recommendationId`, 추천 하한가, 예상 범위, 판매자 최종 선택값, 실제 낙찰가, 모델 버전을 기록한다.
## 폴백
추천 실패 시에도 방 개설은 정상 진행하며 판매자가 하한가를 직접 입력한다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [하한가 추천 원본](https://app.notion.com/12b776ebfd6882cf81e6815d7900e93d)

