package net.rocky.coresuite.test.core;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight mock environment providing state tracking and simulation
 * for PaperMC / Bukkit contracts without launching a heavy native server.
 */
public final class MockBukkitContext {

    public static class MockPlayer {
        private final UUID uniqueId;
        private final String name;
        private boolean op;
        private final Set<String> permissions = new HashSet<>();
        private float attackCooldown = 1.0f;
        private final List<String> soundsPlayed = new ArrayList<>();
        private final List<String> soundsStopped = new ArrayList<>();
        private final List<String> actionBarsReceived = new ArrayList<>();
        private final List<String> messagesReceived = new ArrayList<>();
        private final Map<Integer, MockItemStack> inventory = new HashMap<>();
        private double x, y, z;

        public MockPlayer(UUID uniqueId, String name) {
            this.uniqueId = uniqueId;
            this.name = name;
        }

        public UUID getUniqueId() { return uniqueId; }
        public String getName() { return name; }
        public boolean isOp() { return op; }
        public void setOp(boolean op) { this.op = op; }
        public void addPermission(String perm) { permissions.add(perm); }
        public boolean hasPermission(String perm) { return op || permissions.contains(perm); }

        public float getAttackCooldown() { return attackCooldown; }
        public void setAttackCooldown(float cd) { this.attackCooldown = cd; }

        public void playSound(String soundKey, String category, float volume, float pitch) {
            soundsPlayed.add(soundKey + "@" + category + "[" + volume + "," + pitch + "]");
        }

        public void stopSound(String soundKey, String category) {
            soundsStopped.add(soundKey + "@" + category);
        }

        public void sendActionBar(String message) {
            actionBarsReceived.add(message);
        }

        public void sendMessage(String message) {
            messagesReceived.add(message);
        }

        public List<String> getSoundsPlayed() { return soundsPlayed; }
        public List<String> getSoundsStopped() { return soundsStopped; }
        public List<String> getActionBarsReceived() { return actionBarsReceived; }
        public List<String> getMessagesReceived() { return messagesReceived; }

        public void setLocation(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public double distanceSquared(double tx, double ty, double tz) {
            double dx = x - tx;
            double dy = y - ty;
            double dz = z - tz;
            return dx * dx + dy * dy + dz * dz;
        }

        public double distance(double tx, double ty, double tz) {
            return Math.sqrt(distanceSquared(tx, ty, tz));
        }

        public Map<Integer, MockItemStack> getInventory() { return inventory; }
        public void setItem(int slot, MockItemStack item) { inventory.put(slot, item); }
        public MockItemStack getItem(int slot) { return inventory.get(slot); }
    }

    public static class MockItemStack {
        private final String type;
        private int amount;
        private String displayName;
        private final List<String> lore = new ArrayList<>();
        private final Map<String, String> persistentData = new HashMap<>();

        public MockItemStack(String type, int amount) {
            this.type = type;
            this.amount = amount;
        }

        public String getType() { return type; }
        public int getAmount() { return amount; }
        public void setAmount(int amount) { this.amount = amount; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String name) { this.displayName = name; }
        public List<String> getLore() { return lore; }
        public void setLore(List<String> lore) {
            this.lore.clear();
            if (lore != null) this.lore.addAll(lore);
        }
        public void setPdc(String key, String value) { persistentData.put(key, value); }
        public String getPdc(String key) { return persistentData.get(key); }
    }

    public static class MockLivingEntity {
        private final UUID uuid = UUID.randomUUID();
        private final String type;
        private String customName;
        private double health = 100.0;
        private double maxHealth = 100.0;
        private boolean baby = false;
        private boolean dead = false;

        public MockLivingEntity(String type) {
            this.type = type;
        }

        public UUID getUuid() { return uuid; }
        public String getType() { return type; }
        public String getCustomName() { return customName; }
        public void setCustomName(String customName) { this.customName = customName; }
        public double getHealth() { return health; }
        public void setHealth(double health) { this.health = Math.max(0, Math.min(health, maxHealth)); }
        public double getMaxHealth() { return maxHealth; }
        public void setMaxHealth(double maxHealth) { this.maxHealth = maxHealth; }
        public boolean isBaby() { return baby; }
        public void setBaby(boolean baby) { this.baby = baby; }
        public boolean isDead() { return dead || health <= 0; }
        public void remove() { this.dead = true; }
    }

    public static class MockBossBar {
        private String title;
        private float progress;
        private String color;
        private final Set<String> flags = new HashSet<>();
        private final Set<UUID> players = new HashSet<>();

        public MockBossBar(String title, String color) {
            this.title = title;
            this.color = color;
            this.progress = 1.0f;
        }

        public void addFlag(String flag) { flags.add(flag); }
        public boolean hasFlag(String flag) { return flags.contains(flag); }
        public Set<String> getFlags() { return flags; }
        public void addPlayer(UUID uuid) { players.add(uuid); }
        public void removePlayer(UUID uuid) { players.remove(uuid); }
        public boolean hasPlayer(UUID uuid) { return players.contains(uuid); }
        public Set<UUID> getPlayers() { return players; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public float getProgress() { return progress; }
        public void setProgress(float progress) { this.progress = progress; }
        public String getColor() { return color; }
        public void setColor(String color) { this.color = color; }
    }

    public static class MockInventory {
        private final String title;
        private final int size;
        private final Map<Integer, MockItemStack> contents = new HashMap<>();

        public MockInventory(String title, int size) {
            this.title = title;
            this.size = size;
        }

        public String getTitle() { return title; }
        public int getSize() { return size; }
        public void setItem(int slot, MockItemStack item) { contents.put(slot, item); }
        public MockItemStack getItem(int slot) { return contents.get(slot); }
        public Map<Integer, MockItemStack> getContents() { return contents; }
    }

    public static class MockShapedRecipe {
        private final String key;
        private final String[] shape;
        private final Map<Character, String> ingredientMap = new HashMap<>();
        private MockItemStack result;

        public MockShapedRecipe(String key, String... shape) {
            this.key = key;
            this.shape = shape;
        }

        public void setIngredient(char key, String material) {
            ingredientMap.put(key, material);
        }

        public void setResult(MockItemStack result) {
            this.result = result;
        }

        public String getKey() { return key; }
        public String[] getShape() { return shape; }
        public Map<Character, String> getIngredientMap() { return ingredientMap; }
        public MockItemStack getResult() { return result; }
    }
}
