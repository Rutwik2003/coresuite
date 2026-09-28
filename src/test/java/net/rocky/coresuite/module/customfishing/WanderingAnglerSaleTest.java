package net.rocky.coresuite.module.customfishing;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.economy.EconomyManager;
import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.*;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WanderingAnglerSaleTest {
    @Test void bagSaleConsumesExactlyOneOnlyWhenPaymentSucceeds() throws Exception {
        var plugin=mock(CoreSuitePlugin.class); when(plugin.getName()).thenReturn("CoreSuite");
        var module=mock(CustomFishingModule.class); var bags=mock(FishBagManager.class);
        when(module.getFishBagManager()).thenReturn(bags);
        var speciesKey=new NamespacedKey(plugin,"fish_species"); when(module.getSpeciesKey()).thenReturn(speciesKey);
        var economy=mock(EconomyManager.class); when(plugin.getEconomyManager()).thenReturn(economy);
        var player=mock(Player.class); var inventory=mock(PlayerInventory.class);
        var uuid=UUID.randomUUID(); when(player.getUniqueId()).thenReturn(uuid);
        when(player.getInventory()).thenReturn(inventory); when(inventory.getStorageContents()).thenReturn(new ItemStack[36]);
        var fish=mock(ItemStack.class); when(fish.getAmount()).thenReturn(1); when(fish.hasItemMeta()).thenReturn(true);
        var meta=mock(ItemMeta.class); when(fish.getItemMeta()).thenReturn(meta);
        var data=mock(PersistentDataContainer.class); when(meta.getPersistentDataContainer()).thenReturn(data);
        when(data.get(speciesKey,PersistentDataType.STRING)).thenReturn("Carp");
        when(data.getOrDefault(new NamespacedKey(plugin,"fish_weight"),PersistentDataType.DOUBLE,1.0)).thenReturn(2.0);
        when(data.getOrDefault(new NamespacedKey(plugin,"fish_price"),PersistentDataType.DOUBLE,1.0)).thenReturn(10.0);
        ItemStack[] bag=new ItemStack[450];bag[80]=fish;when(bags.getBag(uuid)).thenReturn(bag);
        var manager=mock(WanderingAnglerManager.class, CALLS_REAL_METHODS);
        for (var entry:java.util.Map.of("plugin",plugin,"module",module).entrySet()) {
            var field=WanderingAnglerManager.class.getDeclaredField(entry.getKey());field.setAccessible(true);field.set(manager,entry.getValue());
        }
        var sell=WanderingAnglerManager.class.getDeclaredMethod("sellFish",Player.class,String.class,String.class);sell.setAccessible(true);
        try(var text=mockStatic(TextUtil.class)) {
            sell.invoke(manager,player,"Carp","Carp");
            assertSame(fish,bag[80]);verify(bags,never()).saveBag(uuid);
            when(economy.deposit(player,50)).thenReturn(true);
            sell.invoke(manager,player,"Carp","Carp");
            assertNull(bag[80]);verify(bags).saveBag(uuid);
            verify(inventory,never()).setItem(anyInt(),any());
        }
    }
}
