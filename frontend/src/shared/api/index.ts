// API 계약 레이어 진입점 (S15P11A704-52)
//
// 사용법: `import { getAuctionRooms, type AuctionRoomSummary } from '@/shared/api'`
// AGENTS.md 규칙 8에 따라 HTTP 호출은 전부 이 레이어를 통한다 — 컴포넌트에서 axios·fetch 직접 호출 금지.
//
// 구성
//   http.ts        axios 인스턴스 + Bearer 주입 + 401 refresh 재시도  (S15P11A704-58)
//   authToken.ts   Access Token 보관소 + 세션 종료 훅
//   errors.ts      ApiError 정규화 + 명세의 에러 코드 상수
//   idempotency.ts Idempotency-Key / clientRequestId 생성
//   types.ts       Id·IsoDateTime·CursorPage 등 공통 타입
//   mock.ts        목 어댑터 스위치 (`VITE_API_MOCK=false`로 실 API 전환)
//   auth/members/rooms/bids/predictions/results/wallets  도메인별 모듈  (S15P11A704-59)
//   terms          약관 조회·동의                                      (S15P11A704-316)
//   questions      질문 큐 조회·항목 제거                              (S15P11A704-70)
//   chat           CHAT-02 고정 공지 등록·해제                         (S15P11A704-559)
//   myAuctions     내 경매 목록 3종(낙찰·개최·참여)                    (S15P11A704-335)
//   deals          거래 할 일 (마이페이지 「지금 할 일」)               (S15P11A704-336)
//   uploads        상품 이미지 업로드 (POST /product-images)      (S15P11A704-324)
//   ai             AI-04 상품 등록 사전 검열 (REJECTED만 차단)         (S15P11A704-560)

export * from '@/shared/api/http'
export * from '@/shared/api/authToken'
export * from '@/shared/api/errors'
export * from '@/shared/api/idempotency'
export * from '@/shared/api/types'
export * from '@/shared/api/mock'

export * from '@/shared/api/auth'
export * from '@/shared/api/members'
export * from '@/shared/api/myAuctions'
export * from '@/shared/api/deals'
export * from '@/shared/api/terms'
export * from '@/shared/api/rooms'
export * from '@/shared/api/questions'
export * from '@/shared/api/chat'
export * from '@/shared/api/bids'
export * from '@/shared/api/predictions'
export * from '@/shared/api/results'
export * from '@/shared/api/reports'
export * from '@/shared/api/wallets'
export * from '@/shared/api/uploads'
export * from '@/shared/api/ai'
