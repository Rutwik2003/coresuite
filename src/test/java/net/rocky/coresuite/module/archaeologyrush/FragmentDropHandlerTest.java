package net.rocky.coresuite.module.archaeologyrush;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.configuration.file.YamlConfiguration;
import net.rocky.coresuite.common.integration.AuthenticationGate;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
class FragmentDropHandlerTest {
    @Test void repeatedPartialBrushesNeverRollButCompletedExcavationRollsOnce() {
        var registry = mock(ArtifactSetRegistry.class);
        var config = new YamlConfiguration(); config.set("fragment-drop-chance", 1.0);
        var handler = new FragmentDropHandler(null, registry, null, config, null, null);
        var player = mock(Player.class); when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        var block = mock(Block.class); when(block.getType()).thenReturn(Material.SUSPICIOUS_SAND);
        var event = mock(EntityChangeBlockEvent.class);
        when(event.getEntity()).thenReturn(player); when(event.getBlock()).thenReturn(block);
        try (var auth = mockStatic(AuthenticationGate.class)) {
            auth.when(() -> AuthenticationGate.isAuthenticated(player)).thenReturn(true);
            when(event.getTo()).thenReturn(Material.SUSPICIOUS_SAND);
            for (int i = 0; i < 100; i++) handler.onExcavationComplete(event);
            verifyNoInteractions(registry);
            when(event.getTo()).thenReturn(Material.SAND);
            when(event.isCancelled()).thenReturn(true);
            handler.onExcavationComplete(event); verifyNoInteractions(registry);
            when(event.isCancelled()).thenReturn(false);
            handler.onExcavationComplete(event); verify(registry).getAllSets();
            // The consumed block cannot qualify again, even if the interaction repeats.
            when(block.getType()).thenReturn(Material.SAND);
            handler.onExcavationComplete(event); verifyNoMoreInteractions(registry);
        }
    }
    @Test void breakingOrFallingBlocksNeverCountAsExcavation() {
        org.junit.jupiter.api.Assertions.assertFalse(FragmentDropHandler.isCompletedExcavation(Material.SUSPICIOUS_SAND, Material.AIR));
        org.junit.jupiter.api.Assertions.assertFalse(FragmentDropHandler.isCompletedExcavation(Material.SUSPICIOUS_GRAVEL, Material.SUSPICIOUS_GRAVEL));
        org.junit.jupiter.api.Assertions.assertTrue(FragmentDropHandler.isCompletedExcavation(Material.SUSPICIOUS_GRAVEL, Material.GRAVEL));
    }
}
