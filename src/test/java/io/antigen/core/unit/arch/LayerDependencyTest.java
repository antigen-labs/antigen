package io.antigen.core.unit.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Dependency-direction guard for the engine/adapter/cli layering (architecture §3, Phase 2).
 *
 * <p>The split into Gradle modules is physical (Phase 2b); this test makes the boundary real
 * <em>now</em>, in the single project, so the module split is mechanical: every package already
 * sits on the correct side of the line. The rule the modules will enforce by classpath is the
 * rule this test enforces by package — <b>adapters depend on the engine, never the reverse</b>.
 *
 * <p>Layer membership (must stay in sync with the planned module boundary, see
 * {@code refactor/phase2.md}):
 * <ul>
 *   <li><b>engine</b> (pure, no test-framework/HTTP-client deps) — config, invariant, injection,
 *       normalizer, report, analytics, coverage, plan, simulation (scoring), and the {@code http}
 *       capture contract ({@code Request}/{@code Response} interfaces + {@code RequestResponsePair}).</li>
 *   <li><b>adapter</b> (JVM runtime glue) — interceptor (AspectJ/JUnit), runner (execution loop),
 *       and the Apache HttpClient impls under {@code http.apache}.</li>
 *   <li><b>cli</b> — the {@code ai.*} generation loop and the {@code gradle} plugin.</li>
 * </ul>
 */
class LayerDependencyTest {

    // Engine = the http capture contract package *exactly* (not the ..apache impls) + pure cores.
    private static final String[] ENGINE = {
            "io.antigen.core.http",            // interfaces + RequestResponsePair only
            "io.antigen.core.config..",
            "io.antigen.core.invariant..",
            "io.antigen.core.injection..",
            "io.antigen.core.normalizer..",
            "io.antigen.core.report..",
            "io.antigen.core.analytics..",
            "io.antigen.core.coverage..",
            "io.antigen.core.plan..",
            "io.antigen.core.simulation..",
    };

    private static final String[] ADAPTER = {
            "io.antigen.core.interceptor..",
            "io.antigen.core.runner..",
            "io.antigen.core.http.apache..",
    };

    private static final String[] CLI = {
            "io.antigen.ai..",
            "io.antigen.gradle..",
    };

    private static JavaClasses antigen;

    @BeforeAll
    static void importClasses() {
        antigen = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("io.antigen");
    }

    @Test
    void engineDoesNotDependOnAdapter() {
        noClasses().that().resideInAnyPackage(ENGINE)
                .should().dependOnClassesThat().resideInAnyPackage(ADAPTER)
                .check(antigen);
    }

    @Test
    void engineDoesNotDependOnCli() {
        noClasses().that().resideInAnyPackage(ENGINE)
                .should().dependOnClassesThat().resideInAnyPackage(CLI)
                .check(antigen);
    }

    @Test
    void adapterDoesNotDependOnCli() {
        noClasses().that().resideInAnyPackage(ADAPTER)
                .should().dependOnClassesThat().resideInAnyPackage(CLI)
                .check(antigen);
    }
}
