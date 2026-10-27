// CHAT-06 질문 큐의 조회·페이지 이어받기 (S15P11A704-371).
//
// 화면에서 떼어 낸 이유는 **커서 부기가 눈으로 안 잡히기 때문**이다. 큐가 100건을 넘긴 상태를
// 브라우저에서 만들려면 실제로 질문 100건을 넣어야 하고, 「재시드와 겹칠 때」 같은 경합은
// 손으로 아예 못 만든다. 여기 있으면 스토어 자리에 평범한 객체를 꽂고 검증할 수 있다.
//
// 질문은 채팅 버퍼가 아니라 전용 API가 소스다 — 식별자(`questionId` ULID)도 다르고 PENDING만 온다.
// REST로 시드한 뒤 소켓 `/questions`(QUESTION_ADDED/REMOVED)가 실시간 유지한다(S15P11A704-364).
//
// 조회·제거가 **같은 403(`NOT_AUCTION_HOST`)을 쓴다.** 그래서 답변 완료도 여기서 처리한다 —
// 화면이 따로 잡던 시절엔 제거만 403을 오류 문구로 흘려서 조회 때와 화면이 달랐다(S15P11A704-680).
import { computed, ref } from 'vue'
import { getQuestionQueue, resolveQuestion, type QuestionQueueItem } from '@/shared/api/questions'
import { expectQuestionRemoval } from '@/features/live/socket/handlers'
import { API_ERROR_CODE, hasErrorCode, toApiError } from '@/shared/api/errors'
import type { CursorPage } from '@/shared/api/types'
import type { QuestionItem } from '@/stores/live'

/**
 * 진입 시 미리 받아 둘 페이지 수 — 20건씩 5페이지(100건).
 *
 * 상한 자체는 남긴다: 대기 질문이 수백 건이면 그만큼 진입이 느려지고, 판매자는 어차피 위에서부터 답한다.
 * 바꾼 것은 **거기서 끝나지 않는다**는 점이다 — 남은 커서를 들고 있다가 「더 보기」로 이어 받는다.
 *
 * 예전에는 조용히 잘린 채 끝났고, 주석은 그 잔여를 소켓 배선(-328/-364)에 떠넘기고 있었다.
 * **사실이 아니다** — `/questions`는 신규 `QUESTION_ADDED` 델타만 나르므로 잘린 구간은 영영 안 들어오고,
 * 재연결 재시드도 같은 REST 경로라 같은 자리에서 잘린다.
 */
export const QUESTION_SEED_PAGES = 5

/** 큐를 담는 곳. 실제로는 `live` 스토어지만, 검증에서 평범한 객체를 꽂을 수 있게 좁게 받는다 */
export interface QuestionSink {
  questions: QuestionItem[]
}

const toItem = (item: QuestionQueueItem): QuestionItem => ({
  questionId: item.questionId,
  nickname: item.nickname,
  content: item.content,
})

