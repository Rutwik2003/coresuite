package net.rocky.coresuite.module.worldbosses.party;

import net.kyori.adventure.text.Component;
import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages temporary Raid Alliances/Parties during World Boss encounters.
 * Players who join the party cannot damage one another (PvP friendly fire disabled).
 * Joining is optional - players who choose not to join can still attack players (PvP sabotage).
 */
public class BossPartyManager {

    private final CoreSuitePlugin plugin;
    private final Set<UUID> partyMembers = ConcurrentHashMap.newKeySet();

    public BossPartyManager(CoreSuitePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Attempts to add a player to the raid party.
     * @param player the player joining
     * @return true if joined, false if already in party
     */
    public boolean joinParty(Player player) {
        if (player == null || !player.isOnline()) return false;
        UUID uuid = player.getUniqueId();
        if (!partyMembers.add(uuid)) {
            return false;
        }

        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.2f, 1.2f);
        TextUtil.sendMessage(player, "&8[&6&lRaid Party&8] &aYou joined the Raid Party! Friendly fire with other party members is now &e&lDISABLED&a.");

        Component announce = TextUtil.colorize("&8[&6&lRaid Party&8] &e" + player.getName() + " &7joined the alliance! &8(&6" + partyMembers.size() + " hunters&8)");
        for (UUID memberUuid : partyMembers) {
            if (!memberUuid.equals(uuid)) {
                Player member = Bukkit.getPlayer(memberUuid);
                if (member != null && member.isOnline()) {
                    member.sendMessage(announce);
                }
            }
        }
        return true;
    }

    /**
     * Removes a player from the raid party.
     */
    public boolean leaveParty(Player player) {
        if (player == null) return false;
        boolean removed = partyMembers.remove(player.getUniqueId());
        if (removed) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.6f);
            TextUtil.sendMessage(player, "&8[&6&lRaid Party&8] &cYou left the Raid Party. You are no longer protected from friendly fire!");
        }
        return removed;
    }

    /**
     * Checks if both players are members of the raid party.
     * If both are members, PvP friendly fire between them should be blocked.
     */
    public boolean areAllies(UUID p1, UUID p2) {
        if (p1 == null || p2 == null || p1.equals(p2)) return false;
        return partyMembers.contains(p1) && partyMembers.contains(p2);
    }

    public boolean isPartyMember(UUID uuid) {
        return uuid != null && partyMembers.contains(uuid);
    }

    public Set<UUID> getPartyMembers() {
        return Collections.unmodifiableSet(partyMembers);
    }

    public int getMemberCount() {
        return partyMembers.size();
    }

    /**
     * Resets/disbands the party when a boss dies or despawns.
     */
    public void reset() {
        partyMembers.clear();
    }
}
