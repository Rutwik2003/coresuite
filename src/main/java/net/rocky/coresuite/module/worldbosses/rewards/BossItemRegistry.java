package net.rocky.coresuite.module.worldbosses.rewards;

import net.kyori.adventure.text.Component;
import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.worldbosses.boss.BossType;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class BossItemRegistry {

    private final CoreSuitePlugin plugin;
    private final NamespacedKey shardKey;
    private final NamespacedKey weaponKey;
    private final NamespacedKey totemKey;

    public BossItemRegistry(CoreSuitePlugin plugin) {
        this.plugin = plugin;
        this.shardKey = new NamespacedKey(plugin, "boss_shard");
        this.weaponKey = new NamespacedKey(plugin, "boss_weapon");
        this.totemKey = new NamespacedKey(plugin, "summon_totem");
    }

    public ItemStack createShard(String shardType, int amount) {
        Material mat;
        String name;
        List<String> lore = new ArrayList<>();

        switch (shardType.toLowerCase()) {
            case "ashen" -> {
                mat = Material.FIRE_CHARGE;
                name = "&c&lAshen Shard";
                lore.add("&8&m────────────────────");
                lore.add("&7A crystalline fragment of the deep forge,");
                lore.add("&7pulsing with Valerius's quenched embers.");
                lore.add("");
                lore.add("&e▶ Trade with &6Garrick the Hunter &eat spawn.");
                lore.add("&8&m────────────────────");
            }
            case "royal_bark" -> {
                mat = Material.OAK_WOOD;
                name = "&a&lRoyal Bark";
                lore.add("&8&m────────────────────");
                lore.add("&7An unyielding slice of petrified heartwood,");
                lore.add("&7steeped in Aethelgard's eternal vitality.");
                lore.add("");
                lore.add("&e▶ Trade with &6Garrick the Hunter &eat spawn.");
                lore.add("&8&m────────────────────");
            }
            case "frost_core" -> {
                mat = Material.PRISMARINE_CRYSTALS;
                name = "&b&lFrost Core";
                lore.add("&7A frozen heart fragment of Borealis.");
                lore.add("&eTrade at /boss shop for any-shard rewards.");
            }
            case "phantom_silk" -> {
                mat = Material.COBWEB;
                name = "&5&lPhantom Silk";
                lore.add("&8&m────────────────────");
                lore.add("&7Gleaming abyssal gossamer woven in pitch black,");
                lore.add("&7humming with Xol'thos's mirage illusions.");
                lore.add("");
                lore.add("&e▶ Trade with &6Garrick the Hunter &eat spawn.");
                lore.add("&8&m────────────────────");
            }
            default -> {
                mat = Material.PRISMARINE_SHARD;
                name = "&b&lBoss Shard";
                lore.add("&7A mysterious relic from a defeated World Boss.");
            }
        }

        ItemStack item = TextUtil.createItem(mat, name, lore.toArray(new String[0]));
        item.setAmount(Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().set(shardKey, PersistentDataType.STRING, shardType.toLowerCase());
            item.setItemMeta(meta);
        }
        return item;
    }

    public String getShardType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(shardKey, PersistentDataType.STRING);
    }

    public int countShards(Player player, String specificShardOrAny) {
        int total = 0;
        for (ItemStack is : player.getInventory().getContents()) {
            if (is == null) continue;
            String type = getShardType(is);
            if (type != null) {
                if (specificShardOrAny == null || specificShardOrAny.equalsIgnoreCase("any") || type.equalsIgnoreCase(specificShardOrAny)) {
                    total += is.getAmount();
                }
            }
        }
        return total;
    }

    public boolean takeShards(Player player, String specificShardOrAny, int required) {
        if (countShards(player, specificShardOrAny) < required) return false;

        int toRemove = required;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack is = contents[i];
            if (is == null) continue;
            String type = getShardType(is);
            if (type != null) {
                if (specificShardOrAny == null || specificShardOrAny.equalsIgnoreCase("any") || type.equalsIgnoreCase(specificShardOrAny)) {
                    int take = Math.min(toRemove, is.getAmount());
                    is.setAmount(is.getAmount() - take);
                    if (is.getAmount() <= 0) {
                        contents[i] = null;
                    }
                    toRemove -= take;
                    if (toRemove <= 0) break;
                }
            }
        }
        player.getInventory().setContents(contents);
        return true;
    }

    public ItemStack createSignatureWeapon(String weaponId) {
        ItemStack item;
        ItemMeta meta;

        switch (weaponId.toLowerCase()) {
            case "vanguards_cleaver", "vanguard-cleaver" -> {
                item = new ItemStack(Material.NETHERITE_AXE);
                meta = item.getItemMeta();
                if (meta != null) {
                    meta.displayName(TextUtil.colorize("&c&lThe Vanguard's Cleaver"));
                    meta.addEnchant(Enchantment.EFFICIENCY, 5, true);
                    meta.addEnchant(Enchantment.SHARPNESS, 5, true);
                    meta.addEnchant(Enchantment.FORTUNE, 3, true);
                    meta.addEnchant(Enchantment.MENDING, 1, true);
                    meta.addEnchant(Enchantment.UNBREAKING, 4, true);
                    meta.addEnchant(Enchantment.FIRE_ASPECT, 2, true);

                    List<Component> lore = new ArrayList<>();
                    lore.add(TextUtil.colorize("&8&m────────────────────────────"));
                    lore.add(TextUtil.colorize("&6Rarity: &c&lMYTHIC WORLD BOSS WEAPON"));
                    lore.add(TextUtil.colorize("&7Forged in the heart of the Ashen Crucible."));
                    lore.add(TextUtil.colorize(""));
                    lore.add(TextUtil.colorize("&c&lSpecial Trait: &eIgnited Edge"));
                    lore.add(TextUtil.colorize("&7Strikes against burning targets deal"));
                    lore.add(TextUtil.colorize("&a+20% bonus damage &7and erupt"));
                    lore.add(TextUtil.colorize("&7in scalding magma sparks."));
                    lore.add(TextUtil.colorize("&8&m────────────────────────────"));
                    meta.lore(lore);

                    meta.getPersistentDataContainer().set(weaponKey, PersistentDataType.STRING, "vanguards_cleaver");
                    item.setItemMeta(meta);
                }
            }
            case "crown_of_the_forest", "crown-of-forest" -> {
                item = new ItemStack(Material.NETHERITE_HELMET);
                meta = item.getItemMeta();
                if (meta != null) {
                    meta.displayName(TextUtil.colorize("&a&lCrown of the Forest"));
                    meta.addEnchant(Enchantment.PROTECTION, 4, true);
                    meta.addEnchant(Enchantment.RESPIRATION, 3, true);
                    meta.addEnchant(Enchantment.AQUA_AFFINITY, 1, true);
                    meta.addEnchant(Enchantment.THORNS, 3, true);
                    meta.addEnchant(Enchantment.UNBREAKING, 4, true);
                    meta.addEnchant(Enchantment.MENDING, 1, true);

                    List<Component> lore = new ArrayList<>();
                    lore.add(TextUtil.colorize("&8&m────────────────────────────"));
                    lore.add(TextUtil.colorize("&6Rarity: &a&lMYTHIC WORLD BOSS RELIC"));
                    lore.add(TextUtil.colorize("&7Carved from the undying roots of Aethelgard."));
                    lore.add(TextUtil.colorize(""));
                    lore.add(TextUtil.colorize("&a&lSpecial Trait: &2Photosynthesis"));
                    lore.add(TextUtil.colorize("&7Grants passive &aRegeneration I &7while"));
                    lore.add(TextUtil.colorize("&7standing in daylight or on grass/moss."));
                    lore.add(TextUtil.colorize("&8&m────────────────────────────"));
                    meta.lore(lore);

                    meta.getPersistentDataContainer().set(weaponKey, PersistentDataType.STRING, "crown_of_the_forest");
                    item.setItemMeta(meta);
                }
            }
            case "frostbound_striders", "frostbound-striders" -> {
                item = new ItemStack(Material.NETHERITE_BOOTS);
                meta = item.getItemMeta();
                if (meta != null) {
                    meta.displayName(TextUtil.colorize("&b&lFrostbound Striders"));
                    meta.addEnchant(Enchantment.PROTECTION, 4, true);
                    meta.addEnchant(Enchantment.FEATHER_FALLING, 4, true);
                    meta.addEnchant(Enchantment.DEPTH_STRIDER, 3, true);
                    meta.addEnchant(Enchantment.SOUL_SPEED, 3, true);
                    meta.addEnchant(Enchantment.THORNS, 3, true);
                    meta.addEnchant(Enchantment.UNBREAKING, 4, true);
                    meta.addEnchant(Enchantment.MENDING, 1, true);
                    meta.lore(List.of(TextUtil.colorize("&6Rarity: &b&lMYTHIC WORLD BOSS RELIC"),
                        TextUtil.colorize("&7Forged from the frozen heart of Borealis."),
                        TextUtil.colorize("&b&lSpecial Trait: Glacial Guard"),
                        TextUtil.colorize("&7While worn: immune to freezing damage."),
                        TextUtil.colorize("&7Reduces incoming fall damage by 25%"),
                        TextUtil.colorize("&7before normal armor/enchantment reductions.")));
                    meta.getPersistentDataContainer().set(weaponKey, PersistentDataType.STRING, "frostbound_striders");
                    item.setItemMeta(meta);
                }
            }
            case "widows_fang", "widows-fang" -> {
                item = new ItemStack(Material.NETHERITE_SWORD);
                meta = item.getItemMeta();
                if (meta != null) {
                    meta.displayName(TextUtil.colorize("&5&lThe Widow's Fang"));
                    meta.addEnchant(Enchantment.SHARPNESS, 6, true);
                    meta.addEnchant(Enchantment.LOOTING, 3, true);
                    meta.addEnchant(Enchantment.SWEEPING_EDGE, 3, true);
                    meta.addEnchant(Enchantment.FIRE_ASPECT, 2, true);
                    meta.addEnchant(Enchantment.KNOCKBACK, 2, true);
                    meta.addEnchant(Enchantment.MENDING, 1, true);
                    meta.addEnchant(Enchantment.UNBREAKING, 4, true);

                    List<Component> lore = new ArrayList<>();
                    lore.add(TextUtil.colorize("&8&m────────────────────────────"));
                    lore.add(TextUtil.colorize("&6Rarity: &5&lMYTHIC WORLD BOSS WEAPON"));
                    lore.add(TextUtil.colorize("&7A venomous dagger harvested from Xol'thos."));
                    lore.add(TextUtil.colorize(""));
                    lore.add(TextUtil.colorize("&5&lSpecial Trait: &dEnvenom"));
                    lore.add(TextUtil.colorize("&7Attacks apply &5Poison II &7for 4s and"));
                    lore.add(TextUtil.colorize("&7grant the wielder &bSpeed I &7for 3s."));
                    lore.add(TextUtil.colorize("&8&m────────────────────────────"));
                    meta.lore(lore);

                    meta.getPersistentDataContainer().set(weaponKey, PersistentDataType.STRING, "widows_fang");
                    item.setItemMeta(meta);
                }
            }
            default -> {
                item = new ItemStack(Material.NETHERITE_SWORD);
            }
        }
        return item;
    }

    public ItemStack createSummonTotem(BossType type) {
        Material mat = switch (type) {
            case VALERIUS -> Material.ECHO_SHARD;
            case AETHELGARD -> Material.TOTEM_OF_UNDYING;
            case XOLTHOS -> Material.FERMENTED_SPIDER_EYE;
            case BOREALIS -> Material.AMETHYST_SHARD;
        };

        String name = switch (type) {
            case VALERIUS -> "&c&lSummon Totem: Valerius";
            case AETHELGARD -> "&a&lSummon Totem: Aethelgard";
            case XOLTHOS -> "&5&lSummon Totem: Xol'thos";
            case BOREALIS -> "&b&lSummon Totem: Borealis";
        };

        List<String> lore = new ArrayList<>();
        lore.add("&8&m────────────────────────────");
        lore.add("&7An ancient artifact bound to &f" + type.getDefaultDisplayName() + "&7.");
        lore.add("");
        lore.add("&e▶ Right-click on valid ground to awaken the World Boss!");
        lore.add("&7Summoning requirements follow the server's boss settings.");
        lore.add("&8&m────────────────────────────");

        ItemStack item = TextUtil.createItem(mat, name, lore.toArray(new String[0]));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(totemKey, PersistentDataType.STRING, type.name());
            meta.setEnchantmentGlintOverride(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    public BossType getTotemType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String raw = item.getItemMeta().getPersistentDataContainer().get(totemKey, PersistentDataType.STRING);
        if (raw != null) {
            try {
                return BossType.valueOf(raw.toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        // Identity comes from our persistent tag, including older totem materials.
        // An anvil-renamed vanilla item must never become a summon item.
        return null;
    }

    public NamespacedKey getWeaponKey() {
        return weaponKey;
    }

    public String getSignatureWeaponId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(weaponKey, PersistentDataType.STRING);
        if (id != null) return id;

        // Robust fallback: Check display name if PDC is absent
        if (item.getItemMeta().hasDisplayName()) {
            String name = item.getItemMeta().getDisplayName().toLowerCase();
            if (name.contains("cleaver") || name.contains("vanguard")) return "vanguards_cleaver";
            if (name.contains("crown") || name.contains("forest")) return "crown_of_the_forest";
            if (name.contains("widow") || name.contains("fang")) return "widows_fang";
        }
        return null;
    }

    public boolean hasSignatureWeapon(ItemStack item, String targetId) {
        String id = getSignatureWeaponId(item);
        return id != null && id.equalsIgnoreCase(targetId);
    }
}
