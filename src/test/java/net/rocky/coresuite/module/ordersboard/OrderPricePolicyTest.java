package net.rocky.coresuite.module.ordersboard;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OrderPricePolicyTest {
    @Test void removesOldCapOnceAndRejectsInvalidNumbersAndOverflow() {
        var config = new YamlConfiguration(); config.set("max-order-value", 500000);
        assertTrue(OrderPricePolicy.upgrade(config));
        assertEquals(0, config.getDouble("max-order-value"));
        assertTrue(OrderPricePolicy.valid(config, 25000000));
        for (double price : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1, 0, 999, Double.MAX_VALUE})
            assertFalse(OrderPricePolicy.valid(config, price));
        config.set("max-order-value", 500000);
        assertFalse(OrderPricePolicy.upgrade(config));
        assertFalse(OrderPricePolicy.valid(config, 500001));
    }
    @Test void preservesCustomLimit() {
        var config = new YamlConfiguration(); config.set("max-order-value", 7000000);
        OrderPricePolicy.upgrade(config);
        assertEquals(7000000, config.getDouble("max-order-value"));
        assertTrue(OrderPricePolicy.valid(config, 7000000));
        assertFalse(OrderPricePolicy.valid(config, 7000001));
    }
}
