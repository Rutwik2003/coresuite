package net.rocky.coresuite.module.customfishing;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.SkinTrait;
import net.citizensnpcs.trait.LookClose;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.Sound;
import org.bukkit.Particle;

import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LegendaryNPCManager implements Listener {
    private final CoreSuitePlugin plugin;
    private final CustomFishingModule module;
    private final NamespacedKey legendaryKey;
    
    private final ConcurrentHashMap<UUID, NPC> activeNPCs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastInteractionTime = new ConcurrentHashMap<>();
    private final Map<UUID, Location> spawnedLilyPads = new ConcurrentHashMap<>();
    private final BukkitTask despawnTask;

    public LegendaryNPCManager(CoreSuitePlugin plugin, CustomFishingModule module) {
        this.plugin = plugin;
        this.module = module;
        this.legendaryKey = new NamespacedKey(plugin, "legendaryFisherman");
        
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (net.citizensnpcs.api.CitizensAPI.hasImplementation()) {
                for (net.citizensnpcs.api.npc.NPC npc : net.citizensnpcs.api.CitizensAPI.getNPCRegistry()) {
                    if (npc.getName().contains("Ancient Angler")) {
                        npc.destroy();
                    }
                }
            }
        }, 20L);

        this.despawnTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int maxDistance = module.getConfig().getInt("legendary-quest.npc-max-distance-blocks", 50);
            long timeoutMs = module.getConfig().getInt("legendary-quest.npc-idle-timeout-seconds", 300) * 1000L;
            long currentTime = System.currentTimeMillis();

            for (Map.Entry<UUID, NPC> entry : activeNPCs.entrySet()) {
                UUID ownerId = entry.getKey();
                NPC npc = entry.getValue();

                Player owner = Bukkit.getPlayer(ownerId);
                if (owner == null || !owner.isOnline()) {
                    removeNPC(ownerId);
                    continue;
                }

                Location npcLoc = npc.getStoredLocation();
                if (npcLoc == null) {
                    if (npc.getEntity() != null) {
                        npcLoc = npc.getEntity().getLocation();
                    }
                }

                if (npcLoc != null) {
                    if (!owner.getWorld().equals(npcLoc.getWorld()) || owner.getLocation().distance(npcLoc) > maxDistance) {
                        despawnWithEffects(ownerId, npcLoc, owner);
                        continue;
                    }
                }

                Long lastInteract = lastInteractionTime.get(ownerId);
                if (lastInteract != null && (currentTime - lastInteract) > timeoutMs) {
                    despawnWithEffects(ownerId, npcLoc != null ? npcLoc : owner.getLocation(), owner);
                }
            }
        }, 20L, 20L);
    }
    
    public NamespacedKey getLegendaryKey() {
        return legendaryKey;
    }
    
    public void spawnLegendaryNPC(UUID ownerId, Location loc) {
        removeNPC(ownerId);
        
        NPC npc = CitizensAPI.getNPCRegistry().createNPC(org.bukkit.entity.EntityType.PLAYER, TextUtil.legacyColorize("&b&lAncient Angler"));
        
        SkinTrait skinTrait = npc.getOrAddTrait(SkinTrait.class);
        String customSkin = module.getConfig().getString("legendary-quest.npc-skin", "Fisherman");
        String skinValue = module.getConfig().getString("legendary-quest.npc-skin-value", "");
        String skinSignature = module.getConfig().getString("legendary-quest.npc-skin-signature", "");
        
        if (!skinValue.isEmpty() && !skinSignature.isEmpty()) {
            skinTrait.setSkinPersistent("LegendaryAngler", skinSignature, skinValue);
        } else {
            skinTrait.setSkinName(customSkin);
        }
        
        LookClose lookClose = npc.getOrAddTrait(LookClose.class);
        lookClose.lookClose(true);
        
        npc.data().set(net.citizensnpcs.api.npc.NPC.Metadata.COLLIDABLE, false);
        npc.data().set(net.citizensnpcs.api.npc.NPC.Metadata.FLYABLE, true);
        
        npc.setUseMinecraftAI(false);
        
        npc.spawn(loc);
        
        if (npc.getEntity() != null) {
            npc.getEntity().setGravity(false);
            npc.getEntity().getPersistentDataContainer().set(legendaryKey, PersistentDataType.STRING, ownerId.toString());
        }
        
        activeNPCs.put(ownerId, npc);
        lastInteractionTime.put(ownerId, System.currentTimeMillis());
    }
    
    public void registerLilyPad(UUID ownerId, Location loc) {
        spawnedLilyPads.put(ownerId, loc);
    }
    
    public void removeNPC(UUID ownerId) {
        NPC npc = activeNPCs.remove(ownerId);
        lastInteractionTime.remove(ownerId);
        if (npc != null) {
            npc.destroy();
        }
        Location lilyLoc = spawnedLilyPads.remove(ownerId);
        if (lilyLoc != null && lilyLoc.getBlock().getType() == org.bukkit.Material.LILY_PAD) {
            lilyLoc.getBlock().setType(org.bukkit.Material.AIR);
        }
    }

    public void updateInteraction(UUID ownerId) {
        if (activeNPCs.containsKey(ownerId)) {
            lastInteractionTime.put(ownerId, System.currentTimeMillis());
        }
    }

    public boolean hasActiveNPC(UUID ownerId) {
        return activeNPCs.containsKey(ownerId);
    }

    private void despawnWithEffects(UUID ownerId, Location loc, Player owner) {
        if (loc != null) {
            loc.getWorld().playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            loc.getWorld().spawnParticle(Particle.PORTAL, loc, 30, 0.5, 1.0, 0.5, 0.1);
        }
        TextUtil.sendMessage(owner, "&e[Ancient Angler]&f The tides call me back... Summon me again when you are ready, mortal.");
        removeNPC(ownerId);
    }

    public void shutdown() {
        if (despawnTask != null) {
            despawnTask.cancel();
        }
        for (UUID ownerId : activeNPCs.keySet()) {
            removeNPC(ownerId);
        }
    }

    @EventHandler
    public void onEntityDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(legendaryKey, PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(org.bukkit.event.block.BlockBreakEvent event) {
        if (event.getBlock().getType() == org.bukkit.Material.LILY_PAD) {
            for (Location loc : spawnedLilyPads.values()) {
                if (loc.getBlockX() == event.getBlock().getX() && 
                    loc.getBlockY() == event.getBlock().getY() && 
                    loc.getBlockZ() == event.getBlock().getZ() && 
                    loc.getWorld().equals(event.getBlock().getWorld())) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onBlockPhysics(org.bukkit.event.block.BlockPhysicsEvent event) {
        if (event.getBlock().getType() == org.bukkit.Material.LILY_PAD) {
            for (Location loc : spawnedLilyPads.values()) {
                if (loc.getBlockX() == event.getBlock().getX() && 
                    loc.getBlockY() == event.getBlock().getY() && 
                    loc.getBlockZ() == event.getBlock().getZ() && 
                    loc.getWorld().equals(event.getBlock().getWorld())) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }
}
