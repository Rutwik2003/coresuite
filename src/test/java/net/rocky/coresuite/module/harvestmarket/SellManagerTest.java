package net.rocky.coresuite.module.harvestmarket;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.economy.EconomyManager;
import net.rocky.coresuite.common.storage.DatabaseManager;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SellManagerTest {
    private final CoreSuitePlugin plugin = mock(CoreSuitePlugin.class);
    private final DatabaseManager database = mock(DatabaseManager.class);
    private final EconomyManager economy = mock(EconomyManager.class);
    private final SeasonManager seasons = mock(SeasonManager.class);
    private final Player player = mock(Player.class);
    private final PlayerInventory inventory = mock(PlayerInventory.class);
    private final YamlConfiguration config = new YamlConfiguration();
    private final ItemStack[] slots = new ItemStack[41];
    private final AtomicReference<Double> paid = new AtomicReference<>();
    private final AtomicInteger failed = new AtomicInteger();
    private SellManager manager;

    @BeforeEach void setup() {
        when(plugin.getDatabaseManager()).thenReturn(database);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getContents()).thenAnswer(i -> slots.clone());
        doAnswer(i -> { slots[i.getArgument(0, Integer.class)] = i.getArgument(1); return null; })
                .when(inventory).setItem(anyInt(), nullable(ItemStack.class));
        config.set("crop-prices.WHEAT", 10.0);
        config.set("max-quota-per-season", 5);
        config.set("bonus-multiplier", 2.5);
        when(seasons.getCurrentSeason()).thenReturn(new Season(1, List.of(Material.WHEAT), 0, Long.MAX_VALUE, true));
        when(seasons.isInSeason(Material.WHEAT)).thenReturn(true);
        manager = new SellManager(plugin, seasons, economy, config);
        clearInvocations(database);
    }

    private ItemStack wheat(int amount) {
        ItemStack stack = mock(ItemStack.class);
        AtomicInteger count = new AtomicInteger(amount);
        when(stack.getType()).thenReturn(Material.WHEAT);
        when(stack.getAmount()).thenAnswer(i -> count.get());
        doAnswer(i -> { count.set(i.getArgument(0)); return null; }).when(stack).setAmount(anyInt());
        when(stack.clone()).thenAnswer(i -> wheat(count.get()));
        return stack;
    }

    private void sell(int amount) {
        manager.sellCrops(player, Material.WHEAT, amount, false, paid::set, failed::incrementAndGet);
    }

    @Test void failedDepositRestoresRemovedAndPartialStacksAndDoesNotConsumeQuota() {
        slots[0] = wheat(3); slots[1] = wheat(10);
        when(economy.deposit(player, 145.0)).thenReturn(false);
        sell(7);
        assertEquals(3, slots[0].getAmount());
        assertEquals(10, slots[1].getAmount());
        assertEquals(0, manager.getQuotaUsed(player.getUniqueId()));
        assertNull(paid.get()); assertEquals(1, failed.get());
        verify(economy).deposit(player, 145.0);
        verifyNoInteractions(database);
    }

    @Test void successfulSaleSplitsQuotaBonusAndRemovesOnlyRequestedAmount() {
        slots[0] = wheat(10);
        when(economy.deposit(player, 145.0)).thenReturn(true);
        sell(7);
        assertEquals(3, slots[0].getAmount());
        assertEquals(145.0, paid.get()); assertEquals(0, failed.get());
        assertEquals(7, manager.getQuotaUsed(player.getUniqueId()));
    }

    @Test void zeroNegativeAndInsufficientSalesNeverReachEconomy() {
        slots[0] = wheat(3);
        sell(0); sell(-1); sell(4);
        assertEquals(3, slots[0].getAmount());
        assertEquals(3, failed.get());
        verifyNoInteractions(economy, database);
    }

    @Test void nonFiniteOrNonPositivePriceCannotConsumeItems() {
        slots[0] = wheat(3);
        for (double price : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1.0, 0.0}) {
            config.set("crop-prices.WHEAT", price); sell(1);
        }
        assertEquals(3, slots[0].getAmount()); assertEquals(4, failed.get());
        verifyNoInteractions(economy, database);
    }

    @Test void offSeasonSaleDoesNotUseBonusOrQuota() {
        slots[0] = wheat(3);
        when(seasons.isInSeason(Material.WHEAT)).thenReturn(false);
        when(economy.deposit(player, 30.0)).thenReturn(true);
        sell(3);
        assertNull(slots[0]); assertEquals(30.0, paid.get());
        assertEquals(0, manager.getQuotaUsed(player.getUniqueId()));
        verifyNoInteractions(database);
    }
}
