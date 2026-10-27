-- ============================================================================
-- PNT-05 가입 보너스(SIGNUP 1000BP) 소급 지급 — 실행 스크립트
-- ============================================================================
-- ⚠️ 기본값은 ROLLBACK 이다. 이 파일을 그대로 실행하면 검증 결과만 출력하고
--    아무것도 반영하지 않는다(dry run). 반영하려면 파일 맨 끝의 ROLLBACK 을
--    COMMIT 으로 바꿔야 한다.
--
-- ── 실행 전 필수 ──────────────────────────────────────────────────────────
--   1) 백업
--      docker exec bidlive-postgres pg_dump -U root bidlive > bidlive_backup_$(date +%F).sql
--   2) 01_investigate_signup_grant.sql 을 돌려 대상 건수를 확인하고
--      아래 :expected_max 를 그 값(또는 약간 여유 있는 상한)으로 설정
--
-- ── 실행 (dry run) ────────────────────────────────────────────────────────
--      docker cp backend/scripts/ops/02_backfill_signup_grant.sql bidlive-postgres:/tmp/
--      docker exec -it bidlive-postgres psql -U root -d bidlive -f /tmp/02_backfill_signup_grant.sql
--
-- ── 되돌리기 ──────────────────────────────────────────────────────────────
--      COMMIT 전:  스크립트가 ROLLBACK 으로 끝나므로 자동으로 되돌아간다.
--      COMMIT 후:  파일 맨 아래 "사후 롤백" 절 참고.
--
-- ── 코드에서 확인한 값 (추측 아님) ────────────────────────────────────────
--   betting_point_ledger.transaction_type = 'GRANT'
--       ← JpaBettingPointGrantPersistenceAdapter.INSERT_LEDGER_QUERY
--       ⚠️ 'SIGNUP' 이 아니다. 'GRANT' 는 SIGNUP/DAILY_ATTENDANCE/LIVE_WATCH 공용이라
--          원장 transaction_type 으로는 중복 판정을 할 수 없다.
--          중복 판정은 betting_point_grant 의 업무 키로만 한다.
--   betting_point_grant.grant_type      = 'SIGNUP'          ← BettingPointGrantType
--   betting_point_grant.source_id       = 'MEMBER:'||member_id
--       ← BettingPointGrantPolicy.isValidSource
--       ⚠️ 이 형식이어야 앱이 나중에 같은 회원에게 다시 지급하려 할 때
--          uq_betting_point_grant_business_key 로 중복을 걸러낸다. 바꾸면 안 된다.
--   amount                              = 1000             ← SIGNUP_AMOUNT
--   policy_version                      = '2026-07-01'     ← BettingPointGrantPolicy.VERSION
--   idempotency_key                     = 'GRANT:'||grant_id
--       ← 앱과 동일한 형식이라 원장 행이 앱이 만든 것과 구분 없이 일관된다.
--   지급 자격                            = member.status ACTIVE 만
--       ← BettingPointGrantService.grantNew 가 ACTIVE 아니면 MEMBER_INACTIVE 로 거부
-- ============================================================================

\set ON_ERROR_STOP on
\pset pager off
\timing on

-- ── 파라미터 ───────────────────────────────────────────────────────────────
\set amount 1000
\set policy_version 2026-07-01
-- 대상 건수가 이 값을 넘으면 스크립트를 중단시킨다. 01 스크립트 [8]의
-- target_count 를 확인하고 그 값으로 맞춘다. (오조사·조건 실수에 대한 안전장치)
-- 2026-08-04 운영 DB 조사 결과 target_count = 6 이었다.
-- 그 사이에 신규 가입이 있으면 대상이 늘어 가드에 걸리므로, 그때는 01 을 다시 돌려
-- 새 target_count 를 확인한 뒤 이 값을 올린다 (막연히 크게 잡지 않는다).
\set expected_max 6

BEGIN;

-- 오래 잠기는 사고를 막는다. 대상이 많아 타임아웃이 나면 값을 늘리기보다
-- member_id 범위로 나눠 여러 번 실행하는 편이 안전하다.
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

\echo
\echo '=== [0] 지급 전 스냅샷 ================================================'

CREATE TEMP TABLE backfill_before ON COMMIT DROP AS
SELECT wallet_id, member_id, available_balance, held_balance
FROM wallet
WHERE wallet_type = 'BETTING';

-- 지급 전 원장 정합성 위반 지갑 (지급 후 이 목록이 늘어나면 안 된다)
CREATE TEMP TABLE backfill_mismatch_before ON COMMIT DROP AS
SELECT w.wallet_id
FROM wallet w
LEFT JOIN (
    SELECT wallet_id, sum(available_delta) AS sum_available_delta
    FROM betting_point_ledger
    GROUP BY wallet_id
) l ON l.wallet_id = w.wallet_id
WHERE w.wallet_type = 'BETTING'
  AND w.available_balance <> COALESCE(l.sum_available_delta, 0);

