package net.rocky.coresuite.module.worldbosses.rewards;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.List;
import java.util.regex.Pattern;

/** Optional, read-first adapter for ExcellentCrates key rewards; no command alias dependency. */
final class ExcellentCratesRewardBridge {
    private static final Pattern GIVE = Pattern.compile(
            "(?i)^(?:excellentcrates:)?(?:crate|crates|excellentcrates)\\s+key\\s+give\\s+(\\S+)\\s+(\\S+)(?:\\s+([0-9]+))?$",
            Pattern.CASE_INSENSITIVE);

    record KeyGrant(String key, int amount) {}
    @FunctionalInterface interface Delivery { boolean deliver() throws ReflectiveOperationException; }

    static KeyGrant parse(String command, String playerName) {
        var match = GIVE.matcher(command.strip());
        if (!match.matches() || !match.group(1).equalsIgnoreCase(playerName)) return null;
        int amount = match.group(3) == null ? 1 : Integer.parseInt(match.group(3));
        if (amount < 1 || amount > 2304) throw new IllegalArgumentException("Key amount must be between 1 and 2304");
        return new KeyGrant(match.group(2), amount);
    }

    /** Resolves every method and validates the key before any inventory/balance mutation. */
    static Delivery prepare(Object provider, Player player, KeyGrant grant) throws ReflectiveOperationException {
        Object manager = provider.getClass().getMethod("getKeyManager").invoke(provider);
        Object key = manager.getClass().getMethod("getKeyById", String.class).invoke(manager, grant.key());
        if (key == null) throw new IllegalArgumentException("ExcellentCrates has no key ID '" + grant.key() + "'");
        boolean virtual = (boolean) key.getClass().getMethod("isVirtual").invoke(key);
        if (!virtual) {
            ItemStack item = ((ItemStack) key.getClass().getMethod("getItemStack").invoke(key)).clone();
            if (item.getType().isAir()) throw new IllegalArgumentException("ExcellentCrates returned an empty key item");
            item.setAmount(grant.amount());
            ItemStack[] plan = BossRewardMailbox.fit(player.getInventory().getStorageContents(), List.of(item), player.getInventory().getMaxStackSize());
            if (plan == null) return () -> false;
            return () -> {
                player.getInventory().setStorageContents(plan);
                player.saveData();
                return true;
            };
        }
        Method count = manager.getClass().getMethod("getKeysAmount", Player.class, key.getClass());
        Method give = manager.getClass().getMethod("giveKey", Player.class, key.getClass(), int.class);
        int before = ((Number) count.invoke(manager, player, key)).intValue();
        int expected = Math.addExact(before, grant.amount());
        return () -> {
            give.invoke(manager, player, key, grant.amount());
            int after = ((Number) count.invoke(manager, player, key)).intValue();
            if (after != expected) throw new IllegalStateException("ExcellentCrates key balance did not match expected delivery");
            return true;
        };
    }

    private ExcellentCratesRewardBridge() {}
}
