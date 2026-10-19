
package com.bugbusters.backend;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class FlywayRepairTest {

    @Test
    void repairFlywayHistory() {
        String url = System.getenv()
                .getOrDefault("DB_URL",
                    "jdbc:postgresql://db:5432/comissai_db");

        String user = System.getenv()
                .getOrDefault("POSTGRES_USER", "postgres");

        String password = System.getenv()
                .getOrDefault("POSTGRES_PASSWORD", "postgres");

        Flyway.configure()
                .dataSource(url, user, password)
                .locations("filesystem:src/main/resources/db/migration")
                .load()
                .repair();
    }
}
