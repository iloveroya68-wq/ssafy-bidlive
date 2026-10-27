-- ============================================================================
-- PNT-05 가입 보너스(SIGNUP 1000BP) 소급 지급 — 사전 조사 스크립트 (읽기 전용)
-- ============================================================================
-- 이 파일은 SELECT / \d 뿐이다. 데이터를 바꾸지 않는다.
--
-- 실행:
--   docker exec -i bidlive-postgres psql -U root -d bidlive \
--     -f /dev/stdin < backend/scripts/ops/01_investigate_signup_grant.sql
--
--   또는 컨테이너로 복사해서:
--   docker cp backend/scripts/ops/01_investigate_signup_grant.sql bidlive-postgres:/tmp/
--   docker exec -it bidlive-postgres psql -U root -d bidlive -f /tmp/01_investigate_signup_grant.sql
-- ============================================================================

\pset pager off
\timing on

\echo
\echo '=== [1] 테이블 구조 ==================================================='
\d betting_point_grant
\d betting_point_ledger
\d wallet
\d member

\echo
\echo '=== [2] 원장 transaction_type 실제 분포 ================================'
\echo '  주의: 온보딩 지급의 원장 타입은 SIGNUP 이 아니라 GRANT 다.'
\echo '        GRANT 는 SIGNUP / DAILY_ATTENDANCE / LIVE_WATCH 가 공유한다.'
SELECT transaction_type, count(*) AS rows, sum(available_delta) AS sum_available_delta
FROM betting_point_ledger
GROUP BY transaction_type
ORDER BY transaction_type;

\echo
\echo '=== [3] betting_point_grant 지급 유형별 분포 ==========================='
SELECT grant_type, policy_version, count(*) AS rows, sum(amount) AS sum_amount,
       min(granted_at) AS first_granted_at, max(granted_at) AS last_granted_at
FROM betting_point_grant
GROUP BY grant_type, policy_version
ORDER BY grant_type, policy_version;

\echo
\echo '=== [4] source_id 형식 확인 (SIGNUP 은 MEMBER:<member_id> 여야 한다) ==='
SELECT member_id, source_id, amount, policy_version,
       source_id = 'MEMBER:' || member_id AS source_id_matches_convention
FROM betting_point_grant
WHERE grant_type = 'SIGNUP'
ORDER BY member_id
LIMIT 10;

\echo
\echo '=== [5] 회원 상태 분포 ================================================'
SELECT status, role, count(*) AS members
FROM member
GROUP BY status, role
ORDER BY status, role;

\echo
\echo '=== [6] 지갑 커버리지 ================================================='
SELECT wallet_type, count(*) AS wallets, sum(available_balance) AS sum_available,
       sum(held_balance) AS sum_held
FROM wallet
GROUP BY wallet_type
ORDER BY wallet_type;

\echo
\echo '--- BETTING 지갑이 없는 회원 (있으면 지급 대상에서 자동 제외된다) ---'
SELECT m.member_id, m.status, m.role, m.created_at
FROM member m
WHERE NOT EXISTS (
    SELECT 1 FROM wallet w
    WHERE w.member_id = m.member_id AND w.wallet_type = 'BETTING'
)
ORDER BY m.member_id;

\echo
\echo '=== [7] 상태별 SIGNUP 지급 이력 대조 =================================='
\echo '  needs_backfill = BETTING 지갑은 있는데 SIGNUP grant 기록이 없는 회원'
SELECT
    m.status,
    count(*) AS members,
    count(*) FILTER (WHERE w.wallet_id IS NULL)         AS no_betting_wallet,
    count(*) FILTER (WHERE g.grant_id IS NOT NULL)      AS already_granted,
    count(*) FILTER (WHERE w.wallet_id IS NOT NULL
                       AND g.grant_id IS NULL)          AS needs_backfill
FROM member m
LEFT JOIN wallet w
       ON w.member_id = m.member_id AND w.wallet_type = 'BETTING'
LEFT JOIN betting_point_grant g
       ON g.member_id = m.member_id
      AND g.grant_type = 'SIGNUP'
      AND g.source_id = 'MEMBER:' || m.member_id
GROUP BY m.status
ORDER BY m.status;

\echo
\echo '=== [8] 지급 대상 목록 (status=ACTIVE 기준) ============================'
SELECT m.member_id, m.status, m.role, m.created_at AS signed_up_at,
       w.wallet_id, w.available_balance AS current_balance
FROM member m
JOIN wallet w
      ON w.member_id = m.member_id AND w.wallet_type = 'BETTING'
WHERE m.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1 FROM betting_point_grant g
      WHERE g.member_id = m.member_id
        AND g.grant_type = 'SIGNUP'
        AND g.source_id = 'MEMBER:' || m.member_id
  )
ORDER BY m.member_id;

\echo
\echo '--- 대상 건수 / 예상 지급 총액 (이 숫자를 02 스크립트의 expected_max 에 쓴다) ---'
SELECT count(*) AS target_count, count(*) * 1000 AS total_amount_to_grant
FROM member m
JOIN wallet w
      ON w.member_id = m.member_id AND w.wallet_type = 'BETTING'
WHERE m.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1 FROM betting_point_grant g
      WHERE g.member_id = m.member_id
        AND g.grant_type = 'SIGNUP'
        AND g.source_id = 'MEMBER:' || m.member_id
  );

\echo
\echo '=== [9] 기존 원장 정합성 (지급 전 기준선) ============================='
\echo '  원장 합계와 지갑 잔액이 어긋난 BETTING 지갑. 지급 후 이 목록이'
\echo '  늘어나지 않아야 한다. 지금 이미 행이 나오면 소급 지급과 무관한'
\echo '  선행 문제이므로 먼저 원인을 확인할 것.'
SELECT w.wallet_id, w.member_id, w.available_balance,
       COALESCE(l.sum_available_delta, 0) AS ledger_sum_available_delta,
       w.available_balance - COALESCE(l.sum_available_delta, 0) AS diff
FROM wallet w
LEFT JOIN (
    SELECT wallet_id, sum(available_delta) AS sum_available_delta
    FROM betting_point_ledger
    GROUP BY wallet_id
) l ON l.wallet_id = w.wallet_id
WHERE w.wallet_type = 'BETTING'
  AND w.available_balance <> COALESCE(l.sum_available_delta, 0)
ORDER BY w.wallet_id;

\echo
\echo '=== [10] SIGNUP 중복 지급 여부 (member 당 1건이어야 한다) =============='
SELECT member_id, count(*) AS signup_grants
FROM betting_point_grant
WHERE grant_type = 'SIGNUP'
GROUP BY member_id
HAVING count(*) > 1
ORDER BY member_id;

\echo
\echo '=== 조사 완료. 데이터는 변경되지 않았다. =============================='