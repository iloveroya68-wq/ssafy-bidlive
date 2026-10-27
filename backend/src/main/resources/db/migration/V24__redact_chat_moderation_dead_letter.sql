ALTER TABLE chat_moderation_job
    ALTER COLUMN member_id DROP NOT NULL,
    ALTER COLUMN filtered_content DROP NOT NULL;
