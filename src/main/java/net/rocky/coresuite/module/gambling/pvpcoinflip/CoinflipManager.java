package net.rocky.coresuite.module.gambling.pvpcoinflip;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.economy.EconomyManager;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.gambling.CasinoStatsManager;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Manages PVP Coinflip duels, escrow holding, tax deduction, and database persistence.
 */
public class CoinflipManager {

    private final CoreSuitePlugin plugin;
    private final FileConfiguration config;
    private final EconomyManager economy;
    private final CasinoStatsManager statsManager;

    private final Map<Integer, CoinflipDuel> activeDuels = new ConcurrentHashMap<>();
    private final AtomicInteger idCounter = new AtomicInteger(1);

    public CoinflipManager(CoreSuitePlugin plugin, FileConfiguration config, CasinoStatsManager statsManager) {
        this.plugin = plugin;
        this.config = config;
        this.economy = plugin.getEconomyManager();
        this.statsManager = statsManager;
        initDatabase();
    }

    private void initDatabase() {
        plugin.getDatabaseManager().execute(
            "CREATE TABLE IF NOT EXISTS coresuite_coinflips (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "creator_uuid TEXT NOT NULL, " +
            "creator_name TEXT NOT NULL, " +
            "opponent_uuid TEXT, " +
            "opponent_name TEXT, " +
            "bet_amount REAL NOT NULL, " +
            "creator_heads INTEGER NOT NULL, " +
            "winner_uuid TEXT, " +
            "created_at BIGINT NOT NULL, " +
            "status TEXT NOT NULL" +
            ")"
        );

        // Fetch highest ID
        plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT MAX(id) FROM coresuite_coinflips");
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int maxId = rs.getInt(1);
                    if (maxId > 0) idCounter.set(maxId + 1);
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to initialize coinflip id counter: " + e.getMessage());
            }
        });
    }

    public List<CoinflipDuel> getOpenDuels() {
        return activeDuels.values().stream()
            .filter(d -> d.getStatus() == CoinflipDuel.Status.OPEN)
            .sorted(Comparator.comparing(CoinflipDuel::getCreatedAt).reversed())
            .toList();
    }

    public Optional<CoinflipDuel> getPlayerOpenDuel(UUID uuid) {
        return activeDuels.values().stream()
            .filter(d -> d.getStatus() == CoinflipDuel.Status.OPEN && d.getCreatorUuid().equals(uuid))
            .findFirst();
    }

    public void createDuel(Player creator, double betAmount, boolean isHeads, Consumer<CoinflipDuel> onSuccess, Consumer<String> onFail) {
        double minBet = config.getDouble("min-bet", 1000);
        double maxBet = config.getDouble("max-bet", 5000000);

        if (betAmount < minBet || betAmount > maxBet) {
            onFail.accept("&cBet must be between &e" + economy.format(minBet) + " &cand &e" + economy.format(maxBet) + "&c.");
            return;
        }

        if (!economy.has(creator, betAmount)) {
            onFail.accept("&cYou do not have &e" + economy.format(betAmount) + " &cto place this bet.");
            return;
        }

        if (getPlayerOpenDuel(creator.getUniqueId()).isPresent()) {
            onFail.accept("&cYou already have an open duel! Cancel it before creating another.");
            return;
        }

        // Withdraw bet into escrow
        if (!economy.withdraw(creator, betAmount)) {
            onFail.accept("&cTransaction failed. Please try again.");
            return;
        }

        int duelId = idCounter.getAndIncrement();
        CoinflipDuel duel = new CoinflipDuel(
            duelId,
            creator.getUniqueId(),
            creator.getName(),
            betAmount,
            isHeads,
            System.currentTimeMillis()
        );

        activeDuels.put(duelId, duel);

        // Persist
        plugin.getDatabaseManager().execute(
            "INSERT INTO coresuite_coinflips (id, creator_uuid, creator_name, bet_amount, creator_heads, created_at, status) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)",
            duelId, creator.getUniqueId().toString(), creator.getName(), betAmount, isHeads ? 1 : 0, duel.getCreatedAt(), "OPEN"
        );

        // Server announcement
        String prefix = config.getString("messages.prefix", "&8[&6&lCasino&8] &r");
        Bukkit.broadcastMessage(TextUtil.legacyColorize(
            prefix + "&e" + creator.getName() + " &7posted a &a" + economy.format(betAmount) +
            " &7Coinflip! &8(&6" + duel.getCreatorSideName() + "&8) &eType &6/cf &eto accept!"
        ));

        onSuccess.accept(duel);
    }

    public void cancelDuel(Player player, int duelId, Runnable onSuccess, Consumer<String> onFail) {
        CoinflipDuel duel = activeDuels.get(duelId);
        if (duel == null || duel.getStatus() != CoinflipDuel.Status.OPEN) {
            onFail.accept("&cDuel not found or already accepted.");
            return;
        }

        if (!duel.getCreatorUuid().equals(player.getUniqueId()) && !player.hasPermission("coresuite.admin")) {
            onFail.accept("&cYou can only cancel your own coinflips.");
            return;
        }

        duel.setStatus(CoinflipDuel.Status.CANCELLED);
        activeDuels.remove(duelId);

        // Refund escrow
        economy.deposit(player, duel.getBetAmount());

        plugin.getDatabaseManager().execute(
            "UPDATE coresuite_coinflips SET status = 'CANCELLED' WHERE id = ?", duelId
        );

        onSuccess.run();
    }

    public void acceptDuel(Player opponent, int duelId, Consumer<CoinflipDuel> onStartRoll, Consumer<String> onFail) {
        CoinflipDuel duel = activeDuels.get(duelId);
        if (duel == null || duel.getStatus() != CoinflipDuel.Status.OPEN) {
            onFail.accept("&cThis duel is no longer available.");
            return;
        }

        if (duel.getCreatorUuid().equals(opponent.getUniqueId())) {
            onFail.accept("&cYou cannot accept your own coinflip!");
            return;
        }

        if (!economy.has(opponent, duel.getBetAmount())) {
            onFail.accept("&cYou need &e" + economy.format(duel.getBetAmount()) + " &cto accept this duel.");
            return;
        }

        Player creator = Bukkit.getPlayer(duel.getCreatorUuid());
        if (creator == null || !creator.isOnline()) {
            onFail.accept("&cThe duel creator is no longer online.");
            return;
        }

        // Withdraw opponent's bet into escrow
        if (!economy.withdraw(opponent, duel.getBetAmount())) {
            onFail.accept("&cTransaction failed.");
            return;
        }

        duel.setStatus(CoinflipDuel.Status.ACTIVE);
        duel.setOpponentUuid(opponent.getUniqueId());
        duel.setOpponentName(opponent.getName());

        plugin.getDatabaseManager().execute(
            "UPDATE coresuite_coinflips SET opponent_uuid = ?, opponent_name = ?, status = 'ACTIVE' WHERE id = ?",
            opponent.getUniqueId().toString(), opponent.getName(), duelId
        );

        onStartRoll.accept(duel);
    }

    public void completeDuel(CoinflipDuel duel, boolean coinLandedHeads) {
        duel.setStatus(CoinflipDuel.Status.COMPLETED);
        activeDuels.remove(duel.getId());

        boolean creatorWon = (coinLandedHeads == duel.isCreatorChoseHeads());
        UUID winnerUuid = creatorWon ? duel.getCreatorUuid() : duel.getOpponentUuid();
        String winnerName = creatorWon ? duel.getCreatorName() : duel.getOpponentName();
        UUID loserUuid = creatorWon ? duel.getOpponentUuid() : duel.getCreatorUuid();
        String loserName = creatorWon ? duel.getOpponentName() : duel.getCreatorName();

        duel.setWinnerUuid(winnerUuid);

        // Calculate tax (money sink)
        double taxPercent = config.getDouble("pvp-coinflip.tax-percent", 5.0);
        double totalPot = duel.getTotalPot();
        double tax = totalPot * (taxPercent / 100.0);
        double payout = totalPot - tax;

        // Payout to winner
        Player winnerPlayer = Bukkit.getPlayer(winnerUuid);
        if (winnerPlayer != null && winnerPlayer.isOnline()) {
            economy.deposit(winnerPlayer, payout);
            winnerPlayer.playSound(winnerPlayer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            spawnCelebrationFirework(winnerPlayer);
        } else {
            economy.deposit(Bukkit.getOfflinePlayer(winnerUuid), payout);
        }

        // Record stats in database
        statsManager.recordGame(winnerUuid, winnerName, duel.getBetAmount(), payout);
        statsManager.recordGame(loserUuid, loserName, duel.getBetAmount(), 0);

        plugin.getDatabaseManager().execute(
            "UPDATE coresuite_coinflips SET winner_uuid = ?, status = 'COMPLETED' WHERE id = ?",
            winnerUuid.toString(), duel.getId()
        );

        // Server announcement
        String prefix = config.getString("messages.prefix", "&8[&6&lCasino&8] &r");
        Bukkit.broadcastMessage(TextUtil.legacyColorize(
            prefix + "&e" + winnerName + " &awon a &e" + economy.format(payout) +
            " &aCoinflip duel against &c" + loserName + "&a! &8(" + taxPercent + "% house tax burned)"
        ));
    }

    public void checkExpiredDuels() {
        int expiryMinutes = config.getInt("pvp-coinflip.duel-expiry-minutes", 10);
        long expiryMillis = expiryMinutes * 60L * 1000L;
        long now = System.currentTimeMillis();

        for (CoinflipDuel duel : new ArrayList<>(activeDuels.values())) {
            if (duel.getStatus() == CoinflipDuel.Status.OPEN && (now - duel.getCreatedAt()) > expiryMillis) {
                duel.setStatus(CoinflipDuel.Status.EXPIRED);
                activeDuels.remove(duel.getId());

                // Refund creator
                economy.deposit(Bukkit.getOfflinePlayer(duel.getCreatorUuid()), duel.getBetAmount());

                plugin.getDatabaseManager().execute(
                    "UPDATE coresuite_coinflips SET status = 'EXPIRED' WHERE id = ?", duel.getId()
                );

                Player creator = Bukkit.getPlayer(duel.getCreatorUuid());
                if (creator != null && creator.isOnline()) {
                    String prefix = config.getString("messages.prefix", "&8[&6&lCasino&8] &r");
                    TextUtil.sendMessage(creator, prefix + "&cYour Coinflip duel of &e" + economy.format(duel.getBetAmount()) + " &cexpired unfilled. Escrow refunded.");
                }
            }
        }
    }

    private void spawnCelebrationFirework(Player player) {
        if (!config.getBoolean("enable-fireworks", true)) return;
        try {
            Firework fw = player.getWorld().spawn(player.getLocation(), Firework.class);
            FireworkMeta meta = fw.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder()
                .withColor(Color.YELLOW, Color.ORANGE, Color.LIME)
                .withFade(Color.WHITE)
                .with(FireworkEffect.Type.BALL_LARGE)
                .withFlicker()
                .build());
            meta.setPower(1);
            fw.setFireworkMeta(meta);
        } catch (Exception ignored) {}
    }
}
