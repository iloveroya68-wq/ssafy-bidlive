# 배송 정보 조회

> **로컬에서 작성한 계약이다.** Notion 최종 종합 명세에 대응 항목이 없다 — 배송지가 `PATCH`로
> 들어가기만 하고 되읽는 경로가 없어서 **판매자가 어디로 보낼지 알 수 없었고**, 그 공백이 실제
> 사용에서 드러나(S15P11A704-694) 여기서 정의한다. 새 컬럼은 만들지 않았다. 이미 `deal_shipping`에
> 저장되고 있던 값을 당사자에게 돌려줄 뿐이다.

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `DEAL-05` |
| 저장소 도메인 | `deal` |
| Method | `GET` |
| Path | `/api/v1/deals/{dealId}/shipping` |
| 상태 | 확정 |
| 인증 | 필요 (해당 거래의 당사자) |
| 공개 범위 | Private |
| 우선순위 | High |

## 목적

거래 한 건의 배송지와 송장 정보를 **그 거래 당사자에게** 돌려준다.

- 판매자: 어디로 보낼지 확인한다 (이 API가 생긴 이유)
- 구매자: 자기가 입력한 배송지와 발송 현황을 확인한다

## 요청

```http
GET /api/v1/deals/{dealId}/shipping
```

## 성공 응답 `200`

```json
{
  "dealId": 300,
  "role": "SELLER",
  "dealStatus": "WAITING_SHIPMENT",
  "addressSubmitted": true,
  "recipientName": "김서연",
  "recipientPhone": "010-1234-5678",
  "postalCode": "06236",
  "address": "서울시 강남구",
  "addressDetail": "101동 202호",
  "carrier": null,
  "trackingNumber": null,
  "addressSubmittedAt": "2026-07-20T21:11:00+09:00",
  "shippedAt": null,
  "receivedConfirmedAt": null,
  "autoConfirmAt": null
}
```

| 필드 | 설명 |
| --- | --- |
| `role` | 조회한 사람의 역할. 화면이 「보낼 곳」과 「받을 곳」 중 무엇으로 읽을지 가른다 |
| `addressSubmitted` | 배송지가 들어왔는가. 필드가 전부 `null`인 것과 「입력 전」을 화면이 다시 해석하지 않게 서버가 판정한다 |
| `carrier`·`trackingNumber` | 발송 확인 전에는 `null` |
| `autoConfirmAt` | 수령 대기 상태가 아니면 `null` |

배송지 입력 전에는 수취인 필드가 전부 `null`이고 `addressSubmitted`가 `false`다. **그 상태도 유효한
응답이다** — 「아직 안 받았다」가 판매자에게 필요한 답이라 404로 감추지 않는다.

## 마스킹하지 않는다

수취인 실명·전화번호·주소를 그대로 내려준다. 물건을 보내려면 그 값이 그대로 필요하고, 조회가 이미
그 거래 당사자로 좁혀져 있다. 다른 화면의 마스킹(`판**`)은 제3자에게 보이는 목록이라 기준이 다르다.

## 오류

| 상태 | 코드 | 조건 |
| --- | --- | --- |
| `401` | `UNAUTHORIZED` | 인증이 없다 |
| `404` | `DEAL_NOT_FOUND` | 거래가 없거나 **내 거래가 아니다** |

**없는 거래와 남의 거래를 같은 404로 답한다.** 구분해 주면 거래 번호를 훑어 「존재하는 거래」를
알아낼 수 있고 그 자체가 남의 거래 정보다. 당사자 판정이 조회 조건(WHERE)에 들어 있어 구현상으로도
둘이 구분되지 않는다.

## 거래 상태와 무관하다

완료된 거래도 당사자면 조회된다. 발송 뒤 분쟁·반품에서 판매자가 배송지와 송장을 다시 봐야 할 수
있어 상태로 닫지 않는다.

## 관련

- [배송지 입력](./deal-05-patch-deals-by-dealid-shipping-address-620776eb.md) — 이 값을 쓰는 쪽
- [판매자 발송 확인](./deal-05-post-deals-by-dealid-shipment-confirmation-255776eb.md) — 송장 정보가 채워지는 시점
- [내 거래 할 일 조회](./mem-02-get-members-me-deals-todos-27e776eb.md) — 판매자가 수령 확인까지 목록에 남는 규칙
