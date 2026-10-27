package com.ssafy.bidlive.chat.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.bidlive.chat.domain.ProhibitedWordRepository;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProhibitedWordCacheSchedulerTest {

	private StubProhibitedWordRepository repository;
	private InMemoryProhibitedWordProvider provider;
	private ProhibitedWordCacheScheduler scheduler;

	@BeforeEach
	void setUp() {
		repository = new StubProhibitedWordRepository();
		provider = new InMemoryProhibitedWordProvider(repository);
		scheduler = new ProhibitedWordCacheScheduler(provider);
	}

	@Test
	void loadsProhibitedWordsOnInitialization() {
		repository.returns(Set.of("금칙어", "차단어"));

		scheduler.initialize();

		assertThat(provider.getProhibitedWords())
			.containsExactlyInAnyOrder("금칙어", "차단어");
	}

	@Test
	void failsInitializationWhenFirstLoadFails() {
		repository.failsWith(new IllegalStateException("load failed"));

		assertThatThrownBy(scheduler::initialize)
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("금칙어 캐시 최초 적재에 실패했습니다.");
	}

	@Test
	void refreshesProhibitedWordsAfterInitialization() {
		repository.returns(Set.of("기존금칙어"));
		scheduler.initialize();
		repository.returns(Set.of("새금칙어"));

		scheduler.refresh();

		assertThat(provider.getProhibitedWords())
			.containsExactly("새금칙어");
	}

	@Test
	void keepsPreviousSnapshotWhenScheduledRefreshFails() {
		repository.returns(Set.of("기존금칙어"));
		scheduler.initialize();
		repository.failsWith(new IllegalStateException("load failed"));

		scheduler.refresh();

		assertThat(provider.getProhibitedWords())
			.containsExactly("기존금칙어");
	}

	@Test
	void rejectsNullProvider() {
		assertThatThrownBy(() -> new ProhibitedWordCacheScheduler(null))
			.isInstanceOf(NullPointerException.class)
			.hasMessage("provider");
	}

	private static final class StubProhibitedWordRepository
		implements ProhibitedWordRepository {

		private Set<String> words = Set.of();
		private RuntimeException failure;

		@Override
		public Set<String> findAll() {
			if (failure != null) {
				throw failure;
			}
			return words;
		}

		void returns(Set<String> words) {
			this.words = words;
			this.failure = null;
		}

		void failsWith(RuntimeException failure) {
			this.failure = failure;
		}
	}
}
