import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Pattern;

public class FixXP {
    public static void main(String[] args) throws Exception {
        String path = "c:/Users/rutwi/OneDrive - MSFT/Desktop/coresuit/src/main/java/net/rocky/coresuite/module/customfishing/CustomFishingModule.java";
        String content = new String(Files.readAllBytes(Paths.get(path)));

        // Add XP helpers
        String helpers = "\n    public int getTotalExperience(org.bukkit.entity.Player player) {\n" +
                         "        int level = player.getLevel();\n" +
                         "        int exp = Math.round(getExpToNext(level) * player.getExp());\n" +
                         "        while (level > 0) {\n" +
                         "            level--;\n" +
                         "            exp += getExpToNext(level);\n" +
                         "        }\n" +
                         "        return exp;\n" +
                         "    }\n\n" +
                         "    private int getExpToNext(int level) {\n" +
                         "        if (level < 16) return 2 * level + 7;\n" +
                         "        if (level < 31) return 5 * level - 38;\n" +
                         "        return 9 * level - 158;\n" +
                         "    }\n\n" +
                         "    public void removeExp(org.bukkit.entity.Player player, int amount) {\n" +
                         "        int exp = getTotalExperience(player) - amount;\n" +
                         "        player.setTotalExperience(0);\n" +
                         "        player.setLevel(0);\n" +
                         "        player.setExp(0);\n" +
                         "        if (exp > 0) player.giveExp(exp);\n" +
                         "    }\n\n";
                         
        content = content.replace("private void openTopGUI(Player player) {", helpers + "    private void openTopGUI(Player player) {");

        // Change enchant logic
        content = content.replace(
            "int pXp = pData != null ? pData.xp : 0;",
            "int pXp = getTotalExperience(player);"
        );
        content = content.replace(
            "lore.add(color + \"Required Fishing XP: \" + reqXp + \" &8(\" + pXp + \")\");",
            "lore.add(color + \"Required Vanilla EXP: \" + reqXp + \" &8(\" + pXp + \")\");"
        );
        content = content.replace(
            "plugin.getEconomyManager().withdraw(player, cost);\n                        enchantManager.setEnchantLevel(rod, ce, level + 1);",
            "plugin.getEconomyManager().withdraw(player, cost);\n                        if (reqXp > 0) removeExp(player, reqXp);\n                        enchantManager.setEnchantLevel(rod, ce, level + 1);"
        );

        Files.write(Paths.get(path), content.getBytes());
    }
}
