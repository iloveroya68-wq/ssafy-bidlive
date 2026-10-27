ALTER TABLE trade_point_ledger
    ADD COLUMN reference_type VARCHAR(30),
    ADD COLUMN reference_id BIGINT;

UPDATE trade_point_ledger ledger
SET reference_type = CASE
        WHEN ledger.deal_id IS NOT NULL THEN 'DEAL'
        WHEN ledger.point_charge_id IS NOT NULL THEN 'PAYMENT'
        WHEN ledger.bid_id IS NOT NULL THEN 'BID'
        ELSE NULL
    END,
    reference_id = CASE
        WHEN ledger.deal_id IS NOT NULL THEN ledger.deal_id
        WHEN ledger.point_charge_id IS NOT NULL THEN ledger.point_charge_id
        WHEN ledger.bid_id IS NOT NULL THEN ledger.bid_id
        ELSE NULL
    END;

ALTER TABLE trade_point_ledger
    ADD CONSTRAINT ck_trade_ledger_reference_pair
        CHECK (
            (reference_type IS NULL AND reference_id IS NULL)
                OR
            (reference_type IS NOT NULL AND reference_id IS NOT NULL)
        ),
    ADD CONSTRAINT ck_trade_ledger_reference_type
        CHECK (
            reference_type IS NULL
                OR reference_type IN (
                    'BID',
                    'AUCTION',
                    'DEAL',
                    'PREDICTION',
                    'PAYMENT',
                    'REWARD',
                    'ADMIN'
                )
        );

CREATE INDEX idx_trade_ledger_reference
    ON trade_point_ledger (
        wallet_id,
        reference_type,
        reference_id,
        created_at DESC
    )
    WHERE reference_type IS NOT NULL;

CREATE UNIQUE INDEX uq_trade_ledger_reference_operation
    ON trade_point_ledger (
        wallet_id,
        reference_type,
        reference_id,
        transaction_type
    )
    WHERE reference_type IS NOT NULL
        AND transaction_type IN ('HOLD', 'RELEASE', 'SETTLEMENT');
