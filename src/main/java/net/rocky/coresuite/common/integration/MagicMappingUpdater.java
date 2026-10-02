package net.rocky.coresuite.common.integration;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Adds missing built-in mappings; preserves operator entries and conflicting IDs. */
public final class MagicMappingUpdater {
  public static boolean update(Path file, String defaults) throws java.io.IOException {
    if (!Files.exists(file)) {
      Files.createDirectories(file.getParent());
      Files.writeString(file, defaults, StandardCharsets.UTF_8);
      return true;
    }
    JsonObject current = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    JsonObject wanted = JsonParser.parseString(defaults).getAsJsonObject();
    if (!current.has("format_version") || current.get("format_version").getAsInt() != 2)
      return false;
    JsonObject items = current.getAsJsonObject("items");
    if (items == null) return false;
    boolean changed = false;
    for (var entry : wanted.getAsJsonObject("items").entrySet()) {
      JsonArray existing = items.getAsJsonArray(entry.getKey());
      if (existing == null) {
        items.add(entry.getKey(), entry.getValue().deepCopy());
        changed = true;
        continue;
      }
      for (JsonElement element : entry.getValue().getAsJsonArray()) {
        JsonObject addition = element.getAsJsonObject();
        boolean present = false;
        for (JsonElement value : existing) {
          JsonObject old = value.getAsJsonObject();
          if ((old.has("custom_model_data")
                  && old.get("custom_model_data").equals(addition.get("custom_model_data")))
              || (old.has("bedrock_identifier")
                  && old.get("bedrock_identifier").equals(addition.get("bedrock_identifier")))) {
            present = true;
            // Known original placeholder; preserve every other operator customization.
            if (old.has("bedrock_identifier")
                && old.get("bedrock_identifier").getAsString().equals("coresuite:sealed_tome")) {
              var options = old.getAsJsonObject("bedrock_options");
              if (options != null
                  && options.has("icon")
                  && options.get("icon").getAsString().equals("coresuite.astral_grimoire")) {
                options.addProperty("icon", "coresuite.sealed_tome");
                changed = true;
              }
            }
            break;
          }
        }
        if (!present) {
          existing.add(addition.deepCopy());
          changed = true;
        }
      }
    }
    if (!changed) return false;
    Path backup = file.resolveSibling(file.getFileName() + ".pre-magic-update.bak");
    if (!Files.exists(backup)) Files.copy(file, backup);
    Path temp = file.resolveSibling(file.getFileName() + ".tmp");
    Files.writeString(
        temp,
        new GsonBuilder().setPrettyPrinting().create().toJson(current),
        StandardCharsets.UTF_8);
    try {
      Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
    }
    return true;
  }

  private MagicMappingUpdater() {}
}
