package net.rocky.coresuite.module.worldbosses.combat;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BossPotionHitTest {
    @Test void waterUsesPhysicalSplashRangeAndDoesNotPassThroughWalls() {
        World world = mock(World.class); LivingEntity boss = mock(LivingEntity.class);
        when(boss.getWorld()).thenReturn(world);
        when(boss.getBoundingBox()).thenReturn(new BoundingBox(-.5, 64, -.5, .5, 67, .5));
        assertTrue(BossPotionHit.waterReaches(new Location(world, 2, 64, 0), boss, false));
        assertFalse(BossPotionHit.waterReaches(new Location(world, 5, 64, 0), boss, false));
        assertFalse(BossPotionHit.waterReaches(new Location(mock(World.class), 0, 64, 0), boss, true));
        when(world.rayTraceBlocks(any(), any(), anyDouble(), eq(FluidCollisionMode.NEVER), eq(true)))
                .thenReturn(new RayTraceResult(new Vector(1, 64, 0)));
        assertFalse(BossPotionHit.waterReaches(new Location(world, 2, 64, 0), boss, false));
        assertTrue(BossPotionHit.waterReaches(new Location(world, .5, 65, 0), boss, true));
    }
}
