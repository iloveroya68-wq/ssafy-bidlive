CREATE TABLE betting_point_grant (
    grant_id        UUID PRIMARY KEY,
    member_id       BIGINT NOT NULL,
    wallet_id       BIGINT NOT NULL,
    grant_type      VARCHAR(30) NOT NULL,
    source_id       VARCHAR(100) NOT NULL,
    amount          BIGINT NOT NULL,
    policy_version  VARCHAR(30) NOT NULL,
    ledger_id       BIGINT NOT NULL UNIQUE,
    occurred_at     TIMESTAMPTZ NOT NULL,
    granted_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_betting_point_grant_member
        FOREIGN KEY (member_id)
            REFERENCES member (member_id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_betting_point_grant_wallet
        FOREIGN KEY (wallet_id)
            REFERENCES wallet (wallet_id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_betting_point_grant_ledger
        FOREIGN KEY (ledger_id)
            REFERENCES betting_point_ledger (betting_point_ledger_id)
            ON DELETE RESTRICT,
    CONSTRAINT uq_betting_point_grant_business_key
        UNIQUE (member_id, grant_type, source_id),
    CONSTRAINT ck_betting_point_grant_type
        CHECK (
            grant_type IN (
                'SIGNUP',
                'DAILY_ATTENDANCE',
                'LIVE_WATCH'
            )
        ),
    CONSTRAINT ck_betting_point_grant_amount
        CHECK (amount > 0),
    CONSTRAINT ck_betting_point_grant_source
        CHECK (BTRIM(source_id) <> ''),
    CONSTRAINT ck_betting_point_grant_policy_version
        CHECK (BTRIM(policy_version) <> '')
);

CREATE INDEX idx_betting_point_grant_member_history
    ON betting_point_grant (
        member_id,
        granted_at DESC,
        grant_id DESC
    );

CREATE INDEX idx_betting_point_grant_source
    ON betting_point_grant (grant_type, source_id);
