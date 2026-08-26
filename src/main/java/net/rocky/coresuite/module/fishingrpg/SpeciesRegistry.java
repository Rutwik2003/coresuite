package net.rocky.coresuite.module.fishingrpg;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class SpeciesRegistry {

    private final Map<String, FishSpecies> speciesMap = new ConcurrentHashMap<>();

    public SpeciesRegistry(FileConfiguration config) {
        loadFromConfig(config);
    }

    public void loadFromConfig(FileConfiguration config) {
        speciesMap.clear();
        ConfigurationSection section = config.getConfigurationSection("species");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            ConfigurationSection speciesSection = section.getConfigurationSection(key);
            if (speciesSection != null) {
                String name = speciesSection.getString("name", key);
                String rarityStr = speciesSection.getString("rarity", "COMMON").toUpperCase();
                FishRarity rarity;
                try {
                    rarity = FishRarity.valueOf(rarityStr);
                } catch (IllegalArgumentException e) {
                    rarity = FishRarity.COMMON;
                }
                
                double minWeight = speciesSection.getDouble("min-weight", 1.0);
                double maxWeight = speciesSection.getDouble("max-weight", 5.0);
                double pricePerKg = speciesSection.getDouble("price-per-kg", 1.0);
                String material = speciesSection.getString("material", "COD");
                String itemModel = speciesSection.getString("item-model", null);
                List<String> biomes = speciesSection.getStringList("biomes");
                List<String> weather = speciesSection.getStringList("weather");
                boolean nightOnly = speciesSection.getBoolean("night-only", false);
                boolean dayOnly = speciesSection.getBoolean("day-only", false);
                List<String> lore = speciesSection.getStringList("lore");

                FishSpecies species = new FishSpecies(
                        key, name, rarity, minWeight, maxWeight, pricePerKg,
                        material, itemModel, biomes, weather, nightOnly, dayOnly, lore
                );
                speciesMap.put(key, species);
            }
        }
    }

    public FishSpecies getSpecies(String id) {
        return speciesMap.get(id);
    }

    public Collection<FishSpecies> getAllSpecies() {
        return speciesMap.values();
    }

    public List<FishSpecies> getSpeciesForBiome(String biomeName) {
        return speciesMap.values().stream()
                .filter(s -> s.getBiomes() == null || s.getBiomes().isEmpty() || s.getBiomes().contains(biomeName.toUpperCase()))
                .collect(Collectors.toList());
    }

    public List<FishSpecies> getSpeciesByRarity(FishRarity rarity) {
        return speciesMap.values().stream()
                .filter(s -> s.getRarity() == rarity)
                .collect(Collectors.toList());
    }

    public int getSpeciesCount() {
        return speciesMap.size();
    }

    public void reload(FileConfiguration config) {
        loadFromConfig(config);
    }
}
