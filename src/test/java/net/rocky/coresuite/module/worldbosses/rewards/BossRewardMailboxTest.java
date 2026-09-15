package net.rocky.coresuite.module.worldbosses.rewards;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class BossRewardMailboxTest {
    @TempDir Path directory;
    private ItemStack stack(int amount) {
        ItemStack item = mock(ItemStack.class);
        AtomicInteger count = new AtomicInteger(amount);
        when(item.getAmount()).thenAnswer(i -> count.get());
        Material diamond = mock(Material.class);
        when(diamond.isAir()).thenReturn(false);
        when(item.getType()).thenReturn(diamond);
        when(item.getMaxStackSize()).thenReturn(64);
        when(item.isSimilar(any())).thenReturn(true);
        doAnswer(i -> { count.set(i.getArgument(0)); return null; }).when(item).setAmount(anyInt());
        when(item.clone()).thenAnswer(i -> stack(count.get()));
        return item;
    }
    @Test void fullInventoryDoesNotPartiallyInsertReward() {
        ItemStack[] original = {stack(63)};
        assertNull(BossRewardMailbox.fit(original, List.of(stack(2)), 64));
        assertEquals(63, original[0].getAmount());
    }
    @Test void fillsPartialStacksAndSplitsOverflowWithoutMutatingInputs() {
        ItemStack[] original = {stack(63), null, null};
        ItemStack reward = stack(70);
        ItemStack[] fitted = BossRewardMailbox.fit(original, List.of(reward), 64);
        assertNotNull(fitted);
        assertEquals(64, fitted[0].getAmount());
        assertEquals(64, fitted[1].getAmount());
        assertEquals(5, fitted[2].getAmount());
        assertEquals(63, original[0].getAmount()); assertEquals(70, reward.getAmount());
    }
    @Test void reservationSurvivesReopenAndCannotBeClaimedTwice() throws Exception {
        String url = "jdbc:sqlite:" + directory.resolve("mailbox.db");
        try (var conn = DriverManager.getConnection(url); var stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE coresuite_boss_rewards (reward_id TEXT PRIMARY KEY, state INTEGER)");
            stmt.execute("INSERT INTO coresuite_boss_rewards VALUES ('receipt', 0)");
            assertTrue(BossRewardMailbox.reserve(conn, "receipt"));
        }
        try (var conn = DriverManager.getConnection(url)) {
            assertFalse(BossRewardMailbox.reserve(conn, "receipt"));
            assertFalse(BossRewardMailbox.reserve(conn, "missing"));
            try (var stmt = conn.createStatement()) { stmt.execute("UPDATE coresuite_boss_rewards SET state=0 WHERE reward_id='receipt'"); }
            assertTrue(BossRewardMailbox.reserve(conn, "receipt"));
        }
    }
}
