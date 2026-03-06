package com.gothamdude.core.test.factory;

import com.gothamdude.core.test.config.DbDialect;
import com.gothamdude.core.test.config.DbTestConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Factory that creates and fully-initialised {@link EmbeddedDatabase} instances
 * backed by H2 v2, with optional dialect emulation for Oracle 11g, MS SQL Server
 * 2022, and PostgreSQL 14.
 *
 * <h2>How it works</h2>
 * <ol>
 *   <li>Reads {@code db-test.properties} from the classpath to determine the
 *       target dialect (and any global defaults).</li>
 *   <li>Constructs an H2 JDBC URL that includes {@code MODE=<dialect>} so that
 *       H2 behaves as the target DB as closely as possible.</li>
 *   <li>Uses Spring's {@link EmbeddedDatabaseBuilder} to run the supplied
 *       schema scripts first and the data scripts afterwards — all in order.</li>
 * </ol>
 *
 * <h2>Properties file (db-test.properties)</h2>
 * <pre>
 * # Dialect: H2 | ORACLE | MSSQL | POSTGRESQL
 * db.dialect=ORACLE
 *
 * # Optional: override the logical in-memory database name
 * db.name=testdb
 * </pre>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * EmbeddedDatabase db = EmbeddedDatabaseFactory.create(
 *         DbTestConfig.builder()
 *                     .schemaScripts("schema/tables.sql", "schema/sequences.sql")
 *                     .dataScripts("data/ref_data.sql", "data/test_fixture.sql")
 *                     .build());
 *
 * // ... run your tests ...
 *
 * db.shutdown();   // always shut down when done
 * }</pre>
 */

public final class EmbeddedDatabaseFactory {

    private static final Logger log = LoggerFactory.getLogger(EmbeddedDatabaseFactory.class);

    /**
     * Classpath location of the properties file that controls global defaults.
     */
    public static final String TEST_PROPERTIES_FILE = "db-test.properties";

    // Property keys
    public static final String TEST_DB_DIALECT = "test.db.dialect";
    public static final String TEST_DB_NAME = "test.db.dialect";

    private EmbeddedDatabaseFactory() {
    }

    { /* static utility */ }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Create an {@link EmbeddedDatabase} using configuration values supplied
     * programmatically via {@link DbTestConfig}.
     *
     * <p>The dialect set on the config object takes priority over
     * {@code db-test.properties}.
     */
    public static EmbeddedDatabase create(DbTestConfig config) {
        log.info("Creating embedded database with config: {}", config);
        return doBuild(config.getDbDialect(),
                config.getDatabaseName(),
                config.getSchemaScripts(),
                config.getDataScripts());
    }

    /**
     * Create an {@link EmbeddedDatabase} by reading all settings from
     * {@code db-test.properties} on the classpath, with no additional scripts.
     *
     * <p>Useful when you want the framework to own all configuration.
     */
    public static EmbeddedDatabase createFromProperties() {
        return createFromProperties(List.of(), List.of());
    }

