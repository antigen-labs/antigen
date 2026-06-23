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
            You are generating API integration tests for the API described by the specification below.

            OBJECTIVE
            Encode the API's contract as assertions. A good test issues a valid request, confirms the
            documented success status, then asserts the full shape and content of the response body.
            These tests are graded by Antigen fault simulation: it corrupts response fields to invalid
            values and re-runs each test, so a test that still passes after a field is corrupted has an
            assertion gap. Complete, faithful assertions catch this automatically. Do NOT try to guess
            what gets corrupted -- assert the contract the spec defines and the faults fall out.

            API SPECIFICATION: {SPEC_PATH}
            Read this file first with the Read tool. Do not assume its contents -- derive every
            endpoint, field, type, and constraint from it.

            OUTPUT: {OUTPUT_DIR}
            - Java source files only. No markdown, no code fences, no prose, no explanations.
            - Each file must compile standalone: correct package declaration (matching the output
              directory) and all imports.
            - One test class per resource / endpoint group.

            MAKE REQUESTS SUCCEED FIRST
            - Supply the auth, headers, and path/query parameters the spec requires.
            - Build request bodies that satisfy the spec, and create any prerequisite state the
              endpoint depends on (e.g. create a resource before fetching it).
            - A request must reach its documented success response before its body assertions mean
              anything. If an endpoint cannot be made to return success, skip it rather than asserting
              against an error body.

            ASSERTION CHECKLIST -- apply to every response:
            - Assert the HTTP status matches the documented success code.
            - For every field in the response schema: assert it is present, assert it is non-null
              (unless the spec marks it nullable), and assert its JSON type.
            - Assert the value constraints the spec declares: enum membership, numeric min/max,
              string format and non-emptiness, required patterns.
            - For arrays: assert the array is present and, where the spec implies content, non-empty,
              and apply the checks above to each element.
            - Recurse into nested objects -- assert their fields too, not only the top level.
            - Asserting only the status code is insufficient and will fail simulation.

            DERIVE FROM THE SPEC ONLY
            Base every assertion on the specification. Do not look for, open, or read any Antigen
            report, fault simulation output, or files under build/. The injected faults are
            intentionally not part of your input, so your assertions encode the real contract rather
            than a leaked list of mutations.

            {USER_PROMPT}
            {FEEDBACK}
            """;

    private static final String USER_PROMPT_FILE = "src/test/resources/antigen/generation/prompt.txt";

    public String buildPrompt(GenerationContext context) throws IOException {
        Path projectPath = context.getProjectPath();
        String specPathStr = pathForPrompt(projectPath, context.getSpecPath());
        String outputRelative = projectPath.relativize(context.getOutputDir()).toString().replace('\\', '/');
        String userPrompt = loadUserPrompt(projectPath);

        String prompt = SYSTEM_PROMPT
                .replace("{SPEC_PATH}", specPathStr)
                .replace("{OUTPUT_DIR}", outputRelative)
                .replace("{USER_PROMPT}", userPrompt.isBlank() ? "" : "\n" + userPrompt)
                .replace("{FEEDBACK}", "");

        log.debug("Built prompt ({} chars, user prompt: {})", prompt.length(),
                userPrompt.isBlank() ? "none" : "loaded from " + USER_PROMPT_FILE);
        return prompt;
    }

    public String buildRetryPrompt(GenerationContext context) {
        if (!context.hasFeedback()) {
            throw new IllegalStateException("Cannot build retry prompt without feedback");
        }

        String outputRelative = context.getProjectPath()
                .relativize(context.getOutputDir()).toString().replace('\\', '/');

        return "The previous test generation attempt failed. Fix the issues below.\n\n"
                + "Read the existing test files in " + outputRelative + "/, fix the reported issues, "
                + "and write the corrected files back. Fix only what is reported - do not rewrite tests that are already correct.\n\n"
                + "ISSUES:\n"
                + context.getLatestFeedback().getFeedback().strip() + "\n";
    }

    /**
     * Path to show the agent for a file it must read. The agent runs with the project as its
     * working directory, so a path inside the project is given relative (clean, sandbox-friendly);
     * a file outside the project tree is given absolute rather than as a fragile {@code ../../..}
     * climb. Forward slashes either way so it reads the same on Windows.
     */
    private String pathForPrompt(Path projectPath, Path target) {
        Path absoluteTarget = target.toAbsolutePath().normalize();
        Path relative = projectPath.toAbsolutePath().normalize().relativize(absoluteTarget);
        Path chosen = relative.startsWith("..") ? absoluteTarget : relative;
        return chosen.toString().replace('\\', '/');
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
