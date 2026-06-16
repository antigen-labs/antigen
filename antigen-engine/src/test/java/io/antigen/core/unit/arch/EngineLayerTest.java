package io.antigen.core.unit.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Engine-purity guard (architecture.md §3, Phase 2b).
 *
 * <p>After the module split, the engine ← adapter ← cli <em>direction</em> is enforced
 * structurally: this module's compile classpath has no adapter/cli code on it, so it cannot
 * depend on them. What the classpath does <b>not</b> catch is the engine accidentally reaching
 * for a runtime <em>library</em> (Apache HttpClient, JUnit, AspectJ, RestAssured, OkHttp) — the
 * leak that {@code coverage.Logger} demonstrated before it was moved to the adapter. This test
 * is that check: the engine must speak only its neutral dependencies (Jackson, SnakeYAML,
 * Swagger, commons-math, org.json, SLF4J).
 *
 * <p>Keeping the engine free of these libraries is also the GraalVM-native precondition
 * (architecture.md §3): no AspectJ weaver, no test-framework reflection in the engine binary.
 */
class EngineLayerTest {

    private static final String[] FORBIDDEN_RUNTIME_LIBS = {
            "org.apache.http..",      // Apache HttpClient — belongs in the adapter (http.apache)
            "org.aspectj..",          // weaving — adapter only
            "org.junit..",            // test frameworks — adapter only
            "io.restassured..",
            "okhttp3..",
    };

    private static JavaClasses engine;

    @BeforeAll
    static void importClasses() {
        engine = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("io.antigen.core");
    }

    @Test
    void engineDoesNotDependOnRuntimeLibraries() {
        noClasses()
                .should().dependOnClassesThat().resideInAnyPackage(FORBIDDEN_RUNTIME_LIBS)
                .check(engine);
    }
}
