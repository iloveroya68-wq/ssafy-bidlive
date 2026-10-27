-- ============================================================================
-- 저장된 이미지 URL의 내부 호스트를 공개 주소로 재작성
-- ============================================================================
-- ⚠️ 기본값은 ROLLBACK 이다. 그대로 실행하면 건수와 검증 결과만 출력하고 아무것도
--    반영하지 않는다(dry run). 반영하려면 파일 맨 끝의 ROLLBACK 을 COMMIT 으로 바꾼다.
--
-- 배경: AWS_S3_PUBLIC_BASE_URL 이 비어 있던 동안 백엔드가 내부 호스트
--       (http://minio:9000/<bucket>/<key>) 를 imageUrl 로 내려주고, 그 문자열이
--       그대로 DB에 저장됐다. 환경변수를 채우면 새 업로드만 고쳐지고 기존 행은
--       계속 깨진 상태로 남는다. 자세한 배경은
--       backend/docs/operations/object-storage-public-url.md 참고.
--
-- 대상 테이블 (둘 다 URL 문자열을 직접 보관한다):
--   product_image.image_url   VARCHAR(500)  상품 대표 이미지
--   live_snapshot.image_url   VARCHAR(500)  라이브 스냅샷
--
-- 방식: 접두사만 치환한다. storage_key 로 URL을 재조립하지 않는 이유는
--       V12 마이그레이션이 기존 행에 'legacy/product-images/<id>' 라는 실제
--       오브젝트와 무관한 키를 넣어 뒀기 때문이다 — 그 키로 URL을 만들면
--       존재하지 않는 오브젝트를 가리킨다. 접두사 치환은 원래 있던 키를
--       그대로 보존한다.
--
-- 멱등성: WHERE 가 내부 접두사로 시작하는 행만 고르므로, 재실행하면 0건이 된다.
--
-- 실행:
--   docker cp backend/scripts/ops/03_rewrite_internal_object_urls.sql bidlive-postgres:/tmp/
--   docker exec -it bidlive-postgres psql -U root -d bidlive -f /tmp/03_rewrite_internal_object_urls.sql
--
-- 실행 전 백업:
--   docker exec bidlive-postgres pg_dump -U root bidlive > bidlive_backup_$(date +%F).sql
-- ============================================================================

\set ON_ERROR_STOP on
\pset pager off
\timing on

-- ── 파라미터: 실제 환경값으로 맞춘다 ──────────────────────────────────────
-- old_prefix 는 아래로 확인한다 (내부 endpoint + 버킷명):
--   docker exec bidlive-backend printenv | grep AWS_S3
-- new_prefix 는 AWS_S3_PUBLIC_BASE_URL 과 정확히 같은 값이어야 한다.
\set old_prefix 'http://minio:9000/bidlive-bucket'
\set new_prefix 'https://i15a704.p.ssafy.io/s3/bidlive-bucket'

BEGIN;

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

\echo
\echo '=== [0] 파라미터 확인 =================================================='
SELECT :'old_prefix' AS old_prefix, :'new_prefix' AS new_prefix;

\echo
\echo '=== [1] 재작성 전 전체 스냅샷 =========================================='
-- 대상만이 아니라 두 테이블의 모든 행을 담는다. 비대상 행이 안 바뀌었는지
-- 검증하려면 그 행들의 원래 값도 필요하다.
CREATE TEMP TABLE rewrite_before ON COMMIT DROP AS
SELECT 'product_image' AS table_name,
       product_image_id AS id,
       image_url,
       (image_url LIKE :'old_prefix' || '/%') AS is_target
FROM product_image
UNION ALL
SELECT 'live_snapshot',
       live_snapshot_id,
       image_url,
       (image_url LIKE :'old_prefix' || '/%')
FROM live_snapshot;

SELECT table_name,
       count(*)                            AS total_rows,
       count(*) FILTER (WHERE is_target)     AS rows_to_rewrite,
       count(*) FILTER (WHERE NOT is_target) AS rows_untouched
FROM rewrite_before
GROUP BY table_name
ORDER BY table_name;

\echo
\echo '--- rows_to_rewrite 가 모두 0 이면 재작성할 것이 없다 (ROLLBACK 으로 종료) ---'
\echo
\echo '--- 재작성 전/후 URL 예시 (최대 5건) ---'
SELECT table_name, id,
       image_url AS before_url,
       :'new_prefix' || substring(image_url from length(:'old_prefix') + 1) AS after_url
FROM rewrite_before
WHERE is_target
ORDER BY table_name, id
LIMIT 5;

\echo
\echo '=== [2] 길이 가드 ====================================================='
-- image_url 은 VARCHAR(500) 이고 공개 접두사가 내부 접두사보다 길다.
-- 500자를 넘는 행이 있으면 UPDATE 가 실패하므로 미리 막는다.
CREATE TEMP TABLE rewrite_len_guard ON COMMIT DROP AS
SELECT table_name, id,
       length(:'new_prefix' || substring(image_url from length(:'old_prefix') + 1)) AS new_length
FROM rewrite_before
WHERE is_target
  AND length(:'new_prefix' || substring(image_url from length(:'old_prefix') + 1)) > 500;

SELECT * FROM rewrite_len_guard;

DO $guard$
DECLARE
    over_limit bigint;
BEGIN
    SELECT count(*) INTO over_limit FROM rewrite_len_guard;
    IF over_limit > 0 THEN
        RAISE EXCEPTION
            '재작성 후 500자를 넘는 행이 %건 있습니다. image_url 컬럼 길이를 먼저 늘려야 합니다.',
            over_limit;
    END IF;
    RAISE NOTICE '길이 가드 통과 (500자 초과 0건)';
END
$guard$;

\echo
\echo '=== [3] 재작성 실행 ==================================================='

UPDATE product_image
SET image_url = :'new_prefix' || substring(image_url from length(:'old_prefix') + 1)
WHERE image_url LIKE :'old_prefix' || '/%';

UPDATE live_snapshot
SET image_url = :'new_prefix' || substring(image_url from length(:'old_prefix') + 1)
WHERE image_url LIKE :'old_prefix' || '/%';

CREATE TEMP TABLE rewrite_after ON COMMIT DROP AS
SELECT 'product_image' AS table_name, product_image_id AS id, image_url FROM product_image
UNION ALL
SELECT 'live_snapshot', live_snapshot_id, image_url FROM live_snapshot;

\echo
\echo '=== [4] 검증 — 아래가 모두 기대와 같을 때만 COMMIT ===================='

\echo
\echo '--- (4-1) 내부 호스트가 남아 있는 행 (remaining = 0 이어야 한다) ---'
SELECT table_name,
       count(*) FILTER (WHERE image_url LIKE :'old_prefix' || '/%') AS remaining
FROM rewrite_after
GROUP BY table_name
ORDER BY table_name;

\echo
\echo '--- (4-2) 대상 행이 기대한 URL로 바뀌었는지 (mismatched = 0 이어야 한다) ---'
SELECT b.table_name,
       count(*) AS targets,
       count(*) FILTER (
           WHERE a.image_url = :'new_prefix'
               || substring(b.image_url from length(:'old_prefix') + 1)
       ) AS rewritten,
       count(*) FILTER (
           WHERE a.image_url <> :'new_prefix'
               || substring(b.image_url from length(:'old_prefix') + 1)
       ) AS mismatched
FROM rewrite_before b
JOIN rewrite_after a USING (table_name, id)
WHERE b.is_target
GROUP BY b.table_name
ORDER BY b.table_name;

\echo
\echo '--- (4-3) 비대상 행이 변경됐는지 (0행이어야 한다) ---'
-- 외부 URL이나 이미 공개 주소인 행은 손대지 않아야 한다.
SELECT b.table_name, b.id, b.image_url AS before_url, a.image_url AS after_url
FROM rewrite_before b
JOIN rewrite_after a USING (table_name, id)
WHERE NOT b.is_target
  AND a.image_url <> b.image_url
ORDER BY b.table_name, b.id;

\echo
\echo '--- (4-4) key 부분이 보존됐는지 — storage_key 와 URL 꼬리 대조 (0행이어야 한다) ---'
-- product_image 만 storage_key 를 갖는다. legacy 행은 키가 실제 오브젝트와
-- 무관하므로 제외한다(그 행은 재작성 전에도 깨져 있었다).
SELECT product_image_id, storage_key, image_url
FROM product_image
WHERE image_url LIKE :'new_prefix' || '/%'
  AND storage_key NOT LIKE 'legacy/%'
  AND substring(image_url from length(:'new_prefix') + 2) <> storage_key
ORDER BY product_image_id;

\echo
\echo '--- (4-5) 재작성된 행 전량 (감사 기록용으로 보관할 것) ---'
SELECT b.table_name, b.id, b.image_url AS before_url, a.image_url AS after_url
FROM rewrite_before b
JOIN rewrite_after a USING (table_name, id)
WHERE b.is_target
ORDER BY b.table_name, b.id;

\echo
\echo '======================================================================'
\echo ' (4-1) remaining = 0, (4-2) mismatched = 0, (4-3)(4-4) 가 0행이면'
\echo ' 이 파일 맨 아래 ROLLBACK 을 COMMIT 으로 바꿔 다시 실행한다.'
\echo
\echo ' 반영 후 확인: (4-5) 의 after_url 중 하나로'
\echo '   curl -I <after_url>   → 200 이어야 한다.'
\echo ' 404 면 오브젝트가 실제로 없는 행이다(재작성 전에도 깨져 있었다) —'
\echo ' 그 상품은 이미지를 다시 업로드하는 편이 맞다.'
\echo '======================================================================'

-- ▼▼▼ 기본값: 아무것도 반영하지 않는다 ▼▼▼
ROLLBACK;
-- ▲▲▲ 반영할 때는 위 줄을 지우고 아래 줄을 살린다 ▲▲▲
-- COMMIT;

-- ============================================================================
-- 사후 롤백 (COMMIT 뒤 되돌려야 할 때)
-- ============================================================================
-- 접두사를 서로 바꿔 같은 스크립트를 돌리면 그대로 되돌아간다:
--   \set old_prefix 'https://i15a704.p.ssafy.io/s3/bidlive-bucket'
--   \set new_prefix 'http://minio:9000/bidlive-bucket'
-- 단 그 사이에 새로 업로드된 행까지 내부 주소로 되돌아가므로,
-- (4-5) 에 남긴 id 목록으로 범위를 좁히는 편이 안전하다.
-- ============================================================================