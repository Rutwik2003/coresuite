package net.rocky.coresuite.test.core;

import java.util.*;

public final class TestSuiteResult {
    private final List<TestResult> results = new ArrayList<>();
    private long totalDurationMs = 0;

    public void addResult(TestResult result) {
        results.add(result);
        totalDurationMs += result.getDurationMs();
    }

    public List<TestResult> getResults() {
        return Collections.unmodifiableList(results);
    }

    public int getTotalCount() {
        return results.size();
    }

    public int getPassCount() {
        return (int) results.stream().filter(r -> r.getStatus() == TestResult.Status.PASS).count();
    }

    public int getFailCount() {
        return (int) results.stream().filter(r -> r.getStatus() == TestResult.Status.FAIL).count();
    }

    public int getEscalateCount() {
        return (int) results.stream().filter(r -> r.getStatus() == TestResult.Status.ESCALATE).count();
    }

    public double getPassRate() {
        if (results.isEmpty()) return 0.0;
        return ((double) getPassCount() / results.size()) * 100.0;
    }

    public Map<Integer, List<TestResult>> getResultsByTier() {
        Map<Integer, List<TestResult>> map = new TreeMap<>();
        for (TestResult r : results) {
            map.computeIfAbsent(r.getTier(), k -> new ArrayList<>()).add(r);
        }
        return map;
    }

    public Map<String, List<TestResult>> getResultsByFeature() {
        Map<String, List<TestResult>> map = new LinkedHashMap<>();
        for (TestResult r : results) {
            map.computeIfAbsent(r.getFeatureName(), k -> new ArrayList<>()).add(r);
        }
        return map;
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"totalCount\": ").append(getTotalCount()).append(",\n");
        sb.append("  \"passCount\": ").append(getPassCount()).append(",\n");
        sb.append("  \"failCount\": ").append(getFailCount()).append(",\n");
        sb.append("  \"escalateCount\": ").append(getEscalateCount()).append(",\n");
        sb.append(String.format(Locale.US, "  \"passRate\": %.2f,\n", getPassRate()));
        sb.append("  \"totalDurationMs\": ").append(totalDurationMs).append(",\n");
        sb.append("  \"tests\": [\n");

        for (int i = 0; i < results.size(); i++) {
            TestResult r = results.get(i);
            sb.append("    {\n");
            sb.append("      \"testId\": \"").append(escapeJson(r.getTestId())).append("\",\n");
            sb.append("      \"featureName\": \"").append(escapeJson(r.getFeatureName())).append("\",\n");
            sb.append("      \"tier\": ").append(r.getTier()).append(",\n");
            sb.append("      \"description\": \"").append(escapeJson(r.getDescription())).append("\",\n");
            sb.append("      \"status\": \"").append(r.getStatus()).append("\",\n");
            sb.append("      \"durationMs\": ").append(r.getDurationMs());
            if (r.getFailureMessage() != null) {
                sb.append(",\n      \"failureMessage\": \"").append(escapeJson(r.getFailureMessage())).append("\"");
            }
            sb.append("\n    }");
            if (i < results.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
