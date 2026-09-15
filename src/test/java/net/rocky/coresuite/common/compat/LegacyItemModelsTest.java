package net.rocky.coresuite.common.compat;
import org.junit.jupiter.api.Test;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class LegacyItemModelsTest {
    @Test void stableIdMatchesPackAndUnknownModelsPreserveMetadata() {
        assertEquals(1000000, LegacyItemModels.id("barely_default:fish_alexpaima"));
        ItemMeta meta = mock(ItemMeta.class);
        LegacyItemModels.apply(meta, NamespacedKey.fromString("barely_default:fish_alexpaima"));
        verify(meta).setCustomModelData(1000000);
        clearInvocations(meta);
        LegacyItemModels.apply(meta, NamespacedKey.fromString("another_pack:custom_fish"));
        verifyNoInteractions(meta);
    }
}
