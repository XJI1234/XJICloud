package com.xjicloud.job;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

final class TrainingJobSchemaUpgrade {

    private static final String MIGRATION_KEY = "training_jobs.cloud_status_legacy_claim_backfill";
    private static final Object LOCK = new Object();

    private TrainingJobSchemaUpgrade() {
    }

    static void applyH2LegacyEvidenceBackfill(JdbcTemplate jdbc, PlatformTransactionManager transactions) {
        if (!isH2(jdbc)) {
            return;
        }

        synchronized (LOCK) {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                Integer applied = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM system_config WHERE config_key = ?", Integer.class, MIGRATION_KEY);
                if (applied != null && applied > 0) {
                    return;
                }

                jdbc.update("UPDATE training_jobs SET eci_cleanup_confirmed = FALSE "
                        + "WHERE eci_cleanup_confirmed IS NULL");
                jdbc.update("""
                        UPDATE training_jobs
                        SET dispatch_request_sent_at = COALESCE(dispatch_claimed_at, created_at)
                        WHERE dispatch_request_sent_at IS NULL
                          AND (dispatch_claimed_at IS NOT NULL OR dispatch_claim_active = TRUE)
                        """);
                jdbc.update("""
                        INSERT INTO system_config (config_key, config_value, updated_at)
                        VALUES (?, 'true', CURRENT_TIMESTAMP)
                        """, MIGRATION_KEY);
            });
        }
    }

    private static boolean isH2(JdbcTemplate jdbc) {
        return Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) connection ->
                "H2".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())));
    }
}
