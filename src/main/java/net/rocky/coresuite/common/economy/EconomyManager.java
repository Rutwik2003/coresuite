/**
 * CoreSuite Plugin
 * Package: net.rocky.coresuite.common.economy
 * 
 * Wrapper for Vault economy integration.
 */
package net.rocky.coresuite.common.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.rocky.coresuite.CoreSuitePlugin;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Manages economy interactions by wrapping the Vault API.
 */
public final class EconomyManager {
    private final CoreSuitePlugin plugin;
    private Economy economy;

    /**
     * Constructs a new EconomyManager.
     *
     * @param plugin The CoreSuite plugin instance.
     */
    public EconomyManager(CoreSuitePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Sets up the economy hook.
     *
     * @return True if successfully hooked, false otherwise.
     */
    public boolean setup() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("Vault not found! Economy features will be disabled.");
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            plugin.getLogger().warning("No economy provider found! Install an economy plugin (e.g., EssentialsX).");
            return false;
        }
        economy = rsp.getProvider();
        plugin.getLogger().info("Economy hooked: " + economy.getName());
        return true;
    }

    /**
     * Checks if the economy service is available.
     *
     * @return True if available.
     */
    public boolean isAvailable() {
        return economy != null;
    }

    /**
     * Deposits money into a player's account.
     *
     * @param player The player to deposit to.
     * @param amount The amount to deposit.
     * @return True if the transaction was successful.
     */
    public boolean deposit(OfflinePlayer player, double amount) {
        if (!isAvailable() || player == null || !Double.isFinite(amount) || amount <= 0) return false;
        try {
            EconomyResponse response = economy.depositPlayer(player, amount);
            if (response != null && response.transactionSuccess()) return true;
        } catch (RuntimeException | LinkageError e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Economy deposit outcome uncertain for " + player.getUniqueId() + "; not retrying", e);
            throw new IllegalStateException("Economy transaction outcome is uncertain", e);
        }
        plugin.getLogger().warning("Economy deposit rejected for " + player.getUniqueId());
        return false;
    }

    /**
     * Withdraws money from a player's account.
     *
     * @param player The player to withdraw from.
     * @param amount The amount to withdraw.
     * @return True if the transaction was successful.
     */
    public boolean withdraw(OfflinePlayer player, double amount) {
        if (!isAvailable() || player == null || !Double.isFinite(amount) || amount <= 0) return false;
        try {
            EconomyResponse response = economy.withdrawPlayer(player, amount);
            if (response != null && response.transactionSuccess()) return true;
        } catch (RuntimeException | LinkageError e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Economy withdraw outcome uncertain for " + player.getUniqueId() + "; not retrying", e);
            throw new IllegalStateException("Economy transaction outcome is uncertain", e);
        }
        plugin.getLogger().warning("Economy withdraw rejected for " + player.getUniqueId());
        return false;
    }

    /**
     * Retrieves the balance of a player's account.
     *
     * @param player The player whose balance to check.
     * @return The balance amount.
     */
    public double getBalance(OfflinePlayer player) {
        if (!isAvailable() || player == null) return 0.0;
        try {
            OfflinePlayer target = (player.getPlayer() != null) ? player.getPlayer() : player;
            return economy.getBalance(target);
        } catch (Throwable ignored) {}
        if (player.getName() != null) {
            try {
                return economy.getBalance(player.getName());
            } catch (Throwable ignored) {}
        }
        return 0.0;
    }

    /**
     * Checks if a player has at least a specified amount.
     *
     * @param player The player to check.
     * @param amount The amount required.
     * @return True if the player has the amount.
     */
    public boolean has(OfflinePlayer player, double amount) {
        if (!isAvailable() || player == null || !Double.isFinite(amount) || amount < 0) return false;
        try {
            OfflinePlayer target = (player.getPlayer() != null) ? player.getPlayer() : player;
            return economy.has(target, amount);
        } catch (Throwable ignored) {}
        if (player.getName() != null) {
            try {
                return economy.has(player.getName(), amount);
            } catch (Throwable ignored) {}
        }
        return false;
    }

    /**
     * Formats an economy amount into a readable string.
     *
     * @param amount The amount to format.
     * @return The formatted string.
     */
    public String format(double amount) {
        if (!isAvailable()) return String.format("$%.2f", amount);
        return economy.format(amount);
    }
}
