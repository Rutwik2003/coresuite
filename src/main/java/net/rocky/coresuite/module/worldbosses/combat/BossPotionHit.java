package net.rocky.coresuite.module.worldbosses.combat;

import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

public final class BossPotionHit {
    private BossPotionHit() {}
    public static boolean waterReaches(Location splash, LivingEntity boss, boolean directHit) {
        if (splash == null || splash.getWorld() == null || !splash.getWorld().equals(boss.getWorld())) return false;
        var box = boss.getBoundingBox();
        Vector nearest = new Vector(Math.max(box.getMinX(), Math.min(box.getMaxX(), splash.getX())),
                Math.max(box.getMinY(), Math.min(box.getMaxY(), splash.getY())),
                Math.max(box.getMinZ(), Math.min(box.getMaxZ(), splash.getZ())));
        if (nearest.distanceSquared(splash.toVector()) > 16) return false;
        if (directHit) return true;
        Location start = splash.clone().add(0, .1, 0);
        Vector toward = box.getCenter().subtract(start.toVector());
        double distance = toward.length();
        return distance < .01 || splash.getWorld().rayTraceBlocks(start, toward.normalize(), distance,
                FluidCollisionMode.NEVER, true) == null;
    }
}
