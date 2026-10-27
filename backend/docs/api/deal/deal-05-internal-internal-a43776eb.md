# 7일 자동 구매 확정 처리

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [7일 자동 구매 확정 처리](https://app.notion.com/a43776ebfd6883f7812701c4545336ae)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `DEAL-05` |
| 저장소 도메인 | `deal` |
| Notion 도메인 | 경매 |
| Method | `INTERNAL` |
| Path / Destination | 내부 처리 |
| 상태 | 확정 |
| 인증 | 불필요 |
| 공개 범위 | Internal |
| 담당자 | 신희원 |
| 구현 | ✅ **구현됨** — `DealAutoConfirmService` (S15P11A704-489) — 발송 7일 경과 자동 확정 배치 (`2026-07-30` 확인) |
| 우선순위 | High |
| 연결 구분 | BE 내부 |
| 의존 대상 | PostgreSQL, 포인트 |
| 마지막 수정 | 2026-07-27 02:08:10Z |

## 요약

발송 후 7일이 지난 미확정 거래를 자동 완료하고 에스크로 대금을 판매자에게 지급한다.

## 비고

구매자 수령 확인과 동일한 거래 완료 로직을 사용하며 거래별 멱등 처리

## 규칙
- `WAITING_RECEIPT`이고 `autoConfirmAt <= now`인 거래만 조회
- 구매자 직접 수령 확인과 동일한 거래 완료 서비스를 사용
- 거래 단위 잠금 또는 조건부 상태 변경 사용
- 판매자 지급, 거래 완료, 포인트 원장 기록을 하나의 트랜잭션으로 처리
- `DEAL_COMPLETION_PAYOUT:{dealId}`와 같은 고유 키로 중복 지급 방지
- 다건 배치라면 `FOR UPDATE SKIP LOCKED` 적용 검토

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [7일 자동 구매 확정 처리 원본](https://app.notion.com/a43776ebfd6883f7812701c4545336ae)

