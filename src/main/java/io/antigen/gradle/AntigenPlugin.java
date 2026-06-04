package io.antigen.gradle;

import io.antigen.ai.config.GenerationConfig;
import io.antigen.ai.config.GenerationConfigLoader;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.JavaExec;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class AntigenPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        AntigenExtension extension = project.getExtensions().create("antigen", AntigenExtension.class);

        project.getTasks().register("generateTests", JavaExec.class, task -> {
            task.setGroup("antigen");
            task.setDescription("Generate API tests using Antigen AI");
            task.getMainClass().set("io.antigen.ai.Antigen");

            task.doFirst(t -> {
                var testRuntimeClasspath = project.getConfigurations().findByName("testRuntimeClasspath");
                var runtimeClasspath = project.getConfigurations().findByName("runtimeClasspath");
                var cp = testRuntimeClasspath != null ? testRuntimeClasspath : runtimeClasspath;
                if (cp == null) {
                    throw new IllegalStateException("generateTests requires the java plugin to be applied");
                }
                task.setClasspath(cp);
                task.setArgs(buildArgs(project, extension));
            });
        });
    }

    private List<String> buildArgs(Project project, AntigenExtension extension) {
        Path projectPath = project.getProjectDir().toPath();
        GenerationConfig fileConfig = GenerationConfigLoader.load(projectPath).orElse(new GenerationConfig());

        List<String> args = new ArrayList<>();
        args.add("generate");

        // spec: -Pspec > extension > generation/config.yml
        String spec = (String) project.findProperty("spec");
        if (spec == null || spec.isBlank()) spec = extension.getSpec();
        if (spec == null || spec.isBlank()) spec = fileConfig.spec;
        if (spec == null || spec.isBlank()) {
            throw new IllegalArgumentException(
                "No spec provided. Use -Pspec=path/to/openapi.yaml, set antigen { spec = '...' }, " +
                "or add spec: to antigen/generation/config.yml"
            );
        }
        args.add("--spec");
        args.add(spec);

        args.add("--project");
        args.add(projectPath.toString());

        // requirements: extension + config.yml (additive)
        for (String req : extension.getRequirements()) {
            args.add("--requirements");
            args.add(req);
        }
        if (fileConfig.requirements != null) {
            for (String req : fileConfig.requirements) {
                args.add("--requirements");
                args.add(req);
            }
        }

        // max_retries: extension > config.yml > default (5)
        int maxRetries = extension.getMaxRetries() > 0 ? extension.getMaxRetries()
                : (fileConfig.max_retries != null ? fileConfig.max_retries : 5);
        args.add("--max-retries");
        args.add(String.valueOf(maxRetries));

        // timeouts: extension > config.yml > defaults
        if (fileConfig.timeouts != null) {
            if (fileConfig.timeouts.build != null) {
                args.add("--timeout-build");
                args.add(String.valueOf(fileConfig.timeouts.build));
            }
            if (fileConfig.timeouts.test != null) {
                args.add("--timeout-test");
                args.add(String.valueOf(fileConfig.timeouts.test));
            }
            if (fileConfig.timeouts.antigen != null) {
                args.add("--timeout-antigen");
                args.add(String.valueOf(fileConfig.timeouts.antigen));
            }
        }

        if (extension.isVerbose()) {
            args.add("--verbose");
        }

        return args;
    }
}
