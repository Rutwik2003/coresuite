package net.rocky.coresuite.module.gambling.gui;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.gui.Gui;
import net.rocky.coresuite.common.gui.GuiButton;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.Consumer;

/**
 * 1-click casino disclaimer screen (no multi-step popups).
 */
public class CasinoTOSGUI {

    public static void open(CoreSuitePlugin plugin, Player player, Consumer<Player> onAccepted) {
        Gui gui = new Gui("&8▶ &6&lCasino Disclaimer &8◀", 3);
        gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        gui.setItem(13, GuiButton.of(Material.GOLD_BLOCK, "&6&lWelcome to Royal Casino!",
            List.of(
                "&8&m────────────────────",
                "&7Please play responsibly!",
                "&7All casino games carry a house edge.",
                "&7In-game money losses are final.",
                "&8&m────────────────────",
                "&e▶ Click below to enter the casino!"
            ), e -> {}
        ));

        // Accept button (1 click) at slot 11
        gui.setItem(11, GuiButton.of(Material.GREEN_STAINED_GLASS_PANE, "&a&l✓ Enter Casino",
            List.of(
                "&8&m────────────────────",
                "&7One-time confirmation.",
                "&7You will not see this screen again.",
                "&8&m────────────────────",
                "&a▶ Click to enter Royal Casino"
            ),
            e -> {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                onAccepted.accept(player);
            }
        ));

        // Decline button at slot 15
        gui.setItem(15, GuiButton.of(Material.RED_STAINED_GLASS_PANE, "&c&l✗ Decline",
            List.of("&7Close and return to the game."),
            e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                player.closeInventory();
            }
        ));

        gui.open(player);
    }
}
