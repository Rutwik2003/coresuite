package net.rocky.coresuite.module.worldbosses.combat;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.bukkit.util.VoxelShape;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BossMovementTest {
    World world;
    Location feet;
    Map<String, Block> blocks;

    @BeforeEach void setup() {
        world = mock(World.class);
        WorldBorder border = mock(WorldBorder.class);
        when(world.getWorldBorder()).thenReturn(border);
        when(border.isInside(any())).thenReturn(true);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        blocks = new HashMap<>();
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call -> {
            int x = call.getArgument(0), y = call.getArgument(1), z = call.getArgument(2);
            return blocks.computeIfAbsent(x + ":" + y + ":" + z,
                    key -> block(x, y, z, y == 63 ? Material.STONE : Material.AIR, y == 63 ? 1 : 0));
        });
        feet = new Location(world, .5, 64, .5);
        when(world.getBlockAt(any(Location.class))).thenAnswer(call -> {
            Location location = call.getArgument(0);
            return world.getBlockAt(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        });
    }

    Block block(int x, int y, int z, Material material, double height) {
        Block block = mock(Block.class);
        when(block.getX()).thenReturn(x); when(block.getY()).thenReturn(y); when(block.getZ()).thenReturn(z);
        when(block.getType()).thenReturn(material);
        VoxelShape shape = mock(VoxelShape.class);
        when(shape.getBoundingBoxes()).thenReturn(height == 0 ? List.of() : List.of(new BoundingBox(0, 0, 0, 1, height, 1)));
        when(block.getCollisionShape()).thenReturn(shape);
        return block;
    }

    @Test void safeFloorAndObstructedHead() {
        assertTrue(BossMovement.standing(feet));
        blocks.put("0:65:0", block(0, 65, 0, Material.STONE, 1));
        assertFalse(BossMovement.clear(feet));
        Location escape = BossMovement.findLanding(feet, loc -> loc.distanceSquared(feet) < 16);
        assertNotNull(escape);
        assertTrue(BossMovement.standing(escape));
    }

    @Test void fenceExtendingIntoFeetIsRejected() {
        blocks.put("0:63:0", block(0, 63, 0, Material.OAK_FENCE, 1.5));
        assertFalse(BossMovement.clear(feet));
    }

    @Test void slabsSupportAValidLanding() {
        blocks.put("0:63:0", block(0, 63, 0, Material.STONE_SLAB, .5));
        assertTrue(BossMovement.standing(feet.clone().subtract(0, .5, 0)));
    }

    @Test void neverLoadsMissingChunksAndRejectsHazards() {
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        assertFalse(BossMovement.clear(feet));
        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        blocks.put("0:63:0", block(0, 63, 0, Material.MAGMA_BLOCK, 1));
        assertFalse(BossMovement.standing(feet));
    }

    @Test void pushChecksTheWholePathThroughAWall() {
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(feet);
        blocks.put("1:64:0", block(1, 64, 0, Material.STONE, 1));
        BossMovement.push(player, new Vector(1.2, .4, 0));
        verify(player).setVelocity(new Vector());
    }

    @Test void cancelledTeleportDoesNotApplyFollowupVelocityOrFallReset() {
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(feet);
        when(player.teleport(any(Location.class))).thenReturn(false);
        assertFalse(BossMovement.teleport(player, feet, loc -> true));
        verify(player, never()).setVelocity(any());
        verify(player, never()).setFallDistance(anyFloat());
    }

    @Test void forbiddenArenaOrInvalidVelocityCannotForceMovement() {
        assertNull(BossMovement.findLanding(feet, loc -> false));
        Player player = mock(Player.class);
        BossMovement.push(player, new Vector(Double.NaN, 0, 0));
        verifyNoInteractions(player);
    }

    @Test void spawnNeedsConnectedWalkingSpace() {
        assertNull(net.rocky.coresuite.module.worldbosses.scheduler.BossSpawnScheduler.arenaAccessProblem(feet));
        for (int x = -7; x <= 7; x++) for (int z = -7; z <= 7; z++) {
            boolean platform = Math.abs(x) <= 1 && Math.abs(z) <= 1;
            blocks.put(x + ":63:" + z, block(x, 63, z, platform ? Material.STONE : Material.AIR, platform ? 1 : 0));
        }
        assertNotNull(net.rocky.coresuite.module.worldbosses.scheduler.BossSpawnScheduler.arenaAccessProblem(feet));
    }
}
