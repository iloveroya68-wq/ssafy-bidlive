ALTER TABLE chat_moderation_job
    ADD COLUMN room_title VARCHAR(100),
    ADD COLUMN product_description TEXT,
    ADD COLUMN product_category_code VARCHAR(50);
