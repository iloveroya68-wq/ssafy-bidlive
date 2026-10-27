-- PRED-03: widen prediction settlement bands and allow the 1.5x reward.
ALTER TABLE prediction_result
    DROP CONSTRAINT ck_prediction_result_multiplier;

ALTER TABLE prediction_result
    ADD CONSTRAINT ck_prediction_result_multiplier
        CHECK (reward_multiplier IN (0, 1, 1.5, 3, 5));
