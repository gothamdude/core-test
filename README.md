# com.gothamdude.core-test 

> A reusable, framework-agnostic integration and end-to-end testing library backed by H2 v2
> in-memory databases with first-class dialect emulation for **Oracle 11g**, **MS SQL Server 2022**,
> and **PostgreSQL 14**. No Spring Boot required — works in plain JUnit 5 tests.

**Java 17** · **H2 v2.2.224** · **Spring JDBC 6.2** · **JUnit 5.10**

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Dependency Chain](#dependency-chain)
- [End-to-End Data Flow](#end-to-end-data-flow)
- [Config Layer](#config-layer)
  - [DbDialect](#dbdialect)
  - [DbTestConfig](#dbtestconfig)
- [Utility Layer](#utility-layer)
  - [ScriptLoader](#scriptloader)
- [Factory Layer](#factory-layer)
  - [EmbeddedDatabaseFactory](#embeddeddatabasefactory)
- [Runner Layer](#runner-layer)
  - [DbTestContext](#dbtestcontext)
- [SQL Resources](#sql-resources)
- [db-test.properties](#db-testproperties)
- [Known Issues](#known-issues)

---

## Architecture Overview

The library is structured into four distinct layers. Each layer has a single responsibility
and communicates only downward — higher layers orchestrate lower ones, never the reverse.

```
┌───────────────────────────────────────────────────────────────────────────┐
│                             RUNNER LAYER                                  │
│   DbTestContext  — lifecycle manager; entry-point for test classes        │
│   Owns the EmbeddedDatabase, exposes DataSource + JdbcTemplate            │
│   Implements AutoCloseable for try-with-resources support                 │
└──────────────────────────────┬────────────────────────────────────────────┘
                               │ creates via
┌──────────────────────────────▼────────────────────────────────────────────┐
│                            FACTORY LAYER                                  │
│   EmbeddedDatabaseFactory  — builds and initialises the H2 database       │
│   Constructs JDBC URLs with dialect MODE flags                            │
│   Delegates SQL execution to Spring EmbeddedDatabaseBuilder               │
└────────────┬───────────────────────────────────┬──────────────────────────┘
             │ reads                             │ reads
┌────────────▼────────────────┐   ┌─────────────▼──────────────────────────┐
│        CONFIG LAYER         │   │           UTILITY LAYER                │
│  DbDialect   DbTestConfig   │   │   ScriptLoader                         │
│  Enum of 4 dialects         │   │   Classpath validation + content read  │
│  Immutable value object     │   │                                        │
└─────────────────────────────┘   └────────────────────────────────────────┘
```

---

## Dependency Chain

Reading left to right — each component depends on the one to its right.

```
DbTestContext → EmbeddedDatabaseFactory → DbTestConfig + DbDialect → ScriptLoader (optional)
```

---

## End-to-End Data Flow

| Step | What Happens |
|------|-------------|
| **1** | Test class calls `DbTestContext.builder()`. Fluent builder collects dialect, database name, schema paths, and data paths. |
| **2** | Builder delegates to `DbTestConfig.builder()`. Produces an immutable `DbTestConfig` with null-checked fields and defensive copies of all script lists. |
| **3** | `EmbeddedDatabaseFactory.create(config)` is called. Reads dialect and database name, constructs the H2 JDBC URL including the correct `MODE=` flag and compatibility knobs. |
| **4** | For non-H2 dialects: because Spring's `EmbeddedDatabaseBuilder` provides no URL parameter setter, the factory injects a JVM system property (`h2.jdbcUrl.<name>`) so H2 picks up the MODE on first connection. |
| **5** | `EmbeddedDatabaseBuilder` runs all schema scripts first (DDL), then all data scripts (DML), strictly in the order supplied. |
| **6** | `DbTestContext` wraps the live database — exposes it as a raw `DataSource` (for repositories under test) and as a pre-wired `JdbcTemplate` (for in-test assertions). |
| **7** | `shutdown()` or `close()` is called. H2 drops all in-memory state. Each test starts completely clean. |

---

## Config Layer

### DbDialect

**Package:** `com.gothamdude.core.test.config`
**Type:** `enum`

Enumerates every database dialect the library can emulate. Each constant carries the exact
`MODE` string that H2 accepts in its JDBC URL. The enum is the single source of truth for
dialect identity — used as configuration input and as a switch key inside
`EmbeddedDatabaseFactory.buildH2Url()`.

#### Constants

| Constant | `h2Mode` value | Description |
|----------|---------------|-------------|
| `H2` | `"H2"` | Native H2 mode. No emulation flags are added to the JDBC URL. |
| `ORACLE` | `"Oracle"` | Emulates Oracle 11g. Enables DUAL support, ROWNUM, and Oracle-style NULL ordering. |
| `MSSQL` | `"MSSQLServer"` | Emulates SQL Server 2022. Enables case-insensitive identifiers. |
| `POSTGRESQL` | `"PostgreSQL"` | Emulates PostgreSQL 14. Enables Postgres-style NULL ordering. |

#### Methods

| Signature | Returns | Description |
|-----------|---------|-------------|
| `getH2Mode()` | `String` | Returns the H2 MODE string for this dialect (e.g. `"Oracle"`). Used by the factory to build the JDBC URL. |
| `fromString(String value)` | `DbDialect` | Parses a string from a properties file into the matching enum constant. Case-insensitive. Accepts aliases. Throws `IllegalArgumentException` for null or unknown values. |

#### String Aliases Accepted by `fromString()`

| Input (case-insensitive) | Resolves to |
|--------------------------|-------------|
| `H2` | `DbDialect.H2` |
| `ORACLE` | `DbDialect.ORACLE` |
| `MSSQL`, `SQLSERVER`, `MSSQLSERVER` | `DbDialect.MSSQL` |
| `POSTGRESQL`, `POSTGRES` | `DbDialect.POSTGRESQL` |

---

### DbTestConfig

**Package:** `com.gothamdude.core.test.config`
**Type:** `final class` — immutable value object

Captures every parameter needed to bootstrap an embedded test database. Instances are built
exclusively through the nested `Builder` class using a fluent API. Once constructed the
object is fully immutable: script lists are wrapped in `List.copyOf()`, and mandatory fields
(`dbDialect`, `databaseName`) are null-checked at construction time via `Objects.requireNonNull()`.

#### Fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `dbDialect` | `DbDialect` | `H2` | The dialect (H2 mode) to emulate. Cannot be null. |
| `databaseName` | `String` | `"testdb"` | Logical in-memory H2 database name. Cannot be null. |
| `schemaScripts` | `List<String>` | `[]` | Ordered list of classpath-relative DDL script paths. Unmodifiable. |
| `dataScripts` | `List<String>` | `[]` | Ordered list of classpath-relative DML script paths. Unmodifiable. |

#### Builder API

| Method | Description |
|--------|-------------|
| `dialect(DbDialect)` | Sets the H2 mode to emulate. |
| `databaseName(String)` | Sets the logical H2 in-memory database name. |
| `schemaScripts(String...)` | Varargs overload for DDL script paths. |
| `schemaScripts(List<String>)` | List overload for DDL script paths. |
| `dataScripts(String...)` | Varargs overload for DML script paths. |
| `dataScripts(List<String>)` | List overload for DML script paths. |
| `build()` | Validates and constructs an immutable `DbTestConfig`. |

#### Usage Example

```java
DbTestConfig config = DbTestConfig.builder()
        .dialect(DbDialect.ORACLE)
        .databaseName("my_test_db")
        .schemaScripts("schema/orders.sql", "schema/products.sql")
        .dataScripts("data/ref_data.sql", "data/fixture.sql")
        .build();
```

---

## Utility Layer

### ScriptLoader

**Package:** `com.gothamdude.core.test.util`
**Type:** `final class` — static utilities only

A static-only utility that performs early classpath validation of SQL script paths before
they reach `EmbeddedDatabaseBuilder`. Without this guard, a missing script produces a cryptic
Spring or H2 bootstrap exception with no clear indication of which file was missing or why.

Path resolution uses Spring's `ClassPathResource`, which honours the thread-context
classloader — the same mechanism Spring uses internally.

**Responsibilities:**
- Collects all missing paths in a single pass and reports them together
- Includes the human-readable script type (`"schema"` or `"data"`) in the error message
- Reads script content as UTF-8 for debugging / logging during test development
- Silently succeeds when the list is empty

#### Methods

| Signature | Throws | Description |
|-----------|--------|-------------|
| `validate(List<String> scripts, String scriptType)` | `IllegalArgumentException` | Iterates all paths, collects any that do not resolve to a classpath resource, then throws a single exception listing every missing file. |
| `readScript(String classpathPath)` | `IllegalStateException` | Opens the resource and reads all bytes as UTF-8. Throws `IllegalStateException` if the resource is absent. |

> **Design note:** `ScriptLoader` is not automatically called by `EmbeddedDatabaseFactory`
> in the current implementation. It is available as an explicit pre-flight check that test
> authors can call to get clear, early error messages before invoking the factory.

---

## Factory Layer

### EmbeddedDatabaseFactory

**Package:** `com.gothamdude.core.test.factory`
**Type:** `final class` — static factory (private constructor, no instances)

The core of the library. Creates and fully initialises `EmbeddedDatabase` instances backed
by H2 v2 with optional dialect emulation. Provides three public entry-points.

#### Public API

| Method | Source of Config | Description |
|--------|-----------------|-------------|
| `create(DbTestConfig config)` | Programmatic | Primary method. All settings come from the supplied `DbTestConfig`. The config's dialect takes priority over any properties file. |
| `createFromProperties()` | `db-test.properties` | Reads dialect and database name entirely from the classpath properties file. No scripts are added. |
| `createFromProperties(List<String> schema, List<String> data)` | Mixed | Reads dialect and name from `db-test.properties` but accepts scripts programmatically. |

#### Internal Helpers

| Method | Visibility | Description |
|--------|-----------|-------------|
| `doBuild(dialect, dbName, schema, data)` | `private static` | Constructs the JDBC URL, configures `EmbeddedDatabaseBuilder`, injects the H2 system property for non-H2 dialects, and runs all scripts. |
| `buildH2Url(DbDialect, String)` | `package-private static` | Pure URL construction. Appends `DB_CLOSE_DELAY=-1`, `DB_CLOSE_ON_EXIT=FALSE`, and dialect-specific flags. Package-private for unit testability without mocking. |
| `buildUniqueName(String baseName)` | `private static` | Appends the sanitised current thread name to the base database name. Prevents name collisions when multiple tests run in parallel on different threads. |
| `loadProperties()` | `private static` | Loads `db-test.properties` from the thread-context classloader. Returns empty `Properties` (with a WARN log) if the file is absent. |
| `resolveDbDialect(Properties)` | `private static` | Reads `test.db.dialect` from properties, delegates to `DbDialect.fromString()`. Falls back to `H2` on parse failure. |

#### JDBC URL Construction per Dialect

| Dialect | Additional URL parameters |
|---------|--------------------------|
| `H2` | `;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE` |
| `ORACLE` | `;MODE=Oracle;NON_KEYWORDS=VALUE;DEFAULT_NULL_ORDERING=LAST` |
| `MSSQL` | `;MODE=MSSQLServer;CASE_INSENSITIVE_IDENTIFIERS=TRUE` |
| `POSTGRESQL` | `;MODE=PostgreSQL;DEFAULT_NULL_ORDERING=LAST` |

#### Dialect Injection Mechanism

Spring's `EmbeddedDatabaseBuilder` does not expose a setter for arbitrary JDBC URL parameters.
To inject `MODE` and compatibility flags, the factory uses a JVM system property workaround:

```java
System.setProperty("h2.jdbcUrl." + buildUniqueName(dbName), jdbcUrl);
```

This property is set before `builder.build()` is called so that H2 picks it up when
initialising the first connection to the named in-memory database.

#### Script Execution Order

1. **Schema scripts (DDL)** — added to the builder first via `builder.addScript()`. Tables, sequences, constraints, and views must exist before data can be inserted.
2. **Data scripts (DML)** — added after all schema scripts. `INSERT`, `UPDATE`, and other DML runs with the schema fully in place.

#### Usage Examples

```java
// Programmatic — all settings inline
EmbeddedDatabase db = EmbeddedDatabaseFactory.create(
        DbTestConfig.builder()
                    .dialect(DbDialect.POSTGRESQL)
                    .schemaScripts("schema/tables.sql")
                    .dataScripts("data/fixture.sql")
                    .build());
// ... run tests ...
db.shutdown();

// Properties-driven — dialect and name from db-test.properties
EmbeddedDatabase db = EmbeddedDatabaseFactory.createFromProperties(
        List.of("schema/tables.sql"),
        List.of("data/fixture.sql"));
```

---

## Runner Layer

### DbTestContext

**Package:** `com.gothamdude.core.test.runner`
**Type:** `final class` — implements `AutoCloseable`

The primary entry-point for test classes. Wires together `EmbeddedDatabaseFactory`, a live
`EmbeddedDatabase`, and a `JdbcTemplate` into a single handle with a clean lifecycle.

Implements `AutoCloseable` — use it in a `try-with-resources` block and the database shuts
down automatically, even if the test throws.

#### Accessors

| Method | Returns | Use Case |
|--------|---------|----------|
| `dataSource()` | `DataSource` | Pass to the repository or DAO under test. The returned object is the live `EmbeddedDatabase` cast to `DataSource`. |
| `jdbc()` | `JdbcTemplate` | Pre-configured for the embedded DB. Use for in-test assertions: `ctx.jdbc().queryForObject(...)`. |

#### Lifecycle Methods

| Method | Description |
|--------|-------------|
| `shutdown()` | Explicitly shuts down the embedded database. Call in `@AfterEach` when not using try-with-resources. |
| `close()` | Implements `AutoCloseable`. Delegates to `shutdown()`. Called automatically by try-with-resources. |

#### Builder API

| Method | Description |
|--------|-------------|
| `dbDialect(DbDialect)` | Override dialect programmatically. Defaults to `H2`. |
| `databaseName(String)` | Logical H2 in-memory name. Defaults to `"testdb"`. |
| `schemaScripts(String...)` | Varargs DDL paths. Executed before data scripts. |
| `schemaScripts(List<String>)` | List overload. |
| `dataScripts(String...)` | Varargs DML paths. Executed after schema scripts. |
| `dataScripts(List<String>)` | List overload. |
| `build()` | Builds `DbTestConfig`, calls `EmbeddedDatabaseFactory.create()`, wraps the result in a new `DbTestContext`. |

#### @BeforeEach / @AfterEach Pattern

```java
class OrderRepositoryIT {

    private DbTestContext ctx;

    @BeforeEach
    void setUp() {
        ctx = DbTestContext.builder()
                .dbDialect(DbDialect.ORACLE)
                .schemaScripts("schema/orders.sql")
                .dataScripts("data/orders_fixture.sql")
                .build();
    }

    @AfterEach
    void tearDown() {
        ctx.shutdown();
    }

    @Test
    void shouldFindOrderById() {
        Integer count = ctx.jdbc()
                           .queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
        assertThat(count).isEqualTo(3);
    }
}
```

#### try-with-resources Pattern

```java
@Test
void shouldQueryProduct() throws Exception {
    try (DbTestContext ctx = DbTestContext.builder()
            .schemaScripts("schema/create_tables.sql")
            .dataScripts("data/sample_fixture.sql")
            .build()) {

        List<Map<String, Object>> rows = ctx.jdbc()
                .queryForList("SELECT * FROM product");
        assertThat(rows).hasSize(3);
    } // db.shutdown() called automatically
}
```

---

## SQL Resources

The library ships with example SQL scripts demonstrating the expected layout.

| Path | Type | Contents |
|------|------|----------|
| `src/main/resources/schema/schema_001.sql` | DDL | Placeholder schema script (currently empty). Starter template for library consumers. |
| `src/main/resources/data/data_001.sql` | DML | Placeholder data script (currently empty). Companion to `schema_001.sql`. |

---

## db-test.properties

Place this file anywhere on the classpath (e.g. `src/test/resources/`) to drive
`createFromProperties()` without writing Java code.

```properties
# Dialect: H2 | ORACLE | MSSQL | POSTGRESQL
test.db.dialect=ORACLE

# Logical in-memory database name
test.db.name=testdb
```

> **Absent file:** If `db-test.properties` is not found on the classpath, the factory logs
> a `WARN` and defaults to dialect `H2` and database name `"testdb"`. No exception is thrown.

---

