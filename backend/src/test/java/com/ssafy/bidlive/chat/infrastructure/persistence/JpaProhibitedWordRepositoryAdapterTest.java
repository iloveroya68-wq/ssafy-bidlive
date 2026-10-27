package com.ssafy.bidlive.chat.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;

import com.ssafy.bidlive.chat.domain.ProhibitedWordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JpaProhibitedWordRepositoryAdapter.class)
class JpaProhibitedWordRepositoryAdapterTest {

	@Autowired
	private SpringDataProhibitedWordRepository springDataRepository;

	@Autowired
	private ProhibitedWordRepository prohibitedWordRepository;

	@Test
	void returnsOnlyActiveWords() {
		springDataRepository.saveAllAndFlush(
			List.of(
				ProhibitedWordJpaEntity.active("금칙어"),
				ProhibitedWordJpaEntity.active("차단어"),
				ProhibitedWordJpaEntity.inactive("비활성단어")
			)
		);

		Set<String> words = prohibitedWordRepository.findAll();

		assertThat(words).containsExactlyInAnyOrder("금칙어", "차단어");
	}

	@Test
	void returnsEmptySetWhenNoActiveWordsExist() {
		springDataRepository.saveAndFlush(
			ProhibitedWordJpaEntity.inactive("비활성단어")
		);

		assertThat(prohibitedWordRepository.findAll()).isEmpty();
	}

	@Test
	void storesTrimmedWord() {
		springDataRepository.saveAndFlush(
			ProhibitedWordJpaEntity.active("  금칙어  ")
		);

		assertThat(prohibitedWordRepository.findAll())
			.containsExactly("금칙어");
	}

	@Test
	void rejectsBlankWord() {
		assertThatThrownBy(
			() -> ProhibitedWordJpaEntity.active("   ")
		)
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("word는 비어 있을 수 없습니다.");
	}

	@Test
	void rejectsWordLongerThanColumnLimit() {
		assertThatThrownBy(
			() -> ProhibitedWordJpaEntity.active("가".repeat(101))
		)
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("word는 100자를 초과할 수 없습니다.");
	}

	@Test
	void returnsImmutableSet() {
		springDataRepository.saveAndFlush(
			ProhibitedWordJpaEntity.active("금칙어")
		);
		Set<String> words = prohibitedWordRepository.findAll();

		assertThatThrownBy(() -> words.add("변경시도"))
			.isInstanceOf(UnsupportedOperationException.class);
	}
}
