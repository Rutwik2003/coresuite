package net.rocky.coresuite.common.compat;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.HashMap;
import java.util.Properties;

/** Stable IDs shared with scripts/build_legacy_fish_pack.py. */
public final class LegacyItemModels {
    private static final Map<String, Integer> IDS = load();
    private LegacyItemModels() {}
    private static Map<String, Integer> load() {
        Properties properties = new Properties();
        try (InputStream stream = LegacyItemModels.class.getResourceAsStream("/compat/fish-models.properties")) {
            if (stream == null) throw new IllegalStateException("Missing legacy fish model mapping");
            properties.load(stream);
        } catch (IOException error) { throw new IllegalStateException("Cannot load legacy fish models", error); }
        Map<String, Integer> result = new HashMap<>();
        properties.forEach((key, value) -> result.put((String)key, Integer.parseInt((String)value)));
        if (result.values().stream().distinct().count() != result.size()) throw new IllegalStateException("Duplicate legacy fish model IDs");
        return Map.copyOf(result);
    }
    public static Integer id(String key) { return IDS.get(key); }
    public static void apply(ItemMeta meta, NamespacedKey key) {
        Integer id = key == null ? null : id(key.toString());
        if (id != null) meta.setCustomModelData(id);
    }
}
