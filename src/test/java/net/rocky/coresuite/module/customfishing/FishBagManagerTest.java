package net.rocky.coresuite.module.customfishing;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.storage.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FishBagManagerTest {
    private final CoreSuitePlugin plugin = mock(CoreSuitePlugin.class);
    private final DatabaseManager database = mock(DatabaseManager.class);
    private final BukkitScheduler scheduler = mock(BukkitScheduler.class);
    private final List<Runnable> jobs = new ArrayList<>();
    private final List<Runnable> callbacks = new ArrayList<>();
    private final UUID uuid = UUID.randomUUID();

    private FishBagManager manager() throws Exception {
        when(plugin.getDatabaseManager()).thenReturn(database);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(plugin.isEnabled()).thenReturn(true);
        doAnswer(call -> { jobs.add(call.getArgument(0)); return null; }).when(database).executeAsync(any(Runnable.class));
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> { callbacks.add(call.getArgument(1)); return null; });
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        when(database.getConnection()).thenReturn(conn);
        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(mock(ResultSet.class));
        return new FishBagManager(plugin);
    }

    @Test void dataIsUnavailableUntilServerThreadAppliesLoad() throws Exception {
        FishBagManager manager = manager();
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            manager.loadBag(uuid);
            assertNull(manager.getBag(uuid));
            jobs.removeFirst().run();
            assertNull(manager.getBag(uuid));
            callbacks.removeFirst().run();
            assertEquals(450, manager.getBag(uuid).length);
        }
    }

    @Test void lateCallbackCannotRestoreLoggedOutBagOrReplaceNewSession() throws Exception {
        FishBagManager manager = manager();
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            manager.loadBag(uuid);
            jobs.removeFirst().run();
            manager.unloadBag(uuid);
            manager.loadBag(uuid);
            callbacks.removeFirst().run();
            assertNull(manager.getBag(uuid));
            jobs.removeFirst().run();
            callbacks.removeFirst().run();
            assertEquals(450, manager.getBag(uuid).length);
        }
    }

    @Test void databaseFailureNeverCreatesOrSavesAnEmptyReplacement() throws Exception {
        FishBagManager manager = manager();
        when(database.getConnection()).thenThrow(new SQLException("offline"));
        manager.loadBag(uuid);
        jobs.removeFirst().run();
        manager.updateBag(uuid, new ItemStack[450]);
        manager.unloadBag(uuid);
        assertNull(manager.getBag(uuid));
        assertTrue(jobs.isEmpty());
        assertTrue(callbacks.isEmpty());
    }

    @Test void corruptStoredSizeDoesNotBecomeASavableEmptyBag() throws Exception {
        FishBagManager manager = manager();
        Connection conn = database.getConnection();
        PreparedStatement stmt = conn.prepareStatement("unused");
        ResultSet rs = stmt.executeQuery();
        when(rs.next()).thenReturn(true);
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (var output = new java.io.ObjectOutputStream(bytes)) { output.writeInt(Integer.MAX_VALUE); }
        when(rs.getString("inventory_data")).thenReturn(Base64.getEncoder().encodeToString(bytes.toByteArray()));
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            manager.loadBag(uuid);
            jobs.removeFirst().run();
            callbacks.removeFirst().run();
            manager.unloadBag(uuid);
            assertNull(manager.getBag(uuid));
            assertTrue(jobs.isEmpty());
        }
    }

    @Test void sortingPreservesFishAndLockedPagesAndSavesTheResult() throws Exception {
        when(plugin.getName()).thenReturn("CoreSuite");
        var manager = spy(manager());
        doNothing().when(manager).saveBag(uuid);
        var module = mock(CustomFishingModule.class);
        var config = new org.bukkit.configuration.file.YamlConfiguration();
        config.set("species.tuna.rarity", "Rare");
        config.set("species.carp.rarity", "Common");
        when(module.getConfig()).thenReturn(config);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            manager.loadBag(uuid); jobs.removeFirst().run(); callbacks.removeFirst().run();
            var heavy = fish("carp", 10); var rare = fish("tuna", 2); var invalid = fish("carp", Double.NaN);
            var locked = fish("tuna", 99);
            var bag = manager.getBag(uuid);
            bag[3]=heavy; bag[8]=rare; bag[20]=invalid; bag[90]=locked;
            manager.sortBag(uuid, module, FishBagManager.SortMode.RARITY, 1);
            assertSame(rare, manager.getBag(uuid)[0]); assertSame(locked, manager.getBag(uuid)[90]);
            manager.sortBag(uuid, module, FishBagManager.SortMode.WEIGHT, 1);
            assertSame(heavy, manager.getBag(uuid)[0]); assertSame(invalid, manager.getBag(uuid)[2]);
            manager.sortBag(uuid, module, FishBagManager.SortMode.SPECIES, 1);
            assertSame(heavy, manager.getBag(uuid)[0]); assertSame(invalid, manager.getBag(uuid)[1]);
            assertSame(rare, manager.getBag(uuid)[2]); assertSame(locked, manager.getBag(uuid)[90]);
            assertEquals(4, Arrays.stream(manager.getBag(uuid)).filter(Objects::nonNull).count());
            verify(manager,times(3)).saveBag(uuid);
        }
    }

    private ItemStack fish(String species, double weight) {
        var item=mock(ItemStack.class); when(item.getType()).thenReturn(mock(org.bukkit.Material.class));
        var meta=mock(org.bukkit.inventory.meta.ItemMeta.class);
        var data=mock(org.bukkit.persistence.PersistentDataContainer.class);
        when(item.getItemMeta()).thenReturn(meta); when(meta.getPersistentDataContainer()).thenReturn(data);
        when(data.get(new org.bukkit.NamespacedKey(plugin,"fish_species"),org.bukkit.persistence.PersistentDataType.STRING)).thenReturn(species);
        when(data.get(new org.bukkit.NamespacedKey(plugin,"fish_weight"),org.bukkit.persistence.PersistentDataType.DOUBLE)).thenReturn(weight);
        return item;
    }

    @Test void pagingKeepsTheSameWindowAndFlushesItemsBeforeChangingPage() throws Exception {
        var manager=spy(manager()); doNothing().when(manager).saveBag(uuid);
        var player=mock(org.bukkit.entity.Player.class); when(player.getUniqueId()).thenReturn(uuid);
        var view=mock(org.bukkit.inventory.InventoryView.class); when(player.getOpenInventory()).thenReturn(view);
        var inventory=mock(org.bukkit.inventory.Inventory.class); when(view.getTopInventory()).thenReturn(inventory);
        try (var bukkit=mockStatic(Bukkit.class);
             var stacks=mockConstruction(ItemStack.class, (item, context) -> {
                 when(item.getItemMeta()).thenReturn(mock(org.bukkit.inventory.meta.ItemMeta.class));
                 when(item.getType()).thenReturn(mock(org.bukkit.Material.class));
             })) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            manager.loadBag(uuid); jobs.removeFirst().run(); callbacks.removeFirst().run();
            var constructor=FishBagManager.BagHolder.class.getDeclaredConstructor(FishBagManager.class,UUID.class,int.class);
            constructor.setAccessible(true);
            var holder=constructor.newInstance(manager,uuid,0);
            var field=FishBagManager.BagHolder.class.getDeclaredField("inventory"); field.setAccessible(true);field.set(holder,inventory);
            when(inventory.getHolder()).thenReturn(holder);
            ItemStack[] visible=new ItemStack[54]; var fish=mock(ItemStack.class);
            when(fish.getType()).thenReturn(mock(org.bukkit.Material.class)); visible[5]=fish;
            when(inventory.getItem(anyInt())).thenAnswer(call->visible[call.getArgument(0,Integer.class)]);
            doAnswer(call->{visible[call.getArgument(0,Integer.class)]=call.getArgument(1);return null;}).when(inventory).setItem(anyInt(),any());
            doAnswer(call->{Arrays.fill(visible,null);return null;}).when(inventory).clear();
            manager.openBag(player,3,1); assertEquals(1,holder.page); assertSame(fish,manager.getBag(uuid)[5]);
            manager.openBag(player,3,2); assertEquals(2,holder.page);
            manager.openBag(player,3,0); assertSame(fish,visible[5]);
            verify(player,never()).closeInventory();
            verify(player,never()).openInventory(any(org.bukkit.inventory.Inventory.class));
        }
    }
}
