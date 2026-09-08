/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.citizensnpcs.api.CitizensAPI
 *  net.citizensnpcs.api.npc.NPC
 *  net.citizensnpcs.api.npc.NPC$Metadata
 *  net.citizensnpcs.trait.LookClose
 *  net.citizensnpcs.trait.SkinTrait
 *  org.bukkit.Bukkit
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.NamespacedKey
 *  org.bukkit.Particle
 *  org.bukkit.Sound
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.EntityType
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.block.BlockBreakEvent
 *  org.bukkit.event.block.BlockPhysicsEvent
 *  org.bukkit.event.entity.EntityDamageEvent
 *  org.bukkit.persistence.PersistentDataType
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.scheduler.BukkitTask
 */
package net.rocky.coresuite.module.customfishing;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.LookClose;
import net.citizensnpcs.trait.SkinTrait;
import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.customfishing.CustomFishingModule;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public class LegendaryNPCManager
implements Listener {
    private final CoreSuitePlugin plugin;
    private final CustomFishingModule module;
    private final NamespacedKey legendaryKey;
    private final ConcurrentHashMap<UUID, NPC> activeNPCs = new ConcurrentHashMap();
    private final Map<UUID, Long> lastInteractionTime = new ConcurrentHashMap<UUID, Long>();
    private final Map<UUID, Location> spawnedLilyPads = new ConcurrentHashMap<UUID, Location>();
    private final BukkitTask despawnTask;

    public LegendaryNPCManager(CoreSuitePlugin plugin, CustomFishingModule module) {
        this.plugin = plugin;
        this.module = module;
        this.legendaryKey = new NamespacedKey((Plugin)plugin, "legendaryFisherman");
        Bukkit.getScheduler().runTaskLater((Plugin)plugin, () -> {
            if (CitizensAPI.hasImplementation()) {
                for (NPC npc : CitizensAPI.getNPCRegistry()) {
                    if (!npc.getName().contains("Ancient Angler")) continue;
                    npc.destroy();
                }
            }
        }, 20L);
        this.despawnTask = Bukkit.getScheduler().runTaskTimer((Plugin)plugin, () -> {
            int maxDistance = module.getConfig().getInt("legendary-quest.npc-max-distance-blocks", 50);
            long timeoutMs = (long)module.getConfig().getInt("legendary-quest.npc-idle-timeout-seconds", 300) * 1000L;
            long currentTime = System.currentTimeMillis();
            for (Map.Entry<UUID, NPC> entry : this.activeNPCs.entrySet()) {
                UUID ownerId = entry.getKey();
                NPC npc = entry.getValue();
                Player owner = Bukkit.getPlayer((UUID)ownerId);
                if (owner == null || !owner.isOnline()) {
                    this.removeNPC(ownerId);
                    continue;
                }
                Location npcLoc = npc.getStoredLocation();
                if (npcLoc == null && npc.getEntity() != null) {
                    npcLoc = npc.getEntity().getLocation();
                }
                if (npcLoc != null && (!owner.getWorld().equals((Object)npcLoc.getWorld()) || owner.getLocation().distance(npcLoc) > (double)maxDistance)) {
                    this.despawnWithEffects(ownerId, npcLoc, owner);
                    continue;
                }
                Long lastInteract = this.lastInteractionTime.get(ownerId);
                if (lastInteract == null || currentTime - lastInteract <= timeoutMs) continue;
                this.despawnWithEffects(ownerId, npcLoc != null ? npcLoc : owner.getLocation(), owner);
            }
        }, 20L, 20L);
    }

    public NamespacedKey getLegendaryKey() {
        return this.legendaryKey;
    }

    public void spawnLegendaryNPC(UUID ownerId, Location loc) {
        this.removeNPC(ownerId);
        NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, TextUtil.legacyColorize("&b&lAncient Angler"));
        SkinTrait skinTrait = (SkinTrait)npc.getOrAddTrait(SkinTrait.class);
        String customSkin = this.module.getConfig().getString("legendary-quest.npc-skin", "Fisherman");
        String skinValue = this.module.getConfig().getString("legendary-quest.npc-skin-value", "");
        String skinSignature = this.module.getConfig().getString("legendary-quest.npc-skin-signature", "");
        if (!skinValue.isEmpty() && !skinSignature.isEmpty()) {
            skinTrait.setSkinPersistent("LegendaryAngler", skinSignature, skinValue);
        } else {
            skinTrait.setSkinName(customSkin);
        }
        LookClose lookClose = (LookClose)npc.getOrAddTrait(LookClose.class);
        lookClose.lookClose(true);
        npc.data().set(NPC.Metadata.COLLIDABLE, false);
        npc.data().set(NPC.Metadata.FLYABLE, true);
        npc.setUseMinecraftAI(false);
        npc.spawn(loc);
        if (npc.getEntity() != null) {
            npc.getEntity().setGravity(false);
            npc.getEntity().getPersistentDataContainer().set(this.legendaryKey, PersistentDataType.STRING, ownerId.toString());
        }
        this.activeNPCs.put(ownerId, npc);
        this.lastInteractionTime.put(ownerId, System.currentTimeMillis());
    }

    public void registerLilyPad(UUID ownerId, Location loc) {
        this.spawnedLilyPads.put(ownerId, loc);
    }

    public void removeNPC(UUID ownerId) {
        Location lilyLoc;
        NPC npc = this.activeNPCs.remove(ownerId);
        this.lastInteractionTime.remove(ownerId);
        if (npc != null) {
            npc.destroy();
        }
        if ((lilyLoc = this.spawnedLilyPads.remove(ownerId)) != null && lilyLoc.getBlock().getType() == Material.LILY_PAD) {
            lilyLoc.getBlock().setType(Material.AIR);
        }
    }

    public void updateInteraction(UUID ownerId) {
        if (this.activeNPCs.containsKey(ownerId)) {
            this.lastInteractionTime.put(ownerId, System.currentTimeMillis());
        }
    }

    public boolean hasActiveNPC(UUID ownerId) {
        return this.activeNPCs.containsKey(ownerId);
    }

    private void despawnWithEffects(UUID ownerId, Location loc, Player owner) {
        if (loc != null) {
            loc.getWorld().playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            loc.getWorld().spawnParticle(Particle.PORTAL, loc, 30, 0.5, 1.0, 0.5, 0.1);
        }
        TextUtil.sendMessage((CommandSender)owner, "&e[Ancient Angler]&f The tides call me back... Summon me again when you are ready, mortal.");
        this.removeNPC(ownerId);
    }

    public void shutdown() {
        if (this.despawnTask != null) {
            this.despawnTask.cancel();
        }
        for (UUID ownerId : this.activeNPCs.keySet()) {
            this.removeNPC(ownerId);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(this.legendaryKey, PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() == Material.LILY_PAD) {
            for (Location loc : this.spawnedLilyPads.values()) {
                if (loc.getBlockX() != event.getBlock().getX() || loc.getBlockY() != event.getBlock().getY() || loc.getBlockZ() != event.getBlock().getZ() || !loc.getWorld().equals((Object)event.getBlock().getWorld())) continue;
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onBlockPhysics(BlockPhysicsEvent event) {
        if (event.getBlock().getType() == Material.LILY_PAD) {
            for (Location loc : this.spawnedLilyPads.values()) {
                if (loc.getBlockX() != event.getBlock().getX() || loc.getBlockY() != event.getBlock().getY() || loc.getBlockZ() != event.getBlock().getZ() || !loc.getWorld().equals((Object)event.getBlock().getWorld())) continue;
                event.setCancelled(true);
                return;
            }
        }
    }
}

