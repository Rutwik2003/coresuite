package net.rocky.coresuite.common.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.rocky.coresuite.CoreSuitePlugin;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EconomyManagerTest {
    private EconomyManager manager(Economy provider) throws Exception {
        CoreSuitePlugin plugin = mock(CoreSuitePlugin.class);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        EconomyManager manager = new EconomyManager(plugin);
        Field field = EconomyManager.class.getDeclaredField("economy");
        field.setAccessible(true); field.set(manager, provider);
        return manager;
    }
    @Test void invalidNumbersNeverReachVault() throws Exception {
        Economy provider = mock(Economy.class);
        EconomyManager manager = manager(provider);
        OfflinePlayer player = mock(OfflinePlayer.class);
        for (double amount : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1}) {
            assertFalse(manager.deposit(player, amount));
            assertFalse(manager.withdraw(player, amount));
            assertFalse(manager.has(player, amount));
        }
        assertFalse(manager.deposit(player, 0)); assertFalse(manager.withdraw(player, 0));
        verifyNoInteractions(provider);
    }
    @Test void validTransactionStillUsesVault() throws Exception {
        Economy provider = mock(Economy.class);
        EconomyManager manager = manager(provider);
        OfflinePlayer player = mock(OfflinePlayer.class);
        when(provider.depositPlayer(player, 12.5)).thenReturn(new EconomyResponse(12.5, 100, EconomyResponse.ResponseType.SUCCESS, null));
        assertTrue(manager.deposit(player, 12.5));
        verify(provider).depositPlayer(player, 12.5);
    }
    @Test void rejectedOrThrowingMutationIsNeverRetriedByName() throws Exception {
        Economy provider = mock(Economy.class);
        EconomyManager manager = manager(provider);
        OfflinePlayer player = mock(OfflinePlayer.class);
        when(player.getName()).thenReturn("Player");
        when(provider.depositPlayer(player, 10)).thenReturn(new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "offline"));
        when(provider.withdrawPlayer(player, 10)).thenThrow(new IllegalStateException("connection lost"));
        assertFalse(manager.deposit(player, 10));
        assertThrows(IllegalStateException.class, () -> manager.withdraw(player, 10));
        verify(provider).depositPlayer(player, 10);
        verify(provider).withdrawPlayer(player, 10);
        verifyNoMoreInteractions(provider);
    }
}
