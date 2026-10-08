package com.gothamdude.core.test.config;

import lombok.Getter;

import java.util.List;
import java.util.Objects;

@Getter
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

        public Builder dialect(DbDialect dialect) {
            this.dbDialect = dialect;
            return this;
        }

        public Builder databaseName(String name) {
            this.databaseName = name;
            return this;
        }

        public Builder withSchemaScripts(String... paths) {
            this.schemaScripts = List.of(paths);
            return this;
        }

        public Builder withSchemaScripts(List<String> paths) {
            this.schemaScripts = List.copyOf(paths);
            return this;
        }

        public Builder withDataScripts(String... paths) {
            this.dataScripts = List.of(paths);
            return this;
        }

        public Builder withDataScripts(List<String> paths) {
            this.dataScripts = List.copyOf(paths);
            return this;
        }

        public DbTestConfig build() {
            return new DbTestConfig(this);
        }
    }
}

