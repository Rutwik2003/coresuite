package net.rocky.coresuite.module.archaeologyrush;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Random;

public class FragmentDropHandler implements Listener {

    private final CoreSuitePlugin plugin;
    private final ArtifactSetRegistry registry;
    private final PlayerCollectionManager collectionManager;
    private final FileConfiguration config;
    private final NamespacedKey setKey;
    private final NamespacedKey fragmentKey;
    private final Random random = new Random();

    public FragmentDropHandler(CoreSuitePlugin plugin, ArtifactSetRegistry registry, PlayerCollectionManager collectionManager, FileConfiguration config, NamespacedKey setKey, NamespacedKey fragmentKey) {
        this.plugin = plugin;
        this.registry = registry;
        this.collectionManager = collectionManager;
        this.config = config;
        this.setKey = setKey;
        this.fragmentKey = fragmentKey;
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onFragmentRightClick(PlayerInteractEvent e) {
        // Prevent fragment items from being used (e.g. maps activating)
        ItemStack held = e.getItem();
        if (held == null || !held.hasItemMeta()) return;
        if (held.getItemMeta().getPersistentDataContainer().has(setKey, PersistentDataType.STRING)) {
            // Allow reading written books!
            if (held.getType() == Material.WRITTEN_BOOK) return;
            // Allow if they're brushing with BRUSH tool (checked via action + clicked block)
            if (e.getAction() == Action.RIGHT_CLICK_BLOCK
                    && held.getType() == Material.BRUSH
                    && e.getClickedBlock() != null
                    && e.getClickedBlock().getType().name().contains("SUSPICIOUS")) return;
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onExcavationComplete(org.bukkit.event.entity.EntityChangeBlockEvent event) {
        if (event.isCancelled() || !(event.getEntity() instanceof Player player)) return;
        Material source = event.getBlock().getType();
        if (!isCompletedExcavation(source, event.getTo())) return;
        if (player.getGameMode() != org.bukkit.GameMode.SURVIVAL
                && player.getGameMode() != org.bukkit.GameMode.ADVENTURE) return;
        if (!net.rocky.coresuite.common.integration.AuthenticationGate.isAuthenticated(player)) return;
        // Intermediate dust stages keep the suspicious material. Only vanilla's final
        // conversion consumes the excavation and permits one reward roll, even with no loot.
        awardFragment(player);
    }

    static boolean isCompletedExcavation(Material before, Material after) {
        return before == Material.SUSPICIOUS_SAND && after == Material.SAND
                || before == Material.SUSPICIOUS_GRAVEL && after == Material.GRAVEL;
    }

    private void awardFragment(Player player) {
        double chance = config.getDouble("fragment-drop-chance", 0.20);
        if (!Double.isFinite(chance) || random.nextDouble() >= Math.max(0, Math.min(1, chance))) {
            return;
        }

        List<ArtifactSet> allSets = registry.getAllSets();
        if (allSets.isEmpty()) return;

        boolean dupProtect = config.getBoolean("duplicate-protection", true);
        
        ArtifactSet chosenSet = allSets.get(random.nextInt(allSets.size()));
        ArtifactFragment chosenFragment = null;

        if (dupProtect) {
            // Try to find a fragment they don't have
            chosenFragment = registry.getRandomFragment(chosenSet, collectionManager.getOwnedFragments(player.getUniqueId(), chosenSet.id()));
            
            // If they have all from this set, try another set
            if (chosenFragment == null) {
                for (ArtifactSet set : allSets) {
                    chosenFragment = registry.getRandomFragment(set, collectionManager.getOwnedFragments(player.getUniqueId(), set.id()));
                    if (chosenFragment != null) {
                        chosenSet = set;
                        break;
                    }
                }
            }
        } else {
            chosenFragment = chosenSet.fragments().get(random.nextInt(chosenSet.fragments().size()));
        }

        if (chosenFragment == null) return; // Completely maxed out, or no fragments exist

        // Duplicate inventory protection
        if (dupProtect && hasFragmentInInventory(player, chosenSet.id(), chosenFragment.fragmentIndex())) {
            return;
        }

        ItemStack drop = chosenFragment.toItem(chosenSet.displayName(), chosenSet.getPieceCount(), setKey, fragmentKey);

        if (player.getInventory().firstEmpty() != -1) {
            player.getInventory().addItem(drop);
        } else {
            player.getWorld().dropItem(player.getLocation(), drop);
        }

        String msg = config.getString("messages.fragment-found", "&6✨ You unearthed &l{fragmentName}&6! &8[{index}/{total} for {setName}]")
                .replace("{fragmentName}", chosenFragment.displayName())
                .replace("{index}", String.valueOf(chosenFragment.fragmentIndex()))
                .replace("{total}", String.valueOf(chosenSet.getPieceCount()))
                .replace("{setName}", chosenSet.displayName());
        
        String prefix = config.getString("messages.prefix", "&8[&6🏺 Curator&8] &r");
        TextUtil.sendMessage(player, prefix + msg);
        
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.0f, 1.0f);
        
        collectionManager.addFragment(player.getUniqueId(), player.getName(), chosenSet.id(), chosenFragment.fragmentIndex());
    }

    private boolean hasFragmentInInventory(Player player, String setId, int fragmentIndex) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isArtifactFragment(item)) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    String itemSetId = meta.getPersistentDataContainer().get(setKey, PersistentDataType.STRING);
                    Integer itemFragIdx = meta.getPersistentDataContainer().get(fragmentKey, PersistentDataType.INTEGER);
                    if (setId.equals(itemSetId) && itemFragIdx != null && itemFragIdx == fragmentIndex) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean isArtifactFragment(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(setKey, PersistentDataType.STRING);
    }
}
