BEGIN;

ALTER TABLE training_jobs
    ADD COLUMN IF NOT EXISTS dispatch_request_sent_at timestamp with time zone,
    ADD COLUMN IF NOT EXISTS compute_status varchar(128),
    ADD COLUMN IF NOT EXISTS cloud_status varchar(128),
    ADD COLUMN IF NOT EXISTS compute_status_reason varchar(512),
    ADD COLUMN IF NOT EXISTS eci_cleanup_confirmed boolean NOT NULL DEFAULT false;

UPDATE training_jobs
SET dispatch_request_sent_at = COALESCE(dispatch_claimed_at, created_at)
WHERE dispatch_request_sent_at IS NULL
  AND (dispatch_claimed_at IS NOT NULL OR dispatch_claim_active = true);

COMMIT;
