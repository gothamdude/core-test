package com.gothamdude.core.test.runner;

import com.gothamdude.core.test.config.DbDialect;
import com.gothamdude.core.test.config.DbTestConfig;
import com.gothamdude.core.test.factory.EmbeddedDatabaseFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;

import javax.sql.DataSource;
import java.util.List;

/**
 * Lifecycle manager for a single embedded test database session.
 *
 * <p>Use this class as the primary handle in your integration / E2E tests.
 * It wires together {@link EmbeddedDatabaseFactory}, {@link JdbcTemplate},
 * and provides a clean {@link #shutdown()} method.
 *
 * <h2>Typical JUnit 5 usage</h2>
 * <pre>{@code
 * class OrderRepositoryIT {
 *
 *     private DbTestContext ctx;
 *
 *     @BeforeEach
 *     void setUp() {
 *         ctx = DbTestContext.builder()
 *                 .schemaScripts("schema/orders.sql")
 *                 .dataScripts("data/orders_fixture.sql")
 *                 .build();
 *     }
 *
 *     @AfterEach
 *     void tearDown() {
 *         ctx.shutdown();
 *     }
 *
 *     @Test
 *     void shouldFindOrderById() {
 *         Integer count = ctx.jdbc()
 *                            .queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
 *         assertThat(count).isEqualTo(3);
 *     }
 * }
 * }</pre>
 */
public final class DbTestContext implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DbTestContext.class);

    private final EmbeddedDatabase embeddedDatabase;
    private final JdbcTemplate jdbcTemplate;

    private DbTestContext(EmbeddedDatabase db) {
        this.embeddedDatabase = db;
        this.jdbcTemplate = new JdbcTemplate(db);
    }

    // ------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------

    /**Raw {@link DataSource} — pass to repositories / DAOs under test. */
    public DataSource dataSource() {
        return embeddedDatabase;
    }

    /**
     * Pre-configured {@link JdbcTemplate} pointing at the embedded DB.
     * Handy for assertions and ad-hoc queries inside tests.
     */
    public JdbcTemplate jdbc() {
        return jdbcTemplate;
    }


    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /**
     * Shuts down the embedded database, releasing all in-memory state.
     * Call this in {@code @AfterEach} (or use try-with-resources).
     */
    public void shutdown() {
        log.info("Shutting down embedded database");
        embeddedDatabase.shutdown();
    }

    /** Implements {@link AutoCloseable} so the context works in try-with-resources. */
    @Override
    public void close() throws Exception {
        shutdown();
    }

    // ------------------------------------------------------------------
    // Builder
    // ------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder{

        private final DbTestConfig.Builder configBuilder = DbTestConfig.builder();

        private Builder(){}

        /**
         * Override the dialect programmatically.  If not called, the dialect
         * is read from {@code db-test.properties} on the classpath.
         */
        public Builder dbDialect(DbDialect dialect){
            configBuilder.dialect(dialect);
            return this;
        }

        /** Logical in-memory database name (default: {@code testdb}). */
        public Builder databaseName(String name){
            configBuilder.databaseName(name);
            return this;
        }

        /**
         * Schema (DDL) scripts to execute before data scripts.
         * Paths are relative to the classpath root.
         *
         * <p>Multiple calls are additive — scripts from all calls are combined.
         */
        public Builder schemaScripts(String... paths){
            configBuilder.schemaScripts(paths);
            return this;
        }

        /** Overload accepting a {@link List}. */
        public Builder schemaScripts(List<String> paths){
            configBuilder.schemaScripts(paths);
            return this;
        }

        /**
         * Data (DML) scripts to execute after schema scripts.
         * Paths are relative to the classpath root.
         */
        public Builder dataScripts(String... paths) {
            configBuilder.dataScripts(paths);
            return this;
        }

        /** Overload accepting a {@link List}. */
        public Builder dataScripts(List<String> paths) {
            configBuilder.dataScripts(paths);
            return this;
        }

        /**
         * Build and immediately start the embedded database.
         *
         * <p>All schema scripts are run first, then all data scripts,
         * all in the order they were supplied.
         */
        public DbTestContext build(){
            DbTestConfig config = configBuilder.build();
            EmbeddedDatabase db = EmbeddedDatabaseFactory.create(config);
            return new DbTestContext(db);
        }

    }

}
