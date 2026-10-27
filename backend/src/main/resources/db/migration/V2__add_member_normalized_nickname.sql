ALTER TABLE member
    ADD COLUMN normalized_nickname VARCHAR(30);

UPDATE member
SET normalized_nickname = LOWER(BTRIM(nickname))
WHERE nickname IS NOT NULL;

ALTER TABLE member
    ADD CONSTRAINT uq_member_normalized_nickname
        UNIQUE (normalized_nickname);
