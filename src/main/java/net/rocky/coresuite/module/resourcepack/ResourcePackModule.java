package net.rocky.coresuite.module.resourcepack;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.module.Module;
import net.rocky.coresuite.common.integration.AuthenticationGate;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ResourcePackModule extends Module implements Listener {
    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("meermc:server-pack".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private final Map<UUID, String> offered = new HashMap<>();
    private volatile long generation;
    private PackManifest current;
    private boolean required, updateOnline;
    private String prompt;

    public ResourcePackModule(CoreSuitePlugin plugin) { super(plugin, "Resource Pack", "resource-pack"); }

    @Override public void onEnable() {
        long run = ++generation;
        if (!Bukkit.getResourcePack().isBlank()) throw new IllegalStateException(
                "Clear resource-pack and resource-pack-sha1 in server.properties, then restart before enabling plugin pack delivery.");
        String repository = config.getString("repository", "").trim();
        if (!repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) throw new IllegalArgumentException("Set resource-pack.repository to owner/repository");
        required = config.getBoolean("required", false);
        updateOnline = config.getBoolean("update-online-players", false);
        prompt = config.getString("prompt", "Download MeerMC music and textures?");
        registerListener(this);
        Path cache = getDataFolder().toPath().resolve("last-verified.properties");
        try { if (Files.exists(cache)) current = PackManifest.parse(Files.readAllBytes(cache), repository); }
        catch (Exception error) { warn("Ignoring invalid cached pack manifest."); }
        AtomicBoolean checking = new AtomicBoolean();
        long period = Math.max(60, Math.min(86400, config.getLong("check-interval-seconds", 300))) * 20;
        registerTask(Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!checking.compareAndSet(false, true)) return;
            try {
                byte[] manifest = PackManifest.download(URI.create("https://github.com/" + repository + "/releases/latest/download/manifest.properties"), 16384);
                PackManifest candidate = PackManifest.parse(manifest, repository);
                // The current value is only changed on the main thread; read via a volatile snapshot.
                if (!candidate.equals(verifiedSnapshot)) {
                    candidate.verify(PackManifest.download(candidate.url(), candidate.size()));
                    if (generation != run) return;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (generation != run || !isEnabled()) return;
                        try {
                            Path temporary = cache.resolveSibling("last-verified.properties.tmp");
                            Files.write(temporary, manifest);
                            try { Files.move(temporary, cache, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, cache, StandardCopyOption.REPLACE_EXISTING); }
                        } catch (Exception error) { warn("Could not persist verified pack; keeping it for this session."); }
                        boolean first = current == null;
                        current = candidate; verifiedSnapshot = candidate;
                        log("Verified resource pack " + candidate.version());
                        if (first || updateOnline) for (Player player : Bukkit.getOnlinePlayers()) offer(player);
                    });
                }
            } catch (Exception error) {
                if (generation == run) warn("Pack update unavailable; keeping the last verified release. " + error.getClass().getSimpleName() + ": " + error.getMessage());
            } finally { checking.set(false); }
        }, 1, period));
        for (Player player : Bukkit.getOnlinePlayers()) offer(player);
    }

    private volatile PackManifest verifiedSnapshot;

    @Override public void onDisable() {
        generation++; current = null; verifiedSnapshot = null; offered.clear();
        // Do not remove an already accepted pack during a reload or shutdown.
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        long run = generation;
        AuthenticationGate.afterLogin(plugin, event.getPlayer(), () -> {
            if (generation == run && isEnabled()) offer(event.getPlayer());
        });
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) { offered.remove(event.getPlayer().getUniqueId()); }

    private void offer(Player player) {
        if (current == null || !player.isOnline() || !AuthenticationGate.isAuthenticated(player)) return;
        if (current.sha1().equals(offered.get(player.getUniqueId()))) return;
        // Java resource packs are not Bedrock packs. Floodgate players must use Geyser's pack system.
        if (Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
            try {
                Class<?> api = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                Object instance = api.getMethod("getInstance").invoke(null);
                if (Boolean.TRUE.equals(api.getMethod("isFloodgatePlayer", UUID.class).invoke(instance, player.getUniqueId()))) return;
            } catch (ReflectiveOperationException | LinkageError error) { warn("Cannot verify Floodgate player; skipping Java pack offer."); return; }
        }
        ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(PACK_ID, current.url(), current.sha1());
        player.sendResourcePacks(ResourcePackRequest.resourcePackRequest().packs(info)
                .replace(false).required(required).prompt(Component.text(prompt)).build());
        offered.put(player.getUniqueId(), current.sha1());
    }
}
