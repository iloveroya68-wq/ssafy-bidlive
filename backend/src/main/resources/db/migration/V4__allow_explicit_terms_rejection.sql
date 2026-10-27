ALTER TABLE member_terms_agreement
    DROP CONSTRAINT ck_member_terms_agreement_state;

ALTER TABLE member_terms_agreement
    ADD CONSTRAINT ck_member_terms_agreement_state
        CHECK (
            (
                is_agreed = TRUE
                    AND agreed_at IS NOT NULL
                    AND withdrawn_at IS NULL
            )
            OR
            (
                is_agreed = FALSE
                    AND agreed_at IS NOT NULL
            )
        );
