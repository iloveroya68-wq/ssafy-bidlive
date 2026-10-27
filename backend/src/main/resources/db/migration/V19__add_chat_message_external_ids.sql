ALTER TABLE chat_message
    ADD COLUMN message_id VARCHAR(26) NOT NULL,
    ADD COLUMN client_message_id VARCHAR(36);

ALTER TABLE chat_message
    ADD CONSTRAINT uq_chat_message_message_id
        UNIQUE (message_id),
    ADD CONSTRAINT uq_chat_message_sender_client_message
        UNIQUE (sender_member_id, client_message_id);
