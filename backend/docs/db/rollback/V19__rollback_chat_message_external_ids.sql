-- Flyway Community에서는 자동 실행되지 않는 V19 수동 롤백 스크립트다.
-- V22 작업 큐가 message_id를 참조하므로 외부 ID 컬럼보다 먼저 제거한다.
DROP TABLE IF EXISTS chat_moderation_job;

ALTER TABLE chat_message
    DROP CONSTRAINT IF EXISTS uq_chat_message_sender_client_message,
    DROP CONSTRAINT IF EXISTS uq_chat_message_message_id,
    DROP COLUMN IF EXISTS client_message_id,
    DROP COLUMN IF EXISTS message_id;