SELECT count(*) AS betting_wallets, sum(available_balance) AS sum_available_before
FROM backfill_before;

SELECT count(*) AS ledger_mismatch_wallets_before FROM backfill_mismatch_before;

\echo
\echo '=== [1] 지급 대상 확정 ================================================'
-- 정책: status = 'ACTIVE' 만.
--   - WITHDRAWN(탈퇴) 제외: 지급해도 쓸 수 없고 앱도 거부한다.
--   - SUSPENDED(정지) 제외: 앱의 grantNew 가 MEMBER_INACTIVE 로 거부하므로 동일하게 맞췄다.
--     정지 회원도 지급해야 한다면 아래 조건을 status <> 'WITHDRAWN' 으로 바꾼다.
-- BETTING 지갑이 없는 회원은 JOIN 에서 자동으로 빠진다([3]에서 별도 보고).
CREATE TEMP TABLE backfill_target ON COMMIT DROP AS
SELECT
    m.member_id,
    w.wallet_id,
    m.created_at AS signed_up_at
FROM member m
JOIN wallet w
      ON w.member_id = m.member_id
     AND w.wallet_type = 'BETTING'
WHERE m.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1
      FROM betting_point_grant g
      WHERE g.member_id = m.member_id
        AND g.grant_type = 'SIGNUP'
        AND g.source_id = 'MEMBER:' || m.member_id
  );

CREATE TEMP TABLE backfill_guard ON COMMIT DROP AS SELECT :expected_max::bigint AS expected_max;

SELECT count(*) AS target_count, count(*) * :amount AS total_amount_to_grant
FROM backfill_target;

-- 상한 초과면 여기서 예외를 던져 트랜잭션을 중단시킨다.
DO $guard$
DECLARE
    target_count bigint;
    limit_value  bigint;
BEGIN
    SELECT count(*) INTO target_count FROM backfill_target;
    SELECT expected_max INTO limit_value FROM backfill_guard;
    IF target_count > limit_value THEN
        RAISE EXCEPTION
            '지급 대상 %건이 예상 상한 %건을 초과했습니다. 조건을 다시 확인하세요.',
            target_count, limit_value;
    END IF;
    RAISE NOTICE '지급 대상 %건 / 상한 %건 — 가드 통과', target_count, limit_value;
END
$guard$;

\echo
\echo '=== [2] 앱과 동일한 advisory lock 획득 ================================'
-- JpaBettingPointGrantPersistenceAdapter.grantKey 와 같은 키다:
--   'BETTING_GRANT:' || memberId || ':SIGNUP:MEMBER:' || memberId
-- 실행 중에 신규 가입이 들어와도 같은 회원에 대해 앱과 직렬화된다.
SELECT count(*) AS advisory_locks_taken
FROM (
    SELECT pg_advisory_xact_lock(
               hashtextextended(
                   CAST('BETTING_GRANT:' || member_id || ':SIGNUP:MEMBER:' || member_id AS TEXT),
                   0
               )
           )
    FROM backfill_target
    ORDER BY member_id
) locks;

\echo
\echo '=== [3] BETTING 지갑이 없어 제외된 회원 (있으면 별도 판단 필요) ========'
SELECT m.member_id, m.status, m.role, m.created_at
FROM member m
WHERE m.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1 FROM wallet w
      WHERE w.member_id = m.member_id AND w.wallet_type = 'BETTING'
  )
ORDER BY m.member_id;

