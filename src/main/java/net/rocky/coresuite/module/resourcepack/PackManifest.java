package net.rocky.coresuite.module.resourcepack;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipInputStream;

/** Small public manifest; pack URLs must belong to the configured GitHub repository. */
public record PackManifest(String version, URI url, String sha1, int size) {
    public static PackManifest parse(byte[] bytes, String repository) throws IOException {
        if (!repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) throw new IOException("Invalid repository");
        Properties p = new Properties();
        p.load(new StringReader(new String(bytes, StandardCharsets.UTF_8)));
        String version = p.getProperty("version", "");
        String hash = p.getProperty("sha1", "").toLowerCase(Locale.ROOT);
        try {
            URI url = URI.create(p.getProperty("url", ""));
            int size = Integer.parseInt(p.getProperty("size", "0"));
            if (!version.matches("[A-Za-z0-9._-]{1,100}") || !hash.matches("[0-9a-f]{40}")
                    || size < 1 || size > 128 * 1024 * 1024
                    || !"https".equals(url.getScheme()) || !"github.com".equals(url.getHost())
                    || url.getUserInfo() != null || url.getPort() != -1 || url.getQuery() != null || url.getFragment() != null
                    || !url.getPath().startsWith("/" + repository + "/releases/download/")
                    || !url.getPath().endsWith("/meermc-pack.zip") || url.getPath().contains(".."))
                throw new IOException("Invalid pack manifest fields");
            return new PackManifest(version, url, hash, size);
        } catch (IllegalArgumentException ex) { throw new IOException("Invalid pack manifest", ex); }
    }

    public void verify(byte[] zip) throws Exception {
        if (zip.length != size || !HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(zip)).equals(sha1))
            throw new IOException("Pack size or SHA-1 mismatch");
        boolean metadata = false;
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(zip))) {
            long expanded = 0;
            byte[] buffer = new byte[8192];
            for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
                if (entry.getName().equals("pack.mcmeta")) metadata = true;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    expanded += read;
                    if (expanded > 512L * 1024 * 1024) throw new IOException("Expanded pack exceeds limit");
                }
            }
        }
        if (!metadata) throw new IOException("ZIP has no root pack.mcmeta");
    }

    static byte[] download(URI uri, int limit) throws IOException {
        long deadline = System.nanoTime() + 90_000_000_000L;
        for (int redirects = 0; redirects <= 5; redirects++) {
            String host = uri.getHost();
            if (!"https".equals(uri.getScheme()) || uri.getUserInfo() != null
                    || !("github.com".equals(host) || "release-assets.githubusercontent.com".equals(host)
                    || "objects.githubusercontent.com".equals(host))) throw new IOException("Untrusted download redirect");
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setConnectTimeout(10000); connection.setReadTimeout(10000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "CoreSuite-ResourcePack/1.0");
            connection.setRequestProperty("Cache-Control", "no-cache");
            try {
                int status = connection.getResponseCode();
                if (status >= 300 && status < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw new IOException("Redirect missing location");
                    uri = uri.resolve(location); continue;
                }
                if (status != 200) throw new IOException("GitHub returned HTTP " + status);
                if (connection.getContentLengthLong() > limit) throw new IOException("Download too large");
                try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192]; int n;
                    while ((n = in.read(buffer)) != -1) {
                        if (out.size() + n > limit || System.nanoTime() > deadline || Thread.currentThread().isInterrupted())
                            throw new IOException("Download exceeded size/time limit");
                        out.write(buffer, 0, n);
                    }
                    return out.toByteArray();
                }
            } finally { connection.disconnect(); }
        }
        throw new IOException("Too many redirects");
    }
}
