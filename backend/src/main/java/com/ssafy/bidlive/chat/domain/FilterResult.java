package com.ssafy.bidlive.chat.domain;

import java.util.Objects;
import java.util.Set;

public record FilterResult(
	String filteredContent,
	Set<MaskingCategory> maskedFields
) {

	public FilterResult {
		Objects.requireNonNull(filteredContent, "filteredContent");
		Objects.requireNonNull(maskedFields, "maskedFields");

		maskedFields = Set.copyOf(maskedFields);
	}

	public boolean masked() {
		return !maskedFields.isEmpty();
	}

	/**
	 * Contact, account, and configured prohibited-word matches are not allowed
	 * in a live-auction chat. The filtered value is still retained in this
	 * result so callers never have to handle the unmasked value downstream.
	 */
	public boolean blocked() {
		return masked();
	}
}