\echo
\echo '=== [4] 지급 실행: 지갑 + 원장 + grant 를 한 문장으로 ==================='
-- 세 테이블을 하나의 문장에서 처리해 서로 어긋날 수 없게 한다.
--   granted     : wallet UPDATE (RETURNING 은 갱신 후 잔액)
--   ledger_rows : 그 잔액을 *_balance_after 로 원장에 INSERT
--   최종 INSERT : 원장 PK 를 ledger_id 로 grant 에 INSERT
-- NOT EXISTS 를 여기서 한 번 더 본다 — [1] 스냅샷 이후 앱이 지급했을 수 있다.
WITH granted AS (
    UPDATE wallet w
       SET available_balance = w.available_balance + :amount
      FROM backfill_target t
     WHERE w.wallet_id = t.wallet_id
       AND w.wallet_type = 'BETTING'
       AND NOT EXISTS (
           SELECT 1
           FROM betting_point_grant g
           WHERE g.member_id = w.member_id
             AND g.grant_type = 'SIGNUP'
             AND g.source_id = 'MEMBER:' || w.member_id
       )
    RETURNING
        w.wallet_id,
        w.member_id,
        w.available_balance AS available_balance_after,
        w.held_balance      AS held_balance_after,
        t.signed_up_at,
        gen_random_uuid()   AS grant_id
),
ledger_rows AS (
    INSERT INTO betting_point_ledger (
        wallet_id,
        transaction_type,
        available_delta,
        held_delta,
        available_balance_after,
        held_balance_after,
        prediction_id,
        prediction_result_id,
        idempotency_key
    )
    SELECT
        g.wallet_id,
        'GRANT',
        :amount,
        0,
        g.available_balance_after,
        g.held_balance_after,
        NULL,
        NULL,
        'GRANT:' || g.grant_id
    FROM granted g
    RETURNING betting_point_ledger_id, wallet_id
)
INSERT INTO betting_point_grant (
    grant_id,
    member_id,
    wallet_id,
    grant_type,
    source_id,
    amount,
    policy_version,
    ledger_id,
    occurred_at
)
SELECT
    g.grant_id,
    g.member_id,
    g.wallet_id,
    'SIGNUP',
    'MEMBER:' || g.member_id,
    :amount,
    :'policy_version',
    l.betting_point_ledger_id,
    -- occurred_at 은 "지급 사유가 발생한 시점" = 가입 시각.
    -- granted_at 은 DEFAULT CURRENT_TIMESTAMP 로 실제 소급 실행 시각이 남는다.
    -- 두 값의 차이가 곧 소급 지급 여부의 흔적이다.
    g.signed_up_at
FROM granted g
JOIN ledger_rows l ON l.wallet_id = g.wallet_id;

\echo
\echo '=== [5] 검증 — 아래가 모두 기대와 같을 때만 COMMIT ===================='

\echo
\echo '--- (5-1) 처리 건수: 세 값이 모두 같아야 한다 ---'
SELECT
    (SELECT count(*) FROM backfill_target)                            AS targets,
    (SELECT count(*) FROM betting_point_grant g
      JOIN backfill_target t ON t.member_id = g.member_id
     WHERE g.grant_type = 'SIGNUP')                                   AS grant_rows,
    (SELECT count(*) FROM betting_point_ledger l
      JOIN backfill_target t ON t.wallet_id = l.wallet_id
     WHERE l.transaction_type = 'GRANT'
       AND l.betting_point_ledger_id IN (
           SELECT ledger_id FROM betting_point_grant bg
            JOIN backfill_target bt ON bt.member_id = bg.member_id
           WHERE bg.grant_type = 'SIGNUP'))                           AS ledger_rows,
    (SELECT count(*) FROM wallet w
      JOIN backfill_before b ON b.wallet_id = w.wallet_id
      JOIN backfill_target t ON t.wallet_id = w.wallet_id
     WHERE w.available_balance = b.available_balance + :amount)        AS wallets_increased_by_amount;

\echo
\echo '--- (5-2) 대상 지갑 중 증가분이 1000이 아닌 것 (0행이어야 한다) ---'
SELECT w.wallet_id, w.member_id, b.available_balance AS before, w.available_balance AS after,
       w.available_balance - b.available_balance AS delta
FROM wallet w
JOIN backfill_before b ON b.wallet_id = w.wallet_id
JOIN backfill_target t ON t.wallet_id = w.wallet_id
WHERE w.available_balance - b.available_balance <> :amount
ORDER BY w.wallet_id;

\echo
\echo '--- (5-3) 비대상 지갑이 변경됐는지 (0행이어야 한다) ---'
SELECT w.wallet_id, w.member_id, b.available_balance AS before, w.available_balance AS after
FROM wallet w
JOIN backfill_before b ON b.wallet_id = w.wallet_id
WHERE w.wallet_id NOT IN (SELECT wallet_id FROM backfill_target)
  AND (w.available_balance <> b.available_balance OR w.held_balance <> b.held_balance)
ORDER BY w.wallet_id;

\echo
\echo '--- (5-4) 새 원장 행의 *_balance_after 가 지갑 실제 잔액과 다른 것 (0행) ---'
SELECT l.betting_point_ledger_id, l.wallet_id,
       l.available_balance_after, w.available_balance,
       l.held_balance_after, w.held_balance
FROM betting_point_ledger l
JOIN betting_point_grant g ON g.ledger_id = l.betting_point_ledger_id
JOIN backfill_target t     ON t.member_id = g.member_id
JOIN wallet w              ON w.wallet_id = l.wallet_id
WHERE g.grant_type = 'SIGNUP'
  AND (l.available_balance_after <> w.available_balance
    OR l.held_balance_after <> w.held_balance)
