import re

with open("c:/Users/rutwi/OneDrive - MSFT/Desktop/coresuit/src/main/java/net/rocky/coresuite/module/customfishing/LegendaryNPCManager.java", "r") as f:
    content = f.read()

# Add the registry sweep in the constructor
# Find constructor end
constructor_pattern = r"(this\.legendaryKey = new NamespacedKey\(plugin, \"legendaryFisherman\"\);.*?\n\s+plugin\.getServer\(\)\.getPluginManager\(\)\.registerEvents\(this, plugin\);\n)(\s*\})"

sweep_code = """
        // Registry sweep for ghost NPCs on startup
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (net.citizensnpcs.api.CitizensAPI.hasImplementation()) {
                for (net.citizensnpcs.api.npc.NPC npc : net.citizensnpcs.api.CitizensAPI.getNPCRegistry()) {
                    if (npc.getName().contains("Ancient Angler")) {
                        npc.destroy();
                    }
                }
            }
        }, 20L); // wait 1 second to ensure Citizens is loaded
"""

def constructor_replacer(match):
    return match.group(1) + sweep_code + match.group(2)

content = re.sub(constructor_pattern, constructor_replacer, content, flags=re.DOTALL)

# Update spawnLegendaryNPC to optimize and disable saving
spawn_pattern = r"(npc\.spawn\(loc\);)"

optimize_code = """
        // OPTIMIZATIONS: Disable AI, gravity, collisions, and saving to make it cost 0 CPU
        npc.data().set(net.citizensnpcs.api.npc.NPC.Metadata.SAVE_TO_DISK, false);
        npc.data().set(net.citizensnpcs.api.npc.NPC.Metadata.COLLIDABLE, false);
        npc.data().set(net.citizensnpcs.api.npc.NPC.Metadata.FLYABLE, true);
        npc.setUseMinecraftAI(false);
        
        npc.spawn(loc);
"""

content = re.sub(spawn_pattern, optimize_code.strip(), content)

with open("c:/Users/rutwi/OneDrive - MSFT/Desktop/coresuit/src/main/java/net/rocky/coresuite/module/customfishing/LegendaryNPCManager.java", "w") as f:
    f.write(content)
