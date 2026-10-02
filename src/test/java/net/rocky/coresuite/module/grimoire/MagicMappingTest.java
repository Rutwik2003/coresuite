package net.rocky.coresuite.module.grimoire;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import java.nio.file.*;
import net.rocky.coresuite.common.integration.MagicMappingUpdater;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MagicMappingTest {
  @TempDir Path directory;

  @Test
  void updateAddsMissingModelsPreservesCustomAndBacksUpOriginal() throws Exception {
    Path file = directory.resolve("mapping.json");
    String original =
        """
{"format_version":2,"items":{"minecraft:book":[
{"custom_model_data":71001,"bedrock_identifier":"coresuite:astral_grimoire","bedrock_options":{"icon":"custom.icon"}},
{"custom_model_data":71002,"bedrock_identifier":"coresuite:sealed_tome","bedrock_options":{"icon":"coresuite.astral_grimoire"}}]}}
""";
    Files.writeString(file, original);
    String defaults =
        Files.readString(Path.of("src/main/resources/integrations/coresuite_magic.json"));
    assertTrue(MagicMappingUpdater.update(file, defaults));
    var json =
        JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonObject("items");
    assertEquals(10, json.getAsJsonArray("minecraft:book").size());
    assertEquals(
        "custom.icon",
        json.getAsJsonArray("minecraft:book")
            .get(0)
            .getAsJsonObject()
            .getAsJsonObject("bedrock_options")
            .get("icon")
            .getAsString());
    assertEquals(
        "coresuite.sealed_tome",
        json.getAsJsonArray("minecraft:book")
            .get(1)
            .getAsJsonObject()
            .getAsJsonObject("bedrock_options")
            .get("icon")
            .getAsString());
    assertTrue(json.has("minecraft:blaze_rod"));
    assertEquals(
        original, Files.readString(directory.resolve("mapping.json.pre-magic-update.bak")));
    assertFalse(MagicMappingUpdater.update(file, defaults));
  }
}
