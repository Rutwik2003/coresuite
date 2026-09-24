package net.rocky.coresuite.module.worldbosses.rewards;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.economy.EconomyManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HunterExchangeTransactionTest {
    CoreSuitePlugin plugin;
    EconomyManager economy;
    BossItemRegistry registry;
    Player player;
    PlayerInventory inventory;
    HunterExchangeGUI shop;
    ExcellentCratesRewardBridge.Delivery delivery;
    @BeforeEach void setup() throws Exception {
        plugin = mock(CoreSuitePlugin.class); economy = mock(EconomyManager.class);
        registry = mock(BossItemRegistry.class); player = mock(Player.class);
        inventory = mock(PlayerInventory.class); delivery = mock(ExcellentCratesRewardBridge.Delivery.class);
        when(plugin.getEconomyManager()).thenReturn(economy);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(player.getInventory()).thenReturn(inventory);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(inventory.getContents()).thenReturn(new ItemStack[41]);
        when(registry.countShards(player, "ashen")).thenReturn(120);
        when(registry.takeShards(player, "ashen", 120)).thenReturn(true);
        when(economy.isAvailable()).thenReturn(true);
        when(economy.getBalance(player)).thenReturn(4000000d);
        when(economy.withdraw(player, 4000000)).thenReturn(true);
        when(economy.deposit(player, 4000000)).thenReturn(true);
        when(delivery.canDeliver()).thenReturn(true);
        when(delivery.deliver()).thenReturn(true);
        shop = new HunterExchangeGUI(plugin, registry, new YamlConfiguration());
    }
    boolean buy() { return shop.purchase(player, "ashen", 120, 4000000, () -> delivery); }
    @Test void oldMenusCannotBuyAfterReloadOrDisable() throws Exception {
        shop.invalidate();
        assertFalse(buy()); verifyNoInteractions(delivery);
        verify(economy, never()).withdraw(any(), anyDouble());
        verify(registry, never()).takeShards(any(), anyString(), anyInt());
    }
    @Test void staleBalanceCannotPurchase() throws Exception {
        when(economy.getBalance(player)).thenReturn(0d);
        assertFalse(buy()); verify(registry, never()).takeShards(any(), anyString(), anyInt());
        verify(delivery, never()).deliver();
    }
    @Test void rejectedPaymentRestoresShardsAndDeliversNothing() throws Exception {
        when(economy.withdraw(player, 4000000)).thenReturn(false);
        assertFalse(buy()); verify(inventory).setContents(any(ItemStack[].class));
        verify(delivery, never()).deliver();
    }
    @Test void fullInventoryDoesNotCharge() throws Exception {
        when(delivery.canDeliver()).thenReturn(false);
        assertFalse(buy()); verify(economy, never()).withdraw(any(), anyDouble());
        verify(inventory).setContents(any(ItemStack[].class)); verify(delivery, never()).deliver();
    }
    @Test void missingProviderDoesNotChargeOrConsumeShards() {
        assertFalse(shop.purchase(player, "ashen", 120, 4000000, () -> { throw new IllegalArgumentException("missing key"); }));
        verify(economy, never()).withdraw(any(), anyDouble());
        verify(inventory).setContents(any(ItemStack[].class));
    }
    @Test void planIsPreparedAfterShardsRemovedAndDeliveredAfterPayment() throws Exception {
        var factory = mock(HunterExchangeGUI.DeliveryFactory.class);
        when(factory.prepare()).thenReturn(delivery);
        assertTrue(shop.purchase(player, "ashen", 120, 4000000, factory));
        var order = inOrder(registry, factory, economy, delivery);
        order.verify(registry).takeShards(player, "ashen", 120);
        order.verify(factory).prepare(); order.verify(economy).withdraw(player, 4000000);
        order.verify(delivery).deliver();
        verify(inventory, never()).setContents(any());
    }
    @Test void definiteDeliveryFailureRefundsButUncertainDeliveryDoesNot() throws Exception {
        when(delivery.deliver()).thenReturn(false);
        assertFalse(buy()); verify(economy).deposit(player, 4000000);
        clearInvocations(economy, inventory);
        when(delivery.deliver()).thenThrow(new IllegalStateException("may already be granted"));
        assertFalse(buy()); verify(economy, never()).deposit(any(), anyDouble());
        verify(inventory, never()).setContents(any());
    }
    @Test void uncertainWithdrawalKeepsShardsWithoutGivingRewardOrGuessingRefund() throws Exception {
        when(economy.withdraw(player, 4000000)).thenThrow(new IllegalStateException("uncertain"));
        assertFalse(buy()); verify(inventory).setContents(any(ItemStack[].class));
        verify(delivery, never()).deliver(); verify(economy, never()).deposit(any(), anyDouble());
    }
    @Test void invalidPricesNeverCharge() {
        for (double price : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY})
            assertFalse(shop.purchase(player, "ashen", 120, price, () -> delivery));
        assertFalse(shop.purchase(player, "ashen", -1, 0, () -> delivery));
        verify(economy, never()).withdraw(any(), anyDouble());
        verify(registry, never()).takeShards(any(), anyString(), anyInt());
    }
}
