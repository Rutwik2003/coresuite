package net.rocky.coresuite.module.resourcepack;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.integration.AuthenticationGate;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResourcePackDeliveryTest {
    @Test void sendsRequiredRequestOnceAndReconnectCanTryAgain() throws Exception {
        var m=module(true);var p=player();
        set(m,"current",new PackManifest("v1",java.net.URI.create("https://github.com/owner/repo/releases/download/v1/pack.zip"),"a".repeat(40),100));
        var offer=ResourcePackModule.class.getDeclaredMethod("offer",Player.class);offer.setAccessible(true);
        var manager=mock(org.bukkit.plugin.PluginManager.class);
        try(var bukkit=mockStatic(Bukkit.class);var auth=mockStatic(AuthenticationGate.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
            auth.when(()->AuthenticationGate.isAuthenticated(p)).thenReturn(true);
            offer.invoke(m,p);offer.invoke(m,p);
            var request=org.mockito.ArgumentCaptor.forClass(ResourcePackRequest.class);
            verify(p).sendResourcePacks(request.capture());assertTrue(request.getValue().required());assertFalse(request.getValue().replace());
            var quit=mock(PlayerQuitEvent.class);when(quit.getPlayer()).thenReturn(p);m.onQuit(quit);
            offer.invoke(m,p);verify(p,times(2)).sendResourcePacks(any(ResourcePackRequest.class));
        }
    }
    @SuppressWarnings("unchecked")
    private Map<UUID, ResourcePackModule.Delivery> deliveries(ResourcePackModule module) throws Exception {
        Field f = ResourcePackModule.class.getDeclaredField("deliveries"); f.setAccessible(true);
        return (Map<UUID, ResourcePackModule.Delivery>) f.get(module);
    }
    private void set(ResourcePackModule m, String name, Object value) throws Exception {
        Field f = ResourcePackModule.class.getDeclaredField(name); f.setAccessible(true); f.set(m, value);
    }
    private ResourcePackModule module(boolean required) throws Exception {
        CoreSuitePlugin plugin = mock(CoreSuitePlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        ResourcePackModule module = new ResourcePackModule(plugin); set(module,"required",required);
        set(module,"prompt","Accept MeerMC's resource pack to play.");return module;
    }
    private Player player() {
        Player p=mock(Player.class);when(p.getUniqueId()).thenReturn(UUID.randomUUID());when(p.getName()).thenReturn("Test");when(p.isOnline()).thenReturn(true);return p;
    }
    private ResourcePackModule.Delivery pending(ResourcePackModule m, Player p) throws Exception {
        var d=new ResourcePackModule.Delivery();d.packId=UUID.randomUUID();d.hash="test";deliveries(m).put(p.getUniqueId(),d);return d;
    }
    private void status(ResourcePackModule m,Player p,UUID id,PlayerResourcePackStatusEvent.Status status) {
        var event=mock(PlayerResourcePackStatusEvent.class);when(event.getPlayer()).thenReturn(p);when(event.getID()).thenReturn(id);when(event.getStatus()).thenReturn(status);m.onPackStatus(event);
    }
    @Test void acceptanceAndDownloadAreNotSuccessfulApplication() throws Exception {
        var m=module(true);var p=player();var d=pending(m,p);
        status(m,p,d.packId,PlayerResourcePackStatusEvent.Status.ACCEPTED);
        status(m,p,d.packId,PlayerResourcePackStatusEvent.Status.DOWNLOADED);
        assertFalse(d.finished);assertFalse(d.loaded);
        status(m,p,d.packId,PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED);
        assertTrue(d.finished);assertTrue(d.loaded);verify(p,never()).kick(any(Component.class));
    }
    @Test void allFailureStatusesDisconnectRequiredPlayersOnce() throws Exception {
        for(var s: new PlayerResourcePackStatusEvent.Status[]{PlayerResourcePackStatusEvent.Status.DECLINED,PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD,PlayerResourcePackStatusEvent.Status.FAILED_RELOAD,PlayerResourcePackStatusEvent.Status.INVALID_URL,PlayerResourcePackStatusEvent.Status.DISCARDED}) {
            var m=module(true);var p=player();var d=pending(m,p);status(m,p,d.packId,s);status(m,p,d.packId,s);
            assertTrue(d.finished);assertFalse(d.loaded);verify(p,times(1)).kick(any(Component.class));
        }
    }
    @Test void unrelatedAndPreviousPackStatusesAreIgnored() throws Exception {
        var m=module(true);var p=player();var d=pending(m,p);
        status(m,p,UUID.randomUUID(),PlayerResourcePackStatusEvent.Status.DECLINED);
        status(m,p,UUID.randomUUID(),PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED);
        assertFalse(d.finished);verify(p,never()).kick(any(Component.class));
    }
    @Test void optionalModeOnlyWarnsAndQuitAndDisableClearState() throws Exception {
        var m=module(false);var p=player();var d=pending(m,p);
        status(m,p,d.packId,PlayerResourcePackStatusEvent.Status.DECLINED);
        verify(p,never()).kick(any(Component.class));verify(p).sendMessage(any(Component.class));
        var quit=mock(PlayerQuitEvent.class);when(quit.getPlayer()).thenReturn(p);m.onQuit(quit);assertTrue(deliveries(m).isEmpty());
        pending(m,p);m.onDisable();assertTrue(deliveries(m).isEmpty());
    }
    @Test void unansweredRequestTimesOutButLoadedPlayerStays() throws Exception {
        var m=module(true);var p=player();var d=pending(m,p);set(m,"loadTimeoutNanos",0L);
        var check=ResourcePackModule.class.getDeclaredMethod("checkDeliveries");check.setAccessible(true);
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.getPlayer(p.getUniqueId())).thenReturn(p);
            check.invoke(m);verify(p).kick(any(Component.class));
            clearInvocations(p);d=pending(m,p);status(m,p,d.packId,PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED);
            check.invoke(m);verify(p,never()).kick(any(Component.class));
        }
    }
}
