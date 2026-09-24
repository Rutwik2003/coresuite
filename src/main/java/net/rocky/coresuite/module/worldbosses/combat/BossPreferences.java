package net.rocky.coresuite.module.worldbosses.combat;

import net.rocky.coresuite.common.gui.Gui;
import net.rocky.coresuite.common.gui.GuiButton;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

public final class BossPreferences {
    private static final NamespacedKey QUIET = new NamespacedKey("coresuite", "boss_quiet");
    private static final NamespacedKey REDUCED = new NamespacedKey("coresuite", "boss_reduced_particles");
    private BossPreferences() {}
    public static boolean quiet(Player player) { return enabled(player, QUIET); }
    public static boolean reduced(Player player) { return enabled(player, REDUCED); }
    private static boolean enabled(Player player, NamespacedKey key) {
        return player.getPersistentDataContainer().getOrDefault(key, PersistentDataType.BYTE, (byte) 0) == 1;
    }
    private static void toggle(Player player, NamespacedKey key) {
        player.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) (enabled(player, key) ? 0 : 1));
    }
    public static void open(Player player, Runnable refreshMusic) {
        Gui gui = new Gui("&8Boss Settings", 3);
        gui.setItem(11, GuiButton.of(Material.NOTE_BLOCK, "&eQuieter boss music: " + quiet(player),
                java.util.List.of("&7Click to toggle background music volume."), event -> {
                    toggle(player, QUIET); refreshMusic.run(); open(player, refreshMusic);
                }));
        gui.setItem(15, GuiButton.of(Material.BLAZE_POWDER, "&eReduced decoration: " + reduced(player),
                java.util.List.of("&7Fewer boundary and ambient particles.", "&7Attack warnings stay visible."), event -> {
                    toggle(player, REDUCED); open(player, refreshMusic);
                }));
        gui.open(player);
    }
}
