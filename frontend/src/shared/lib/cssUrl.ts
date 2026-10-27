/**
 * `background-image`에 넣을 `url(...)` 문자열을 만든다.
 *
 * 상품 이미지 URL은 판매자가 등록한 값이라 신뢰 경계 밖이다. 따옴표 없이 그대로 끼우면
 * `")` 가 섞인 값이 `url()`을 빠져나와 인라인 스타일에 CSS를 주입할 수 있다.
 *
 * `encodeURI`는 공백과 큰따옴표를 퍼센트 인코딩한다(괄호·작은따옴표는 그대로 둔다).
 * 큰따옴표로 감싼 형태에서는 그 둘만 막으면 문자열을 빠져나올 수 없다 —
 * 따옴표 안의 `)`는 종결자가 아니기 때문이다.
 */
export function cssUrl(url: string): string {
  return `url("${encodeURI(url)}")`
}
