package net.rocky.coresuite;

import net.rocky.coresuite.common.command.CoreSuiteCommand;
import net.rocky.coresuite.common.economy.EconomyManager;
import net.rocky.coresuite.common.gui.GuiManager;
import net.rocky.coresuite.common.integration.IntegrationManager;
import net.rocky.coresuite.common.storage.DatabaseManager;
import net.rocky.coresuite.module.ModuleManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * CoreSuite — A professional, modular multi-purpose plugin.
 * <p>
 * Main plugin class handling lifecycle, manager initialization,
 * and module orchestration.
 *
 * @author Rocky_Rutwik
 */
public final class CoreSuitePlugin extends JavaPlugin {

    private static CoreSuitePlugin instance;

    private ModuleManager moduleManager;
    private DatabaseManager databaseManager;
    private EconomyManager economyManager;
    private GuiManager guiManager;
    private IntegrationManager integrationManager;

    // ANSI escape codes for colored console output
    private static final String RESET  = "\u001B[0m";
    private static final String CYAN   = "\u001B[36;1m";
    private static final String GOLD   = "\u001B[33;1m";
    private static final String GRAY   = "\u001B[37m";
    private static final String DGRAY  = "\u001B[90m";
    private static final String GREEN  = "\u001B[32m";
    private static final String LINE   = DGRAY + " ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET;

    @Override
    public void onEnable() {
        long startTime = System.currentTimeMillis();
        instance = this;
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();

        // ── Banner start ──
        printBanner();

        // ── Initialize core managers ──
        getLogger().info("Initializing storage...");
        databaseManager = new DatabaseManager(this);
        databaseManager.initialize();

        getLogger().info("Initializing economy...");
        economyManager = new EconomyManager(this);
        economyManager.setup();

        getLogger().info("Initializing GUI framework...");
        guiManager = new GuiManager();
        getServer().getPluginManager().registerEvents(guiManager, this);

        getLogger().info("Scanning integrations...");
        integrationManager = new IntegrationManager(this);

        // ── Initialize modules ──
        getLogger().info("Loading modules...");
        moduleManager = new ModuleManager(this);
        registerModules();
        moduleManager.enableModules();

        // ── Check migrations after modules have created their tables (CS-014) ──
        if (databaseManager != null && "MYSQL".equalsIgnoreCase(getConfig().getString("storage.type", "SQLITE"))) {
            databaseManager.checkAndMigrate();
        }

        // ── Register admin command ──
        CoreSuiteCommand coreCommand = new CoreSuiteCommand(this);
        PluginCommand cmd = getCommand("coresuite");
        if (cmd != null) {
            cmd.setExecutor(coreCommand);
            cmd.setTabCompleter(coreCommand);
        }

        // ── Startup complete ──
        long loadTime = System.currentTimeMillis() - startTime;
        printStartupComplete(loadTime);
    }

    @Override
    public void onDisable() {
        if (moduleManager != null) {
            moduleManager.disableAllModules();
        }

        if (databaseManager != null) {
            databaseManager.close();
        }

        // Safety net: cancel remaining tasks and unregister listeners (CS-010)
        getServer().getScheduler().cancelTasks(this);
        org.bukkit.event.HandlerList.unregisterAll(this);

        getServer().getConsoleSender().sendMessage(
            DGRAY + " [" + CYAN + "CoreSuite" + DGRAY + "] " + GRAY + "Shutdown complete. Goodbye!" + RESET
        );
        instance = null;
    }

    /**
     * Prints the CoreSuite ASCII art banner to the console.
     */
    private void printBanner() {
        String[] banner = {
            "",
            LINE,
            "",
            CYAN + "  ██████╗ ██████╗ ██████╗ ███████╗███████╗██╗   ██╗██╗████████╗███████╗" + RESET,
            CYAN + " ██╔════╝██╔═══██╗██╔══██╗██╔════╝██╔════╝██║   ██║██║╚══██╔══╝██╔════╝" + RESET,
            CYAN + " ██║     ██║   ██║██████╔╝█████╗  ███████╗██║   ██║██║   ██║   █████╗  " + RESET,
            CYAN + " ██║     ██║   ██║██╔══██╗██╔══╝  ╚════██║██║   ██║██║   ██║   ██╔══╝  " + RESET,
            CYAN + " ╚██████╗╚██████╔╝██║  ██║███████╗███████║╚██████╔╝██║   ██║   ███████╗" + RESET,
            CYAN + "  ╚═════╝ ╚═════╝ ╚═╝  ╚═╝╚══════╝╚══════╝ ╚═════╝ ╚═╝   ╚═╝   ╚══════╝" + RESET,
            "",
            GOLD + "                        by ROCKY_RUTWIK" + RESET,
            GRAY + "                v" + getDescription().getVersion() + " | Paper 1.20.6+" + RESET,
            "",
            LINE,
            ""
        };

        for (String line : banner) {
            getServer().getConsoleSender().sendMessage(line);
        }
    }