ORDER BY l.betting_point_ledger_id;

\echo
\echo '--- (5-5) 원장 정합성 위반 지갑이 새로 늘었는지 (0행이어야 한다) ---'
SELECT w.wallet_id, w.member_id, w.available_balance,
       COALESCE(l.sum_available_delta, 0) AS ledger_sum_available_delta
FROM wallet w
LEFT JOIN (
    SELECT wallet_id, sum(available_delta) AS sum_available_delta
    FROM betting_point_ledger
    GROUP BY wallet_id
) l ON l.wallet_id = w.wallet_id
WHERE w.wallet_type = 'BETTING'
  AND w.available_balance <> COALESCE(l.sum_available_delta, 0)
  AND w.wallet_id NOT IN (SELECT wallet_id FROM backfill_mismatch_before)
ORDER BY w.wallet_id;

\echo
\echo '--- (5-6) SIGNUP 중복 지급 (0행이어야 한다) ---'
SELECT member_id, count(*) AS signup_grants
FROM betting_point_grant
WHERE grant_type = 'SIGNUP'
GROUP BY member_id
HAVING count(*) > 1
ORDER BY member_id;

\echo
\echo '--- (5-7) 잔액 합계 변화 ---'
SELECT
    (SELECT sum(available_balance) FROM backfill_before)                       AS sum_before,
    (SELECT sum(available_balance) FROM wallet WHERE wallet_type = 'BETTING')  AS sum_after,
    (SELECT sum(available_balance) FROM wallet WHERE wallet_type = 'BETTING')
      - (SELECT sum(available_balance) FROM backfill_before)                   AS increase,
    (SELECT count(*) * :amount FROM backfill_target)                           AS expected_increase;

\echo
\echo '--- (5-8) 지급된 회원 목록 (감사 기록용으로 보관할 것) ---'
SELECT g.member_id, g.wallet_id, g.grant_id, g.ledger_id, g.amount,
       g.policy_version, g.source_id, g.occurred_at, g.granted_at
FROM betting_point_grant g
JOIN backfill_target t ON t.member_id = g.member_id
WHERE g.grant_type = 'SIGNUP'
ORDER BY g.member_id;

\echo
\echo '======================================================================'
\echo ' 위 검증 결과를 확인했다면:'
\echo '   - (5-1) 네 값이 모두 같고'
\echo '   - (5-2)~(5-6) 이 모두 0행이고'
\echo '   - (5-7) increase = expected_increase 이면'
\echo ' 이 파일 맨 아래 ROLLBACK 을 COMMIT 으로 바꿔 다시 실행한다.'
\echo '======================================================================'

-- ▼▼▼ 기본값: 아무것도 반영하지 않는다 ▼▼▼
ROLLBACK;
-- ▲▲▲ 반영할 때는 위 줄을 지우고 아래 줄을 살린다 ▲▲▲
-- COMMIT;

-- ============================================================================
-- 사후 롤백 (COMMIT 까지 한 뒤 되돌려야 할 때)
-- ============================================================================
-- 소급 지급분만 정확히 되돌리려면 (5-8)에서 남긴 grant_id 목록이 필요하다.
-- granted_at 실행 창으로 특정할 수도 있다. 아래는 참고용이며,
-- 실행 전에 SELECT 로 대상을 먼저 눈으로 확인할 것.
--
-- BEGIN;
-- CREATE TEMP TABLE undo AS
-- SELECT g.grant_id, g.member_id, g.wallet_id, g.ledger_id, g.amount
-- FROM betting_point_grant g
-- WHERE g.grant_type = 'SIGNUP'
--   AND g.granted_at BETWEEN '<실행 시작 시각>' AND '<실행 종료 시각>';
--
-- SELECT * FROM undo;                       -- ← 먼저 눈으로 확인
--
-- UPDATE wallet w SET available_balance = w.available_balance - u.amount
--   FROM undo u WHERE w.wallet_id = u.wallet_id;
-- DELETE FROM betting_point_grant  WHERE grant_id IN (SELECT grant_id FROM undo);
-- DELETE FROM betting_point_ledger WHERE betting_point_ledger_id IN (SELECT ledger_id FROM undo);
-- -- 검증 후 COMMIT / ROLLBACK
-- ROLLBACK;
--
-- ⚠️ 되돌린 뒤 available_balance 가 음수가 되면 ck_wallet_available_balance 가 막는다.
--    지급 후 회원이 포인트를 써 버렸다면 단순 차감으로 되돌릴 수 없다 —
--    그 경우 원장은 추가전용 원칙에 맞게 보정 원장을 새로 넣는 편이 맞다.
-- ============================================================================