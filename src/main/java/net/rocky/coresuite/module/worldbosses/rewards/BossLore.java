package net.rocky.coresuite.module.worldbosses.rewards;
import java.util.List;
import net.rocky.coresuite.module.worldbosses.boss.BossType;
public final class BossLore {
    private BossLore() {}
    public static List<String> lines(BossType type) {
        return switch(type) {
            case VALERIUS -> List.of("&7Beneath the dead caldera, a sentinel still keeps the forge alight.","&7His blackened armour carries the names of those the mountain swallowed.","&7The oldest smiths left no instructions, only a song of a furnace gone silent.");
            case AETHELGARD -> List.of("&7A king begged the forest for a life without end. The forest collected his kingdom.","&7Now a hollow crown wanders beneath branches that whisper in stolen voices.","&7Deep beneath the throne, the oldest rings remember a season he fears.");
            case XOLTHOS -> List.of("&7In halls forgotten by dawn, the widow weaves memories into her web.","&7Travellers swear her shadow arrives before her, and lingers after she is gone.","&7An unfinished chronicle speaks of one night when every shadow lost its name.");
            case BOREALIS -> List.of("&7Borealis sealed the Glacial Vault with his soldiers still inside.","&7True Ice claimed their hearts, but their oath outlived the warmth of the world.","&7A buried pilgrim wrote of a winter that forgot itself for a single breath.","&7The rest of the page is missing. Beneath the ice, the court still waits.");
        };
    }
}
