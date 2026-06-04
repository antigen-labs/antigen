package io.antigen.ai.llm;

import io.antigen.ai.orchestrator.GenerationContext;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
public class PromptBuilder {

    /**
     * Framework-agnostic quality contract. Never mentions a specific testing library —
     * that belongs in the user's prompt.txt. Only states what Antigen requires:
     * tests must catch injected faults, not just pass.
     */
    private static final String SYSTEM_PROMPT = """
            You are generating API tests that will be validated by Antigen fault simulation.

            Goal: produce tests that CATCH injected faults — not tests that merely pass.
            Antigen mutates API responses (null fields, missing fields, semantic violations) and
            re-runs each test. A test that passes despite a mutation is a quality gap.

            API SPECIFICATION: {SPEC_PATH}
            Read this file first using the Read tool. Do not assume its contents.

            Output: {OUTPUT_DIR}
            - Valid Java source files only — no markdown, no code blocks, no explanations
            - Each file must compile standalone: correct package declaration and all imports
            - Organise by resource into separate test classes

            Quality bar enforced by Antigen fault simulation:
            - Assert every field in every response, not just the status code
            - Include null checks, type checks, and value constraints
            - Tests that only assert statusCode(200) will fail simulation

            {USER_PROMPT}
            {FEEDBACK}
            """;

    private static final String USER_PROMPT_FILE = "src/test/resources/antigen/generation/prompt.txt";

    public String buildPrompt(GenerationContext context) throws IOException {
        Path projectPath = context.getProjectPath();
        String specRelative = projectPath.relativize(context.getSpecPath()).toString().replace('\\', '/');
        String outputRelative = projectPath.relativize(context.getOutputDir()).toString().replace('\\', '/');
        String userPrompt = loadUserPrompt(projectPath);

        String prompt = SYSTEM_PROMPT
                .replace("{SPEC_PATH}", specRelative)
                .replace("{OUTPUT_DIR}", outputRelative)
                .replace("{USER_PROMPT}", userPrompt.isBlank() ? "" : "\n" + userPrompt)
                .replace("{FEEDBACK}", "");

        log.debug("Built prompt ({} chars, user prompt: {})", prompt.length(),
                userPrompt.isBlank() ? "none" : "loaded from " + USER_PROMPT_FILE);
        return prompt;
    }

    public String buildRetryPrompt(GenerationContext context) throws IOException {
        if (!context.hasFeedback()) {
            throw new IllegalStateException("Cannot build retry prompt without feedback");
        }

        Path projectPath = context.getProjectPath();
        String specRelative = projectPath.relativize(context.getSpecPath()).toString().replace('\\', '/');
        String outputRelative = projectPath.relativize(context.getOutputDir()).toString().replace('\\', '/');
        String userPrompt = loadUserPrompt(projectPath);

        String feedback = "\nPREVIOUS ATTEMPT FAILED:\n"
                + context.getLatestFeedback().getFeedback()
                + "\n\nFix only these specific issues. "
                + "Review existing files in " + outputRelative + "/ and correct them.\n";

        return SYSTEM_PROMPT
                .replace("{SPEC_PATH}", specRelative)
                .replace("{OUTPUT_DIR}", outputRelative)
                .replace("{USER_PROMPT}", userPrompt.isBlank() ? "" : "\n" + userPrompt)
                .replace("{FEEDBACK}", feedback);
    }

    private String loadUserPrompt(Path projectPath) {
        Path promptFile = projectPath.resolve(USER_PROMPT_FILE);
        if (!Files.exists(promptFile)) {
            log.debug("No user prompt found at {}", USER_PROMPT_FILE);
            return "";
        }
        try {
            String content = Files.readString(promptFile).strip();
            log.info("Loaded user prompt from {}", USER_PROMPT_FILE);
            return content;
        } catch (IOException e) {
            log.warn("Failed to read user prompt at {}: {}", USER_PROMPT_FILE, e.getMessage());
            return "";
        }
    }
}
