CREATE TABLE chat_moderation_job (
    job_id BIGSERIAL PRIMARY KEY,
    auction_room_id BIGINT NOT NULL,
    message_id VARCHAR(26) NOT NULL,
    member_id BIGINT NOT NULL,
    filtered_content VARCHAR(250) NOT NULL,
    is_question BOOLEAN NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_at TIMESTAMPTZ NULL,
    last_error_code VARCHAR(40) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_chat_moderation_job_message UNIQUE (message_id),
    CONSTRAINT fk_chat_moderation_job_message
        FOREIGN KEY (message_id) REFERENCES chat_message(message_id),
    CONSTRAINT ck_chat_moderation_job_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'DEAD_LETTER')),
    CONSTRAINT ck_chat_moderation_job_attempt_count
        CHECK (attempt_count >= 0),
    CONSTRAINT ck_chat_moderation_job_content
        CHECK (char_length(filtered_content) BETWEEN 1 AND 250)
);

CREATE INDEX idx_chat_moderation_job_due
    ON chat_moderation_job (status, next_attempt_at, job_id);

CREATE INDEX idx_chat_moderation_job_locked
    ON chat_moderation_job (status, locked_at)
    WHERE status = 'PROCESSING';
