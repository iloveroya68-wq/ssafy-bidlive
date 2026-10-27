ALTER TABLE settlement_account
    ADD COLUMN encrypted_account_number TEXT,
    ADD COLUMN encryption_iv VARCHAR(32),
    ADD COLUMN encryption_key_version VARCHAR(30);

ALTER TABLE settlement_account
    ADD CONSTRAINT ck_settlement_account_encryption_fields
        CHECK (
            (
                encrypted_account_number IS NULL
                AND encryption_iv IS NULL
                AND encryption_key_version IS NULL
            )
            OR
            (
                encrypted_account_number IS NOT NULL
                AND encryption_iv IS NOT NULL
                AND encryption_key_version IS NOT NULL
            )
        );
