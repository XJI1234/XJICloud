-- PostgreSQL. Apply once BEFORE deploying with ddl-auto=validate.
-- Additive migration; legacy accounts keep phone=NULL and do not require onboarding.
BEGIN;
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone varchar(11);
ALTER TABLE users ADD COLUMN IF NOT EXISTS email varchar(254);
ALTER TABLE users ADD COLUMN IF NOT EXISTS organization varchar(200);
ALTER TABLE users ADD COLUMN IF NOT EXISTS profile_completed_at timestamp(6) with time zone;
CREATE UNIQUE INDEX IF NOT EXISTS users_phone_unique ON users (phone);
COMMIT;
