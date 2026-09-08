package net.rocky.coresuite.module.archaeologyrush;

import net.rocky.coresuite.CoreSuitePlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class ArtifactSetRegistry {

    private final CoreSuitePlugin plugin;
    private final FileConfiguration config;
    private final Map<String, ArtifactSet> sets = new HashMap<>();
    private final Random random = new Random();

    public ArtifactSetRegistry(CoreSuitePlugin plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void load() {
        sets.clear();
        ConfigurationSection setsSection = config.getConfigurationSection("sets");
        if (setsSection == null) return;

        for (String id : setsSection.getKeys(false)) {
            ConfigurationSection setConfig = setsSection.getConfigurationSection(id);
            if (setConfig == null) continue;

            String name = setConfig.getString("name", id);
            String desc = setConfig.getString("description", "");
            String displayItemStr = setConfig.getString("display-item", "DIRT");
            Material displayItem = Material.matchMaterial(displayItemStr);
            if (displayItem == null) displayItem = Material.DIRT;

            double money = setConfig.getDouble("rewards.money", 0);
            List<String> commands = setConfig.getStringList("rewards.commands");

            ConfigurationSection fragmentsSection = setConfig.getConfigurationSection("fragments");
            List<ArtifactFragment> fragments = new ArrayList<>();
            
            if (fragmentsSection != null) {
                for (String fragIndexStr : fragmentsSection.getKeys(false)) {
                    try {
                        int index = Integer.parseInt(fragIndexStr);
                        String fragName = fragmentsSection.getString(fragIndexStr + ".name", "&fFragment " + index);
                        String matStr = fragmentsSection.getString(fragIndexStr + ".material", "PAPER");
                        Material mat = Material.matchMaterial(matStr);
                        if (mat == null) mat = Material.PAPER;

                        fragments.add(new ArtifactFragment(id, index, fragName, mat));
                    } catch (NumberFormatException ignored) {}
                }
            }
            fragments.sort((f1, f2) -> Integer.compare(f1.fragmentIndex(), f2.fragmentIndex()));

            ArtifactSet set = new ArtifactSet(id, name, desc, displayItem, fragments, money, commands);
            sets.put(id, set);
        }
    }

    public List<ArtifactSet> getAllSets() {
        return new ArrayList<>(sets.values());
    }

    public ArtifactSet getSet(String id) {
        return sets.get(id);
    }

    public ArtifactFragment getRandomFragment(ArtifactSet set, Set<Integer> alreadyOwned) {
        List<ArtifactFragment> available = new ArrayList<>();
        for (ArtifactFragment fragment : set.fragments()) {
            if (!alreadyOwned.contains(fragment.fragmentIndex())) {
                available.add(fragment);
            }
        }
        if (available.isEmpty()) {
            return null;
        }
        return available.get(random.nextInt(available.size()));
    }
}
