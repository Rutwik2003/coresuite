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
    private static final UUID LEGACY_PACK_ID = UUID.nameUUIDFromBytes("meermc:server-pack".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private final Map<UUID, Delivery> deliveries = new HashMap<>();
    private long loadTimeoutNanos;
    static final class Delivery {
        final long started = System.nanoTime();
        String hash;
        UUID packId;
        boolean loaded, finished;
    }
    private volatile long generation;
    private PackManifest current;
    private boolean required, updateOnline;
    private String prompt;
    private GeyserPackBridge bedrock;

    public ResourcePackModule(CoreSuitePlugin plugin) { super(plugin, "Resource Pack", "resource-pack"); }

    @Override public void onEnable() {
        long run = ++generation;
        if (!Bukkit.getResourcePack().isBlank()) throw new IllegalStateException(
                "Clear resource-pack and resource-pack-sha1 in server.properties, then restart before enabling plugin pack delivery.");
        String repository = config.getString("repository", "").trim();
        if (!repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) throw new IllegalArgumentException("Set resource-pack.repository to owner/repository");
        required = config.getBoolean("required", true);
        loadTimeoutNanos = Math.max(30, Math.min(900, config.getLong("load-timeout-seconds", 180))) * 1_000_000_000L;
        updateOnline = config.getBoolean("update-online-players", false);
        prompt = config.getString("prompt", "Download MeerMC music and textures?");
        registerListener(this);
        registerTask(Bukkit.getScheduler().runTaskTimer(plugin, this::checkDeliveries, 20L, 20L));
        Path cache = getDataFolder().toPath().resolve("last-verified.properties");
        try { if (Files.exists(cache)) current = PackManifest.parse(Files.readAllBytes(cache), repository); }
        catch (Exception error) { warn("Ignoring invalid cached pack manifest."); }
        AtomicBoolean checking = new AtomicBoolean();
        long period = Math.max(60, Math.min(86400, config.getLong("check-interval-seconds", 300))) * 20;
        if (config.getBoolean("bedrock-audio.enabled", true)) {
            try { bedrock = GeyserPackBridge.start(plugin, getDataFolder().toPath().resolve("bedrock-audio"), repository, period); }
            catch (Exception | LinkageError error) { warn("Geyser audio bridge unavailable; Java delivery remains enabled. " + error.getClass().getSimpleName()); }
        }
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
        for (Player player : Bukkit.getOnlinePlayers()) afterLogin(player);
    }

    private volatile PackManifest verifiedSnapshot;

    @Override public void onDisable() {
        if (bedrock != null) { bedrock.close(); bedrock = null; }
        generation++; current = null; verifiedSnapshot = null; deliveries.clear();
        // Do not remove an already accepted pack during a reload or shutdown.
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        afterLogin(event.getPlayer());
    }

    private void afterLogin(Player player) {
        long run = generation;
        AuthenticationGate.afterLogin(plugin, player, () -> {
            if (generation == run && isEnabled()) offer(player);
        });
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) { deliveries.remove(event.getPlayer().getUniqueId()); }

    @EventHandler public void onPackStatus(PlayerResourcePackStatusEvent event) {
        Delivery delivery = deliveries.get(event.getPlayer().getUniqueId());
        if (delivery == null || delivery.packId == null || !delivery.packId.equals(event.getID()) || delivery.finished) return;
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> { delivery.loaded = true; delivery.finished = true; }
            case DECLINED, FAILED_DOWNLOAD, FAILED_RELOAD, INVALID_URL, DISCARDED ->
                    fail(event.getPlayer(), delivery, event.getStatus().name());
            default -> { } // ACCEPTED/DOWNLOADED are not proof that textures were applied.
        }
    }

    private void fail(Player player, Delivery delivery, String reason) {
        delivery.finished = true;
        warn("Resource pack not loaded for " + player.getName() + ": " + reason);
        Component message = Component.text("MeerMC's resource pack did not load. Set Server Resource Packs to Enabled "
                + "in Multiplayer > Edit, then reconnect. If prompted, choose Accept instead of ESC. "
                + "If the download fails again, please contact staff. (" + reason + ")");
        if (required) player.kick(message);
        else player.sendMessage(message);
    }

    private void checkDeliveries() {
        // Snapshot permits disconnect callbacks to remove entries during this pass.
        for (UUID id : new ArrayList<>(deliveries.keySet())) {
            Player player = Bukkit.getPlayer(id);
            Delivery delivery = deliveries.get(id);
            if (player == null || !player.isOnline()) { deliveries.remove(id); continue; }
            if (delivery == null || delivery.finished) continue;
            if (System.nanoTime() - delivery.started >= loadTimeoutNanos) {
                fail(player, delivery, delivery.hash == null ? "PACK_UNAVAILABLE" : "LOAD_TIMEOUT");
            } else if (delivery.hash == null && current != null) offer(player);
        }
    }

    private void offer(Player player) {
        if (!player.isOnline() || !AuthenticationGate.isAuthenticated(player)) return;
        // Java resource packs are not Bedrock packs. Floodgate players must use Geyser's pack system.
        if (Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
            try {
                Class<?> api = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                Object instance = api.getMethod("getInstance").invoke(null);
                if (Boolean.TRUE.equals(api.getMethod("isFloodgatePlayer", UUID.class).invoke(instance, player.getUniqueId()))) return;
            } catch (ReflectiveOperationException | LinkageError error) { warn("Cannot verify Floodgate player; skipping Java pack offer."); return; }
        }
        Delivery delivery = deliveries.computeIfAbsent(player.getUniqueId(), ignored -> new Delivery());
        if (current == null || current.sha1().equals(delivery.hash)) return;
        if (delivery.hash != null) {
            player.removeResourcePack(delivery.packId);
            delivery = new Delivery();
            deliveries.put(player.getUniqueId(), delivery);
        }
        delivery.hash = current.sha1();
        // Stable for a release (including reloads), distinct across updates: old status events cannot
        // mark a different release as loaded. Do not replace packs belonging to other plugins.
        delivery.packId = UUID.nameUUIDFromBytes(("meermc:server-pack:" + current.sha1()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(delivery.packId, current.url(), current.sha1());
        try {
            player.removeResourcePack(LEGACY_PACK_ID);
            player.sendResourcePacks(ResourcePackRequest.resourcePackRequest().packs(info)
                    .replace(false).required(required).prompt(Component.text(prompt)).build());
        } catch (RuntimeException error) {
            fail(player, delivery, "REQUEST_FAILED");
        }
    }
}
