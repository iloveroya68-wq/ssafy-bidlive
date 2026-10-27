package com.ssafy.bidlive.chat.infrastructure.persistence;

import java.util.Objects;
import java.util.Set;

import com.ssafy.bidlive.chat.domain.ProhibitedWordRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaProhibitedWordRepositoryAdapter
	implements ProhibitedWordRepository {

	private final SpringDataProhibitedWordRepository repository;

	public JpaProhibitedWordRepositoryAdapter(
		SpringDataProhibitedWordRepository repository
	) {
		this.repository = Objects.requireNonNull(repository, "repository");
	}

	@Override
	@Transactional(readOnly = true)
	public Set<String> findAll() {
		return Set.copyOf(repository.findAllActiveWords());
	}
}
