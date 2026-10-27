ALTER TABLE chat_message
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD COLUMN deleted_by_member_id BIGINT,
    ADD COLUMN deletion_reason VARCHAR(30);

ALTER TABLE chat_message
    ADD CONSTRAINT fk_chat_message_deleted_by_member
        FOREIGN KEY (deleted_by_member_id)
            REFERENCES member (member_id)
            ON DELETE SET NULL,
    ADD CONSTRAINT ck_chat_message_deletion_reason
        CHECK (
            deletion_reason IS NULL
            OR deletion_reason IN (
                'SELLER_DELETED',
                'ADMIN_DELETED',
                'MODERATION_BLOCKED'
            )
        ),
    ADD CONSTRAINT ck_chat_message_deletion_audit
        CHECK (
            (
                is_deleted = FALSE
                AND deleted_at IS NULL
                AND deleted_by_member_id IS NULL
                AND deletion_reason IS NULL
            )
            OR
            (
                is_deleted = TRUE
                AND (
                    (
                        deleted_at IS NULL
                        AND deleted_by_member_id IS NULL
                        AND deletion_reason IS NULL
                    )
                    OR
                    (
                        deleted_at IS NOT NULL
                        AND deletion_reason IS NOT NULL
                    )
                )
            )
        );

CREATE INDEX idx_chat_message_deleted_by
    ON chat_message (deleted_by_member_id, deleted_at DESC)
    WHERE deleted_by_member_id IS NOT NULL;
