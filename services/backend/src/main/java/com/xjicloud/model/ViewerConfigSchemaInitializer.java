package com.xjicloud.model;

import jakarta.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.Statement;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ViewerConfigSchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(ViewerConfigSchemaInitializer.class);

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS viewer_configs (
                model_id UUID NOT NULL PRIMARY KEY,
                json_payload TEXT NOT NULL,
                updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
            )
            """;

    private final DataSource dataSource;

    public ViewerConfigSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    void ensureTable() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(CREATE_TABLE);
        } catch (Exception ex) {
            log.error("Failed to ensure viewer_configs table exists: {}", ex.getMessage());
            throw new IllegalStateException("viewer_configs table is missing and could not be created", ex);
        }
    }
}
