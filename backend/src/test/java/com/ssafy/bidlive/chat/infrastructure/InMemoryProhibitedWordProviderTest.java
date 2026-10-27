package com.ssafy.bidlive.chat.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.bidlive.chat.domain.ProhibitedWordRepository;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryProhibitedWordProviderTest {

	private static final long TEST_TIMEOUT_SECONDS = 5;

	private StubProhibitedWordRepository repository;
	private InMemoryProhibitedWordProvider provider;

	@BeforeEach
	void setUp() {
		repository = new StubProhibitedWordRepository();
		provider = new InMemoryProhibitedWordProvider(repository);
	}

	@Test
	void startsWithEmptySnapshot() {
		assertThat(provider.getProhibitedWords()).isEmpty();
	}

	@Test
	void refreshesSnapshotFromRepository() {
		repository.returns(Set.of("금칙어", "나쁜말"));

		boolean refreshed = provider.refresh();

		assertThat(refreshed).isTrue();
		assertThat(provider.getProhibitedWords())
			.containsExactlyInAnyOrder("금칙어", "나쁜말");
	}

	@Test
	void normalizesAndDeduplicatesLoadedWords() {
		Set<String> loadedWords = new HashSet<>();
		loadedWords.add(" 금칙어 ");
		loadedWords.add("금칙어");
		loadedWords.add("");
		loadedWords.add("   ");
		loadedWords.add(null);
		repository.returns(loadedWords);

		boolean refreshed = provider.refresh();

		assertThat(refreshed).isTrue();
		assertThat(provider.getProhibitedWords())
			.containsExactly("금칙어");
	}

	@Test
	void replacesEntireSnapshot() {
		repository.returns(Set.of("기존단어"));
		provider.refresh();
		repository.returns(Set.of("새단어"));

		boolean refreshed = provider.refresh();

		assertThat(refreshed).isTrue();
		assertThat(provider.getProhibitedWords())
			.containsExactly("새단어");
	}

	@Test
	void keepsPreviousSnapshotWhenRefreshFails() {
		repository.returns(Set.of("기존단어"));
		provider.refresh();
		repository.failsWith(new IllegalStateException("load failed"));

		boolean refreshed = provider.refresh();

		assertThat(refreshed).isFalse();
		assertThat(provider.getProhibitedWords())
			.containsExactly("기존단어");
	}

	@Test
	void keepsPreviousSnapshotWhenRepositoryReturnsNull() {
		repository.returns(null);

		boolean refreshed = provider.refresh();

		assertThat(refreshed).isFalse();
		assertThat(provider.getProhibitedWords()).isEmpty();
	}

	@Test
	void treatsEmptyRepositoryResultAsValidSnapshot() {
		repository.returns(Set.of("기존단어"));
		provider.refresh();
		repository.returns(Set.of());

		boolean refreshed = provider.refresh();

		assertThat(refreshed).isTrue();
		assertThat(provider.getProhibitedWords()).isEmpty();
	}

	@Test
	void returnsImmutableSnapshotDetachedFromRepositoryResult() {
		Set<String> loadedWords = new HashSet<>(Set.of("금칙어"));
		repository.returns(loadedWords);
		provider.refresh();

		loadedWords.add("외부변경");

		assertThat(provider.getProhibitedWords())
			.containsExactly("금칙어");
		assertThatThrownBy(
			() -> provider.getProhibitedWords().add("변경시도")
		)
			.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void exposesPreviousSnapshotUntilRefreshCompletes() throws Exception {
		repository.returns(Set.of("기존단어"));
		provider.refresh();
		CountDownLatch loadStarted = new CountDownLatch(1);
		CountDownLatch allowLoadCompletion = new CountDownLatch(1);
		repository.loadsWith(() -> {
			loadStarted.countDown();
			await(allowLoadCompletion);
			return Set.of("새단어");
		});
		ExecutorService executor = Executors.newSingleThreadExecutor();

		try {
			Future<Boolean> refreshResult = executor.submit(provider::refresh);
			assertThat(
				loadStarted.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
			).isTrue();
			assertThat(provider.getProhibitedWords())
				.containsExactly("기존단어");

			allowLoadCompletion.countDown();

			assertThat(
				refreshResult.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
			).isTrue();
			assertThat(provider.getProhibitedWords())
				.containsExactly("새단어");
		} finally {
			allowLoadCompletion.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void rejectsNullRepository() {
		assertThatThrownBy(
			() -> new InMemoryProhibitedWordProvider(null)
		)
			.isInstanceOf(NullPointerException.class)
			.hasMessage("repository");
	}

	private static void await(CountDownLatch latch) {
		try {
			if (!latch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
				throw new IllegalStateException("timed out waiting for test latch");
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("test thread interrupted", exception);
		}
	}

	private static final class StubProhibitedWordRepository
		implements ProhibitedWordRepository {

		private Supplier<Set<String>> loader = Set::of;

		@Override
		public Set<String> findAll() {
			return loader.get();
		}

		void returns(Set<String> words) {
			loadsWith(() -> words);
		}

		void failsWith(RuntimeException exception) {
			loadsWith(() -> {
				throw exception;
			});
		}

		void loadsWith(Supplier<Set<String>> loader) {
			this.loader = loader;
		}
	}
}
