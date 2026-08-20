package net.rocky.coresuite.module.mentions;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataType;

import java.util.regex.Pattern;

public class MentionModule extends Module implements Listener, CommandExecutor, TabCompleter {

    private NamespacedKey disabledKey;

    public MentionModule(CoreSuitePlugin plugin) {
        super(plugin, "Mentions", "chat-mentions");
        this.disabledKey = new NamespacedKey(plugin, "mentions_disabled");
    }

    @Override
    public void onEnable() {
        registerListener(this);
        PluginCommand command = plugin.getCommand("togglementions");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
    }

    @Override
    public void onDisable() {
        PluginCommand command = plugin.getCommand("togglementions");
        if (command != null && command.getExecutor() == this) {
            command.setExecutor(null);
            command.setTabCompleter(null);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        String originalMessage = TextUtil.legacyColorize(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.message()));
        String lowerMessage = originalMessage.toLowerCase();
        
        java.util.List<Player> matchedPlayers = new java.util.ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            String name = p.getName();
            if (lowerMessage.contains(name.toLowerCase())) {
                // Highlight the name in the message
                String colorName = config.getString("highlight-color", "YELLOW");
                NamedTextColor color = NamedTextColor.NAMES.value(colorName.toLowerCase());
                if (color == null) color = NamedTextColor.YELLOW;
                
                TextReplacementConfig replacementConfig = TextReplacementConfig.builder()
                        .match("(?i)\\b" + Pattern.quote(name) + "\\b")
                        .replacement(Component.text("@" + name, color))
                        .build();
                
                event.message(event.message().replaceText(replacementConfig));
                matchedPlayers.add(p);
            }
        }
        
        if (!matchedPlayers.isEmpty()) {
            final Player senderPlayer = event.getPlayer();
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Player p : matchedPlayers) {
                    Byte disabledFlag = p.getPersistentDataContainer().get(disabledKey, PersistentDataType.BYTE);
                    if (disabledFlag != null && disabledFlag == 1) {
                        continue;
                    }
                    
                    if (config.getBoolean("show-action-bar", true)) {
                        String actionText = config.getString("action-bar-text", "&e{player} &7mentioned you!");
                        TextUtil.sendActionBar(p, actionText.replace("{player}", senderPlayer.getName()));
                    }
                    
                    try {
                        String soundName = config.getString("mention-sound", "BLOCK_NOTE_BLOCK_BIT");
                        Sound sound = Sound.valueOf(soundName.toUpperCase());
                        float volume = (float) config.getDouble("sound-volume", 1.0);
                        float pitch = (float) config.getDouble("sound-pitch", 1.5);
                        p.playSound(p.getLocation(), sound, volume, pitch);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid mention sound in chat-mentions.yml: " + config.getString("mention-sound"));
                    }
                }
            });
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can toggle mentions.");
            return true;
        }

        Player player = (Player) sender;
        String prefix = config.getString("prefix", "<##ffe474>&lMENTIONS &7→ &f");

        Byte disabledFlag = player.getPersistentDataContainer().get(disabledKey, PersistentDataType.BYTE);
        boolean currentlyDisabled = (disabledFlag != null && disabledFlag == 1);

        if (currentlyDisabled) {
            player.getPersistentDataContainer().set(disabledKey, PersistentDataType.BYTE, (byte) 0);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 2.0f, 1.0f);
            TextUtil.sendMessage(player, prefix + config.getString("messages.mention-toggle-on", "&aEnabled &fchat mentions for you."));
        } else {
            player.getPersistentDataContainer().set(disabledKey, PersistentDataType.BYTE, (byte) 1);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 2.0f, 1.0f);
            TextUtil.sendMessage(player, prefix + config.getString("messages.mention-toggle-off", "&cDisabled &fchat mentions for you."));
        }

        return true;
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return java.util.Collections.emptyList();
    }
}
