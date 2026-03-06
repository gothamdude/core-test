package com.gothamdude.core.test.runner;

import com.gothamdude.core.test.config.DbDialect;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests that exercise the full stack:
 * EmbeddedDatabaseFactory → H2 → schema scripts → data scripts.
 */
class DbTestContextTest {

    // ----------------------------------------------------------------
    // Basic lifecycle
    // ----------------------------------------------------------------

    @Test
    void shouldStartAndShutdownCleanly() throws Exception {
        try (DbTestContext ctx = DbTestContext.builder()
                .schemaScripts("schema/create_tables.sql")
                .build()) {

            assertThat(ctx.dataSource()).isNotNull();
            assertThat(ctx.jdbc()).isNotNull();
        }
        // No exception == pass
    }

    // ----------------------------------------------------------------
    // Schema script verification
    // ----------------------------------------------------------------

    @Test
    void shouldCreateTablesFromSchemaScript() throws Exception {
        try (DbTestContext ctx = DbTestContext.builder()
                .schemaScripts("schema/create_tables.sql")
                .build()) {

            JdbcTemplate jdbc = ctx.jdbc();

            // H2 information_schema approach
            Integer productTableCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables " +
                            "WHERE LOWER(table_name) = 'product'", Integer.class);
            Integer ordersTableCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables " +
                            "WHERE LOWER(table_name) = 'orders'", Integer.class);

            assertThat(productTableCount).isEqualTo(1);
            assertThat(ordersTableCount).isEqualTo(1);
        }
    }

    // ----------------------------------------------------------------
    // Data script verification
    // ----------------------------------------------------------------

    @Test
    void shouldPopulateTablesFromDataScripts() throws Exception {
        try (DbTestContext ctx = DbTestContext.builder()
                .schemaScripts("schema/create_tables.sql")
                .dataScripts("data/sample_fixture.sql")
                .build()) {

            JdbcTemplate jdbc = ctx.jdbc();

            Integer productCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM product", Integer.class);
            Integer orderCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM orders", Integer.class);

            assertThat(productCount).isEqualTo(3);
            assertThat(orderCount).isEqualTo(2);
        }
    }

    @Test
    void shouldQueryFixtureDataCorrectly() throws Exception {
        try (DbTestContext ctx = DbTestContext.builder()
                .schemaScripts("schema/create_tables.sql")
                .dataScripts("data/sample_fixture.sql")
                .build()) {

            JdbcTemplate jdbc = ctx.jdbc();

            List<Map<String, Object>> pendingOrders = jdbc.queryForList(
                    "SELECT * FROM orders WHERE status = 'PENDING'");

            assertThat(pendingOrders).hasSize(1);
        }
    }

    // ----------------------------------------------------------------
    // Multiple scripts in order
    // ----------------------------------------------------------------

    @Test
    void shouldRunMultipleSchemaAndDataScriptsInOrder() throws Exception {
        // Both scripts point to the same file here; in real usage they'd differ.
        // The point is to verify no ordering / collision errors occur.
        try (DbTestContext ctx = DbTestContext.builder()
                .schemaScripts(List.of("schema/create_tables.sql"))
                .dataScripts(List.of("data/sample_fixture.sql"))
                .build()) {

            Integer total = ctx.jdbc()
                    .queryForObject("SELECT COUNT(*) FROM product", Integer.class);
            assertThat(total).isGreaterThan(0);
        }
    }

    // ----------------------------------------------------------------
    // Dialect selection
    // ----------------------------------------------------------------

    @Test
    void shouldStartWithOracleDialect() throws Exception {
        try (DbTestContext ctx = DbTestContext.builder()
                .dbDialect(DbDialect.ORACLE)
                .schemaScripts("schema/create_tables.sql")
                .dataScripts("data/sample_fixture.sql")
                .build()) {

            // DUAL is an Oracle construct — H2 in Oracle mode supports it
            String result = ctx.jdbc().queryForObject("SELECT 'OK' FROM DUAL", String.class);
            assertThat(result).isEqualTo("OK");
        }
    }

    @Test
    void shouldStartWithPostgresDialect() throws Exception {
        try (DbTestContext ctx = DbTestContext.builder()
                .dbDialect(DbDialect.POSTGRESQL)
                .schemaScripts("schema/create_tables.sql")
                .dataScripts("data/sample_fixture.sql")
                .build()) {

            Integer count = ctx.jdbc()
                    .queryForObject("SELECT COUNT(*) FROM product", Integer.class);
            assertThat(count).isEqualTo(3);
        }
    }

    @Test
    void shouldStartWithMssqlDialect() throws Exception {
        try (DbTestContext ctx = DbTestContext.builder()
                .dbDialect(DbDialect.MSSQL)
                .schemaScripts("schema/create_tables.sql")
                .dataScripts("data/sample_fixture.sql")
                .build()) {

            Integer count = ctx.jdbc()
                    .queryForObject("SELECT COUNT(*) FROM product", Integer.class);
            assertThat(count).isEqualTo(3);
        }
    }

    // ----------------------------------------------------------------
    // Isolation — each test gets a fresh DB
    // ----------------------------------------------------------------

    @Test
    void dbShouldBeEmptyWhenNoDataScriptSupplied() throws Exception {
        try (DbTestContext ctx = DbTestContext.builder()
                .schemaScripts("schema/create_tables.sql")
                // no data scripts
                .build()) {

            Integer count = ctx.jdbc()
                    .queryForObject("SELECT COUNT(*) FROM product", Integer.class);
            assertThat(count).isZero();
        }
    }




}