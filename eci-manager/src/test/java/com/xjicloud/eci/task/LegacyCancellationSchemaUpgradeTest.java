package com.xjicloud.eci.task;

import com.xjicloud.eci.EciManagerApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class LegacyCancellationSchemaUpgradeTest {

    private static final String LEGACY_TASK_ENUM =
            "'CANCELLED','FAILED','PENDING','RUNNING','STARTING','SUCCEEDED'";

    @Test
    void upgradesLegacyEnumOnApplicationStartupAndPreservesHistory() throws SQLException {
        String url = databaseUrl();
        UUID taskId = UUID.randomUUID();
        seedLegacyTask(url, taskId);

        try (ConfigurableApplicationContext context = startApplication(url)) {
            EciTaskRepository tasks = context.getBean(EciTaskRepository.class);
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);

            EciTaskEntity historical = tasks.findById(taskId).orElseThrow();
            assertThat(historical.getStatus()).isEqualTo(TaskStatus.SUCCEEDED);
            assertThat(historical.getName()).isEqualTo("legacy-history");
            assertThat(jdbc.queryForObject(
                    "SELECT legacy_note FROM eci_task WHERE id = ?", String.class, taskId))
                    .isEqualTo("preserve-this-column");
            assertThat(jdbc.queryForObject(
                    "SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS "
                            + "WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME = 'ECI_TASK' AND COLUMN_NAME = 'STATUS'",
                    String.class)).isEqualTo("NO");

            assertThat(jdbc.update("UPDATE eci_task SET status = 'CANCELLING' WHERE id = ?", taskId))
                    .isEqualTo(1);
            EciTaskEntity cancelling = tasks.findById(taskId).orElseThrow();
            assertThat(cancelling.getStatus()).isEqualTo(TaskStatus.CANCELLING);
            assertThat(cancelling.getName()).isEqualTo("legacy-history");
            assertThat(jdbc.queryForObject(
                    "SELECT legacy_note FROM eci_task WHERE id = ?", String.class, taskId))
                    .isEqualTo("preserve-this-column");
        }
    }

    private static ConfigurableApplicationContext startApplication(String url) {
        return new SpringApplicationBuilder(EciManagerApplication.class, SchedulingIsolationConfiguration.class)
                .run(
                        "--spring.profiles.active=test",
                        "--server.port=0",
                        "--spring.datasource.url=" + url,
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=",
                        "--spring.jpa.hibernate.ddl-auto=update",
                        "--spring.main.banner-mode=off",
                        "--eci.mock=true",
                        "--eci.api-key=cancellation-schema-test-key",
                        "--eci.console.open-browser=false",
                        "--eci.mock-auto-complete-sec=0",
                        "--eci.lifecycle-action.dispatch-interval-ms=3600000",
                        "--eci.task-poll-interval-sec=3600",
                        "--eci.poll-interval-ms=1");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SchedulingIsolationConfiguration {

        @Bean(name = "taskScheduler")
        TaskScheduler taskScheduler() {
            return mock(TaskScheduler.class);
        }
    }

    private static String databaseUrl() {
        return "jdbc:h2:mem:eci-cancellation-" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
    }

    private static void seedLegacyTask(String url, UUID taskId) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE eci_task ("
                        + "id UUID NOT NULL PRIMARY KEY, status ENUM(" + LEGACY_TASK_ENUM + ") NOT NULL, "
                        + "name VARCHAR(128), image VARCHAR(512) NOT NULL, cpu DOUBLE PRECISION NOT NULL, "
                        + "memory DOUBLE PRECISION NOT NULL, restart_policy VARCHAR(32) NOT NULL, "
                        + "max_runtime_sec INTEGER NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL, "
                        + "updated_at TIMESTAMP WITH TIME ZONE NOT NULL, "
                        + "legacy_note VARCHAR(64) DEFAULT 'preserve-this-column' NOT NULL)");
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO eci_task (id, status, name, image, cpu, memory, restart_policy, "
                            + "max_runtime_sec, created_at, updated_at, legacy_note) "
                            + "VALUES (?, 'SUCCEEDED', 'legacy-history', 'nginx:alpine', 1, 2, 'Never', 600, "
                            + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'preserve-this-column')")) {
                insert.setObject(1, taskId);
                insert.executeUpdate();
            }
        }
    }
}