export function useQuestionQueue(
  auctionId: () => number,
  sink: QuestionSink,
  /** 서버가 호스트가 아니라고 거절했다 — 화면이 안내 카드로 바꾼다 */
  onHostRejected: () => void,
) {
  const error = ref<string | null>(null)
  const loadingMore = ref(false)
  /** 아직 안 받은 다음 페이지의 커서. null이면 더 없다 */
  const cursor = ref<string | null>(null)
  const hasMore = computed(() => cursor.value !== null)
  /** 직전 「더 보기」가 실제로 붙인 건수 — 통지 문구에 쓴다 */
  const lastAppended = ref(0)

  /**
   * REST로 받은 질문의 id 집합.
   *
   * **소켓이 넣은 것과 가르는 유일한 근거다.** 스토어 항목에 `createdAt`이 없어 정렬로는 못 가른다.
   * 왜 갈라야 하냐면 — 소켓이 넣은 것(`handlers.handleQuestionAdded`)은 항상 맨 뒤에 붙고 그게
   * 제일 **최신**인데, 「더 보기」로 받는 구간은 그보다 **오래된** 자리다. 그냥 뒤에 붙이면
   * `createdAt ASC`가 깨진다. 그래서 REST 구간 뒤·소켓 구간 앞에 끼워 넣는다.
   */
  let restIds = new Set<string>()

  /**
   * 조회 세대. 재연결 재시드(`load`)와 「더 보기」(`loadMore`)가 겹치면 **늦게 도착한 쪽이 커서를 덮는다** —
   * 재시드가 세운 커서를 낡은 응답이 덮으면, 방금 받은 목록에 남은 게 있는데도 「더 보기」가 사라진다.
   *
   * ⚠️ **번호를 올리는 것은 `load`뿐이다. 둘의 손실이 대칭이 아니다.**
   * 「더 보기」가 취소되면 한 페이지를 잃고 버튼을 다시 누르면 끝이지만, 재시드가 취소되면
   * **끊긴 동안 들어온 질문 전량**을 잃고 다음 재연결까지 복구 지점이 없다 — 이 티켓이 막으려던
   * 「답변 안 한 질문이 큐에 안 보임」과 같은 증상이다. 그래서 `loadMore`는 번호를 읽기만 한다.
   */
  let seq = 0

  /** 재시드가 도는 중인가 — 그동안 「더 보기」를 아예 안 내보낸다(아래 `loadMore` 주석) */
  const loading = ref(false)

  /**
   * 「더 보기」로 **추가로 받은 페이지 수**. 재시드가 그만큼 더 받아 원래 자리로 돌아간다.
   *
   * 없으면 재시드가 상한(5페이지)까지만 받고 끝나서, 「더 보기」로 받아 둔 6페이지가
   * `restIds`에서 빠진다(스토어에는 남아 있는데 소켓 항목으로 재분류된다). 그러면
   *  · 재시드 직후 클릭 → 커서가 6페이지로 되돌아가 같은 걸 또 받는다(붙는 게 없다)
   *  · 그다음 클릭 → 7페이지가 6페이지 **앞**에 끼어 정렬이 다시 깨진다
   *
   * 커서를 이월하는 방법도 있지만 불투명 문자열이라 「어디까지 갔었는지」를 복원할 수 없다.
   * 페이지 수를 세는 편이 상태를 원래대로 되돌린다. 대가는 재연결 때 클릭 횟수만큼
   * 요청이 더 나가는 것뿐이다(보통 0~1회).
   */
  let extraPages = 0

  /** 진입·재시드 — 첫 페이지부터, 「더 보기」로 갔던 만큼 더 받는다 */
  async function load(): Promise<void> {
    const mine = ++seq
    loading.value = true
    error.value = null
    try {
      const items: QuestionQueueItem[] = []
      let next: string | null = null
      const pages = QUESTION_SEED_PAGES + extraPages
      for (let page = 0; page < pages; page += 1) {
        let result: CursorPage<QuestionQueueItem>
        try {
          result = await getQuestionQueue(auctionId(), next === null ? {} : { cursor: next })
        } catch (caught) {
          if (mine !== seq) return
          // 첫 페이지는 커서를 안 보내므로 여기 안 온다 — 아래는 2페이지 이후의 커서 실패만이다.
          if (next === null) throw caught
          if (!hasErrorCode(toApiError(caught), API_ERROR_CODE.INVALID_CURSOR)) throw caught
          // 쥐고 있던 커서가 그사이 죽었다(그 질문 메시지가 소프트 삭제됨). **여기서 시드를 끝내고
          // 받은 만큼 반영한다** — 통째로 던지면 이미 받은 앞 페이지까지 버려지고, 그 앞 페이지가
          // 제일 오래된 = 판매자가 제일 먼저 답할 질문이다. 한 페이지 못 받았다고 큐를 비울 이유가 없다.
          //
          // 죽은 커서는 그대로 쥔다. 남은 게 있다는 사실은 맞으므로 「더 보기」는 계속 떠야 하고,
          // 눌러서 한 번 부딪히면 `loadMore`의 INVALID_CURSOR 복구가 첫 페이지부터 다시 받는다.
          // (그 재조회는 서버가 준 새 커서로 도므로 같은 죽은 커서로 되돌아오지 않는다.)
          break
        }
        if (mine !== seq) return
        items.push(...result.items)
        next = result.hasNext ? result.nextCursor : null
        if (next === null) break
      }
      // 상한에 걸려 남은 자리를 기억한다. 다 받았으면 null이라 「더 보기」가 안 뜬다.
      cursor.value = next
      const seeded = items.map(toItem)
      restIds = new Set(seeded.map((q) => q.questionId))
      // 시드가 델타를 덮지 않게 — 소켓이 이미 넣은 것은 뒤에 남긴다(그쪽이 더 최신이다).
      const fromSocket = sink.questions.filter((q) => !restIds.has(q.questionId))
      sink.questions = [...seeded, ...fromSocket]
    } catch (caught) {
      if (mine !== seq) return
      const apiError = toApiError(caught)
      // 질문 큐는 호스트 전용이라 아니면 여기서 막힌다. 진입 판정은 `isSeller`가 하고,
      // 이건 서버가 화면 판정과 다르게 본 경우의 방어다.
      if (hasErrorCode(apiError, API_ERROR_CODE.NOT_AUCTION_HOST)) {
        onHostRejected()
        return
      }
      error.value = apiError.message
    } finally {
      // 더 새로운 재시드가 이미 돌고 있으면 그쪽이 주인이다 — 여기서 풀면 그 사이 「더 보기」가 끼어든다.
      if (mine === seq) loading.value = false
    }
  }

  /**
   * 상한 뒤로 남은 질문을 한 페이지씩 이어 받는다.
   *
   * **재시드가 도는 동안에는 아예 안 나간다.** 번호를 올려 재시드를 취소시키는 것보다
   * 클릭 한 번을 무시하는 쪽이 싸다 — 재시드가 끝나면 커서와 `hasMore`가 갱신되고 버튼은 그대로라
   * 다시 누르면 된다. 잃는 게 없다.
   */
  async function loadMore(): Promise<void> {
    const from = cursor.value
    if (from === null || loadingMore.value || loading.value) return
    // 읽기만 한다 — 올리면 이 클릭이 재시드를 죽인다(위 `seq` 주석).
    const mine = seq
    loadingMore.value = true
    error.value = null
    lastAppended.value = 0
    try {
      const result = await getQuestionQueue(auctionId(), { cursor: from })
      // 그사이 재시드가 돌았으면 **그쪽 커서가 권위다.** 여기서 덮으면 남은 게 있는데 버튼이 사라진다.
      if (mine !== seq) return
      cursor.value = result.hasNext ? result.nextCursor : null
      // 붙은 항목이 0건이어도 센다 — 세는 것은 **커서가 지나간 페이지 수**지 항목 수가 아니다.
      // (그 페이지가 전부 소켓으로 이미 들어왔으면 0건일 수 있고, 재시드는 그래도 거기까지 가야 한다.)
      extraPages += 1
      const known = new Set(sink.questions.map((q) => q.questionId))
      const appended = result.items.filter((item) => !known.has(item.questionId)).map(toItem)
      appended.forEach((q) => restIds.add(q.questionId))
      // REST 구간 뒤, 소켓이 넣은 최신 구간 앞에 끼운다 (위 `restIds` 주석).
      const rest = sink.questions.filter((q) => restIds.has(q.questionId))
      const fromSocket = sink.questions.filter((q) => !restIds.has(q.questionId))
      sink.questions = [...rest, ...appended, ...fromSocket]
      lastAppended.value = appended.length
    } catch (caught) {
      if (mine !== seq) return
      const apiError = toApiError(caught)
      // 커서로 쥔 질문 **메시지가 소프트 삭제**되면(호스트·관리자 삭제, 모더레이션 BLOCKED)
      // 서버가 `INVALID_CURSOR`로 막는다 — 커서 조회는 `is_deleted`만 보므로 그 질문이
      // RESOLVED·FILTERED가 된 것만으로는 여기 안 온다. 죽은 커서는 영영 못 쓰고 다시 눌러도
      // 같은 400만 반복됐다 — 커서를 버리고 첫 페이지부터 다시 받는다.
      // `load`는 커서 없이 시작하고 `loadMore`를 부르지 않으니 되풀이되지 않는다.
      // 재조회 **1페이지**가 실패하면 `load`가 자기 오류를 세운다(기존 오류 표시 그대로).
      // 재조회 도중의 커서 실패는 `load`가 받은 만큼만 반영하고 조용히 끝낸다(위 시드 루프).
      if (hasErrorCode(apiError, API_ERROR_CODE.INVALID_CURSOR)) {
        cursor.value = null
        const before = sink.questions.length
        await load()
        // 복구가 실제로 붙인 만큼을 통지에 싣는다 — 0으로 두면 항목이 늘었는데도 화면이
        // 「새로 불러온 질문은 없어요」로 읽어 준다(`ConsoleView.onLoadMoreQuestions`).
        // 재시드는 서버 삭제분을 반영해 줄어들 수도 있어서 음수는 0으로 깎는다.
        lastAppended.value = Math.max(0, sink.questions.length - before)
        return
      }
      error.value = apiError.message
    } finally {
      // 이 호출의 잠금이므로 세대와 무관하게 푼다 — 안 풀면 진 쪽이 버튼을 영구히 잠근다.
      loadingMore.value = false
    }
  }

  /** 지금 답변 완료 요청이 나가 있는 질문들. 질문끼리 독립이라 하나씩 잠근다 */
  const resolving = ref<string[]>([])

  /**
   * 답변 완료 = PENDING → RESOLVED. 이미 처리된 질문도 204라 재시도가 안전하다(멱등).
   * 잠금은 **질문 단위**다 — 다른 질문 처리를 막을 이유가 없다.
   */
  async function resolve(questionId: string): Promise<void> {
    if (resolving.value.includes(questionId)) return
    resolving.value = [...resolving.value, questionId]
    const drop = () => {
      // 서버 QUESTION_REMOVED 에코가 "이미 없음" 헛경고를 울리지 않게 먼저 예약한다.
      expectQuestionRemoval(questionId)
      sink.questions = sink.questions.filter((item) => item.questionId !== questionId)
    }
    try {
      await resolveQuestion(auctionId(), questionId)
      drop()
    } catch (caught) {
      const apiError = toApiError(caught)
      // 이미 없는 질문이면 화면에서도 지운다 — 서버 기준으로 처리가 끝난 항목이다
      if (hasErrorCode(apiError, API_ERROR_CODE.QUESTION_NOT_FOUND)) return drop()
      // 조회와 **같은 403**이다. 오류 문구만 띄우면 큐는 그대로 남아서 눌러도 계속 실패한다.
      if (hasErrorCode(apiError, API_ERROR_CODE.NOT_AUCTION_HOST)) return onHostRejected()
      error.value = apiError.message
    } finally {
      resolving.value = resolving.value.filter((id) => id !== questionId)
    }
  }

  return { error, loadingMore, hasMore, lastAppended, resolving, load, loadMore, resolve }
}
