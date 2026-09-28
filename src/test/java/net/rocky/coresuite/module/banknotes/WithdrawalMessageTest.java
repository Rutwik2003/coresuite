package net.rocky.coresuite.module.banknotes;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.economy.EconomyManager;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.Module;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class WithdrawalMessageTest {
    @Test void confirmationShowsCurrentMoneyOrCalculatedXpWithoutMutatingFunds() throws Exception {
        var core = mock(CoreSuitePlugin.class); when(core.getName()).thenReturn("CoreSuite");
        var economy = mock(EconomyManager.class); when(core.getEconomyManager()).thenReturn(economy);
        var player = mock(Player.class); when(economy.getBalance(player)).thenReturn(12345.0);
        when(player.getLevel()).thenReturn(10); when(player.getExp()).thenReturn(0f);
        var module = new BankNotesModule(core);
        var config = new YamlConfiguration(); config.load(new java.io.File("src/main/resources/modules/bank-notes.yml"));
        var field = Module.class.getDeclaredField("config"); field.setAccessible(true); field.set(module,config);
        var message = BankNotesModule.class.getDeclaredMethod("sendWithdrawalConfirmation",Player.class,double.class,boolean.class);
        message.setAccessible(true);
        try (var text = mockStatic(TextUtil.class)) {
            text.when(() -> TextUtil.formatNumber(anyDouble())).thenCallRealMethod();
            message.invoke(module,player,1000d,false);
            text.verify(() -> TextUtil.sendMessage(eq(player),contains("12,345")));
            text.verify(() -> TextUtil.sendMessage(eq(player),contains("1,000")));
            message.invoke(module,player,500d,true);
            text.verify(() -> TextUtil.sendMessage(player,"&7Withdrawn: &a500 XP points"));
            text.verify(() -> TextUtil.sendMessage(player,"&7Remaining experience: &e160 XP points"));
        }
        verify(economy,never()).withdraw(any(),anyDouble());
        verify(player,never()).giveExp(anyInt());
    }
}
