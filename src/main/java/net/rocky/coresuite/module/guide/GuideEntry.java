package net.rocky.coresuite.module.guide;

import org.bukkit.Material;

import java.util.Collections;
import java.util.List;

/**
 * Represents a single guided command/mechanic entry in the Guide GUI.
 */
public record GuideEntry(
        String key,
        String name,
        String command,
        Material material,
        int slot,
        List<String> description,
        List<String> chatExplanation
) {
    public GuideEntry {
        if (key == null) key = "";
        if (name == null) name = "";
        if (command == null) command = "";
        if (material == null) material = Material.BOOK;
        if (description == null) description = Collections.emptyList();
        if (chatExplanation == null) chatExplanation = Collections.emptyList();
    }
}
