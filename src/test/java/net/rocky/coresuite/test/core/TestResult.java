package net.rocky.coresuite.test.core;

public final class TestResult {
    public enum Status { PASS, FAIL, ESCALATE, SKIP }

    private final String testId;
    private final String featureName;
    private final int tier;
    private final String description;
    private final Status status;
    private final String failureMessage;
    private final Throwable exception;
    private final long durationMs;

    public TestResult(String testId, String featureName, int tier, String description,
                      Status status, String failureMessage, Throwable exception, long durationMs) {
        this.testId = testId;
        this.featureName = featureName;
        this.tier = tier;
        this.description = description;
        this.status = status;
        this.failureMessage = failureMessage;
        this.exception = exception;
        this.durationMs = durationMs;
    }

    public static TestResult pass(String testId, String featureName, int tier, String description, long durationMs) {
        return new TestResult(testId, featureName, tier, description, Status.PASS, null, null, durationMs);
    }

    public static TestResult fail(String testId, String featureName, int tier, String description, String msg, long durationMs) {
        return new TestResult(testId, featureName, tier, description, Status.FAIL, msg, null, durationMs);
    }

    public static TestResult escalate(String testId, String featureName, int tier, String description, String msg, Throwable t, long durationMs) {
        return new TestResult(testId, featureName, tier, description, Status.ESCALATE, msg, t, durationMs);
    }

    public String getTestId() { return testId; }
    public String getFeatureName() { return featureName; }
    public int getTier() { return tier; }
    public String getDescription() { return description; }
    public Status getStatus() { return status; }
    public String getFailureMessage() { return failureMessage; }
    public Throwable getException() { return exception; }
    public long getDurationMs() { return durationMs; }

    @Override
    public String toString() {
        return String.format("[%s] [T%d] %s - %s (%dms)%s",
                status, tier, testId, description, durationMs,
                failureMessage != null ? " -> " + failureMessage : "");
    }
}
