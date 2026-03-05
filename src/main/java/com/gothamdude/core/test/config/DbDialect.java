package com.gothamdude.core.test.config;


/**
 * Supported database dialects that H2 can emulate.
 * <p>The value of each constant matches the MODE string accepted by H2's
 * {@code INIT} / {@code MODE} connection property (case-insensitive in H2 itself, but we use exact casing for clarity).
 */
public enum DbDialect {

    /** H2 native mode – no emulation. */
    H2("H2"),

    /** Emulate PostgreSQL 14 behaviour. */
    POSTGRESQL("PostgreSQL"),

    /** Emulate Oracle 11g behaviour (data types, dual, rownum, …). */
    ORACLE("Oracle"),

    /** Emulate MS SQL Server 2022 behaviour. */
    MSSQL("MSSQLServer");

    private final String h2Mode;

    /**
     * Returns the MODE string that must be appended to the H2 JDBC URL,
     * e.g. {@code ;MODE=Oracle}.
     */
    DbDialect(String h2Mode) {
        this.h2Mode = h2Mode;
    }

    /**
     * Parse a string value (from a properties file) into a {@link DbDialect}.
     *
     * <p>Accepted values (case-insensitive): {@code H2}, {@code ORACLE},
     * {@code MSSQL}, {@code POSTGRESQL} / {@code POSTGRES}.
     *
     * @throws IllegalArgumentException if the value is unrecognised.
     */
    public static DbDialect fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Dialect value must not be null");
        }
        return switch (value.trim().toUpperCase()) {
            case "H2" -> H2;
            case "ORACLE" -> ORACLE;
            case "MSSQL", "SQLSERVER", "MSSQLSERVER" -> MSSQL;
            case "POSTGRESQL", "POSTGRES" -> POSTGRESQL;
            default ->
                    throw new IllegalArgumentException("Unknown dialect '%s'. Supported: H2, ORACLE, MSSQL, POSTGRESQL".formatted(value));
        };
    }

}
