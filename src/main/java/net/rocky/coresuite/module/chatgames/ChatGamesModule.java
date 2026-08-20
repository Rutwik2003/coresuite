package net.rocky.coresuite.module.chatgames;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;
import org.bukkit.command.TabCompleter;

public class ChatGamesModule extends Module implements Listener, CommandExecutor, TabCompleter {

    private boolean active = false;
    private String currentQuestion;
    private List<String> currentAnswers;
    private String currentDifficulty;

    private int timeUntilNext;
    private int schedulerTaskId = -1;
    private int gameTimerTaskId = -1;

    private final List<String> recentTrivia = new ArrayList<>();
    private final List<String> recentScramble = new ArrayList<>();
    private final Random random = new Random();

    public ChatGamesModule(CoreSuitePlugin plugin) {
        super(plugin, "ChatGames", "chat-games");
    }

    @Override
    public void onEnable() {
        registerListener(this);
        initDatabase();

        PluginCommand command = plugin.getCommand("chatgame");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
        
        PluginCommand topCommand = plugin.getCommand("chatgametop");
        if (topCommand != null) {
            topCommand.setExecutor(this);
            topCommand.setTabCompleter(this);
        }

        scheduleNext();
    }

    @Override
    public void onDisable() {
        if (schedulerTaskId != -1) {
            Bukkit.getScheduler().cancelTask(schedulerTaskId);
            schedulerTaskId = -1;
        }
        if (gameTimerTaskId != -1) {
            Bukkit.getScheduler().cancelTask(gameTimerTaskId);
            gameTimerTaskId = -1;
        }
        active = false;
        
        PluginCommand command = plugin.getCommand("chatgame");
        if (command != null && command.getExecutor() == this) {
            command.setExecutor(null);
            command.setTabCompleter(null);
        }
        
        PluginCommand topCommand = plugin.getCommand("chatgametop");
        if (topCommand != null && topCommand.getExecutor() == this) {
            topCommand.setExecutor(null);
            topCommand.setTabCompleter(null);
        }
    }

    private void initDatabase() {
        plugin.getDatabaseManager().execute(
            "CREATE TABLE IF NOT EXISTS coresuite_chatgames (" +
            "uuid TEXT PRIMARY KEY, " +
            "player_name TEXT, " +
            "wins INTEGER DEFAULT 0)"
        );
    }

