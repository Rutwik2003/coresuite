package net.rocky.coresuite.module.resourcepack;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class PackManifestTest {
    @Test void bedrockManifestUsesItsOwnArtifactAndRootMetadata() throws Exception {
        byte[] data=zip("manifest.json");
        byte[] manifest=manifest(data,"https://github.com/owner/pack/releases/download/v1/meermc-bedrock-audio.mcpack");
        var pack=PackManifest.parse(manifest,"owner/pack","meermc-bedrock-audio.mcpack");
        pack.verify(data,"manifest.json");
        assertThrows(IOException.class,()->PackManifest.parse(manifest,"owner/pack"));
        assertThrows(IOException.class,()->pack.verify(data));
        data[10]^=1;
        assertThrows(IOException.class,()->pack.verify(data,"manifest.json"));
    }
    private byte[] manifest(byte[] zip, String url) throws Exception {
        return ("version=pack-1\nurl=" + url + "\nsha1=" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(zip))
            + "\nsize=" + zip.length + "\n").getBytes(StandardCharsets.UTF_8);
    }
    private byte[] zip(String entry) throws Exception {
        var out = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(entry)); zip.write("{\"pack\":{\"pack_format\":34}}".getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
        }
        return out.toByteArray();
    }
    @Test void verifiesRealZipAndRejectsCorruptedPayload() throws Exception {
        byte[] zip = zip("pack.mcmeta");
        var pack = PackManifest.parse(manifest(zip,"https://github.com/owner/pack/releases/download/pack-1/meermc-pack.zip"), "owner/pack");
        pack.verify(zip);
        zip[10] ^= 1;
        assertThrows(IOException.class, () -> pack.verify(zip));
    }
    @Test void rejectsWrongRepositoryUntrustedHostAndMutableUrl() throws Exception {
        for (String url : new String[]{"http://github.com/owner/pack/releases/download/v1/meermc-pack.zip",
            "https://evil.example/owner/pack/releases/download/v1/meermc-pack.zip",
            "https://github.com/other/pack/releases/download/v1/meermc-pack.zip",
            "https://github.com/owner/pack/releases/latest/download/meermc-pack.zip"}) {
            byte[] bytes = manifest(zip("pack.mcmeta"), url);
            assertThrows(IOException.class, () -> PackManifest.parse(bytes, "owner/pack"));
        }
    }
    @Test void rejectsArchiveWithOnlyNestedMetadata() throws Exception {
        byte[] zip = zip("folder/pack.mcmeta");
        var pack = PackManifest.parse(manifest(zip,"https://github.com/owner/pack/releases/download/v1/meermc-pack.zip"), "owner/pack");
        assertThrows(IOException.class, () -> pack.verify(zip));
    }
    @Test void rejectsOversizedOrInvalidHashManifest() {
        String base = "version=v1\nurl=https://github.com/owner/pack/releases/download/v1/meermc-pack.zip\n";
        assertThrows(IOException.class, () -> PackManifest.parse((base + "sha1=broken\nsize=1").getBytes(), "owner/pack"));
        assertThrows(IOException.class, () -> PackManifest.parse((base + "sha1=" + "a".repeat(40) + "\nsize=2147483647").getBytes(), "owner/pack"));
    }
}
