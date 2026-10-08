package com.gothamdude.core.test.runner;

import com.gothamdude.core.test.config.DbDialect;
import com.gothamdude.core.test.config.DbTestConfig;
import com.gothamdude.core.test.factory.EmbeddedDatabaseFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;

import javax.sql.DataSource;
import java.util.List;

@Slf4j
public final class DbTestContext implements AutoCloseable {

    private final EmbeddedDatabase embeddedDatabase;
    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private DbTestContext(EmbeddedDatabase db) {
        this.embeddedDatabase = db;
        this.jdbcTemplate = new JdbcTemplate(db);
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);

    }

    public DataSource dataSource() {
        return embeddedDatabase;
    }

    public JdbcTemplate jdbc() {
        return jdbcTemplate;
    }

    public  NamedParameterJdbcTemplate namedJdbc() {
        return namedParameterJdbcTemplate;
    }

    public void shutdown() {
        log.info("Shutting down embedded database");
        embeddedDatabase.shutdown();
    }

    @Override
    public void close() throws Exception {
        shutdown();
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder{

        private final DbTestConfig.Builder configBuilder = DbTestConfig.builder();

        private Builder(){}

        public Builder dbDialect(DbDialect dialect){
            configBuilder.dialect(dialect);
            return this;
        }

        public Builder databaseName(String name){
            configBuilder.databaseName(name);
            return this;
        }

        public Builder schemaScripts(String... paths){
            configBuilder.withSchemaScripts(paths);
            return this;
        }

        public Builder schemaScripts(List<String> paths){
            configBuilder.withSchemaScripts(paths);
            return this;
        }

        public Builder dataScripts(String... paths) {
            configBuilder.withDataScripts(paths);
            return this;
        }

        public Builder dataScripts(List<String> paths) {
            configBuilder.withDataScripts(paths);
            return this;
        }

        public DbTestContext build(){
            DbTestConfig config = configBuilder.build();
            EmbeddedDatabase db = EmbeddedDatabaseFactory.create(config);
            return new DbTestContext(db);
        }

    }

}
