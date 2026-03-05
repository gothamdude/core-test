package com.gothamdude.core.test.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class DbDialectTest {

    @ParameterizedTest
    @CsvSource({
            "H2,         H2",
            "h2,         H2",
            "ORACLE,     ORACLE",
            "oracle,     ORACLE",
            "MSSQL,      MSSQL",
            "mssql,      MSSQL",
            "SQLSERVER,  MSSQL",
            "sqlserver,  MSSQL",
            "MSSQLSERVER,MSSQL",
            "mssqlserver,MSSQL",
            "POSTGRESQL, POSTGRESQL",
            "postgresql, POSTGRESQL",
            "POSTGRES,   POSTGRESQL",
            "postgres,   POSTGRESQL",
    })
    void fromString_validAlias_returnsExpectedDialect(String input, String expectedName) {
        DbDialect result = DbDialect.fromString(input.trim());
        assertEquals(DbDialect.valueOf(expectedName), result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"  H2  ", " oracle ", "\tMSSQL\t", " postgres "})
    void fromString_withSurroundingWhitespace_returnsDialect(String input) {
        assertDoesNotThrow(() -> DbDialect.fromString(input));
    }

    @Test
    void fromString_null_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> DbDialect.fromString(null)
        );
        assertEquals("Dialect value must not be null", ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "MYSQL", "DB2", "SQLITE"})
    void fromString_unknownValue_throwsIllegalArgumentException(String input) {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> DbDialect.fromString(input)
        );
        assertTrue(ex.getMessage().contains("Unknown dialect"));
    }
}