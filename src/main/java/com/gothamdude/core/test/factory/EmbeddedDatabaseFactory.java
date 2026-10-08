package com.gothamdude.core.test.factory;

import com.gothamdude.core.test.config.DbDialect;
import com.gothamdude.core.test.config.DbTestConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;


@Slf4j
public final class EmbeddedDatabaseFactory {

    public static final String TEST_PROPERTIES_FILE = "db-test.properties";
    public static final String TEST_DB_DIALECT = "test.db.dialect";
    public static final String TEST_DB_NAME = "test.db.name";

    private EmbeddedDatabaseFactory() {} /* static utility */

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

        if (dialect != DbDialect.H2) {
            System.setProperty("h2.jdbcUrl." + buildUniqueName(dbName),jdbcUrl);
        }

        EmbeddedDatabase db = builder.build();
        log.info("Embedded '{}' created successfully (dialect={})", dbName, dialect);
        return db;
    }

    static String buildH2Url(DbDialect dialect, String dbName) {
        StringBuilder url = new StringBuilder("jdbc:h2:mem:")
                .append(buildUniqueName(dbName))
                .append(dialect != DbDialect.H2 ? ";MODE=" + dialect.getH2Mode() : "")
                .append(";DB_CLOSE_DELAY=-1")
                .append(";DB_CLOSE_ON_EXIT=FALSE");
        return url.toString();
    }

    private static String buildUniqueName(String baseName) {
        String tempDb = baseName + "_" + UUID.randomUUID();
        return tempDb.substring(0,tempDb.indexOf("-"));
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
