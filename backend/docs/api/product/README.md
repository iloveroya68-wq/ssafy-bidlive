# product API

`src/main/java/com/ssafy/bidlive/product/` 도메인이 담당하는 로컬 API 계약이다.

## API 및 내부 계약

| 기능 ID | Method | Path / Destination | 상태 | 명세 |
| --- | --- | --- | --- | --- |
| `AI-01` | `POST` | `/api/v1/ai/products/analyze` | 확정 | [AI 상품 자동 완성](./ai-01-post-ai-products-analyze-e36776eb.md) |
| `AI-02` | `POST` | `/api/v1/ai/price-recommendations` | 확정 | [하한가 추천](./ai-02-post-ai-price-recommendations-12b776eb.md) |
| `AI-04` | `POST` | `/api/v1/ai/products/inspect` | 확정 | [AI 기반 상품 이미지·텍스트 검열](./ai-04-post-ai-products-inspect-d62776eb.md) |

## 출처

- [Notion 최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)

