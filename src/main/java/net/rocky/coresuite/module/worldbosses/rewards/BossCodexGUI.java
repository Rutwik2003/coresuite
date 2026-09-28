package net.rocky.coresuite.module.worldbosses.rewards;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.gui.Gui;
import net.rocky.coresuite.common.gui.GuiButton;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.worldbosses.WorldBossesModule;
import net.rocky.coresuite.module.worldbosses.boss.BossType;
import net.rocky.coresuite.module.worldbosses.boss.WorldBoss;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class BossCodexGUI {

    private final CoreSuitePlugin plugin;
    private final WorldBossesModule module;
    private final HunterExchangeGUI exchangeGUI;
    private final BossDiscoveryManager discoveryManager;

    public BossCodexGUI(CoreSuitePlugin plugin, WorldBossesModule module, HunterExchangeGUI exchangeGUI, BossDiscoveryManager discoveryManager) {
        this.plugin = plugin;
        this.module = module;
        this.exchangeGUI = exchangeGUI;
        this.discoveryManager = discoveryManager;
    }

    public BossCodexGUI(CoreSuitePlugin plugin, WorldBossesModule module, HunterExchangeGUI exchangeGUI) {
        this(plugin, module, exchangeGUI, null);
    }

    public void open(Player player) {
        Gui gui = new Gui("&8▶ &4&lWorld Bosses &8- &6Hunting Codex &8◀", 5);
        gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        // Header: Overview
        List<String> overviewLore = List.of(
            "&8&m────────────────────────────────",
            "&7The Hunting Codex chronicles the realm's primordial",
            "&7titans. Slay or participate in defeating bosses to",
            "&7permanently decipher their ancient mythological lore.",
            "",
            "&e▶ Click any discovered boss below to view full lore!",
            "&8&m────────────────────────────────"
        );
        gui.setItem(4, GuiButton.of(Material.WRITABLE_BOOK, "&6&lThe Hunting Codex", overviewLore, e -> {
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);
        }));

        // ── Boss 1: Valerius, The Ashen Vanguard ──
        renderBossSlot(gui, player, 11, BossType.VALERIUS, Material.NETHERITE_AXE,
            "&c&lValerius, The Ashen Vanguard",
            "&8Type: &7Wither Skeleton | &cAdaptive HP",
            BossLore.lines(BossType.VALERIUS),
            List.of(
                "  &c• Phase 1 (100%-60%): &7The Forge",
                "  &6• Phase 2 (60%-20%): &7Meltdown",
                "  &4• Phase 3 (20%-0%): &7Ash Void"
            ),
            "&c&lThe Vanguard's Cleaver &7(+20% damage vs burning foes)"
        );

        // ── Boss 2: Aethelgard, The Root-Bound King ──
        renderBossSlot(gui, player, 13, BossType.AETHELGARD, Material.CARVED_PUMPKIN,
            "&2&lAethelgard, The Root-Bound King",
            "&8Type: &7Husk | &2Adaptive HP",
            BossLore.lines(BossType.AETHELGARD),
            List.of(
                "  &a• Phase 1 (100%-50%): &7Overgrowth",
                "  &e• Phase 2 (50%-20%): &7The Harvest",
                "  &c• Phase 3 (20%-0%): &7Deadwood"
            ),
            "&a&lCrown of the Forest &7(Regeneration I in daylight/grass)"
        );

        // ── Boss 3: Xol'thos, The Weeping Widow ──
        renderBossSlot(gui, player, 15, BossType.XOLTHOS, Material.SPIDER_EYE,
            "&5&lXol'thos, The Weeping Widow",
            "&8Type: &7Cave Spider | &5Adaptive HP",
            BossLore.lines(BossType.XOLTHOS),
            List.of(
                "  &5• Phase 1 (100%-70%): &7The Trap",
                "  &e• Phase 2 (70%-30%): &7The Brood",
                "  &c• Phase 3 (30%-0%): &7Frenzy"
            ),
            "&5&lThe Widow's Fang &7(Poison II + 15% Movement Speed)"
        );

        renderBossSlot(gui, player, 22, BossType.BOREALIS, Material.BLUE_ICE,
            "&b&lBorealis, The Permafrost Tyrant", "&8Type: &7Stray | &bAdaptive HP",
            BossLore.lines(BossType.BOREALIS),
            List.of("&b100%-60%: Permafrost", "&360%-20%: Whiteout", "&c20%-0%: Absolute Zero"),
            "&bFrostbound Striders &7(Glacial Guard boots)");
        // Active Boss Status / Next Spawn Tracker
        WorldBoss active = module.getActiveBoss();
        List<String> statusLore = new ArrayList<>();
        statusLore.add("&8&m────────────────────────────────");
        if (active != null && active.isActive()) {
            statusLore.add("&aStatus: &2&lALIVE AND RAGING");
            statusLore.add("&7Boss: &e" + active.getDisplayName());
            statusLore.add("&7Health: &c" + String.format("%,.0f", active.getCurrentHealth()) + "/" + String.format("%,.0f", active.getMaxHealth()));
            if (active.getCurrentPhase() != null) {
                statusLore.add("&7Phase: &6Phase " + active.getCurrentPhase().getPhaseNumber() + " (" + active.getCurrentPhase().getName() + ")");
            }
            if (active.getSpawnLocation() != null) {
                Location l = active.getSpawnLocation();
                statusLore.add("&7Domain: &e" + l.getWorld().getName() + " (" + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ() + ")");
            }
        } else {
            int sec = (module.getScheduler() != null) ? module.getScheduler().getSecondsRemaining() : 0;
            statusLore.add("&aStatus: &7No Boss Active");
            statusLore.add("&7Next Awakening in: &e" + formatTime(sec));
        }
        statusLore.add("&8&m────────────────────────────────");
        gui.setItem(30, GuiButton.of(Material.COMPASS, "&e&lInvasion Tracker", statusLore, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        }));

        // Garrick's Exchange Shop Button
        List<String> shopLore = List.of(
            "&8&m────────────────────────────────",
            "&7Exchange your accumulated boss shards",
            "&7for Crate Keys and Mythic Signature Weapons!",
            "",
            "&e▶ Click to open Garrick's Hunter Exchange!",
            "&8&m────────────────────────────────"
        );
        gui.setItem(32, GuiButton.of(Material.EMERALD, "&6&lGarrick's Hunter Exchange", shopLore, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            exchangeGUI.open(player);
        }));

        // Close button
        gui.setItem(40, GuiButton.of(Material.BARRIER, "&c&lClose", List.of("&7Click to close the codex."), e -> {
            player.closeInventory();
        }));

        gui.open(player);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.2f);
    }

    private void renderBossSlot(Gui gui, Player player, int slot, BossType type, Material icon,
                                String title, String headerLine, List<String> loreLines,
                                List<String> phaseLines, String signatureDrop) {
        boolean discovered = (discoveryManager != null && discoveryManager.hasDiscovered(player.getUniqueId(), type));

        if (!discovered) {
            List<String> lockedLore = List.of(
                "&8&m────────────────────────────────",
                "&7This primordial terror remains undiscovered.",
                "&7Participate in defeating or slaying this World Boss",
                "&7to reveal its ancient mythological lore and combat phases.",
                "&8&m────────────────────────────────",
                "&c🔒 Locked Codex Entry"
            );
            gui.setItem(slot, GuiButton.of(Material.BARRIER, "&8&l??? Undiscovered World Boss ???", lockedLore, e -> {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                TextUtil.sendMessage(player, "&cThis World Boss is undiscovered! Slay it first to unlock its codex entry.");
            }));
            return;
        }

        List<String> lore = new ArrayList<>();
        lore.add(headerLine);
        lore.add("&8&m────────────────────────────────");
        lore.add("&e📜 Lore Backstory:");
        lore.addAll(loreLines);
        lore.add("");
        lore.add("&6⚔ Combat Phases:");
        lore.addAll(phaseLines);
        lore.add("");
        lore.add("&e🏆 Signature Shop Equipment:");
        lore.add("  " + signatureDrop);
        lore.add("&8&m────────────────────────────────");
        lore.add("&a▶ Click to print chronicles to chat!");

        gui.setItem(slot, GuiButton.of(icon, title, lore, e -> {
            player.closeInventory();
            sendBossCodexChat(player, type);
        }));
    }

    public void sendBossCodexChat(Player player, BossType type) {
        if (discoveryManager != null && !discoveryManager.hasDiscovered(player.getUniqueId(), type)) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            TextUtil.sendMessage(player, "&cThis World Boss is undiscovered! Slay it first to unlock its codex entry.");
            return;
        }

        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.2f);
        TextUtil.sendMessage(player, "&6Ancient Chronicles — " + type.name());
        for (String line : BossLore.lines(type)) TextUtil.sendMessage(player, line);
    }

    private String formatTime(int seconds) {
        if (seconds <= 0) return "Imminent!";
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }
}