    private void scheduleNext() {
        if (schedulerTaskId != -1) {
            Bukkit.getScheduler().cancelTask(schedulerTaskId);
        }
        
        int minDelay = config.getInt("min-delay", 300);
        int maxDelay = config.getInt("max-delay", 600);
        timeUntilNext = minDelay + random.nextInt(maxDelay - minDelay + 1);

        schedulerTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (!config.getBoolean("enabled", true) || active) return;
            
            timeUntilNext--;
            if (timeUntilNext <= 0) {
                if (Bukkit.getOnlinePlayers().size() >= config.getInt("min-players", 2)) {
                    startChatGame("random");
                } else {
                    timeUntilNext = minDelay; // Reset and wait
                }
            }
        }, 20L, 20L); // 1 second intervals
    }

    private void startChatGame(String difficulty) {
        if (!config.getBoolean("enabled", true)) return;

        if (difficulty.equalsIgnoreCase("random")) {
            int roll = random.nextInt(100) + 1;
            if (roll <= 35) difficulty = "easy";
            else if (roll <= 60) difficulty = "normal";
            else if (roll <= 78) difficulty = "intermediate";
            else if (roll <= 90) difficulty = "hard";
            else if (roll <= 97) difficulty = "difficult";
            else difficulty = "extreme";
        }

        int typeRoll = random.nextInt(100) + 1;
        String[] qna = null;

        if (typeRoll <= 35) {
            qna = pickNonRepeatingTrivia(difficulty);
        } else if (typeRoll <= 65) {
            qna = generateMathQuestion(difficulty);
        } else if (typeRoll <= 85) {
            qna = generateScrambleQuestion(difficulty);
        } else {
            qna = generateServerDataQuestion(difficulty);
        }

        if (qna == null) {
            // Fallback if pool is empty
            qna = generateMathQuestion(difficulty);
        }

        currentQuestion = qna[0];
        currentAnswers = Arrays.asList(qna[1].split(","));
        currentDifficulty = difficulty;
        active = true;

        String prefix = config.getString("prefix", "&8[&6&lChat Games&8] &r");
        String color = difficultyColor(difficulty);
        String label = difficultyLabel(difficulty);

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.5f, 1.1f);
        }

        Bukkit.broadcast(TextUtil.colorize(""));
        Bukkit.broadcast(TextUtil.colorize("&8&m--------------------------------------------------"));
        Bukkit.broadcast(TextUtil.colorize("              &6&l⭐ CHAT GAME ⭐"));
        Bukkit.broadcast(TextUtil.colorize("        " + color + label + " &7difficulty question!"));
        Bukkit.broadcast(TextUtil.colorize(""));
        Bukkit.broadcast(TextUtil.colorize("  &f❓ " + currentQuestion));
        Bukkit.broadcast(TextUtil.colorize(""));
        Bukkit.broadcast(TextUtil.colorize("  &7Type your answer in chat! &e(" + config.getInt("answer-time", 45) + "s to answer)"));
        Bukkit.broadcast(TextUtil.colorize("&8&m--------------------------------------------------"));
        Bukkit.broadcast(TextUtil.colorize(""));

        if (gameTimerTaskId != -1) {
            Bukkit.getScheduler().cancelTask(gameTimerTaskId);
        }

        gameTimerTaskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            if (active) {
                if (config.getBoolean("reveal-answer", true) && currentAnswers != null && !currentAnswers.isEmpty()) {
                    Bukkit.broadcast(TextUtil.colorize(prefix + "&cTime's up! Nobody answered correctly."));
                    Bukkit.broadcast(TextUtil.colorize("&7The answer was: &f" + currentAnswers.get(0)));
                } else {
                    Bukkit.broadcast(TextUtil.colorize(prefix + "&cTime's up! Nobody answered correctly."));
                }
                active = false;
                scheduleNext();
            }
        }, config.getInt("answer-time", 45) * 20L);
    }

    private String[] pickNonRepeatingTrivia(String diff) {
        List<String> pool = config.getStringList("trivia." + diff);
        if (pool == null || pool.isEmpty()) return null;

        String entry = pool.get(random.nextInt(pool.size()));
        int tries = 0;
        while (tries < 12 && recentTrivia.contains(entry)) {
            entry = pool.get(random.nextInt(pool.size()));
            tries++;
        }

        recentTrivia.add(entry);
        if (recentTrivia.size() > config.getInt("no-repeat-size", 15)) {
            recentTrivia.remove(0);
        }

        String[] parts = entry.split("\\|");
        if (parts.length < 2) return null;
        return parts;
    }

    private String[] generateScrambleQuestion(String diff) {
        List<String> pool = config.getStringList("scramble." + diff);
        if (pool == null || pool.isEmpty()) return generateMathQuestion(diff);

        String word = pool.get(random.nextInt(pool.size()));
        int tries = 0;
        while (tries < 12 && recentScramble.contains(word)) {
            word = pool.get(random.nextInt(pool.size()));
            tries++;
        }

        recentScramble.add(word);
        if (recentScramble.size() > config.getInt("no-repeat-size", 15)) {
            recentScramble.remove(0);
        }

        String scrambled = shuffleWord(word);
        for (int i = 0; i < 5; i++) {
            if (!scrambled.equals(word)) break;
            scrambled = shuffleWord(word);
        }

        StringBuilder display = new StringBuilder();
        for (char c : scrambled.toCharArray()) {
            display.append(c).append(" ");
        }

        return new String[]{"Unscramble this word: " + display.toString().trim(), word};
    }

    private String shuffleWord(String word) {
        List<Character> chars = new ArrayList<>();
        for (char c : word.toCharArray()) chars.add(c);
        Collections.shuffle(chars, random);
        StringBuilder sb = new StringBuilder();
        for (char c : chars) sb.append(c);
        return sb.toString();
    }

    private String[] generateServerDataQuestion(String diff) {
        int count = Bukkit.getOnlinePlayers().size();
        if (random.nextBoolean()) {
            return new String[]{"How many players are online on the server right at this moment?", String.valueOf(count)};
        } else {
            return new String[]{count + " players are online right now. If one more player joined, how many would that be?", String.valueOf(count + 1)};
        }
    }

    private String[] generateMathQuestion(String diff) {
        int a, b, c, d, ans;
        String q;
        switch (diff) {
            case "normal":
                b = random.nextInt(11) + 2;
                if (random.nextBoolean()) {
                    a = random.nextInt(11) + 2;
                    ans = a * b;
                    q = a + " * " + b + " = ?";
                } else {
                    ans = random.nextInt(11) + 2;
                    a = ans * b;
                    q = a + " / " + b + " = ?";
                }
                break;
            case "intermediate":
                a = random.nextInt(14) + 2;
                b = random.nextInt(14) + 2;
                c = random.nextInt(14) + 2;
                if (random.nextBoolean()) {
                    ans = (a * b) + c;
                    q = a + " * " + b + " + " + c + " = ?";
                } else {
                    ans = a + (b * c);
                    q = a + " + " + b + " * " + c + " = ?";
                }
                break;
            case "hard":
                a = random.nextInt(21) + 5;
                b = random.nextInt(14) + 2;
                c = random.nextInt(21) + 5;
                d = random.nextInt(14) + 2;
                ans = (a * b) - (c * d);
                if (ans < 0) {
                    ans = (c * d) - (a * b);
                    q = "(" + c + " * " + d + ") - (" + a + " * " + b + ") = ?";
                } else {
                    q = "(" + a + " * " + b + ") - (" + c + " * " + d + ") = ?";
                }
                break;
            case "difficult":
                a = random.nextInt(31) + 10;
                b = random.nextInt(31) + 10;
                c = random.nextInt(19) + 2;
                ans = ((a + b) * c) - a;
                q = "(" + a + " + " + b + ") * " + c + " - " + a + " = ?";
                break;
            case "extreme":
                a = random.nextInt(31) + 15;
                b = random.nextInt(31) + 15;
                if (a < b) {
                    int t = a; a = b; b = t;
                }
                ans = (a * a) - (b * b);
                q = "(" + a + " * " + a + ") - (" + b + " * " + b + ") = ?";
                break;
            default: // easy
                a = random.nextInt(20) + 1;
                b = random.nextInt(20) + 1;
                if (random.nextBoolean()) {
                    ans = a + b;
                    q = a + " + " + b + " = ?";
                } else {
                    if (a < b) {
                        int t = a; a = b; b = t;
                    }
                    ans = a - b;
                    q = a + " - " + b + " = ?";
                }
        }
        return new String[]{q, String.valueOf(ans)};
    }

    private String difficultyColor(String diff) {
        switch (diff) {
            case "easy": return "&a";
            case "normal": return "&2";
            case "intermediate": return "&e";
            case "hard": return "&6";
            case "difficult": return "&c";
            default: return "&4&l";
        }
    }

    private String difficultyLabel(String diff) {
        switch (diff) {
            case "easy": return "Easy";
            case "normal": return "Normal";
            case "intermediate": return "Intermediate";
            case "hard": return "Hard";
            case "difficult": return "Difficult";
            default: return "EXTREME";
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (!active || currentAnswers == null) return;
        
        String msg = event.getMessage().trim();
        boolean correct = false;
        for (String ans : currentAnswers) {
            if (msg.equalsIgnoreCase(ans.trim())) {
                correct = true;
                break;
            }
        }

        if (correct) {
            active = false;
            event.setCancelled(true);
            Player winner = event.getPlayer();
            String shownAnswer = currentAnswers.get(0);

            plugin.getDatabaseManager().executeAsync(() -> {
                plugin.getDatabaseManager().execute("INSERT INTO coresuite_chatgames (uuid, player_name, wins) VALUES (?, ?, 1) ON CONFLICT(uuid) DO UPDATE SET player_name = ?, wins = wins + 1",
                        winner.getUniqueId().toString(), winner.getName(), winner.getName());
            });

            Bukkit.broadcast(TextUtil.colorize(""));
            Bukkit.broadcast(TextUtil.colorize("&8&m--------------------------------------------------"));
            Bukkit.broadcast(TextUtil.colorize("  &a&l✔ CORRECT! &f" + winner.getName() + " &7answered first!"));
            Bukkit.broadcast(TextUtil.colorize("  &7Answer: &f" + shownAnswer));
            Bukkit.getScheduler().runTask(plugin, () -> rewardWinner(winner, currentDifficulty));
            Bukkit.broadcast(TextUtil.colorize("&8&m--------------------------------------------------"));
            Bukkit.broadcast(TextUtil.colorize(""));
            
            scheduleNext();
        }
    }

    private void rewardWinner(Player p, String diff) {
        String path = "rewards." + diff;
        if (!config.contains(path)) return;

        int minEco = config.getInt(path + ".economy-min", 0);
        int maxEco = config.getInt(path + ".economy-max", 0);
        if (maxEco > 0 && maxEco >= minEco) {
            int eco = minEco + random.nextInt(maxEco - minEco + 1);
            if (eco > 0) {
                dispatchEco(p, eco);
            }
        }

        List<String> items = config.getStringList(path + ".items");
        for (String itemStr : items) {
            String[] parts = itemStr.split(":");
            if (parts.length == 2) {
                Material mat = Material.matchMaterial(parts[0]);
                if (mat != null) {
                    try {
                        int amt = Integer.parseInt(parts[1]);
                        p.getInventory().addItem(new ItemStack(mat, amt));
                        Bukkit.broadcast(TextUtil.colorize(" &f+ " + amt + " " + parts[0]));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        List<String> commands = config.getStringList(path + ".commands");
        for (String cmd : commands) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", p.getName()));
        }

        List<String> crateKeys = config.getStringList(path + ".crate-keys");
        for (String ck : crateKeys) {
            String[] parts = ck.split(":");
            if (parts.length == 2) {
                dispatchCrate("crate key give " + p.getName() + " " + parts[0] + " " + parts[1]);
                Bukkit.broadcast(TextUtil.colorize(" &b+ " + parts[1] + " " + parts[0] + " Key"));
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("chatgame")) {
            if (!sender.hasPermission("chatgames.admin")) {
                TextUtil.sendMessage(sender, "&cYou don't have permission to manage Chat Games.");
                return true;
            }

            String prefix = config.getString("prefix", "&8[&6&lChat Games&8] &r");

            if (args.length == 0) {
                TextUtil.sendMessage(sender, prefix + "&7Usage: &f/chatgame <start|force|stop|toggle|reload|top|revealanswer> [option]");
                return true;
            }

            String sub = args[0].toLowerCase();
            if (sub.equals("reload")) {
                this.loadConfig();
                scheduleNext();
                TextUtil.sendMessage(sender, prefix + "&aConfig and question pools reloaded.");
            } else if (sub.equals("toggle")) {
                boolean enabled = config.getBoolean("enabled", true);
                config.set("enabled", !enabled);
                this.saveConfig();
                TextUtil.sendMessage(sender, prefix + (enabled ? "&cChat Games disabled." : "&aChat Games enabled."));
            } else if (sub.equals("revealanswer")) {
                if (args.length > 1) {
                    boolean val = args[1].equalsIgnoreCase("on");
                    config.set("reveal-answer", val);
                    this.saveConfig();
                    TextUtil.sendMessage(sender, prefix + (val ? "&aAnswer reveal on timeout is now &lENABLED&r&a." : "&cAnswer reveal on timeout is now &lDISABLED&r&c."));
                } else {
                    boolean val = !config.getBoolean("reveal-answer", true);
                    config.set("reveal-answer", val);
                    this.saveConfig();
                    TextUtil.sendMessage(sender, prefix + (val ? "&aAnswer reveal on timeout toggled &lON&r&a." : "&cAnswer reveal on timeout toggled &lOFF&r&c."));
                }
            } else if (sub.equals("stop")) {
                if (!active) {
                    TextUtil.sendMessage(sender, prefix + "&cThere is no active game to stop.");
                } else {
                    active = false;
                    if (gameTimerTaskId != -1) {
                        Bukkit.getScheduler().cancelTask(gameTimerTaskId);
                    }
                    Bukkit.broadcast(TextUtil.colorize(prefix + "&cThe current game was cancelled by an admin."));
                    scheduleNext();
                }
            } else if (sub.equals("start") || sub.equals("force")) {
                if (active) {
                    TextUtil.sendMessage(sender, prefix + "&cA game is already active!");
                    return true;
                }
                if (sub.equals("start")) {
                    if (Bukkit.getOnlinePlayers().size() < config.getInt("min-players", 2)) {
                        TextUtil.sendMessage(sender, prefix + "&cNeed at least " + config.getInt("min-players", 2) + " players online to start a game. Use &f/chatgame force <difficulty> &cto override.");
                        return true;
                    }
                }
                String diff = args.length > 1 ? args[1] : "random";
                startChatGame(diff);
            } else if (sub.equals("top")) {
                showTop(sender);
            }
            return true;
        }
        
        if (command.getName().equalsIgnoreCase("chatgametop")) {
            showTop(sender);
            return true;
        }

        return false;
    }
    
    private void showTop(CommandSender sender) {
        plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT player_name, wins FROM coresuite_chatgames ORDER BY wins DESC LIMIT 10");
                 ResultSet rs = stmt.executeQuery()) {
                
                List<String> lines = new ArrayList<>();
                lines.add("&8&m--------------------------------------------------");
                lines.add("              &6&l⭐ Chat Games Top Winners ⭐");
                int rank = 1;
                while (rs.next()) {
                    lines.add("  &e#" + rank + ". &f" + rs.getString("player_name") + " &7- &a" + rs.getInt("wins") + " wins");
                    rank++;
                }
                if (rank == 1) {
                    lines.add("  &7No one has won any chat games yet!");
                }
                lines.add("&8&m--------------------------------------------------");
                
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (String line : lines) {
                        sender.sendMessage(TextUtil.colorize(line));
                    }
                });
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to get top chatgames", e);
            }
        });
    }

    private void dispatchEco(Player p, int amount) {
        if (Bukkit.getPluginManager().isPluginEnabled("Vault") || Bukkit.getPluginManager().isPluginEnabled("Essentials")) {
            plugin.getEconomyManager().deposit(p, amount);
            Bukkit.broadcast(TextUtil.colorize(" &6+ " + amount + " coins &7rewarded to " + p.getName()));
        } else {
            plugin.getLogger().warning("[ChatGames] Skipped economy reward because Vault/Essentials is not loaded.");
        }
    }

    private void dispatchCrate(String cmd) {
        if (Bukkit.getPluginManager().isPluginEnabled("ExcellentCrates")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        } else {
            plugin.getLogger().warning("[ChatGames] Skipped crate command because ExcellentCrates is not loaded: " + cmd);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("chatgame")) {
            if (args.length == 1) {
                org.bukkit.util.StringUtil.copyPartialMatches(args[0], Arrays.asList("start", "force", "stop", "toggle", "reload", "top", "revealanswer"), completions);
                Collections.sort(completions);
            } else if (args.length == 2 && (args[0].equalsIgnoreCase("start") || args[0].equalsIgnoreCase("force"))) {
                org.bukkit.util.StringUtil.copyPartialMatches(args[1], Arrays.asList("easy", "normal", "intermediate", "hard", "difficult", "extreme", "random"), completions);
                Collections.sort(completions);
            } else if (args.length == 2 && args[0].equalsIgnoreCase("revealanswer")) {
                org.bukkit.util.StringUtil.copyPartialMatches(args[1], Arrays.asList("on", "off"), completions);
                Collections.sort(completions);
            }
            return completions;
        }
        return Collections.emptyList();
    }

}
