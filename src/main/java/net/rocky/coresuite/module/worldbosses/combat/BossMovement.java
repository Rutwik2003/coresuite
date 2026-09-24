package net.rocky.coresuite.module.worldbosses.combat;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.function.Predicate;

/** Loaded-chunk-only collision checks for forced movement. Call on the server thread. */
public final class BossMovement {
    private BossMovement() {}

    public static boolean clear(Location feet) {
        return clear(feet, true);
    }

    public static boolean embedded(Location feet) {
        return !clear(feet, false);
    }

    public static boolean embedded(Player player) {
        return !clear(player.getLocation(), false, Math.max(.1, player.getWidth() / 2 - .01),
                Math.max(.1, player.getHeight() - .02));
    }

    private static boolean clear(Location feet, boolean avoidHazards) {
        return clear(feet, avoidHazards, .31, 1.81);
    }

    private static boolean clear(Location feet, boolean avoidHazards, double halfWidth, double height) {
        World world = feet.getWorld();
        if (world == null || !Double.isFinite(feet.getX()) || !Double.isFinite(feet.getY())
                || !Double.isFinite(feet.getZ()) || feet.getY() <= world.getMinHeight()
                || feet.getY() + 1.8 >= world.getMaxHeight()) return false;
        BoundingBox body = new BoundingBox(feet.getX() - halfWidth, feet.getY() + .01, feet.getZ() - halfWidth,
                feet.getX() + halfWidth, feet.getY() + height, feet.getZ() + halfWidth);
        for (int x = (int) Math.floor(body.getMinX()); x <= Math.floor(body.getMaxX()); x++) {
            for (int z = (int) Math.floor(body.getMinZ()); z <= Math.floor(body.getMaxZ()); z++) {
                if (!world.isChunkLoaded(x >> 4, z >> 4)
                        || !world.getWorldBorder().isInside(new Location(world, x + .5, feet.getY(), z + .5))) return false;
                // Fences/walls can extend above the block beneath the player's feet.
                for (int y = feet.getBlockY() - 1; y <= Math.floor(body.getMaxY()); y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (avoidHazards && y >= feet.getBlockY() && hazardous(block)) return false;
                    for (BoundingBox shape : block.getCollisionShape().getBoundingBoxes()) {
                        if (body.overlaps(shape.clone().shift(x, y, z))) return false;
                    }
                }
            }
        }
        return true;
    }

    public static boolean standing(Location feet) {
        if (!clear(feet)) return false;
        Block floor = feet.clone().subtract(0, .05, 0).getBlock();
        if (hazardous(floor)) return false;
        double x = feet.getX(), z = feet.getZ();
        for (BoundingBox shape : floor.getCollisionShape().getBoundingBoxes()) {
            BoundingBox box = shape.clone().shift(floor.getX(), floor.getY(), floor.getZ());
            if (x >= box.getMinX() && x <= box.getMaxX() && z >= box.getMinZ() && z <= box.getMaxZ()
                    && Math.abs(box.getMaxY() - feet.getY()) < .06) return true;
        }
        return false;
    }

    private static boolean hazardous(Block block) {
        return switch (block.getType()) {
            case WATER, LAVA, FIRE, SOUL_FIRE, MAGMA_BLOCK, CAMPFIRE, SOUL_CAMPFIRE,
                    CACTUS, SWEET_BERRY_BUSH, POWDER_SNOW, COBWEB -> true;
            default -> false;
        };
    }

    public static Location findLanding(Location preferred, Predicate<Location> allowed) {
        if (allowed.test(preferred) && standing(preferred)) return preferred.clone();
        for (int radius = 0; radius <= 4; radius++) {
            for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
                if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
                for (int dy : new int[]{0, 1, -1, 2, -2, 3, -3}) {
                    Location at = new Location(preferred.getWorld(), preferred.getBlockX() + x + .5,
                            preferred.getBlockY() + dy, preferred.getBlockZ() + z + .5,
                            preferred.getYaw(), preferred.getPitch());
                    if (allowed.test(at) && standing(at)) return at;
                }
            }
        }
        return null;
    }

    public static boolean teleport(Player player, Location preferred, Predicate<Location> allowed) {
        Location landing = findLanding(preferred, at -> allowed.test(at)
                && clear(at, true, Math.max(.31, player.getWidth() / 2 + .01), Math.max(1.81, player.getHeight() + .01)));
        if (landing == null) return false;
        landing.setYaw(player.getLocation().getYaw());
        landing.setPitch(player.getLocation().getPitch());
        if (!player.teleport(landing)) return false;
        player.setVelocity(new Vector());
        player.setFallDistance(0);
        return true;
    }

    public static void push(Player player, Vector requested) {
        if (!Double.isFinite(requested.getX()) || !Double.isFinite(requested.getY())
                || !Double.isFinite(requested.getZ())) return;
        Vector velocity = requested.clone();
        if (velocity.lengthSquared() > 4) velocity.normalize().multiply(2);
        // Check the swept body, including several ticks of momentum, not just the destination.
        Location origin = player.getLocation();
        for (double step = .1; step <= 3; step += .1) {
            if (!clear(origin.clone().add(velocity.clone().multiply(step)))) {
                player.setVelocity(new Vector());
                return;
            }
        }
        player.setVelocity(velocity);
    }
}