    /**
     * Create an {@link EmbeddedDatabase} by reading the dialect and database-name
     * from {@code db-test.properties}, but supplying scripts programmatically.
     *
     * @param schemaScripts classpath-relative DDL scripts (executed first, in order)
     * @param dataScripts   classpath-relative DML scripts (executed after schema, in order)
     */
    public static EmbeddedDatabase createFromProperties(List<String> schemaScripts, List<String> dataScripts) {
        Properties properties = loadProperties();
        DbDialect dialect = resolveDbDialect(properties);
        String dbName = properties.getProperty(TEST_DB_NAME, "testdb");

        log.info("Resolved dialect={} dbName={} from {}", dialect, dbName, TEST_PROPERTIES_FILE);
        return doBuild(dialect, dbName, schemaScripts, dataScripts);
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------
    private static EmbeddedDatabase doBuild(DbDialect dialect, String dbName,
                                            List<String> schemaScripts, List<String> dataScripts) {
        String jdbcUrl = buildH2Url(dialect, dbName);
        log.debug("H2 JDBC URL: {}", jdbcUrl);

        EmbeddedDatabaseBuilder builder = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName(buildUniqueName(dbName))
                .generateUniqueName(false);

        // Add schema scripts first DDL
        List<String> allSchemaScripts = new ArrayList<>(schemaScripts);
        if (allSchemaScripts.isEmpty()) {
            log.warn("No schemas scripts supplied - database will have no objects.");
        }
        allSchemaScripts.forEach(builder::addScript);
        // then data scripts (DML)
        List<String> allDataScripts = new ArrayList<>(dataScripts);
        if (allDataScripts.isEmpty()) {
            log.warn("No data scripts supplied — database will have no objects.");
        }
        allDataScripts.forEach(builder::addScript);
        /*
         * H2 does not expose a "URL setter" on EmbeddedDatabaseBuilder, so we
         * inject the MODE and COMPATIBILITY flags via a system property that H2
         * picks up through its URL-alias mechanism.
         *
         * We use the Spring EmbeddedDatabaseBuilder's setName() to derive an
         * in-memory URL of the form:
         *   jdbc:h2:mem:<name>;DB_CLOSE_DELAY=-1;MODE=Oracle;...
         *
         * Spring internally passes the name to H2's EmbeddedDatabase URL builder,
         * but it doesn't let us inject extra parameters.  The cleanest solution
         * without forking Spring is to use the H2 URL alias approach: we set the
         * system property so that every new connection to the named DB picks up
         * the MODE automatically.
         *
         * Alternative: subclass EmbeddedDatabaseConfigurer (see H2EmbeddedDatabaseConfigurer).
         */
        if (dialect != DbDialect.H2) {
            System.setProperty("h2.jdbcUrl." + buildUniqueName(dbName),
                    jdbcUrl);
        }
        EmbeddedDatabase db = builder.build();
        log.info("Embedded '{}' created successfully (dialect={})", dbName, dialect);
        return db;

    }

    /**
     * Builds the full H2 connection URL including MODE and compatibility flags.
     */
    static String buildH2Url(DbDialect dialect, String dbName) {
        StringBuilder url = new StringBuilder("jdbc:h2:mem:")
                .append("")
                .append(";DB_CLOSE_DELAY=-1")
                .append(";DB_CLOSE_ON_EXIT=FALSE");

        if (dialect != DbDialect.H2) {
            url.append(";MODE=").append(dialect.getH2Mode());
        }

        // Dialect-specific compatibility knobs
        switch (dialect) {
            case ORACLE -> url.append(";NON_KEYWORDS=VALUE") // Oracle reserved words
                    .append(";DEFAULT_NULL_ORDERING=LAST");  // Oracle null ordering
            case MSSQL -> url.append(";CASE_INSENSITIVE_IDENTIFIERS=TRUE");
            case POSTGRESQL -> url.append(";DEFAULT_NULL_ORDERING=LAST");
            default -> { /* H2 native — no extras */ }
        }
        return url.toString();
    }

    /**
     * Derives a unique but stable name so that repeated calls within the same
     * JVM do not clash.
     */
    private static String buildUniqueName(String baseName) {
        // Thread name makes it unique per tes thread without randomness
        return baseName + "_" + Thread.currentThread().getName()
                .replaceAll("[^a-zA-Z0-9]", "_");
    }

    // load properties file
    private static Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream is = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(TEST_PROPERTIES_FILE)) {
            if (is == null) {
                log.warn("{} not found on classpath - using defaults (dialect=H2)", TEST_PROPERTIES_FILE);
                return props;
            }
            props.load(is);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + TEST_PROPERTIES_FILE, e);
        }
        return props;
    }

    // Resolve dbDialect via enum
    private static DbDialect resolveDbDialect(Properties props) {
        String raw = props.getProperty(TEST_DB_DIALECT, "H2");
        try {
            return DbDialect.fromString(raw);
        } catch (IllegalArgumentException e) {
            log.error("Invalid db dialect '{}' in {}. Falling back to H2", raw, TEST_PROPERTIES_FILE);
            return DbDialect.H2;
        }
    }


}
