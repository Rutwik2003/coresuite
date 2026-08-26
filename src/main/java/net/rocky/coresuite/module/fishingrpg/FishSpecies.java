package net.rocky.coresuite.module.fishingrpg;

import java.util.Collections;
import java.util.List;

public class FishSpecies {
    private final String id;
    private final String name;
    private final FishRarity rarity;
    private final double minWeight;
    private final double maxWeight;
    private final double pricePerKg;
    private final String material;
    private final String itemModel;
    private final List<String> biomes;
    private final List<String> weather;
    private final boolean nightOnly;
    private final boolean dayOnly;
    private final List<String> lore;

    public FishSpecies(String id, String name, FishRarity rarity, double minWeight, double maxWeight, double pricePerKg, String material, String itemModel, List<String> biomes, List<String> weather, boolean nightOnly, boolean dayOnly, List<String> lore) {
        this.id = id;
        this.name = name;
        this.rarity = rarity;
        this.minWeight = minWeight;
        this.maxWeight = maxWeight;
        this.pricePerKg = pricePerKg;
        this.material = material;
        this.itemModel = itemModel;
        this.biomes = biomes != null ? List.copyOf(biomes) : Collections.emptyList();
        this.weather = weather != null ? List.copyOf(weather) : Collections.emptyList();
        this.nightOnly = nightOnly;
        this.dayOnly = dayOnly;
        this.lore = lore != null ? List.copyOf(lore) : Collections.emptyList();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public FishRarity getRarity() { return rarity; }
    public double getMinWeight() { return minWeight; }
    public double getMaxWeight() { return maxWeight; }
    public double getPricePerKg() { return pricePerKg; }
    public String getMaterial() { return material; }
    public String getItemModel() { return itemModel; }
    public List<String> getBiomes() { return biomes; }
    public List<String> getWeather() { return weather; }
    public boolean isNightOnly() { return nightOnly; }
    public boolean isDayOnly() { return dayOnly; }
    public List<String> getLore() { return lore; }

    public boolean canCatchIn(String biomeName, boolean isThundering, boolean isNight) {
        if (nightOnly && !isNight) return false;
        if (dayOnly && isNight) return false;
        if (!biomes.isEmpty() && !biomes.contains(biomeName.toUpperCase())) return false;
        if (!weather.isEmpty()) {
            String currentWeather = isThundering ? "THUNDER" : "CLEAR";
            if (!weather.contains(currentWeather) && !(weather.contains("RAIN") && isThundering)) {
                return false;
            }
        }
        return true;
    }
}
