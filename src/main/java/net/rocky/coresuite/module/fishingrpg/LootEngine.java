package net.rocky.coresuite.module.fishingrpg;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class LootEngine {

    private final SpeciesRegistry registry;
    private final FileConfiguration config;

    public LootEngine(SpeciesRegistry registry, FileConfiguration config) {
        this.registry = registry;
        this.config = config;
    }

    public enum CatchType {
        FISH, JUNK, VANILLA_TREASURE, CORRUPTED_FISH
    }

    public record CatchResult(
            FishSpecies species,
            double weight,
            double price,
            boolean isPerfectCatch,
            boolean isVanillaTreasure,
            ItemStack treasureItem,
            CatchType type
    ) {}

    public CatchResult rollCatch(Player player, AnglerProfile profile, String biomeName, boolean isThundering, boolean isNight, boolean inHotspot, boolean hasBaitActive, double baitCatchBonus, String baitRarityBias) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // 1. Roll Junk
        double junkChance = Math.max(0.0, 0.15 - (profile.getFishingPower() * 0.001));
        if (random.nextDouble() < junkChance) {
            return new CatchResult(null, 0, 0, false, false, null, CatchType.JUNK);
        }

        // 2. Roll Vanilla Treasure
        double treasureChance = 0.05 + (profile.getFishingLuck() * 0.001);
        if (random.nextDouble() < treasureChance) {
            Material[] treasures = {
                    Material.ENCHANTED_BOOK, Material.NAME_TAG, Material.DIAMOND,
                    Material.NAUTILUS_SHELL, Material.HEART_OF_THE_SEA, Material.SADDLE
            };
            Material treasureMat = treasures[random.nextInt(treasures.length)];
            return new CatchResult(null, 0, 0, false, true, new ItemStack(treasureMat), CatchType.VANILLA_TREASURE);
        }

        // 3. Roll Rarity Tier
        double roll = random.nextDouble();
        
        double commonChance = 0.50;
        double uncommonChance = 0.30;
        double rareChance = 0.15;
        double epicChance = 0.04;
        double legendaryChance = 0.01;
        
        double powerBonus = profile.getFishingPower() * 0.002;
        commonChance = Math.max(0, commonChance - powerBonus);
        legendaryChance += powerBonus * 0.1;
        epicChance += powerBonus * 0.3;
        rareChance += powerBonus * 0.6;
        
        if (baitRarityBias != null) {
            try {
                FishRarity biasRarity = FishRarity.valueOf(baitRarityBias.toUpperCase());
                switch (biasRarity) {
                    case COMMON -> commonChance += 0.10;
                    case UNCOMMON -> uncommonChance += 0.10;
                    case RARE -> rareChance += 0.10;
                    case EPIC -> epicChance += 0.10;
                    case LEGENDARY -> legendaryChance += 0.10;
                }
            } catch (Exception ignored) {}
        }
        
        double total = commonChance + uncommonChance + rareChance + epicChance + legendaryChance;
        commonChance /= total;
        uncommonChance /= total;
        rareChance /= total;
        epicChance /= total;
        legendaryChance /= total;

        FishRarity rolledRarity = FishRarity.COMMON;
        if (roll > 1.0 - legendaryChance) {
            rolledRarity = FishRarity.LEGENDARY;
        } else if (roll > 1.0 - legendaryChance - epicChance) {
            rolledRarity = FishRarity.EPIC;
        } else if (roll > 1.0 - legendaryChance - epicChance - rareChance) {
            rolledRarity = FishRarity.RARE;
        } else if (roll > 1.0 - legendaryChance - epicChance - rareChance - uncommonChance) {
            rolledRarity = FishRarity.UNCOMMON;
        }
        
        FishRarity finalRarity = rolledRarity;

        // 4. Filter Species
        List<FishSpecies> validSpecies = registry.getSpeciesByRarity(rolledRarity).stream()
                .filter(s -> s.getBiomes() == null || s.getBiomes().isEmpty() || s.getBiomes().contains(biomeName.toUpperCase()))
                .filter(s -> {
                    if (s.isNightOnly() && !isNight) return false;
                    if (s.isDayOnly() && isNight) return false;
                    return true;
                })
                .filter(s -> s.getWeather() == null || s.getWeather().isEmpty() || 
                        (isThundering && s.getWeather().contains("THUNDER")) || 
                        (!isThundering && s.getWeather().contains("CLEAR")))
                .collect(Collectors.toList());

        if (validSpecies.isEmpty()) {
            validSpecies = registry.getSpeciesByRarity(rolledRarity);
        }

        FishSpecies caught = null;
        if (!validSpecies.isEmpty()) {
            caught = validSpecies.get(random.nextInt(validSpecies.size()));
        }

        if (caught == null) {
            return new CatchResult(null, 0, 0, false, false, null, CatchType.JUNK); // fallback
        }

        // 5. Weight calculation
        double weight = caught.getMinWeight() + (caught.getMaxWeight() - caught.getMinWeight()) * random.nextDouble();
        weight *= profile.getWeightBonus();
        if (inHotspot) {
            weight *= 1.5;
        }

        // 6. Price calculation
        double price = weight * caught.getPricePerKg();
        
        // 7. Corrupted fish check
        double corruptedChance = isThundering ? 0.03 : 0.01;
        CatchType type = CatchType.FISH;
        if (random.nextDouble() < corruptedChance) {
            type = CatchType.CORRUPTED_FISH;
        }

        return new CatchResult(caught, weight, price, false, false, null, type);
    }

    public static boolean rollLegendaryCompass(AnglerProfile profile, double baseChance, int pityThreshold) {
        if (profile.getLegendaryPity() >= pityThreshold) {
            return true;
        }
        double currentChance = baseChance + (1.0 - baseChance) * ((double) profile.getLegendaryPity() / pityThreshold);
        return ThreadLocalRandom.current().nextDouble() < currentChance;
    }
}
