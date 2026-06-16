package io.antigen.ai.orchestrator;

import lombok.Builder;
import lombok.Value;

import java.time.Duration;

@Value
@Builder
public class AntigenConfig {

    @Builder.Default
    int maxRetries = 5;

    @Builder.Default
    String claudeCommand = detectClaudeCommand();

    @Builder.Default
    String allowedTools = "Write,Read,Edit";

    @Builder.Default
    String permissionMode = "acceptEdits";  // Options: acceptEdits (recommended), bypassPermissions, default, plan

    @Builder.Default
    boolean useDangerousSkip = false;  // Set to true to use --dangerously-skip-permissions instead of --permission-mode

    @Builder.Default
    Duration buildTimeout = Duration.ofMinutes(5);

    @Builder.Default
    Duration testTimeout = Duration.ofMinutes(10);

    @Builder.Default
    Duration antigenTimeout = Duration.ofMinutes(30);

    @Builder.Default
    Duration llmTimeout = Duration.ofMinutes(5);

    @Builder.Default
    double faultDetectionThreshold = 1.0;  // 100% = no escaped faults

    @Builder.Default
    boolean verbose = false;

    public static AntigenConfig defaults() {
        return AntigenConfig.builder().build();
    }

    private static String detectClaudeCommand() {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            String userHome = System.getProperty("user.home");
            String base = userHome + "\\AppData\\Roaming\\npm\\node_modules\\@anthropic-ai\\claude-code\\";

            // Native exe (current installs)
            if (new java.io.File(base + "bin\\claude.exe").exists()) {
                return base + "bin\\claude.exe";
            }
            // Legacy Node.js script
            if (new java.io.File(base + "cli.js").exists()) {
                return "node;" + base + "cli.js";
            }
            // Fallback: claude.cmd on npm global PATH
            String claudeCmd = userHome + "\\AppData\\Roaming\\npm\\claude.cmd";
            if (new java.io.File(claudeCmd).exists()) {
                return claudeCmd;
            }
        }

        return "claude";
    }
}