    /**
     * Prints startup completion stats after all modules are loaded.
     */
    private void printStartupComplete(long loadTimeMs) {
        int enabled = moduleManager != null ? moduleManager.getEnabledCount() : 0;
        int total = moduleManager != null ? moduleManager.getAllModules().size() : 0;

        getServer().getConsoleSender().sendMessage(
            GREEN + " ✓ " + GRAY + "CoreSuite loaded " +
            GREEN + enabled + GRAY + "/" + total + " modules in " +
            GREEN + loadTimeMs + "ms" + RESET
        );
        getServer().getConsoleSender().sendMessage(LINE);
        getServer().getConsoleSender().sendMessage("");
    }

    /**
     * Registers all 14 module instances with the ModuleManager.
     * Modules are commented out until their classes are created in later phases.
     */
    private void registerModules() {
        // Phase 1 — Low-risk standalone
        moduleManager.registerModule(new net.rocky.coresuite.module.automessage.AutoMessengerModule(this));
        moduleManager.registerModule(new net.rocky.coresuite.module.mentions.MentionModule(this));
        moduleManager.registerModule(new net.rocky.coresuite.module.clearlag.ClearLagModule(this));
        moduleManager.registerModule(new net.rocky.coresuite.module.worldmanager.WorldManagerModule(this));
        moduleManager.registerModule(new net.rocky.coresuite.module.jumppad.JumpPadModule(this));

        // Phase 2 — Economy-integrated
        moduleManager.registerModule(new net.rocky.coresuite.module.banknotes.BankNotesModule(this));
        moduleManager.registerModule(new net.rocky.coresuite.module.rewardcodes.RewardCodesModule(this));
        moduleManager.registerModule(new net.rocky.coresuite.module.referral.ReferralModule(this));

        // Phase 3 — Playtime
        moduleManager.registerModule(new net.rocky.coresuite.module.playtimerewards.PlaytimeRewardsModule(this));

        // Phase 4 — Gameplay
        moduleManager.registerModule(new net.rocky.coresuite.module.combattag.CombatTagModule(this));
        moduleManager.registerModule(new net.rocky.coresuite.module.customfishing.CustomFishingModule(this));
        moduleManager.registerModule(new net.rocky.coresuite.module.profile.ProfileModule(this));

        // Phase 5 — Content-heavy
        moduleManager.registerModule(new net.rocky.coresuite.module.chatgames.ChatGamesModule(this));

        // Phase 6 — External integrations
        moduleManager.registerModule(new net.rocky.coresuite.module.killpodium.KillPodiumModule(this));
        moduleManager.registerModule(new net.rocky.coresuite.module.vulcanpunish.VulcanPunishModule(this));

        // Phase 7 — Admin Tools
        moduleManager.registerModule(new net.rocky.coresuite.module.admin.AdminModule(this));

        // Phase 8 — Store
        moduleManager.registerModule(new net.rocky.coresuite.module.store.StoreModule(this));

        // Phase 9 — Announce & Gambling
        moduleManager.registerModule(new net.rocky.coresuite.module.announce.AnnounceModule(this));
        moduleManager.registerModule(new net.rocky.coresuite.module.gambling.GamblingModule(this));

        // Phase 10 — Website Integration & System
        moduleManager.registerModule(new net.rocky.coresuite.module.website.WebsiteModule(this));
        
        // Phase 11 — Creator
        moduleManager.registerModule(new net.rocky.coresuite.module.creator.CreatorModule(this));

        // Phase 12 — Custom Economy & Gameplay Modules
        moduleManager.registerModule(new net.rocky.coresuite.module.ordersboard.OrdersBoardModule(this));
        moduleManager.registerModule(new net.rocky.coresuite.module.wishingwell.WishingWellModule(this));
        moduleManager.registerModule(new net.rocky.coresuite.module.archaeologyrush.ArchaeologyRushModule(this));
        moduleManager.registerModule(new net.rocky.coresuite.module.harvestmarket.HarvestMarketModule(this));

        // Phase 13 — Discord Bridge
        moduleManager.registerModule(new net.rocky.coresuite.module.discord.DiscordModule(this));

        // Phase 14 — World Bosses (Shangri-La Frontier custom multi-phase bosses)
        moduleManager.registerModule(new net.rocky.coresuite.module.worldbosses.WorldBossesModule(this));
    }

    // ── Static accessor ──

    /**
     * Gets the singleton plugin instance.
     *
     * @return The CoreSuitePlugin instance, or null if the plugin is not enabled.
     */
    public static CoreSuitePlugin getInstance() {
        return instance;
    }

    // ── Manager accessors ──

    public ModuleManager getModuleManager() { return moduleManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public GuiManager getGuiManager() { return guiManager; }
    public IntegrationManager getIntegrationManager() { return integrationManager; }
}
