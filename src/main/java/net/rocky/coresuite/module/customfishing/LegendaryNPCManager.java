package net.rocky.coresuite.module.customfishing;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.SkinTrait;
import net.citizensnpcs.trait.LookClose;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LegendaryNPCManager implements Listener {
    private final CoreSuitePlugin plugin;
    private final CustomFishingModule module;
    private final NamespacedKey legendaryKey;
    
    // UUID of player to NPC object
    private final ConcurrentHashMap<UUID, NPC> activeNPCs = new ConcurrentHashMap<>();

    public LegendaryNPCManager(CoreSuitePlugin plugin, CustomFishingModule module) {
        this.plugin = plugin;
        this.module = module;
        this.legendaryKey = new NamespacedKey(plugin, "legendaryFisherman");
        
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    
    public NamespacedKey getLegendaryKey() {
        return legendaryKey;
    }
    
    public void spawnLegendaryNPC(UUID ownerId, Location loc) {
        // If they already have one, remove it
        removeNPC(ownerId);
        
        NPC npc = CitizensAPI.getNPCRegistry().createNPC(org.bukkit.entity.EntityType.PLAYER, TextUtil.legacyColorize("&b&lAncient Angler"));
        
        // Add SkinTrait
        SkinTrait skinTrait = npc.getOrAddTrait(SkinTrait.class);
        String customSkin = module.getConfig().getString("legendary-quest.npc-skin", "Fisherman");
        String skinValue = module.getConfig().getString("legendary-quest.npc-skin-value", "");
        String skinSignature = module.getConfig().getString("legendary-quest.npc-skin-signature", "");
        
        if (!skinValue.isEmpty() && !skinSignature.isEmpty()) {
            skinTrait.setSkinPersistent("LegendaryAngler", skinSignature, skinValue);
        } else {
            skinTrait.setSkinName(customSkin);
        }
        
        // Add LookClose trait
        LookClose lookClose = npc.getOrAddTrait(LookClose.class);
        lookClose.lookClose(true);
        
        npc.spawn(loc);
        
        if (npc.getEntity() != null) {
            npc.getEntity().getPersistentDataContainer().set(legendaryKey, PersistentDataType.STRING, ownerId.toString());
        }
        
        activeNPCs.put(ownerId, npc);
    }
    
    public void removeNPC(UUID ownerId) {
        NPC npc = activeNPCs.remove(ownerId);
        if (npc != null) {
            npc.destroy();
        }
    }

    @EventHandler
    public void onEntityDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(legendaryKey, PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }
}
