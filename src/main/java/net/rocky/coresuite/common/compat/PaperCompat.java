package net.rocky.coresuite.common.compat;

import java.lang.reflect.Method;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.inventory.meta.ItemMeta;

/** API differences within the supported Paper releases; no server-internal access. */
public final class PaperCompat {
    public static final Attribute MAX_HEALTH = attribute("MAX_HEALTH", "GENERIC_MAX_HEALTH");
    public static final Attribute MOVEMENT_SPEED = attribute("MOVEMENT_SPEED", "GENERIC_MOVEMENT_SPEED");
    public static final Attribute ATTACK_DAMAGE = attribute("ATTACK_DAMAGE", "GENERIC_ATTACK_DAMAGE");
    public static final Attribute SCALE = attribute("SCALE", "GENERIC_SCALE");
    private static final Method ITEM_MODEL = itemModelMethod();

    private PaperCompat() {}

    private static Attribute attribute(String current, String legacy) {
        for (String name : new String[]{current, legacy}) {
            try { return (Attribute) Attribute.class.getField(name).get(null); }
            catch (NoSuchFieldException ignored) {}
            catch (ReflectiveOperationException e) { throw new IllegalStateException("Cannot access attribute " + name, e); }
        }
        throw new IllegalStateException("Missing required attribute " + current);
    }

    private static Method itemModelMethod() {
        try { return ItemMeta.class.getMethod("setItemModel", NamespacedKey.class); }
        catch (NoSuchMethodException ignored) { return null; }
    }

    private static final java.util.Map<String, org.bukkit.Sound> SOUNDS = new java.util.concurrent.ConcurrentHashMap<>();

    public static org.bukkit.Sound sound(String name) {
        return SOUNDS.computeIfAbsent(name, value -> {
            try { return (org.bukkit.Sound) org.bukkit.Sound.class.getField(value).get(null); }
            catch (ReflectiveOperationException e) { throw new IllegalArgumentException("Unknown sound " + value, e); }
        });
    }

    public static void setItemModel(ItemMeta meta, NamespacedKey key) {
        if (ITEM_MODEL != null) {
            try { ITEM_MODEL.invoke(meta, key); }
            catch (ReflectiveOperationException e) { throw new IllegalStateException("Cannot set item model", e); }
        } else {
            LegacyItemModels.apply(meta, key);
        }
    }
}
