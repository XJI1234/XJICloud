BEGIN;

ALTER TABLE training_jobs
    ADD COLUMN IF NOT EXISTS dispatch_attempt integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS client_request_id varchar(128),
    ADD COLUMN IF NOT EXISTS eci_task_id uuid,
    ADD COLUMN IF NOT EXISTS dispatch_claimed_at timestamp with time zone,
    ADD COLUMN IF NOT EXISTS dispatch_claim_active boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS dispatch_claim_token uuid,
    ADD COLUMN IF NOT EXISTS dispatch_enqueued_at timestamp with time zone,
    ADD COLUMN IF NOT EXISTS eci_cancellation_pending boolean NOT NULL DEFAULT false;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uq_training_jobs_client_request_id'
          AND conrelid = 'training_jobs'::regclass
    ) THEN
        ALTER TABLE training_jobs
            ADD CONSTRAINT uq_training_jobs_client_request_id UNIQUE (client_request_id);
    END IF;
END $$;

COMMIT;
