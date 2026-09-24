package net.rocky.coresuite.module.worldbosses.rewards;
import net.rocky.coresuite.module.worldbosses.WorldBossesModule;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
class GlacialGuardTest {
    @Test void onlyEquippedRelicProtectsAgainstFallAndFreeze() throws Exception {
        var module = mock(WorldBossesModule.class, CALLS_REAL_METHODS);
        var registry = mock(BossItemRegistry.class);
        var field = WorldBossesModule.class.getDeclaredField("itemRegistry");
        field.setAccessible(true); field.set(module, registry);
        var player = mock(Player.class); var inventory = mock(PlayerInventory.class);
        var boots = mock(ItemStack.class);
        when(player.getInventory()).thenReturn(inventory); when(inventory.getBoots()).thenReturn(boots);
        var event = mock(EntityDamageEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.FALL);
        when(event.getDamage()).thenReturn(20d);
        module.onGlacialGuardDamage(event);
        verify(event, never()).setDamage(anyDouble());
        when(registry.hasSignatureWeapon(boots, "frostbound_striders")).thenReturn(true);
        module.onGlacialGuardDamage(event); verify(event).setDamage(15d);
        when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.FREEZE);
        module.onGlacialGuardDamage(event); verify(event).setCancelled(true);
        clearInvocations(event);
        when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.ENTITY_ATTACK);
        module.onGlacialGuardDamage(event);
        verify(event, never()).setDamage(anyDouble()); verify(event, never()).setCancelled(anyBoolean());
    }
}
