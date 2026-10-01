package net.rocky.coresuite.module.worldbosses.rewards;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class BossLootItemParserTest {

    @Test void invalidRangesAndChancesDoNotAbortOtherRewards() {
        var config = new YamlConfiguration();
        config.set("tier.items", List.of("DIAMOND:5-", "DIAMOND:-1", "DIAMOND:999999999999",
                "DIAMOND:2:NaN", "DIAMOND:2:invalid", "DIAMOND:2:101%", "GOLD_INGOT:3"));
        var items = BossLootManager.parseConfiguredItems(config.getConfigurationSection("tier"), TestItem::new);
        assertEquals(List.of(new TestItem(Material.GOLD_INGOT, 3)), items);
        config.set("tier.items", List.of("DIAMOND:1-2147483647", "OAK_STAIRS:2"));
        items = BossLootManager.parseConfiguredItems(config.getConfigurationSection("tier"), TestItem::new);
        assertTrue(items.stream().filter(i -> i.material() == Material.DIAMOND).mapToInt(TestItem::amount).sum() <= 2304);
        assertTrue(items.contains(new TestItem(Material.OAK_STAIRS, 2)));
    }

    record TestItem(Material material, int amount) {}

    @Test
    public void testParseConfiguredItems() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("tier.items", List.of(
                "DIAMOND:10",
                "GOLD_INGOT:5-10",
                "NETHERITE_INGOT:2",
                "TOTEM_OF_UNDYING:1",
                "EXPERIENCE_BOTTLE:20",
                "INVALID_MATERIAL_XYZ:5",
                "EMERALD:5:100%",
                "NETHERITE_SCRAP:1:0%" // 0% chance should never drop
        ));

        List<TestItem> items = BossLootManager.parseConfiguredItems(config.getConfigurationSection("tier"), TestItem::new);
        assertNotNull(items);
        assertFalse(items.isEmpty());

        int diamondCount = items.stream().filter(i -> i.material() == Material.DIAMOND).mapToInt(TestItem::amount).sum();
        assertEquals(10, diamondCount);

        int netheriteCount = items.stream().filter(i -> i.material() == Material.NETHERITE_INGOT).mapToInt(TestItem::amount).sum();
        assertEquals(2, netheriteCount);

        int totemCount = items.stream().filter(i -> i.material() == Material.TOTEM_OF_UNDYING).mapToInt(TestItem::amount).sum();
        assertEquals(1, totemCount);

        int expCount = items.stream().filter(i -> i.material() == Material.EXPERIENCE_BOTTLE).mapToInt(TestItem::amount).sum();
        assertEquals(20, expCount);

        int emeraldCount = items.stream().filter(i -> i.material() == Material.EMERALD).mapToInt(TestItem::amount).sum();
        assertEquals(5, emeraldCount);

        int scrapCount = items.stream().filter(i -> i.material() == Material.NETHERITE_SCRAP).mapToInt(TestItem::amount).sum();
        assertEquals(0, scrapCount, "0% chance item must not drop");

        int goldCount = items.stream().filter(i -> i.material() == Material.GOLD_INGOT).mapToInt(TestItem::amount).sum();
        assertTrue(goldCount >= 5 && goldCount <= 10, "Gold count must be within 5 to 10 range: " + goldCount);
    }

    @Test
    public void testStackSplitting() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("tier.items", List.of("DIAMOND:100", "TOTEM_OF_UNDYING:2"));

        List<TestItem> items = BossLootManager.parseConfiguredItems(config.getConfigurationSection("tier"), TestItem::new);
        int diamondCount = items.stream().filter(i -> i.material() == Material.DIAMOND).mapToInt(TestItem::amount).sum();
        assertEquals(100, diamondCount);

        // Diamonds max stack is 64, so 100 should be split into 64 + 36
        long diamondStacks = items.stream().filter(i -> i.material() == Material.DIAMOND).count();
        assertEquals(2, diamondStacks);

        // Totem max stack is 1, so 2 should be split into 1 + 1
        long totemStacks = items.stream().filter(i -> i.material() == Material.TOTEM_OF_UNDYING).count();
        assertEquals(2, totemStacks);
    }
}
