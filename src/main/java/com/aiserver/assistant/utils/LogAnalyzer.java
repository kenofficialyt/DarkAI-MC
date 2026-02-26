package com.aiserver.assistant.utils;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

public class LogAnalyzer {
    private static final Pattern ERROR_PATTERN = Pattern.compile(
        "(?i)(error|exception|failed|fatal|critical|warning)"
    );
    
    private static final Pattern STACK_TRACE_PATTERN = Pattern.compile(
        "(\\sat\\s+[\\w.$]+\\([\\w.]+:\\d+\\))"
    );

    public static String analyzeRecentLogs(Path logsFolder, int maxLines) {
        if (!Files.exists(logsFolder)) {
            return "No logs folder found";
        }

        try {
            Optional<Path> latestLog = Files.walk(logsFolder)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".log"))
                .max(Comparator.comparingLong(p -> {
                    try { return Files.getLastModifiedTime(p).toMillis(); }
                    catch (Exception e) { return 0L; }
                }));

            return latestLog
                .map(path -> {
                    try {
                        List<String> lines = Files.readAllLines(path);
                        int start = Math.max(0, lines.size() - maxLines);
                        return lines.subList(start, lines.size())
                            .stream()
                            .collect(Collectors.joining("\n"));
                    } catch (IOException e) {
                        return "Error reading log file: " + e.getMessage();
                    }
                })
                .orElse("No log files found");
        } catch (IOException e) {
            return "Error analyzing logs: " + e.getMessage();
        }
    }

    public static List<String> extractErrors(String logContent) {
        List<String> errors = new ArrayList<>();
        
        String[] lines = logContent.split("\n");
        boolean inStackTrace = false;
        StringBuilder stackTrace = new StringBuilder();

        for (String line : lines) {
            if (ERROR_PATTERN.matcher(line).find()) {
                if (inStackTrace && stackTrace.length() > 0) {
                    errors.add(stackTrace.toString());
                    stackTrace = new StringBuilder();
                }
                inStackTrace = false;
                errors.add(line.trim());
            } else if (STACK_TRACE_PATTERN.matcher(line).find()) {
                inStackTrace = true;
                stackTrace.append(line).append("\n");
            } else if (inStackTrace && line.trim().isEmpty()) {
                inStackTrace = false;
                if (stackTrace.length() > 0) {
                    errors.add(stackTrace.toString());
                    stackTrace = new StringBuilder();
                }
            }
        }

        if (stackTrace.length() > 0) {
            errors.add(stackTrace.toString());
        }

        return errors;
    }

    public static String summarizeErrors(String logContent) {
        List<String> errors = extractErrors(logContent);
        
        if (errors.isEmpty()) {
            return "No errors found in recent logs.";
        }

        StringBuilder summary = new StringBuilder();
        summary.append("Found ").append(errors.size()).append(" error(s):\n\n");

        int count = 1;
        for (String error : errors) {
            String firstLine = error.split("\n")[0];
            summary.append(count).append(". ").append(firstLine).append("\n");
            count++;
            
            if (count > 10) {
                summary.append("\n... and ").append(errors.size() - 10).append(" more");
                break;
            }
        }

        return summary.toString();
    }

    public static String getServerInfo(Path serverFolder) {
        StringBuilder info = new StringBuilder();
        
        Path spigotyml = serverFolder.resolve("spigot.yml");
        Path paperyml = serverFolder.resolve("paper.yml");
        Path bukkityml = serverFolder.resolve("bukkit.yml");

        for (Path yml : Arrays.asList(spigotyml, paperyml, bukkityml)) {
            if (Files.exists(yml)) {
                try {
                    String content = Files.readString(yml);
                    if (yml.getFileName().toString().equals("spigot.yml")) {
                        Matcher versionMatcher = Pattern.compile("version:\\s*(\\S+)").matcher(content);
                        if (versionMatcher.find()) {
                            info.append("Spigot version: ").append(versionMatcher.group(1)).append("\n");
                        }
                    }
                } catch (IOException ignored) {}
            }
        }

        return info.length() > 0 ? info.toString() : "Server info not available";
    }

    public static String getPluginList(Path pluginsFolder) {
        if (!Files.exists(pluginsFolder)) {
            return "No plugins folder found";
        }

        try {
            return Files.list(pluginsFolder)
                .filter(p -> p.toString().endsWith(".jar"))
                .map(p -> p.getFileName().toString().replace(".jar", ""))
                .sorted()
                .collect(Collectors.joining(", "));
        } catch (IOException e) {
            return "Error listing plugins: " + e.getMessage();
        }
    }
}
