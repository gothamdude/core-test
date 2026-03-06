# core-test — Test Suite Reference

> Complete reference for all test classes in the **core-test** library.
> Covers purpose, test-case breakdown, assertion strategy, and how each suite
> interacts with the production source classes.

**JUnit 5.10** · **AssertJ 3.27** · **Mockito 5.12** · **H2 v2.2.224**

---

## Table of Contents

- [Suite Map](#suite-map)
- [Testing Strategy](#testing-strategy)
- [Test Resources](#test-resources)
- [Config Layer Tests](#config-layer-tests)
  - [DbDialectTest](#dbdialecttest)
  - [DbTestConfigTest](#dbtestconfigtest)
- [Utility Layer Tests](#utility-layer-tests)
  - [ScriptLoaderTest](#scriptloadertest)
- [Factory Layer Tests](#factory-layer-tests)
  - [EmbeddedDatabaseFactoryTest](#embeddeddatabasefactorytest)
- [Runner Layer Tests](#runner-layer-tests)
  - [DbTestContextTest](#dbtestcontexttest)
- [Coverage Summary](#coverage-summary)
- [Test Isolation](#test-isolation)

---

## Suite Map

| Source Class Under Test | Test Class | Type |
|-------------------------|-----------|------|
| `DbDialect` | `DbDialectTest` | Unit / Parameterized |
| `DbTestConfig` + Builder | `DbTestConfigTest` | Unit |
| `ScriptLoader` | `ScriptLoaderTest` | Unit + Classpath I/O |
| `EmbeddedDatabaseFactory` | `EmbeddedDatabaseFactoryTest` | Unit + Integration |
| `DbTestContext` | `DbTestContextTest` | Integration (full stack) |

**5 test classes · 42 test cases · 4 SQL resource files · 3 dialect integration tests**

---

## Testing Strategy

| Layer | Test Style | What Is Exercised | External I/O |
|-------|-----------|-------------------|--------------|
| Config (`DbDialect`, `DbTestConfig`) | Unit, Parameterized | Pure Java logic: enum parsing, builder defaults, null guards, immutability | None |
| Utility (`ScriptLoader`) | Unit | Classpath resource resolution, UTF-8 reading, error messages | Reads test classpath only |
| Factory (`EmbeddedDatabaseFactory`) | Unit + Integration | URL construction logic (unit); full H2 DB bootstrap per dialect (integration) | Spins up real H2 in-memory database |
| Runner (`DbTestContext`) | Integration | Full end-to-end stack: schema DDL → data DML → JDBC queries → shutdown | H2 + SQL scripts from classpath |

> **No mocking of H2 or Spring:** The factory and runner tests deliberately spin up real H2
> instances. This verifies that the H2 URL parameters, the Spring `EmbeddedDatabaseBuilder`
> wiring, and the SQL scripts all work together correctly — something a mock cannot prove.

---

## Test Resources

All SQL files live under `src/test/resources/` and are resolved via the thread-context classloader.

| Path | Type | Contents |
|------|------|----------|
| `scripts/sample.sql` | DDL — minimal | Creates `CREATE TABLE sample (id INT PRIMARY KEY)`. Used by `ScriptLoaderTest` and `EmbeddedDatabaseFactoryTest` as the "existing script" sentinel. |
| `scripts/sample_data.sql` | DML — minimal | `INSERT INTO sample (id) VALUES (1)`. Used by `EmbeddedDatabaseFactoryTest` to verify schema + data script ordering. |
| `schema/create_tables.sql` | DDL — realistic | Creates `product` (id, code, name, price, created_at) and `orders` (id, product_id, quantity, status, ordered_at) with a foreign-key constraint. Used by `DbTestContextTest`. |
| `data/sample_fixture.sql` | DML — realistic | Inserts 3 products (WIDGET-A, WIDGET-B, GADGET-X) and 2 orders (id 101 PENDING, id 102 SHIPPED). Used by `DbTestContextTest`. |

---

## Config Layer Tests

### DbDialectTest

**File:** `src/test/java/com/gothamdude/core/test/config/DbDialectTest.java`
**Type:** Parameterized · Negative

Verifies the `DbDialect.fromString()` parsing logic in isolation. All four tests target a
single static method, covering 14 distinct input values through parameterisation plus 6
negative/edge cases. No database is created — zero I/O.

Uses `@ExtendWith(MockitoExtension.class)` for lifecycle management. No mocks are created —
the extension is present for consistency with the rest of the suite.

#### Test Cases

| Test Method | Type | Inputs / Cases | Assertion |
|-------------|------|---------------|-----------|
| `fromString_validAlias_returnsExpectedDialect` | `@CsvSource` | 14 rows: mixed-case H2, ORACLE, MSSQL, SQLSERVER, MSSQLSERVER, POSTGRESQL, POSTGRES | `assertEquals(DbDialect.valueOf(expectedName), result)` |
| `fromString_withSurroundingWhitespace_returnsDialect` | `@ValueSource` | 4 strings with leading/trailing spaces and tabs | `assertDoesNotThrow()` — confirms `trim()` is applied |
| `fromString_null_throwsIllegalArgumentException` | Negative | `null` | Asserts exception type and exact message: `"Dialect value must not be null"` |
| `fromString_unknownValue_throwsIllegalArgumentException` | `@ValueSource` Negative | `""`, `"  "`, `"MYSQL"`, `"DB2"`, `"SQLITE"` | Exception message contains `"Unknown dialect"` |

#### What It Proves

- Every documented alias resolves to the correct enum constant
- Parsing is case-insensitive end-to-end (both upper and lower case tested per dialect)
- Whitespace is stripped before matching
- `null` input is caught with a precise message (not a `NullPointerException`)
- Unsupported dialects fail fast with a message containing `"Unknown dialect"`
- Empty/blank strings do not accidentally match any dialect

---

### DbTestConfigTest

**File:** `src/test/java/com/gothamdude/core/test/config/DbTestConfigTest.java`
**Type:** Unit

Comprehensively tests the `DbTestConfig.Builder` through 13 test cases, verifying defaults,
each builder setter, immutability guarantees, null guards, and `toString()` output.
No database or I/O involved.

#### Test Cases

| Test Method | Group | What Is Verified |
|-------------|-------|-----------------|
| `build_withNoCustomisation_appliesDefaults` | Defaults | Dialect = H2, name = `"testdb"`, both script lists empty |
| `build_withDialect_storesDialect` | `dialect()` | Builder correctly stores a non-default dialect (POSTGRESQL) |
| `build_withDatabaseName_storesDatabaseName` | `databaseName()` | Custom name `"my_test_db"` is stored and retrieved |
| `build_withSchemaScriptsVarargs_storesScripts` | `schemaScripts()` | Varargs overload stores paths in order |
| `build_withSchemaScriptsList_storesScripts` | `schemaScripts()` | List overload stores paths correctly |
| `schemaScripts_isImmutable` | Immutability | Mutating the returned list throws `UnsupportedOperationException` |
| `build_withDataScriptsVarargs_storesScripts` | `dataScripts()` | Varargs overload stores paths in order |
| `build_withDataScriptsList_storesScripts` | `dataScripts()` | List overload stores paths correctly |
| `dataScripts_isImmutable` | Immutability | Mutating the data script list throws `UnsupportedOperationException` |
| `build_withNullDialect_throwsNullPointerException` | Null guards | `.dialect(null).build()` throws `NullPointerException` |
| `build_withNullDatabaseName_throwsNullPointerException` | Null guards | `.databaseName(null).build()` throws `NullPointerException` |
| `build_fullConfiguration_storesAllFields` | Full build | All four fields set in a single fluent chain; verified with `assertAll()` |
| `toString_containsAllRelevantFields` | `toString()` | String representation includes the dialect name and database name |

#### Key Assertion Patterns

```java
// Immutability check
assertThrows(UnsupportedOperationException.class,
        () -> config.getSchemaScripts().add("db/extra.sql"));

// Full-config assertAll — reports all failures, not just the first
assertAll(
        () -> assertEquals(DbDialect.ORACLE, config.getDbDialect()),
        () -> assertEquals("oratest", config.getDatabaseName()),
        () -> assertEquals(List.of("db/schema.sql"), config.getSchemaScripts()),
        () -> assertEquals(List.of("db/data.sql"), config.getDataScripts())
);
```

---

## Utility Layer Tests

### ScriptLoaderTest

**File:** `src/test/java/com/gothamdude/core/test/util/ScriptLoaderTest.java`
**Type:** Unit · Negative

Tests both public methods of `ScriptLoader` with a focus on error-message correctness and
the "collect all missing, report together" behaviour. Uses the real classpath via
`scripts/sample.sql` as the "existing file" sentinel.

#### Test Cases — `validate()`

| Test Method | Input | Assertion |
|-------------|-------|-----------|
| `validate_emptyList_doesNotThrow` | Empty list | `assertDoesNotThrow()` — empty input is a no-op |
| `validate_existingScript_doesNotThrow` | `["scripts/sample.sql"]` | `assertDoesNotThrow()` — valid path passes silently |
| `validate_missingScript_throwsIllegalArgumentException` | `["nonexistent/missing.sql"]` | Exception message contains both the `scriptType` label and the path |
| `validate_mixedScripts_reportsOnlyMissingOnes` | 1 valid + 2 missing paths | Exception lists both missing paths; the valid path is absent from the message |
| `validate_scriptTypeAppearsInErrorMessage` | 1 missing path, scriptType = `"data"` | Exception message contains the word `"data"` |

#### Test Cases — `readScript()`

| Test Method | Input | Assertion |
|-------------|-------|-----------|
| `readScript_existingResource_returnsContent` | `"scripts/sample.sql"` | Non-null, non-blank, contains `"CREATE TABLE"` |
| `readScript_missingResource_throwsIllegalStateException` | `"nonexistent/missing.sql"` | `IllegalStateException` with the path in the message |

#### Mixed-list Assertion Detail

```java
@Test
void validate_mixedScripts_reportsOnlyMissingOnes() {
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> ScriptLoader.validate(
                    List.of(EXISTING_SCRIPT, "missing-a.sql", "missing-b.sql"), "data")
    );
    assertAll(
            () -> assertTrue(ex.getMessage().contains("missing-a.sql")),
            () -> assertTrue(ex.getMessage().contains("missing-b.sql")),
            // valid path must NOT appear in the error
            () -> assertFalse(ex.getMessage().contains(EXISTING_SCRIPT))
    );
}
```

> **Design insight:** The mixed-list test is the most important one. It proves that
> `validate()` does a full pass over all paths before throwing — callers get one error
> listing *all* missing files rather than discovering them one at a time.

---

## Factory Layer Tests

### EmbeddedDatabaseFactoryTest

**File:** `src/test/java/com/gothamdude/core/test/factory/EmbeddedDatabaseFactoryTest.java`
**Type:** Unit + Integration

A hybrid test class that validates both the pure URL-construction logic (unit tests, no DB)
and the end-to-end database creation pipeline (integration tests that spin up real H2 instances).

The class lives in the **same package** as the production code
(`com.gothamdude.core.test.factory`) so that the package-private method `buildH2Url()` is
directly accessible without reflection or mocking.

#### Lifecycle

A field `private EmbeddedDatabase db` is set by each integration test. An `@AfterEach`
method calls `db.shutdown()` if non-null, preventing any in-memory database from leaking
into the next test.

#### Unit Test Cases — `buildH2Url()`

| Test Method | Dialect | Key Assertions |
|-------------|---------|---------------|
| `buildH2Url_withH2Dialect_returnsBaseUrlWithNoModeFlag` | H2 | Starts with `jdbc:h2:mem:`; contains `DB_CLOSE_DELAY=-1` and `DB_CLOSE_ON_EXIT=FALSE`; does **not** contain `MODE=` |
| `buildH2Url_withOracleDialect_containsModeAndCompatibilityFlags` | ORACLE | Contains `MODE=Oracle`, `NON_KEYWORDS=VALUE`, `DEFAULT_NULL_ORDERING=LAST` |
| `buildH2Url_withMssqlDialect_containsModeAndCaseInsensitiveFlag` | MSSQL | Contains `MODE=MSSQLServer` and `CASE_INSENSITIVE_IDENTIFIERS=TRUE` |
| `buildH2Url_withPostgresqlDialect_containsModeAndNullOrdering` | POSTGRESQL | Contains `MODE=PostgreSQL` and `DEFAULT_NULL_ORDERING=LAST` |

#### Integration Test Cases — `create(DbTestConfig)`

| Test Method | Config | Assertion |
|-------------|--------|-----------|
| `create_withH2DialectAndNoScripts_returnsLiveDatabase` | H2, no scripts | `assertNotNull(db)` |
| `create_withSchemaScript_tableIsQueryable` | H2 + `scripts/sample.sql` | `SELECT COUNT(*) FROM sample` returns 0 via JdbcTemplate |
| `create_withSchemaAndDataScripts_dataIsLoadedInOrder` | H2 + sample.sql + sample_data.sql | `COUNT(*) FROM sample` returns 1 — proves DDL ran before DML |
| `create_withOracleDialect_returnsLiveDatabase` | ORACLE + sample.sql | `assertNotNull(db)` — Oracle MODE boots without error |
| `create_withMssqlDialect_returnsLiveDatabase` | MSSQL + sample.sql | `assertNotNull(db)` — SQL Server MODE boots without error |
| `create_withPostgresqlDialect_returnsLiveDatabase` | POSTGRESQL + sample.sql | `assertNotNull(db)` — PostgreSQL MODE boots without error |

#### Integration Test Cases — `createFromProperties()`

| Test Method | Scenario | Assertion |
|-------------|----------|-----------|
| `createFromProperties_withoutPropertiesFile_defaultsToH2AndCreatesDatabase` | No `db-test.properties` on classpath | Falls back to H2 defaults; returns non-null database |
| `createFromProperties_withScripts_tableIsQueryable` | Scripts passed programmatically, no properties file | `COUNT(*) FROM sample` = 0; schema loaded correctly |

> **Why same-package placement matters:** `buildH2Url()` is declared package-private rather
> than `private` precisely so that tests in the same package can call it directly. This avoids
> PowerMock or reflection while still keeping the method hidden from library consumers in
> other packages.

---

## Runner Layer Tests

### DbTestContextTest

**File:** `src/test/java/com/gothamdude/core/test/runner/DbTestContextTest.java`
**Type:** Integration — Full Stack

The highest-level test class in the suite. Every test exercises the complete production stack:
`DbTestContext.builder()` → `DbTestConfig` → `EmbeddedDatabaseFactory` → H2 → SQL scripts →
`JdbcTemplate` queries. No mocking anywhere. All tests use **try-with-resources** so the
database shuts down automatically even if an assertion fails.

#### Full Stack Flow (per test)

```
Test method
  └─ DbTestContext.builder()
       ├─ .schemaScripts("schema/create_tables.sql")
       ├─ .dataScripts("data/sample_fixture.sql")   [optional]
       ├─ .dbDialect(DbDialect.ORACLE)               [optional]
       └─ .build()
            └─ DbTestConfig.builder().build()              [config layer]
                 └─ EmbeddedDatabaseFactory.create()       [factory layer]
                      ├─ buildH2Url(dialect, name)
                      ├─ System.setProperty(...)            [for non-H2]
                      └─ EmbeddedDatabaseBuilder
                           ├─ runs schema/create_tables.sql  (DDL)
                           └─ runs data/sample_fixture.sql   (DML)
                                └─ EmbeddedDatabase (live H2)
                                     └─ JdbcTemplate
                                          └─ test assertions
  └─ } // try-with-resources → ctx.close() → db.shutdown()
```

#### Test Cases

| Test Method | Category | What Is Verified |
|-------------|----------|-----------------|
| `shouldStartAndShutdownCleanly` | Lifecycle | `dataSource()` and `jdbc()` are non-null; `AutoCloseable` contract works without exception |
| `shouldCreateTablesFromSchemaScript` | Schema | Queries `information_schema.tables` to confirm both `product` and `orders` tables were created |
| `shouldPopulateTablesFromDataScripts` | Data | `COUNT(*)` confirms 3 products and 2 orders were inserted by the fixture |
| `shouldQueryFixtureDataCorrectly` | Data | Filters orders by `status = 'PENDING'`; asserts exactly 1 row — verifies data content, not just count |
| `shouldRunMultipleSchemaAndDataScriptsInOrder` | Ordering | Passes both script lists via `List.of()` overloads; asserts product count > 0 |
| `shouldStartWithOracleDialect` | Dialect | Executes `SELECT 'OK' FROM DUAL` — DUAL only exists in Oracle mode, proving `MODE=Oracle` is active |
| `shouldStartWithPostgresDialect` | Dialect | POSTGRESQL mode boots and rows are queryable |
| `shouldStartWithMssqlDialect` | Dialect | MSSQL mode boots and rows are queryable |
| `dbShouldBeEmptyWhenNoDataScriptSupplied` | Isolation | Schema only, no data scripts; `COUNT(*) FROM product` = 0 |

#### Oracle DUAL Test — the Gold Standard Dialect Check

```java
@Test
void shouldStartWithOracleDialect() throws Exception {
    try (DbTestContext ctx = DbTestContext.builder()
            .dbDialect(DbDialect.ORACLE)
            .schemaScripts("schema/create_tables.sql")
            .dataScripts("data/sample_fixture.sql")
            .build()) {

        // DUAL only exists in Oracle mode — this query fails on plain H2
        String result = ctx.jdbc()
                .queryForObject("SELECT 'OK' FROM DUAL", String.class);
        assertThat(result).isEqualTo("OK");
    }
}
```

> **Why DUAL?** In H2's Oracle emulation mode, the synthetic `DUAL` table is created
> automatically. Querying it is the simplest reliable test that `MODE=Oracle` was actually
> applied — no MODE means the query fails with a table-not-found error.

---

## Coverage Summary

| Production Class | Covered By | Covered Behaviour |
|-----------------|-----------|-------------------|
| `DbDialect` | DbDialectTest, EmbeddedDatabaseFactoryTest, DbTestContextTest | All 4 enum constants; 14 parse aliases; whitespace trimming; null and unknown-value errors; H2 URL segment output per dialect |
| `DbTestConfig.Builder` | DbTestConfigTest, (all integration tests implicitly) | All builder methods; both varargs and List overloads; default values; null guards; immutability of script lists; `toString()` |
| `ScriptLoader` | ScriptLoaderTest | Empty list; valid path; missing path; mixed valid+missing bulk error; script type label in error; `readScript()` happy path; `readScript()` missing file |
| `EmbeddedDatabaseFactory` | EmbeddedDatabaseFactoryTest | `buildH2Url()` for all 4 dialects; `create()` with no scripts; `create()` with schema; `create()` with schema+data; `create()` for Oracle/MSSQL/PostgreSQL; `createFromProperties()` fallback; `createFromProperties(List,List)` |
| `DbTestContext` | DbTestContextTest | Builder construction; `dataSource()` and `jdbc()` accessors; `AutoCloseable`/try-with-resources; schema DDL; data DML; data content queries; all 3 non-H2 dialects; empty DB isolation |

---

## Test Isolation

Isolation is achieved at multiple levels to prevent any test from influencing another.

| Mechanism | Applied In | Effect |
|-----------|-----------|--------|
| **try-with-resources** | DbTestContextTest (all tests) | Database is shut down even if an assertion throws. No state leaks to the next test. |
| **`@AfterEach` shutdown** | EmbeddedDatabaseFactoryTest | Field `db` is shut down unconditionally after each test. |
| **Thread-based unique DB name** | `EmbeddedDatabaseFactory.buildUniqueName()` | Different threads (parallel test execution) get different H2 in-memory database names, preventing shared state. |
| **Unique `databaseName` per test** | EmbeddedDatabaseFactoryTest | Each integration test uses a different base name (`h2_noscripts`, `h2_schema`, `oracle_db`, …) to avoid H2 reusing a live database. |
| **H2 in-memory only** | All integration tests | No disk files are ever written. A `shutdown()` call leaves no residual state. |