package net.rocky.coresuite.module.worldbosses.scheduler;
import org.junit.jupiter.api.Test;
import org.bukkit.*;
import org.bukkit.block.Block;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class BossSpawnSchedulerTest {
    private Material material(String name, boolean air, boolean solid) {
        Material material = mock(Material.class);
        when(material.name()).thenReturn(name); when(material.isAir()).thenReturn(air); when(material.isSolid()).thenReturn(solid);
        return material;
    }
    private World ground() {
        World world = mock(World.class); WorldBorder border = mock(WorldBorder.class);
        when(world.getMinHeight()).thenReturn(-64); when(world.getMaxHeight()).thenReturn(320);
        when(world.getWorldBorder()).thenReturn(border); when(border.isInside(any())).thenReturn(true);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        Block air = mock(Block.class), stone = mock(Block.class);
        Material airType = material("AIR", true, false), stoneType = material("STONE", false, true);
        when(air.getType()).thenReturn(airType); when(stone.getType()).thenReturn(stoneType);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(i -> i.getArgument(1, Integer.class) <= 64 ? stone : air);
        return world;
    }
    @Test void feetMustBeAboveTheHighestGroundBlock() {
        World world = ground();
        assertFalse(BossSpawnScheduler.safeAt(world, 0, 64, 0));
        assertTrue(BossSpawnScheduler.safeAt(world, 0, 65, 0));
        assertFalse(BossSpawnScheduler.safeAt(world, 0, 66, 0));
    }
    @Test void cannotUseUnloadedChunksOrOutsideBorder() {
        World world = ground(); when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        assertFalse(BossSpawnScheduler.safeAt(world, 0, 65, 0));
        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
        world = ground(); when(world.getWorldBorder().isInside(any())).thenReturn(false);
        assertFalse(BossSpawnScheduler.safeAt(world, 0, 65, 0));
    }
    @Test void rejectsObstructedHeadroomAndWorldHeightLimit() {
        World world = ground(); Block wall = mock(Block.class); Material stoneType = material("STONE", false, true); when(wall.getType()).thenReturn(stoneType);
        when(world.getBlockAt(1, 68, 1)).thenReturn(wall);
        assertFalse(BossSpawnScheduler.safeAt(world, 0, 65, 0));
        assertFalse(BossSpawnScheduler.safeAt(world, 0, 319, 0));
    }
}
