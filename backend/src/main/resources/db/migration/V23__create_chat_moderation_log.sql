CREATE TABLE chat_moderation_log (
    moderation_log_id BIGSERIAL PRIMARY KEY,
    message_id VARCHAR(26) NOT NULL,
    auction_room_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    encrypted_original_content TEXT NOT NULL,
    encryption_iv VARCHAR(24) NOT NULL,
    encryption_key_version VARCHAR(30) NOT NULL,
    original_content_hash CHAR(64) NOT NULL,
    masked_fields VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_chat_moderation_log_message UNIQUE (message_id),
    CONSTRAINT ck_chat_moderation_log_context
        CHECK (auction_room_id > 0 AND member_id > 0),
    CONSTRAINT ck_chat_moderation_log_encryption
        CHECK (
            char_length(encrypted_original_content) > 0
            AND char_length(encryption_iv) > 0
            AND char_length(encryption_key_version) > 0
        ),
    CONSTRAINT ck_chat_moderation_log_hash
        CHECK (original_content_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_chat_moderation_log_masked_fields
        CHECK (char_length(masked_fields) > 0),
    CONSTRAINT ck_chat_moderation_log_retention
        CHECK (expires_at > created_at)
);

CREATE INDEX idx_chat_moderation_log_expires_at
    ON chat_moderation_log (expires_at, moderation_log_id);
