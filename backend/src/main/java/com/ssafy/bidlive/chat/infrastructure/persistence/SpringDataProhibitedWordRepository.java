package com.ssafy.bidlive.chat.infrastructure.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SpringDataProhibitedWordRepository
	extends JpaRepository<ProhibitedWordJpaEntity, Long> {

	@Query("""
		select prohibitedWord.word
		from ProhibitedWordJpaEntity prohibitedWord
		where prohibitedWord.active = true
		order by prohibitedWord.id
		""")
	List<String> findAllActiveWords();
}
