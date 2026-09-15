package net.rocky.coresuite.test.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TestContext {
    private static TestContext instance;

    private final Path rootDir;
    private final Path resourcePackDir;
    private final Path configFile;
    private final Map<String, Object> configMap = new HashMap<>();

    private TestContext() {
        this.rootDir = Paths.get(".").toAbsolutePath().normalize();
        this.resourcePackDir = rootDir.resolve("resourcepack");
        this.configFile = rootDir.resolve("src/main/resources/modules/world-bosses.yml");
        loadConfig();
    }

    public static synchronized TestContext get() {
        if (instance == null) {
            instance = new TestContext();
        }
        return instance;
    }

    public Path getRootDir() { return rootDir; }
    public Path getResourcePackDir() { return resourcePackDir; }
    public Path getConfigFile() { return configFile; }

    public Map<String, Object> getConfig() {
        return Collections.unmodifiableMap(configMap);
    }

    public String getConfigString(String key, String def) {
        Object val = getNested(key);
        return val != null ? String.valueOf(val) : def;
    }

    public double getConfigDouble(String key, double def) {
        Object val = getNested(key);
        if (val instanceof Number num) return num.doubleValue();
        if (val != null) {
            try { return Double.parseDouble(String.valueOf(val)); } catch (Exception ignored) {}
        }
        return def;
    }

    public int getConfigInt(String key, int def) {
        Object val = getNested(key);
        if (val instanceof Number num) return num.intValue();
        if (val != null) {
            try { return Integer.parseInt(String.valueOf(val)); } catch (Exception ignored) {}
        }
        return def;
    }

    public boolean getConfigBoolean(String key, boolean def) {
        Object val = getNested(key);
        if (val instanceof Boolean b) return b;
        if (val != null) return Boolean.parseBoolean(String.valueOf(val));
        return def;
    }

    private Object getNested(String key) {
        String[] parts = key.split("\\.");
        Map<String, Object> curr = configMap;
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = curr.get(parts[i]);
            if (next instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) next;
                curr = m;
            } else {
                return null;
            }
        }
        return curr.get(parts[parts.length - 1]);
    }

    private void loadConfig() {
        if (!Files.exists(configFile)) return;
        try {
            List<String> lines = Files.readAllLines(configFile, StandardCharsets.UTF_8);
            parseSimpleYaml(lines, configMap);
        } catch (Exception e) {
            System.err.println("Warning: could not parse world-bosses.yml: " + e.getMessage());
        }
    }

    private void parseSimpleYaml(List<String> lines, Map<String, Object> target) {
        // Simple hierarchical YAML parser for standard key: value pairs
        Map<Integer, Map<String, Object>> indentStack = new HashMap<>();
        indentStack.put(0, target);

        for (String rawLine : lines) {
            String line = rawLine.replaceAll("#.*$", ""); // remove comments
            if (line.trim().isEmpty()) continue;

            int indent = 0;
            while (indent < rawLine.length() && rawLine.charAt(indent) == ' ') {
                indent++;
            }

            int level = indent / 2;
            int colonIdx = line.indexOf(':');
            if (colonIdx == -1) continue;

            String key = line.substring(0, colonIdx).trim();
            String value = line.substring(colonIdx + 1).trim();

            Map<String, Object> parent = indentStack.getOrDefault(level, target);

            if (value.isEmpty()) {
                Map<String, Object> child = new HashMap<>();
                parent.put(key, child);
                indentStack.put(level + 1, child);
            } else {
                if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                    value = value.substring(1, value.length() - 1);
                } else if (value.startsWith("'") && value.endsWith("'") && value.length() >= 2) {
                    value = value.substring(1, value.length() - 1);
                }
                parent.put(key, value);
            }
        }
    }

    /**
     * Mathematical model of 72-point perimeter circle
     */
    public static List<double[]> calculatePerimeterRing(double centerX, double centerZ, double radius, int points) {
        List<double[]> coords = new ArrayList<>(points);
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            double x = centerX + (radius * Math.cos(angle));
            double z = centerZ + (radius * Math.sin(angle));
            coords.add(new double[]{x, z, angle});
        }
        return coords;
    }

    /**
     * Checks OGG Vorbis magic header: 'OggS' (0x4F, 0x67, 0x67, 0x53)
     */
    public static boolean isValidOggVorbis(Path oggPath) {
        if (!Files.exists(oggPath) || !Files.isRegularFile(oggPath)) return false;
        try {
            if (Files.size(oggPath) < 4) return false;
            byte[] header = new byte[4];
            try (InputStream is = Files.newInputStream(oggPath)) {
                int read = is.read(header);
                if (read < 4) return false;
            }
            return header[0] == 'O' && header[1] == 'g' && header[2] == 'g' && header[3] == 'S';
        } catch (IOException e) {
            return false;
        }
    }
}
