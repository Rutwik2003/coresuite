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

import java.security.SecureRandom;
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
    private String currentGameType = "TRIVIA";

    private int timeUntilNext;
    private int schedulerTaskId = -1;
    private int gameTimerTaskId = -1;

    private final List<String> recentTrivia = new ArrayList<>();
    private final List<String> recentScramble = new ArrayList<>();
    private final List<String> recentReverse = new ArrayList<>();
    private final List<String> recentFillBlank = new ArrayList<>();
    private final List<String> recentTypeRace = new ArrayList<>();
    private final SecureRandom random = new SecureRandom();

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

        if (typeRoll <= 24) {
            currentGameType = "Trivia";
            qna = pickNonRepeatingTrivia(difficulty);
        } else if (typeRoll <= 40) {
            currentGameType = "Word Scramble";
            qna = generateScrambleQuestion(difficulty);
        } else if (typeRoll <= 55) {
            currentGameType = "Crafting Math";
            qna = generateCraftingMathQuestion(difficulty);
        } else if (typeRoll <= 70) {
            currentGameType = "Arithmetic Math";
            qna = generateMathQuestion(difficulty);
        } else if (typeRoll <= 82) {
            currentGameType = "Speed Typing";
            qna = generateTypeRaceQuestion(difficulty);
        } else if (typeRoll <= 91) {
            currentGameType = "Reverse Word";
            qna = generateReverseWordQuestion(difficulty);
        } else {
            currentGameType = "Fill in the Blank";
            qna = generateFillBlankQuestion(difficulty);
        }

        if (qna == null) {
            currentGameType = "Crafting Math";
            qna = generateCraftingMathQuestion(difficulty);
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
        Bukkit.broadcast(TextUtil.colorize("      " + color + label + " &7difficulty &e[" + currentGameType + "]&7!"));
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
        int noRepeat = config.getInt("no-repeat-size", 35);
        while (tries < 15 && recentTrivia.contains(entry)) {
            entry = pool.get(random.nextInt(pool.size()));
            tries++;
        }

        recentTrivia.add(entry);
        if (recentTrivia.size() > noRepeat) {
            recentTrivia.remove(0);
        }

        String[] parts = entry.split("\\|");
        if (parts.length < 2) return null;
        return parts;
    }

    private String[] generateScrambleQuestion(String diff) {
        List<String> pool = config.getStringList("scramble." + diff);
        if (pool == null || pool.isEmpty()) return generateCraftingMathQuestion(diff);

        String word = pool.get(random.nextInt(pool.size()));
        int tries = 0;
        int noRepeat = config.getInt("no-repeat-size", 35);
        while (tries < 15 && recentScramble.contains(word)) {
            word = pool.get(random.nextInt(pool.size()));
            tries++;
        }

        recentScramble.add(word);
        if (recentScramble.size() > noRepeat) {
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

    private String[] generateCraftingMathQuestion(String diff) {
        int mode = random.nextInt(10);
        switch (mode) {
            case 0 -> {
                int logs = random.nextInt(16) + 3;
                int ans = logs * 4;
                return new String[]{"How many wood planks can you craft from " + logs + " wood logs?", String.valueOf(ans)};
            }
            case 1 -> {
                int pairs = random.nextInt(10) + 2;
                int planks = pairs * 2;
                int ans = pairs * 4;
                return new String[]{"How many sticks can you craft from " + planks + " wood planks?", String.valueOf(ans)};
            }
            case 2 -> {
                int ingots = random.nextInt(12) + 2;
                int ans = ingots * 9;
                String metal = random.nextBoolean() ? "iron" : "gold";
                return new String[]{"How many " + metal + " nuggets can you craft from " + ingots + " " + metal + " ingots?", String.valueOf(ans)};
            }
            case 3 -> {
                int blocks = random.nextInt(12) + 3;
                int ans = blocks * 9;
                String ore = random.nextBoolean() ? "diamond" : (random.nextBoolean() ? "emerald" : "iron");
                return new String[]{"How many " + ore + "s are needed to craft " + blocks + " " + ore + " blocks?", String.valueOf(ans)};
            }
            case 4 -> {
                int anvils = random.nextInt(4) + 1;
                int ans = anvils * 31;
                return new String[]{"How many total iron ingots are needed to craft " + anvils + " anvil" + (anvils > 1 ? "s" : "") + "? (1 anvil = 3 blocks + 4 ingots)", String.valueOf(ans)};
            }
            case 5 -> {
                int sets = random.nextInt(3) + 1;
                int ans = sets * 24;
                String mat = random.nextBoolean() ? "diamond" : "iron";
                return new String[]{"How many " + mat + "s are needed to craft " + sets + " full set" + (sets > 1 ? "s" : "") + " of " + mat + " armor?", String.valueOf(ans)};
            }
            case 6 -> {
                int hoppers = random.nextInt(6) + 2;
                int ans = hoppers * 5;
                return new String[]{"How many iron ingots are required to craft " + hoppers + " hoppers? (5 per hopper)", String.valueOf(ans)};
            }
            case 7 -> {
                int portals = random.nextInt(5) + 2;
                int ans = portals * 10;
                return new String[]{"How many obsidian blocks are needed to build " + portals + " minimal nether portals without corners?", String.valueOf(ans)};
            }
            case 8 -> {
                int apples = random.nextInt(8) + 2;
                int ans = apples * 8;
                return new String[]{"How many gold ingots are needed to craft " + apples + " golden apples? (8 per apple)", String.valueOf(ans)};
            }
            default -> {
                int coal = random.nextInt(12) + 3;
                int ans = coal * 4;
                return new String[]{"How many torches can you craft with " + coal + " coal and enough sticks?", String.valueOf(ans)};
            }
        }
    }

    private String[] generateTypeRaceQuestion(String diff) {
        if (random.nextBoolean()) {
            int length = switch (diff) {
                case "easy" -> 5;
                case "normal" -> 6;
                case "intermediate" -> 7;
                case "hard" -> 8;
                case "difficult" -> 9;
                default -> 10;
            };
            String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
            StringBuilder code = new StringBuilder();
            for (int i = 0; i < length; i++) {
                code.append(chars.charAt(random.nextInt(chars.length())));
            }
            String finalCode = code.toString();
            return new String[]{"Fast Fingers! Type this code: &e&l" + finalCode, finalCode};
        } else {
            List<String> pool = config.getStringList("type_race_phrases");
            if (pool == null || pool.isEmpty()) {
                return generateCraftingMathQuestion(diff);
            }
            String entry = pool.get(random.nextInt(pool.size()));
            int tries = 0;
            int noRepeat = config.getInt("no-repeat-size", 35);
            while (tries < 10 && recentTypeRace.contains(entry)) {
                entry = pool.get(random.nextInt(pool.size()));
                tries++;
            }
            recentTypeRace.add(entry);
            if (recentTypeRace.size() > noRepeat) recentTypeRace.remove(0);

            String[] parts = entry.split("\\|");
            if (parts.length < 2) return new String[]{"Type this phrase: &f" + entry, entry};
            return new String[]{"Type this phrase: &f\"" + parts[0] + "\"", parts[1]};
        }
    }

    private String[] generateReverseWordQuestion(String diff) {
        List<String> pool = config.getStringList("scramble." + diff);
        if (pool == null || pool.isEmpty()) pool = config.getStringList("scramble.normal");
        if (pool == null || pool.isEmpty()) return generateCraftingMathQuestion(diff);

        String word = pool.get(random.nextInt(pool.size()));
        int tries = 0;
        int noRepeat = config.getInt("no-repeat-size", 35);
        while (tries < 10 && recentReverse.contains(word)) {
            word = pool.get(random.nextInt(pool.size()));
            tries++;
        }
        recentReverse.add(word);
        if (recentReverse.size() > noRepeat) recentReverse.remove(0);

        String reversed = new StringBuilder(word).reverse().toString();
        return new String[]{"Type this word BACKWARDS: &e&l" + word.toUpperCase(), reversed};
    }

    private String[] generateFillBlankQuestion(String diff) {
        List<String> pool = config.getStringList("fill_blank");
        if (pool == null || pool.isEmpty()) return generateCraftingMathQuestion(diff);

        String entry = pool.get(random.nextInt(pool.size()));
        int tries = 0;
        int noRepeat = config.getInt("no-repeat-size", 35);
        while (tries < 10 && recentFillBlank.contains(entry)) {
            entry = pool.get(random.nextInt(pool.size()));
            tries++;
        }
        recentFillBlank.add(entry);
        if (recentFillBlank.size() > noRepeat) recentFillBlank.remove(0);

        String[] parts = entry.split("\\|");
        if (parts.length < 2) return generateCraftingMathQuestion(diff);
        return new String[]{"Complete the blank: &e" + parts[0], parts[1]};
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
