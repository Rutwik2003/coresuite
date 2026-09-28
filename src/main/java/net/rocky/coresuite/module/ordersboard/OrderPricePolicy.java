package net.rocky.coresuite.module.ordersboard;

import org.bukkit.configuration.file.FileConfiguration;

public final class OrderPricePolicy {
    private OrderPricePolicy() {}
    public static boolean valid(FileConfiguration config, double price) {
        double minimum = config.getDouble("min-order-value", 1000);
        double maximum = config.getDouble("max-order-value", 0);
        double fee = config.getDouble("posting-fee-percent", 5);
        return Double.isFinite(price) && price > 0 && Double.isFinite(minimum) && price >= minimum
                && Double.isFinite(maximum) && maximum >= 0 && (maximum == 0 || price <= maximum)
                && Double.isFinite(fee) && fee >= 0 && Double.isFinite(price + price * fee / 100.0);
    }
    public static boolean upgrade(FileConfiguration config) {
        if (config.getBoolean("unlimited-price-upgraded", false)) return false;
        if (config.getDouble("max-order-value", 0) == 500000) config.set("max-order-value", 0);
        config.set("unlimited-price-upgraded", true);
        return true;
    }
}
