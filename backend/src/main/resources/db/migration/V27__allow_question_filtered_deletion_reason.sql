ALTER TABLE chat_message
    DROP CONSTRAINT ck_chat_message_deletion_reason;

ALTER TABLE chat_message
    ADD CONSTRAINT ck_chat_message_deletion_reason
        CHECK (
            deletion_reason IS NULL
            OR deletion_reason IN (
                'SELLER_DELETED',
                'ADMIN_DELETED',
                'MODERATION_BLOCKED',
                'QUESTION_FILTERED'
            )
        );
