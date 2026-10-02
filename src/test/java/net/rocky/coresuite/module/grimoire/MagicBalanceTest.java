package net.rocky.coresuite.module.grimoire;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class MagicBalanceTest {
  @Test
  void recoveryWaitsAfterCastingAndNeverOverfills() {
    var p = new MagicProfile();
    p.mana = 95;
    p.lastCast = 1000;
    p.regenerate(3999, 100, 2, 3000);
    assertEquals(95, p.mana);
    p.regenerate(4000, 100, 2, 3000);
    assertEquals(97, p.mana);
    p.regenerate(5000, 100, 10, 3000);
    assertEquals(100, p.mana);
    p.mana = Double.NaN;
    p.regenerate(6000, Double.NaN, Double.NaN, 3000);
    assertEquals(2, p.mana);
  }

  @Test
  void tiersReduceCostWithoutMakingSpellsFree() {
    var spell = new SpellSpec("test", "test", Material.BOOK, 20, 1000, 4, 20, true);
    assertEquals(20, spell.tierMana(1, .05));
    assertEquals(19, spell.tierMana(2, .05));
    assertEquals(18, spell.tierMana(3, .05));
    assertEquals(12, spell.tierMana(999, 999));
    assertEquals(18, spell.tierMana(3, Double.NaN));
  }

  @Test
  void replantingSelectsOnlyPlainMatchingSupplies() {
    assertEquals(Material.WHEAT_SEEDS, SpellEngine.seedFor(Material.WHEAT));
    assertEquals(Material.CARROT, SpellEngine.seedFor(Material.CARROTS));
    assertEquals(Material.POTATO, SpellEngine.seedFor(Material.POTATOES));
    assertEquals(Material.BEETROOT_SEEDS, SpellEngine.seedFor(Material.BEETROOTS));
    var custom = mock(ItemStack.class);
    when(custom.getType()).thenReturn(Material.CARROT);
    when(custom.getAmount()).thenReturn(64);
    when(custom.hasItemMeta()).thenReturn(true);
    var plain = mock(ItemStack.class);
    when(plain.getType()).thenReturn(Material.CARROT);
    when(plain.getAmount()).thenReturn(3);
    assertEquals(
        2, SpellEngine.plantingSlot(new ItemStack[] {null, custom, plain}, Material.CARROT));
    assertEquals(-1, SpellEngine.plantingSlot(new ItemStack[] {custom}, Material.CARROT));
    assertEquals(-1, SpellEngine.plantingSlot(new ItemStack[] {plain}, Material.POTATO));
  }
}
