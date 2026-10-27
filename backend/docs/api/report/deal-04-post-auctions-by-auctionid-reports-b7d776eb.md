# 경매 신고 접수

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [경매 신고 접수](https://app.notion.com/b7d776ebfd6882709d4601fa4443e319)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `DEAL-04` |
| 저장소 도메인 | `report` |
| Notion 도메인 | 경매 |
| Method | `POST` |
| Path / Destination | `/api/v1/auctions/{auctionId}/reports` |
| 상태 | 확정 |
| 인증 | 필요 |
| 공개 범위 | User |
| 담당자 | 신희원 |
| 구현 | ✅ **구현됨** — `AuctionReportController` (`2026-07-30` 확인) |
| 우선순위 | Medium |
| 연결 구분 | Front↔BE |
| 의존 대상 | Front, PostgreSQL, 경매 |
| 마지막 수정 | 2026-07-27 02:08:12Z |

## 요약

대기방·라이브 경매 신고 접수

## 개요
인증된 사용자가 대기방 또는 라이브 진행 중 허위 매물, 금지 품목, 부적절한 방송·언행을 신고한다.
신고 접수만 처리하며 거래 상태 변경, 정산 보류, 강제 종료는 자동으로 수행하지 않는다. 강제 종료는 운영자 검토 후 `DEAL-09`를 통해 처리한다.
## Request
```plain text
POST /api/v1/auctions/{auctionId}/reports
Authorization: Bearer {accessToken}
Content-Type: application/json
```
```plain text
{
  "reasonCode": "INAPPROPRIATE_BROADCAST",
  "description": "판매자가 방송 중 부적절한 발언을 반복했습니다.",
  "evidenceUrls": [
    "https://cdn.example.com/evidence/1.jpg"
  ]
}
```
### Path Variable
<table>
<tr>
<td>이름</td>
<td>타입</td>
<td>필수</td>
<td>설명</td>
</tr>
<tr>
<td>`auctionId`</td>
<td>Long</td>
<td>필수</td>
<td>신고 대상 경매 ID</td>
</tr>
</table>
기존의 `신고 대상 거래 ID`는 `신고 대상 경매 ID`로 바꿔야 합니다.
### Request Body
<table>
<tr>
<td>필드</td>
<td>타입</td>
<td>필수</td>
<td>설명</td>
</tr>
<tr>
<td>`reasonCode`</td>
<td>Enum</td>
<td>필수</td>
<td>신고 사유 코드</td>
</tr>
<tr>
<td>`description`</td>
<td>String</td>
<td>조건부</td>
<td>상세 내용. `OTHER`인 경우 필수</td>
</tr>
<tr>
<td>`evidenceUrls`</td>
<td>Array\<String\></td>
<td>선택</td>
<td>사전에 업로드한 증빙 파일 URL 목록</td>
</tr>
</table>
### 신고 사유
<table>
<tr>
<td>값</td>
<td>설명</td>
</tr>
<tr>
<td>`FALSE_LISTING`</td>
<td>허위·오해를 유발하는 매물</td>
</tr>
<tr>
<td>`PROHIBITED_ITEM`</td>
<td>거래 금지 품목</td>
</tr>
<tr>
<td>`INAPPROPRIATE_BROADCAST`</td>
<td>부적절한 방송 또는 언행</td>
</tr>
<tr>
<td>`OTHER`</td>
<td>기타 사유. 상세 설명 필수</td>
</tr>
</table>
`ITEM_NOT_AS_DESCRIBED`는 일반적으로 상품 수령 후 발생하는 거래 분쟁 사유이므로 대기방·라이브 신고에서는 제외하는 것이 적절합니다.
## Response
현재 응답은 그대로 사용해도 됩니다.
### 201 Created
```plain text
{
  "reportId": 123,
  "status": "RECEIVED",
  "receivedAt": "2026-07-22T15:00:00+09:00"
}
```
신고 검토 결과나 강제 종료 여부는 응답에 포함하지 않습니다.
## 검증 규칙
다음과 같이 바꿔야 합니다.
- 신고 대상 경매가 존재해야 한다.
- 경매 상태가 `WAITING` 또는 `LIVE`여야 한다.
- 인증된 사용자만 신고할 수 있다.
- 판매자는 자신의 경매를 신고할 수 없다.
- `reasonCode`는 서버가 지원하는 값이어야 한다.
- `OTHER`인 경우 `description`은 필수다.
- 설명 길이, 증빙 개수 및 허용 URL 형식을 제한한다.
- 동일 사용자의 반복 신고에 대한 중복 정책을 적용한다.
- 신고자의 신원은 판매자와 다른 참여자에게 공개하지 않는다.
`해당 거래의 구매자 또는 판매자만 신고할 수 있다`는 검증은 삭제해야 합니다. 대기방·라이브 단계에는 아직 구매자와 거래가 존재하지 않기 때문입니다.
## 오류 응답
<table>
<tr>
<td>HTTP</td>
<td>코드</td>
<td>설명</td>
</tr>
<tr>
<td>400</td>
<td>`INVALID_REPORT_REQUEST`</td>
<td>사유·설명·증빙 형식 오류</td>
</tr>
<tr>
<td>401</td>
<td>`UNAUTHORIZED`</td>
<td>인증 실패</td>
</tr>
<tr>
<td>403</td>
<td>`OWN_AUCTION_REPORT_NOT_ALLOWED`</td>
<td>판매자가 자신의 경매를 신고함</td>
</tr>
<tr>
<td>404</td>
<td>`AUCTION_NOT_FOUND`</td>
<td>경매가 존재하지 않음</td>
</tr>
<tr>
<td>409</td>
<td>`AUCTION_NOT_REPORTABLE`</td>
<td>신고할 수 없는 경매 상태</td>
</tr>
<tr>
<td>409</td>
<td>`DUPLICATE_AUCTION_REPORT`</td>
<td>동일 경매에 중복 신고함</td>
</tr>
</table>
다음 기존 오류는 삭제합니다.
- `NOT_DEAL_PARTICIPANT`
- `DEAL_NOT_FOUND`
- `DUPLICATE_DEAL_REPORT`
## 처리 흐름
```plain text
인증 사용자 확인
→ 경매 및 WAITING/LIVE 상태 확인
→ 본인 경매 신고 여부 확인
→ 신고 내용·증빙 검증
→ 중복 신고 확인
→ AuctionReport 생성
→ RECEIVED 응답 반환
→ 운영자 검토
→ 필요 시 DEAL-09 호출
```

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [경매 신고 접수 원본](https://app.notion.com/b7d776ebfd6882709d4601fa4443e319)

