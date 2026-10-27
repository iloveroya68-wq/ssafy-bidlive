// ROOM-01 상품 이미지 업로드 — `POST /api/v1/product-images`
//
// 방 개설(`POST /auction-rooms`)이 `product.imageUrl`을 필수로 받는데, 그 URL은 **여기서 발급받은 값**이어야
// 한다 — 서버가 생성 시 "현재 판매자가 업로드한 URL인지"까지 검증한다. 외부 URL을 직접 넣을 수 없다.
//
// 명세 상태는 `검토 중`이지만(저장소·보존 정책 미확정) 경로·요청·응답 형식은 확정된 것으로 보고 배선한다.
// 대표 이미지 **1개**만 받는다 — DB의 `product_image`는 다중이지만 명세가 단일이 정본이다.
//
import { http } from '@/shared/api/http'
import type { IsoDateTime } from '@/shared/api/types'

const UPLOAD_PATH = '/product-images'

/** 201 Created (명세 Response Field와 1:1) */
export interface UploadProductImageResponse {
  /**
   * 업로드한 이미지 식별자. **공용 `Id`(number)가 아니라 문자열이다** —
   * 명세 Response Field 표가 `String`으로 정의했고 예시도 UUID다
   * (`019d2f6f-2ce5-7f73-8cb5-00db2f54d69d`). "파일명은 서버가 임의 생성한다"는 보안 규칙과 맞는다.
   */
  imageId: string
  /** 방 생성의 `product.imageUrl`에 그대로 전달할 서버 관리 URL */
  imageUrl: string
  createdAt: IsoDateTime
}

/**
 * 상품 대표 이미지 1개 업로드.
 * 형식·용량 제한은 서버 설정이라 화면에서 미리 판정하지 않는다 —
 * `UNSUPPORTED_IMAGE_TYPE`(400) · `IMAGE_SIZE_EXCEEDED`(413)로 돌아온다.
 */
export async function uploadProductImage(file: File): Promise<string> {
  const form = new FormData()
  form.append('image', file)
  const { data } = await http.post<UploadProductImageResponse>(UPLOAD_PATH, form)
  return data.imageUrl
}
