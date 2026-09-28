package net.rocky.coresuite.module.rewardcodes;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RewardCodeEditorTest {
    @Test void draftCanBeBuiltAndEditedWithoutOverwritingOtherCommands() {
        var config = new YamlConfiguration();
        RewardCodeEditor.edit(config, new String[]{"create", "Summer", "20"});
        assertFalse(config.getBoolean("codes.Summer.enabled"));
        assertThrows(IllegalArgumentException.class, () -> RewardCodeEditor.edit(config, new String[]{"enable", "Summer"}));
        RewardCodeEditor.edit(config, new String[]{"addcommand", "summer", "/uc", "give", "key", "1", "%player%"});
        RewardCodeEditor.edit(config, new String[]{"addcommand", "Summer", "give", "%player%", "diamond", "2"});
        assertEquals(2, config.getStringList("codes.Summer.commands").size());
        RewardCodeEditor.edit(config, new String[]{"setcommand", "Summer", "2", "give", "%player%", "diamond", "3"});
        assertEquals("give %player% diamond 3", config.getStringList("codes.Summer.commands").get(1));
        RewardCodeEditor.edit(config, new String[]{"enable", "Summer"});
        assertTrue(config.getBoolean("codes.Summer.enabled"));
        assertThrows(IllegalArgumentException.class, () -> RewardCodeEditor.edit(config, new String[]{"create", "SUMMER", "1", "bad"}));
        RewardCodeEditor.edit(config, new String[]{"removecommand", "Summer", "1"});
        assertEquals(1, config.getStringList("codes.Summer.commands").size());
        assertThrows(IllegalArgumentException.class, () -> RewardCodeEditor.edit(config, new String[]{"create", "bad.code"}));
    }
    @Test void defaultCodeCommandsAreEditableAndInvalidIndexDoesNotChangeThem() {
        var config = new YamlConfiguration(); config.set("code", "DEFAULT");
        RewardCodeEditor.edit(config, new String[]{"addcommand", "default", "say", "hello"});
        assertEquals(java.util.List.of("say hello"), config.getStringList("commands"));
        assertThrows(IllegalArgumentException.class, () -> RewardCodeEditor.edit(config, new String[]{"removecommand", "default", "0"}));
        assertEquals(java.util.List.of("say hello"), config.getStringList("commands"));
    }
}
