package com.ssafy.bidlive.chat.infrastructure;

import com.ssafy.bidlive.chat.domain.ProhibitedWordProvider;
import com.ssafy.bidlive.chat.domain.ProhibitedWordRepository;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class InMemoryProhibitedWordProvider
	implements ProhibitedWordProvider {

	private static final Logger LOGGER = LoggerFactory.getLogger(
		InMemoryProhibitedWordProvider.class
	);

	private final ProhibitedWordRepository repository;
	private final AtomicReference<Set<String>> cachedWords =
		new AtomicReference<>(Set.of());

	public InMemoryProhibitedWordProvider(
		ProhibitedWordRepository repository
	) {
		this.repository = Objects.requireNonNull(
			repository,
			"repository"
		);
	}

	@Override
	public Set<String> getProhibitedWords() {
		return cachedWords.get();
	}

	public synchronized boolean refresh() {
		try {
			Set<String> refreshedWords = normalize(repository.findAll());
			cachedWords.set(refreshedWords);
			return true;
		} catch (RuntimeException exception) {
			LOGGER.warn(
				"Failed to refresh prohibited word cache; retaining previous snapshot. "
					+ "cachedWordCount={}, failureType={}",
				cachedWords.get().size(),
				exception.getClass().getSimpleName()
			);
			return false;
		}
	}

	private static Set<String> normalize(Set<String> words) {
		Objects.requireNonNull(words, "words");

		return words.stream()
			.filter(Objects::nonNull)
			.map(String::strip)
			.filter(word -> !word.isBlank())
			.collect(Collectors.toUnmodifiableSet());
	}
}
