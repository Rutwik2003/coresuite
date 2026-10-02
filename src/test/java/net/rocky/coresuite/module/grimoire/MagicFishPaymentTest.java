package net.rocky.coresuite.module.grimoire;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

class MagicFishPaymentTest {
  final NamespacedKey key = new NamespacedKey("coresuite", "fish_species");

  ItemStack fish(String species, int amount) {
    var item = mock(ItemStack.class);
    var meta = mock(ItemMeta.class);
    var data = mock(PersistentDataContainer.class);
    when(item.hasItemMeta()).thenReturn(true);
    when(item.getItemMeta()).thenReturn(meta);
    when(meta.getPersistentDataContainer()).thenReturn(data);
    when(data.get(key, PersistentDataType.STRING)).thenReturn(species);
    var count = new AtomicInteger(amount);
    when(item.getAmount()).thenAnswer(c -> count.get());
    doAnswer(
            c -> {
              count.set(c.getArgument(0));
              return null;
            })
        .when(item)
        .setAmount(anyInt());
    when(item.clone()).thenAnswer(c -> fish(species, count.get()));
    return item;
  }

  @Test
  void inventoryThenUnlockedBagAreUsedWithoutMutatingOriginals() {
    var inv = new ItemStack[] {fish("atlantic_cod", 1)};
    var bag = new ItemStack[90];
    bag[0] = fish("atlantic_cod", 5);
    var result = MagicFishPayment.plan(inv, bag, 45, key, Map.of("atlantic_cod", 3));
    assertNotNull(result);
    assertNull(result.inventory()[0]);
    assertEquals(3, result.bag()[0].getAmount());
    assertTrue(result.bagChanged());
    assertEquals(1, inv[0].getAmount());
    assertEquals(5, bag[0].getAmount());
  }

  @Test
  void lockedPagesAndWrongSpeciesCannotPay() {
    var bag = new ItemStack[90];
    bag[45] = fish("atlantic_cod", 10);
    assertNull(
        MagicFishPayment.plan(
            new ItemStack[] {fish("anchovy", 10)}, bag, 45, key, Map.of("atlantic_cod", 1)));
    assertEquals(10, bag[45].getAmount());
  }

  @Test
  void insufficientFishLeavesEverythingUntouched() {
    var inv = new ItemStack[] {fish("atlantic_cod", 1)};
    assertNull(MagicFishPayment.plan(inv, null, 0, key, Map.of("atlantic_cod", 2)));
    assertEquals(1, inv[0].getAmount());
  }
}
