package net.rocky.coresuite.test.core;

import java.util.Collection;
import java.util.Objects;

public final class TestAssertion {
    private TestAssertion() {}

    public static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message != null ? message : "Expected condition to be true, but was false");
        }
    }

    public static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError(message != null ? message : "Expected condition to be false, but was true");
        }
    }

    public static void assertEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError((message != null ? message + " " : "")
                    + "Expected [" + expected + "] but was [" + actual + "]");
        }
    }

    public static void assertEquals(double expected, double actual, double delta, String message) {
        if (Math.abs(expected - actual) > delta) {
            throw new AssertionError((message != null ? message + " " : "")
                    + "Expected [" + expected + "] but was [" + actual + "] with delta " + delta);
        }
    }

    public static void assertNotEquals(Object unexpected, Object actual, String message) {
        if (Objects.equals(unexpected, actual)) {
            throw new AssertionError((message != null ? message + " " : "")
                    + "Expected NOT [" + unexpected + "] but got equal value");
        }
    }

    public static void assertNotNull(Object obj, String message) {
        if (obj == null) {
            throw new AssertionError(message != null ? message : "Expected object to not be null");
        }
    }

    public static void assertNull(Object obj, String message) {
        if (obj != null) {
            throw new AssertionError(message != null ? message : "Expected object to be null, but was [" + obj + "]");
        }
    }

    public static void assertInRange(double value, double min, double max, String message) {
        if (value < min || value > max) {
            throw new AssertionError((message != null ? message + " " : "")
                    + "Value [" + value + "] not in expected range [" + min + ", " + max + "]");
        }
    }

    public static void assertContains(String text, String substring, String message) {
        if (text == null || !text.contains(substring)) {
            throw new AssertionError((message != null ? message + " " : "")
                    + "Expected text to contain [" + substring + "] but got [" + text + "]");
        }
    }

    public static void assertDoesNotContain(String text, String forbiddenSubstring, String message) {
        if (text != null && text.contains(forbiddenSubstring)) {
            throw new AssertionError((message != null ? message + " " : "")
                    + "Expected text to NOT contain [" + forbiddenSubstring + "] but found it in [" + text + "]");
        }
    }

    public static void assertContainsIgnoreCase(String text, String substring, String message) {
        if (text == null || !text.toLowerCase().contains(substring.toLowerCase())) {
            throw new AssertionError((message != null ? message + " " : "")
                    + "Expected text to contain (case-insensitive) [" + substring + "] but got [" + text + "]");
        }
    }

    public static void assertDoesNotContainIgnoreCase(String text, String forbiddenSubstring, String message) {
        if (text != null && text.toLowerCase().contains(forbiddenSubstring.toLowerCase())) {
            throw new AssertionError((message != null ? message + " " : "")
                    + "Expected text to NOT contain (case-insensitive) [" + forbiddenSubstring + "] but found it in [" + text + "]");
        }
    }

    public static <T> void assertContainsElement(Collection<T> collection, T element, String message) {
        if (collection == null || !collection.contains(element)) {
            throw new AssertionError((message != null ? message + " " : "")
                    + "Collection does not contain expected element [" + element + "]");
        }
    }

    public static void fail(String message) {
        throw new AssertionError(message);
    }
}
