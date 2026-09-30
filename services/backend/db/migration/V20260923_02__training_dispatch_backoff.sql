BEGIN;

ALTER TABLE training_jobs
    ADD COLUMN IF NOT EXISTS dispatch_next_attempt_at timestamp with time zone;

COMMIT;
