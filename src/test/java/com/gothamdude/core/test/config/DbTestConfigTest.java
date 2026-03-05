package com.gothamdude.core.test.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class DbTestConfigTest {

    // -------------------------------------------------------------------------
    // Defaults
    // -------------------------------------------------------------------------

    @Test
    void build_withNoCustomisation_appliesDefaults() {
        DbTestConfig config = DbTestConfig.builder().build();

        assertEquals(DbDialect.H2, config.getDbDialect());
        assertEquals("testdb", config.getDatabaseName());
        assertTrue(config.getSchemaScripts().isEmpty());
        assertTrue(config.getDataScripts().isEmpty());
    }

    // -------------------------------------------------------------------------
    // dialect()
    // -------------------------------------------------------------------------

    @Test
    void build_withDialect_storesDialect() {
        DbTestConfig config = DbTestConfig.builder()
                .dialect(DbDialect.POSTGRESQL)
                .build();

        assertEquals(DbDialect.POSTGRESQL, config.getDbDialect());
    }

    // -------------------------------------------------------------------------
    // databaseName()
    // -------------------------------------------------------------------------

    @Test
    void build_withDatabaseName_storesDatabaseName() {
        DbTestConfig config = DbTestConfig.builder()
                .databaseName("my_test_db")
                .build();

        assertEquals("my_test_db", config.getDatabaseName());
    }

    // -------------------------------------------------------------------------
    // schemaScripts()
    // -------------------------------------------------------------------------

    @Test
    void build_withSchemaScriptsVarargs_storesScripts() {
        DbTestConfig config = DbTestConfig.builder()
                .schemaScripts("db/schema.sql", "db/constraints.sql")
                .build();

        assertEquals(List.of("db/schema.sql", "db/constraints.sql"), config.getSchemaScripts());
    }

    @Test
    void build_withSchemaScriptsList_storesScripts() {
        List<String> scripts = List.of("db/schema.sql");
        DbTestConfig config = DbTestConfig.builder()
                .schemaScripts(scripts)
                .build();

        assertEquals(scripts, config.getSchemaScripts());
    }

    @Test
    void schemaScripts_isImmutable() {
        DbTestConfig config = DbTestConfig.builder()
                .schemaScripts("db/schema.sql")
                .build();

        assertThrows(UnsupportedOperationException.class,
                () -> config.getSchemaScripts().add("db/extra.sql"));
    }

    // -------------------------------------------------------------------------
    // dataScripts()
    // -------------------------------------------------------------------------

    @Test
    void build_withDataScriptsVarargs_storesScripts() {
        DbTestConfig config = DbTestConfig.builder()
                .dataScripts("db/data.sql", "db/seed.sql")
                .build();

        assertEquals(List.of("db/data.sql", "db/seed.sql"), config.getDataScripts());
    }

    @Test
    void build_withDataScriptsList_storesScripts() {
        List<String> scripts = List.of("db/data.sql");
        DbTestConfig config = DbTestConfig.builder()
                .dataScripts(scripts)
                .build();

        assertEquals(scripts, config.getDataScripts());
    }

    @Test
    void dataScripts_isImmutable() {
        DbTestConfig config = DbTestConfig.builder()
                .dataScripts("db/data.sql")
                .build();

        assertThrows(UnsupportedOperationException.class,
                () -> config.getDataScripts().add("db/extra.sql"));
    }

    // -------------------------------------------------------------------------
    // Null guards
    // -------------------------------------------------------------------------

    @Test
    void build_withNullDialect_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () ->
                DbTestConfig.builder().dialect(null).build());
    }

    @Test
    void build_withNullDatabaseName_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () ->
                DbTestConfig.builder().databaseName(null).build());
    }

    // -------------------------------------------------------------------------
    // Fluent chain / full build
    // -------------------------------------------------------------------------

    @Test
    void build_fullConfiguration_storesAllFields() {
        DbTestConfig config = DbTestConfig.builder()
                .dialect(DbDialect.ORACLE)
                .databaseName("oratest")
                .schemaScripts("db/schema.sql")
                .dataScripts("db/data.sql")
                .build();

        assertAll(
                () -> assertEquals(DbDialect.ORACLE, config.getDbDialect()),
                () -> assertEquals("oratest", config.getDatabaseName()),
                () -> assertEquals(List.of("db/schema.sql"), config.getSchemaScripts()),
                () -> assertEquals(List.of("db/data.sql"), config.getDataScripts())
        );
    }

    // -------------------------------------------------------------------------
    // toString()
    // -------------------------------------------------------------------------

    @Test
    void toString_containsAllRelevantFields() {
        DbTestConfig config = DbTestConfig.builder()
                .dialect(DbDialect.MSSQL)
                .databaseName("mssqltest")
                .build();

        String s = config.toString();
        assertTrue(s.contains("MSSQL"));
        assertTrue(s.contains("mssqltest"));
    }
}