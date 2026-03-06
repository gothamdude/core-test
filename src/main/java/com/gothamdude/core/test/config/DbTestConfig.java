package com.gothamdude.core.test.config;

import java.util.List;
import java.util.Objects;

/**
 * Immutable value-object that captures every configuration parameter needed
 * to bootstrap an embedded test database.
 *
 * <p>Build instances via {@link DbTestConfig.Builder}.
 *
 * <pre>{@code
 * DbTestConfig config = DbTestConfig.builder()
 *         .dialect(DbDialect.ORACLE)
 *         .databaseName("my_test_db")
 *         .schemaScripts("schema/create_tables.sql", "schema/create_indexes.sql")
 *         .dataScripts("data/ref_data.sql", "data/test_fixtures.sql")
 *         .build();
 * }</pre>
 */
public final class DbTestConfig {

    private final DbDialect dbDialect;
    private final String databaseName;
    private final List<String> schemaScripts;
    private final List<String> dataScripts;

    private DbTestConfig(Builder b) {
        this.dbDialect = Objects.requireNonNull(b.dbDialect, "dbDialect must not be null");
        this.databaseName = Objects.requireNonNull(b.databaseName, "databaseName must not be null");
        this.schemaScripts = List.copyOf(b.schemaScripts);
        this.dataScripts = List.copyOf(b.dataScripts);
    }

    public DbDialect getDbDialect() {
        return dbDialect;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public List<String> getSchemaScripts() {
        return schemaScripts;
    }

    public List<String> getDataScripts() {
        return dataScripts;
    }

    @Override
    public String toString() {
        return "DbTestConfig{dialect=%s, db='%s', schema=%s, data=%s}"
                .formatted(dbDialect, databaseName, schemaScripts, dataScripts);
    }

    // ------------------------------------------------------------------
    // Builder
    // ------------------------------------------------------------------
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private DbDialect dbDialect = DbDialect.H2;
        private String databaseName = "testdb";
        private List<String> schemaScripts = List.of();
        private List<String> dataScripts = List.of();

        private Builder() {
        }

        /**
         * Set the dialect (H2 mode) to emulate. Defaults to {@link DbDialect#H2}.
         */
        public Builder dialect(DbDialect dialect) {
            this.dbDialect = dialect;
            return this;
        }

        /**
         * Logical H2 in-memory database name. Defaults to {@code testdb}.
         */
        public Builder databaseName(String name) {
            this.databaseName = name;
            return this;
        }

        /**
         * One or more classpath-relative SQL script paths whose DDL will be
         * executed <em>in order</em> to create all schema objects
         * (tables, sequences, views, …) before each test.
         *
         * <p>Example: {@code "schema/orders.sql", "schema/products.sql"}
         */
        public Builder schemaScripts(String... paths) {
            this.schemaScripts = List.of(paths);
            return this;
        }

        /**
         * Overload accepting a {@link List}.
         */
        public Builder schemaScripts(List<String> paths) {
            this.schemaScripts = List.copyOf(paths);
            return this;
        }

        /**
         * One or more classpath-relative SQL script paths whose DML will be
         * executed <em>in order</em> after all schema scripts to pre-populate
         * tables before each test.
         *
         * <p>Example: {@code "data/reference_data.sql", "data/orders_fixture.sql"}
         */
        public Builder dataScripts(String... paths) {
            this.dataScripts = List.of(paths);
            return this;
        }

        /**
         * Overload accepting a {@link List}.
         */
        public Builder dataScripts(List<String> paths) {
            this.dataScripts = List.copyOf(paths);
            return this;
        }

        public DbTestConfig build() {
            return new DbTestConfig(this);
        }

    }
}

