package net.rocky.coresuite.test;

import net.rocky.coresuite.test.core.*;
import net.rocky.coresuite.test.tier1.Tier1FeatureCoverageTests;
import net.rocky.coresuite.test.tier2.Tier2BoundaryCornerCaseTests;
import net.rocky.coresuite.test.tier3.Tier3CrossFeatureTests;
import net.rocky.coresuite.test.tier4.Tier4RealWorldScenarioTests;
import net.rocky.coresuite.test.tier5.Tier5AdversarialCombatAtmosphereTests;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class E2ETestRunner {

    public static void main(String[] args) {
        System.out.println("===============================================================================");
        System.out.println("            CORESUITE WORLD BOSSES MODULE OVERHAUL - E2E TEST RUNNER           ");
        System.out.println("===============================================================================");
        System.out.println("Java Version : " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
        System.out.println("Working Dir  : " + Paths.get(".").toAbsolutePath().normalize());
        System.out.println();

        TestContext ctx = TestContext.get();
        TestSuiteResult suiteResult = new TestSuiteResult();

        long suiteStart = System.currentTimeMillis();

        // 1. Run Tier 1
        System.out.println(">>> Executing Tier 1: Feature Coverage (>=5 per feature across 19 features)...");
        List<TestResult> t1Results = Tier1FeatureCoverageTests.runAll(ctx);
        t1Results.forEach(suiteResult::addResult);
        printTierSummary(1, t1Results);

        // 2. Run Tier 2
        System.out.println(">>> Executing Tier 2: Boundary & Corner Cases (>=5 per feature across 19 features)...");
        List<TestResult> t2Results = Tier2BoundaryCornerCaseTests.runAll(ctx);
        t2Results.forEach(suiteResult::addResult);
        printTierSummary(2, t2Results);

        // 3. Run Tier 3
        System.out.println(">>> Executing Tier 3: Cross-Feature Combinations (Pairwise interactions)...");
        List<TestResult> t3Results = Tier3CrossFeatureTests.runAll(ctx);
        t3Results.forEach(suiteResult::addResult);
        printTierSummary(3, t3Results);

        // 4. Run Tier 4
        System.out.println(">>> Executing Tier 4: Real-World Application Scenarios (Full workflows)...");
        List<TestResult> t4Results = Tier4RealWorldScenarioTests.runAll(ctx);
        t4Results.forEach(suiteResult::addResult);
        printTierSummary(4, t4Results);

        // 5. Run Tier 5
        System.out.println(">>> Executing Tier 5: Adversarial Hardening (Combat, Atmosphere & Minions)...");
        List<TestResult> t5Results = Tier5AdversarialCombatAtmosphereTests.runAll(ctx);
        t5Results.forEach(suiteResult::addResult);
        printTierSummary(5, t5Results);

        long suiteDuration = System.currentTimeMillis() - suiteStart;

        // Print Global Summary
        System.out.println();
        System.out.println("===============================================================================");
        System.out.println("                           GLOBAL E2E TEST SUITE SUMMARY                       ");
        System.out.println("===============================================================================");
        System.out.printf("Total Tests Executed : %d%n", suiteResult.getTotalCount());
        System.out.printf("Tests Passed         : %d%n", suiteResult.getPassCount());
        System.out.printf("Tests Failed         : %d%n", suiteResult.getFailCount());
        System.out.printf("Tests Escalated      : %d%n", suiteResult.getEscalateCount());
        System.out.printf("Pass Rate            : %.2f%%%n", suiteResult.getPassRate());
        System.out.printf("Total Duration       : %d ms%n", suiteDuration);
        System.out.println("===============================================================================");

        // Write JSON report
        try {
            Path reportDir = Paths.get("build/test-results");
            Files.createDirectories(reportDir);
            Path reportFile = reportDir.resolve("e2e-report.json");
            Files.writeString(reportFile, suiteResult.toJson(), StandardCharsets.UTF_8);
            System.out.println("E2E Test Report saved to: " + reportFile.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to write e2e-report.json: " + e.getMessage());
        }

        if (suiteResult.getFailCount() > 0) {
            System.out.println("\nFailures Details:");
            for (TestResult r : suiteResult.getResults()) {
                if (r.getStatus() == TestResult.Status.FAIL) {
                    System.out.printf("  - [%s] %s: %s%n", r.getTestId(), r.getDescription(), r.getFailureMessage());
                }
            }
        }

        System.exit(suiteResult.getFailCount() > 0 ? 1 : 0);
    }

    private static void printTierSummary(int tier, List<TestResult> results) {
        long pass = results.stream().filter(r -> r.getStatus() == TestResult.Status.PASS).count();
        long fail = results.stream().filter(r -> r.getStatus() == TestResult.Status.FAIL).count();
        double rate = results.isEmpty() ? 0.0 : ((double) pass / results.size()) * 100.0;
        System.out.printf("    Tier %d Results: %d/%d Passed (%.1f%%), %d Failed%n%n",
                tier, pass, results.size(), rate, fail);
    }
}
