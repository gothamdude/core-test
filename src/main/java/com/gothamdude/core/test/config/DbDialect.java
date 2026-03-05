package com.gothamdude.core.test.config;


/**
 * Supported database dialects that H2 can emulate.
 * <p>The value of each constant matches the MODE string accepted by H2's
 * {@code INIT} / {@code MODE} connection property (case-insensitive in H2 itself, but we use exact casing for clarity).
 */
public enum DbDialect {

    H2("H2"),

    POSTGRESQL("PostgreSQL"),

    ORACLE("Oracle"),

    MSSQL("MSSQLServer");

    private final String h2Mode;

    DbDialect(String h2Mode) {
        this.h2Mode = h2Mode;
    }

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
