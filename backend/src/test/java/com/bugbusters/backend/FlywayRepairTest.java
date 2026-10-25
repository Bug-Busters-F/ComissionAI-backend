
package com.bugbusters.backend;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
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

        try (var conn = java.sql.DriverManager.getConnection(url, user, password)) {
            Flyway.configure()
                    .dataSource(url, user, password)
                    .locations("filesystem:src/main/resources/db/migration")
                    .load()
                    .repair();
        } catch (Exception e) {
            Assumptions.abort("Banco de dados indisponível para FlywayRepair: " + e.getMessage());
        }
    }
}
