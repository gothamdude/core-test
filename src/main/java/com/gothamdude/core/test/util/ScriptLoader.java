package com.gothamdude.core.test.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility for validating that SQL script paths exist on the classpath
 * before they are handed to {@link org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder}.
 *
 * <p>Early validation produces a clear error message rather than a cryptic
 * Spring or H2 exception at database-bootstrap time.
 */
public final class ScriptLoader {

    private static final Logger log = LoggerFactory.getLogger(ScriptLoader.class);

    private ScriptLoader() {
    }

    /**
     * Validate that every path in {@code scripts} exists on the classpath.
     *
     * @param scripts    classpath-relative paths
     * @param scriptType human-readable label for error messages ("schema" or "data")
     * @throws IllegalArgumentException if any path is missing
     */
    public static void validate(List<String> scripts, String scriptType) {
        List<String> missing = new ArrayList<>();
        for (String path : scripts) {
            ClassPathResource resource = new ClassPathResource(path);
            if (!resource.exists()) {
                missing.add(path);
            } else {
                log.debug("Found {} script: {}", scriptType, path);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "The following %s scripts were not found on the classpath: %s"
                            .formatted(scriptType, missing));
        }
    }

    /**
     * Read the content of a classpath resource as a UTF-8 string.
     * Mainly useful for debugging / logging during test development.
     */
    public static String readScript(String classpathPath) {
        ClassPathResource resource = new ClassPathResource(classpathPath);
        try (var is = resource.getInputStream()) {
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read classpath resource: " + classpathPath, e);
        }
    }

}
