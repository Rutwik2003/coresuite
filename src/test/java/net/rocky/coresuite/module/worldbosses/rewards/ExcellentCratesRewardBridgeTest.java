package net.rocky.coresuite.module.worldbosses.rewards;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ExcellentCratesRewardBridgeTest {
    public static class Key {
        final boolean virtual;
        final ItemStack item;
        Key(boolean virtual, ItemStack item) { this.virtual = virtual; this.item = item; }
        public boolean isVirtual() { return virtual; }
        public ItemStack getItemStack() { return item; }
    }
    public static class Manager {
        Key key;
        int balance, calls;
        boolean apply = true;
        public Key getKeyById(String id) { return id.equalsIgnoreCase("rare") ? key : null; }
        public int getKeysAmount(Player player, Key key) { return balance; }
        public void giveKey(Player player, Key key, int amount) { calls++; if (apply) balance += amount; }
    }
    public static class Provider {
        final Manager manager = new Manager();
        public Manager getKeyManager() { return manager; }
    }
    @Test void recognizesStockAndLegacyAliasesWithoutChangingKeyIdsOrOtherCommands() {
        for (String root : new String[]{"crate", "crates", "excellentcrates", "excellentcrates:crates"}) {
            var grant = ExcellentCratesRewardBridge.parse(root + " key give Rocky Rare 2", "Rocky");
            assertEquals("Rare", grant.key()); assertEquals(2, grant.amount());
        }
        assertEquals(1, ExcellentCratesRewardBridge.parse("crates key give Rocky rare", "Rocky").amount());
        assertNull(ExcellentCratesRewardBridge.parse("crates key give SomeoneElse rare 1", "Rocky"));
        assertNull(ExcellentCratesRewardBridge.parse("crates key take Rocky rare 1", "Rocky"));
        assertThrows(IllegalArgumentException.class, () -> ExcellentCratesRewardBridge.parse("crates key give Rocky rare 0", "Rocky"));
    }
    @Test void unknownKeyFailsBeforeAnyDelivery() {
        Provider provider = new Provider();
        Player player = mock(Player.class);
        assertThrows(IllegalArgumentException.class, () -> ExcellentCratesRewardBridge.prepare(provider, player, new ExcellentCratesRewardBridge.KeyGrant("missing", 1)));
        verifyNoInteractions(player); assertEquals(0, provider.manager.calls);
    }
    @Test void fullInventoryDefersPhysicalKeyWithoutInvokingGiveOrSavingPlayer() throws Exception {
        Provider provider = new Provider();
        ItemStack key = mock(ItemStack.class), occupied = mock(ItemStack.class);
        Material material = mock(Material.class);
        when(key.clone()).thenReturn(key); when(key.getType()).thenReturn(material);
        when(key.getAmount()).thenReturn(1); when(key.getMaxStackSize()).thenReturn(64);
        when(occupied.clone()).thenReturn(occupied); when(occupied.getType()).thenReturn(material);
        provider.manager.key = new Key(false, key);
        Player player = mock(Player.class); PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getStorageContents()).thenReturn(new ItemStack[]{occupied});
        when(inventory.getMaxStackSize()).thenReturn(64);
        assertFalse(ExcellentCratesRewardBridge.prepare(provider, player, new ExcellentCratesRewardBridge.KeyGrant("rare", 1)).deliver());
        verify(inventory, never()).setStorageContents(any()); verify(player, never()).saveData();
        assertEquals(0, provider.manager.calls);
    }
    @Test void virtualKeyDeliveryVerifiesTheBalance() throws Exception {
        Provider provider = new Provider(); provider.manager.key = new Key(true, null); provider.manager.balance = 4;
        assertTrue(ExcellentCratesRewardBridge.prepare(provider, mock(Player.class), new ExcellentCratesRewardBridge.KeyGrant("rare", 2)).deliver());
        assertEquals(6, provider.manager.balance); assertEquals(1, provider.manager.calls);
    }
    @Test void virtualBalanceMismatchDoesNotRetryMutation() throws Exception {
        Provider provider = new Provider(); provider.manager.key = new Key(true, null); provider.manager.apply = false;
        var delivery = ExcellentCratesRewardBridge.prepare(provider, mock(Player.class), new ExcellentCratesRewardBridge.KeyGrant("rare", 1));
        assertThrows(IllegalStateException.class, delivery::deliver); assertEquals(1, provider.manager.calls);
    }
    @Test void virtualOverflowFailsBeforeMutation() {
        Provider provider = new Provider(); provider.manager.key = new Key(true, null); provider.manager.balance = Integer.MAX_VALUE;
        assertThrows(ArithmeticException.class, () -> ExcellentCratesRewardBridge.prepare(provider, mock(Player.class), new ExcellentCratesRewardBridge.KeyGrant("rare", 1)));
        assertEquals(0, provider.manager.calls);
    }
}
