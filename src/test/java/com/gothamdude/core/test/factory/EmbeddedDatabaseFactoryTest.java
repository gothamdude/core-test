package com.gothamdude.core.test.factory;

import com.gothamdude.core.test.config.DbDialect;
import com.gothamdude.core.test.config.DbTestConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EmbeddedDatabaseFactoryTest {

    private EmbeddedDatabase db;

    @AfterEach
    void shutdownDb() {
        if (db != null) {
            db.shutdown();
            db = null;
        }
    }

    // -------------------------------------------------------------------------
    // buildH2Url — pure URL construction (package-private, tested directly)
    // -------------------------------------------------------------------------

    @Test
    void buildH2Url_withH2Dialect_returnsBaseUrlWithNoModeFlag() {
        String url = EmbeddedDatabaseFactory.buildH2Url(DbDialect.H2, "testdb");

        assertAll(
                () -> assertThat(url).startsWith("jdbc:h2:mem:"),
                () -> assertThat(url).contains("DB_CLOSE_DELAY=-1"),
                () -> assertThat(url).contains("DB_CLOSE_ON_EXIT=FALSE"),
                () -> assertThat(url).doesNotContain("MODE=")
        );
    }

    @Test
    void buildH2Url_withOracleDialect_containsModeAndCompatibilityFlags() {
        String url = EmbeddedDatabaseFactory.buildH2Url(DbDialect.ORACLE, "testdb");

        assertAll(
                () -> assertThat(url).contains("MODE=Oracle"),
                () -> assertThat(url).contains("NON_KEYWORDS=VALUE"),
                () -> assertThat(url).contains("DEFAULT_NULL_ORDERING=LAST")
        );
    }

    @Test
    void buildH2Url_withMssqlDialect_containsModeAndCaseInsensitiveFlag() {
        String url = EmbeddedDatabaseFactory.buildH2Url(DbDialect.MSSQL, "testdb");

        assertAll(
                () -> assertThat(url).contains("MODE=MSSQLServer"),
                () -> assertThat(url).contains("CASE_INSENSITIVE_IDENTIFIERS=TRUE")
        );
    }

    @Test
    void buildH2Url_withPostgresqlDialect_containsModeAndNullOrdering() {
        String url = EmbeddedDatabaseFactory.buildH2Url(DbDialect.POSTGRESQL, "testdb");

        assertAll(
                () -> assertThat(url).contains("MODE=PostgreSQL"),
                () -> assertThat(url).contains("DEFAULT_NULL_ORDERING=LAST")
        );
    }

    // -------------------------------------------------------------------------
    // create(DbTestConfig) — integration: actual H2 database is spun up
    // -------------------------------------------------------------------------

    @Test
    void create_withH2DialectAndNoScripts_returnsLiveDatabase() {
        DbTestConfig config = DbTestConfig.builder()
                .dialect(DbDialect.H2)
                .databaseName("h2_noscripts")
                .build();

        db = EmbeddedDatabaseFactory.create(config);

        assertNotNull(db);
    }

    @Test
    void create_withSchemaScript_tableIsQueryable() {
        DbTestConfig config = DbTestConfig.builder()
                .dialect(DbDialect.H2)
                .databaseName("h2_schema")
                .schemaScripts("scripts/sample.sql")
                .build();

        db = EmbeddedDatabaseFactory.create(config);

        JdbcTemplate jdbc = new JdbcTemplate(db);
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM sample", Integer.class);
        assertThat(count).isZero();
    }

    @Test
    void create_withSchemaAndDataScripts_dataIsLoadedInOrder() {
        DbTestConfig config = DbTestConfig.builder()
                .dialect(DbDialect.H2)
                .databaseName("h2_data")
                .schemaScripts("scripts/sample.sql")
                .dataScripts("scripts/sample_data.sql")
                .build();

        db = EmbeddedDatabaseFactory.create(config);

        JdbcTemplate jdbc = new JdbcTemplate(db);
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM sample", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void create_withOracleDialect_returnsLiveDatabase() {
        DbTestConfig config = DbTestConfig.builder()
                .dialect(DbDialect.ORACLE)
                .databaseName("oracle_db")
                .schemaScripts("scripts/sample.sql")
                .build();

        db = EmbeddedDatabaseFactory.create(config);

        assertNotNull(db);
    }

    @Test
    void create_withMssqlDialect_returnsLiveDatabase() {
        DbTestConfig config = DbTestConfig.builder()
                .dialect(DbDialect.MSSQL)
                .databaseName("mssql_db")
                .schemaScripts("scripts/sample.sql")
                .build();

        db = EmbeddedDatabaseFactory.create(config);

        assertNotNull(db);
    }

    @Test
    void create_withPostgresqlDialect_returnsLiveDatabase() {
        DbTestConfig config = DbTestConfig.builder()
                .dialect(DbDialect.POSTGRESQL)
                .databaseName("pg_db")
                .schemaScripts("scripts/sample.sql")
                .build();

        db = EmbeddedDatabaseFactory.create(config);

        assertNotNull(db);
    }

    // -------------------------------------------------------------------------
    // createFromProperties() — reads db-test.properties; absent → H2 defaults
    // -------------------------------------------------------------------------

    @Test
    void createFromProperties_withoutPropertiesFile_defaultsToH2AndCreatesDatabase() {
        // No db-test.properties on the test classpath → factory falls back to H2
        db = EmbeddedDatabaseFactory.createFromProperties();

        assertNotNull(db);
    }

    @Test
    void createFromProperties_withScripts_tableIsQueryable() {
        db = EmbeddedDatabaseFactory.createFromProperties(
                List.of("scripts/sample.sql"),
                List.of()
        );

        JdbcTemplate jdbc = new JdbcTemplate(db);
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM sample", Integer.class);
        assertThat(count).isZero();
    }
}