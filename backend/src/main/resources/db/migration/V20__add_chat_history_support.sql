ALTER TABLE chat_message
    ADD COLUMN masked_fields VARCHAR(30)[] NOT NULL
        DEFAULT ARRAY[]::VARCHAR(30)[];

CREATE INDEX idx_chat_message_visible_history
    ON chat_message (
        auction_room_id,
        created_at DESC,
        chat_message_id DESC
    )
    WHERE is_deleted = FALSE
      AND moderation_status <> 'BLOCKED'
      AND sender_member_id IS NOT NULL
      AND client_message_id IS NOT NULL;
