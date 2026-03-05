package com.gothamdude.core.test.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ScriptLoaderTest {

    private static final String EXISTING_SCRIPT = "scripts/sample.sql";

    // -------------------------------------------------------------------------
    // validate()
    // -------------------------------------------------------------------------

    @Test
    void validate_emptyList_doesNotThrow() {
        assertDoesNotThrow(() -> ScriptLoader.validate(List.of(), "schema"));
    }

    @Test
    void validate_existingScript_doesNotThrow() {
        assertDoesNotThrow(() -> ScriptLoader.validate(List.of(EXISTING_SCRIPT), "schema"));
    }

    @Test
    void validate_missingScript_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> ScriptLoader.validate(List.of("nonexistent/missing.sql"), "schema")
        );
        assertTrue(ex.getMessage().contains("schema"));
        assertTrue(ex.getMessage().contains("nonexistent/missing.sql"));
    }

    @Test
    void validate_mixedScripts_reportsOnlyMissingOnes() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> ScriptLoader.validate(
                        List.of(EXISTING_SCRIPT, "missing-a.sql", "missing-b.sql"), "data")
        );
        assertAll(
                () -> assertTrue(ex.getMessage().contains("missing-a.sql")),
                () -> assertTrue(ex.getMessage().contains("missing-b.sql")),
                () -> assertFalse(ex.getMessage().contains(EXISTING_SCRIPT))
        );
    }

    @Test
    void validate_scriptTypeAppearsInErrorMessage() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> ScriptLoader.validate(List.of("gone.sql"), "data")
        );
        assertTrue(ex.getMessage().contains("data"));
    }

    // -------------------------------------------------------------------------
    // readScript()
    // -------------------------------------------------------------------------

    @Test
    void readScript_existingResource_returnsContent() {
        String content = ScriptLoader.readScript(EXISTING_SCRIPT);

        assertNotNull(content);
        assertFalse(content.isBlank());
        assertTrue(content.contains("CREATE TABLE"));
    }

    @Test
    void readScript_missingResource_throwsIllegalStateException() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> ScriptLoader.readScript("nonexistent/missing.sql")
        );
        assertTrue(ex.getMessage().contains("nonexistent/missing.sql"));
    }
}