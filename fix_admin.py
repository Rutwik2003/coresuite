import re

with open("c:/Users/rutwi/OneDrive - MSFT/Desktop/coresuit/src/main/java/net/rocky/coresuite/module/customfishing/CustomFishingModule.java", "r") as f:
    original = f.read()

# First, find the bad admin block and remove it
# It looks like:
#                 if (args[0].equalsIgnoreCase("admin")) {
#                     if (!player.hasPermission("coresuite.admin")) {
# ...
#                     return true;
#                 } else if (args[0].equalsIgnoreCase("top")) {

bad_block_pattern = r"(\s*)if \(args\[0\]\.equalsIgnoreCase\(\"admin\"\)\) \{\s*if \(\!player\.hasPermission\(\"coresuite\.admin\"\)\) \{.*?return true;\s*\}\s*return true;\s*\} else if \(args\[0\]\.equalsIgnoreCase\(\"top\"\)\) \{"

def bad_block_replacer(match):
    return match.group(1) + 'if (args[0].equalsIgnoreCase("top")) {'

new_file = re.sub(bad_block_pattern, bad_block_replacer, original, flags=re.DOTALL)

# Next, find the real admin block and inject the legendary subcommands
# It looks like:
#                     String sub = args[1].toLowerCase();
#                     
#                     if (sub.equals("forcepond")) {

legendary_code = """
                    if (sub.equals("legendary")) {
                        if (args.length > 2 && args[2].equalsIgnoreCase("givemap")) {
                            Player target = args.length > 3 ? Bukkit.getPlayer(args[3]) : player;
                            if (target != null && legendaryQuestManager != null) {
                                legendaryQuestManager.giveMap(target);
                                TextUtil.sendMessage(player, "&aGave Legendary Fisherman's Compass to " + target.getName());
                            } else {
                                TextUtil.sendMessage(player, "&cPlayer not found.");
                            }
                        } else if (args.length > 3 && args[2].equalsIgnoreCase("setstage")) {
                            Player target = Bukkit.getPlayer(args[3]);
                            if (target != null && legendaryQuestManager != null) {
                                try {
                                    int stage = Integer.parseInt(args[4]);
                                    legendaryQuestManager.setStage(target.getUniqueId(), stage);
                                    TextUtil.sendMessage(player, "&aSet " + target.getName() + "'s legendary quest stage to " + stage);
                                } catch (Exception e) {
                                    TextUtil.sendMessage(player, "&cUsage: /fish admin legendary setstage <player> <stage>");
                                }
                            } else {
                                TextUtil.sendMessage(player, "&cPlayer not found.");
                            }
                        } else if (args.length > 2 && args[2].equalsIgnoreCase("reset")) {
                            Player target = args.length > 3 ? Bukkit.getPlayer(args[3]) : player;
                            if (target != null && legendaryQuestManager != null) {
                                legendaryQuestManager.setStage(target.getUniqueId(), 0);
                                legendaryNPCManager.removeNPC(target.getUniqueId());
                                TextUtil.sendMessage(player, "&aReset " + target.getName() + "'s legendary quest progress.");
                            } else {
                                TextUtil.sendMessage(player, "&cPlayer not found.");
                            }
                        } else {
                            TextUtil.sendMessage(player, "&cUsage: /fish admin legendary <givemap|setstage|reset>");
                        }
                        return true;
                    }
"""

real_admin_pattern = r'(String sub = args\[1\]\.toLowerCase\(\);\s*)(if \(sub\.equals\("forcepond"\)\) \{)'

def real_admin_replacer(match):
    return match.group(1) + legendary_code.strip('\n') + '\n                    ' + match.group(2)

new_file = re.sub(real_admin_pattern, real_admin_replacer, new_file, flags=re.DOTALL)

with open("c:/Users/rutwi/OneDrive - MSFT/Desktop/coresuit/src/main/java/net/rocky/coresuite/module/customfishing/CustomFishingModule.java", "w") as f:
    f.write(new_file)
