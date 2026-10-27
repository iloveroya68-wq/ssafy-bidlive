-- PRED-03 can produce error rates above 100% when a prediction exceeds twice the final price.
ALTER TABLE prediction_result
    ALTER COLUMN error_rate TYPE NUMERIC(27, 6);
