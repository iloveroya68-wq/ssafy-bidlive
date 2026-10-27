package com.ssafy.bidlive.deal.domain;

/**
 * 「내 거래 할 일」이 내려주는 액션 3종이다. 버튼 문구와 안내까지 **서버가 정한다** — 명세가
 * {@code actionLabel}·{@code actionDescription}을 응답 필드로 두었고, 화면이 상태·역할 조합을
 * 다시 해석하지 않게 하려는 설계다.
 *
 * <p>⚠️ <b>상태만으로는 결정되지 않는다</b> (S15P11A704-694). 예전에는 「할 일 판정 규칙」이 상태별로
 * 한 쪽만 주체로 지정해서(주소=구매자, 발송=판매자, 수령=구매자) 상태 하나면 충분했다. 판매자가
 * 발송 확인 뒤에도 목록에 남게 되면서 `WAITING_RECEIPT`가 <b>역할에 따라 갈린다</b> —
 * 구매자에게는 「수령 확인」이고 판매자에게는 <b>버튼 없는 표시</b>다.
 */
public enum DealTodoAction {
	SUBMIT_ADDRESS("배송지 입력", "상품을 받을 배송지를 입력해 주세요.", "address"),
	CONFIRM_SHIPMENT("발송 확인", "상품을 발송한 뒤 발송 확인을 완료해 주세요.", "shipment"),
	CONFIRM_RECEIPT("수령 확인", "상품을 확인한 뒤 수령 확인을 완료해 주세요.", "receipt"),
	/**
	 * 판매자가 발송을 마치고 구매자의 수령 확인을 기다리는 상태다 (S15P11A704-694).
	 *
	 * <p><b>이 항목만 처리할 일이 없다.</b> 「지금 할 일」에 버튼 없는 행이 섞이는 셈이라 계약이
	 * 넓어지는데, 그 대신 판매자가 자기가 보낸 배송지·송장을 수령 확인 때까지 계속 볼 수 있다.
	 * 프론트는 `actionCode`로 액션 있는 항목과 가른다.
	 */
	AWAIT_RECEIPT("수령 대기 중", "구매자가 수령을 확인하면 정산이 완료돼요.", "shipping");

	private final String label;
	private final String description;
	private final String pathSegment;

	DealTodoAction(String label, String description, String pathSegment) {
		this.label = label;
		this.description = description;
		this.pathSegment = pathSegment;
	}

	/**
	 * 상태와 역할에 대응하는 액션. 할 일이 아닌 상태는 조회 단계에서 걸러지므로 여기 오면 계약 위반이다.
	 *
	 * <p>역할을 함께 받는 이유는 `WAITING_RECEIPT` 하나뿐이다 — 나머지 둘은 조회 WHERE가 이미
	 * 한 쪽만 남긴다. 그래도 인자로 받는 편이 「상태만 보면 된다」는 낡은 전제를 남기지 않는다.
	 */
	public static DealTodoAction forStatusAndRole(DealStatus status, DealRole role) {
		return switch (status) {
			case WAITING_ADDRESS -> SUBMIT_ADDRESS;
			case WAITING_SHIPMENT -> CONFIRM_SHIPMENT;
			case WAITING_RECEIPT -> role == DealRole.SELLER ? AWAIT_RECEIPT : CONFIRM_RECEIPT;
			case COMPLETED, AUTO_COMPLETED, CANCELLED -> throw new IllegalArgumentException(
				"할 일이 없는 거래 상태다: " + status
			);
		};
	}

	/** 버튼을 그릴 항목인가. `AWAIT_RECEIPT`만 false다 */
	public boolean actionable() {
		return this != AWAIT_RECEIPT;
	}

	public String label() {
		return label;
	}

	public String description() {
		return description;
	}

	/**
	 * 명세 예시(`/deals/300/address`)를 그대로 만든다.
	 *
	 * <p>프론트는 이 값을 쓰지 않는다 — 와이어프레임에 거래 화면이 없어 대응 라우트를 만들지 않았고
	 * 분기는 {@code actionCode}로 한다(S15P11A704-380에서 조율 중). 명세 응답 필드라 계속 내려준다.
	 */
	public String pathFor(long dealId) {
		return "/deals/" + dealId + "/" + pathSegment;
	}
}
