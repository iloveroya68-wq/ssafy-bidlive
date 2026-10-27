// ROOM-02 상품 카테고리 목 시드 — 서버의 `product_category` 마스터를 흉내 낸 것.
// 참조: docs/04-백엔드-스키마-메모.md §3.4
//
// ⚠️ **이제 테스트 픽스처다** (S15P11A704-504). 카테고리 조회가 실 API가 되면서
//    `getAuctionCategories()`의 목 분기가 사라졌고, 소비자는 `mocks/roomServer.ts`와
//    `stores/category.spec.ts` 둘뿐이다 — **화면 코드는 이 파일을 임포트하지 않는다.**
//    명세가 "프론트의 하드코딩된 카테고리 목록 대신 이 API 결과를 사용한다"고 못 박은 부분이라,
//    S15P11A704-397에서 화면 넷의 직접 참조를 끊었다. 그 결론은 그대로다.
//
// 스키마 반영 사항
// - `product_category.code`가 UNIQUE → 전송·매칭 키는 `code`, 화면 표시는 `name`
// - 필터 쿼리는 `categoryId`(Long)라 코드와 ID를 **한 행에** 들고 있어야 대응이 가능하다
// - `displayOrder` 오름차순이 서버 계약이다 (API 레이어가 한 번 더 세운다)

import type { AuctionCategory } from '@/shared/api/rooms'

export const mockCategories: AuctionCategory[] = [
  { categoryId: 1, code: 'ELECTRONICS', name: '전자기기', displayOrder: 1 },
  { categoryId: 2, code: 'HOBBY', name: '취미·수집', displayOrder: 2 },
  { categoryId: 3, code: 'FASHION', name: '패션·잡화', displayOrder: 3 },
  { categoryId: 4, code: 'LIVING', name: '생활용품', displayOrder: 4 },
  { categoryId: 5, code: 'MEDIA', name: '도서·음반', displayOrder: 5 },
  { categoryId: 6, code: 'SPORTS', name: '스포츠·레저', displayOrder: 6 },
]
