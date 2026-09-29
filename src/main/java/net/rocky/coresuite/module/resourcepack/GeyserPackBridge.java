package net.rocky.coresuite.module.resourcepack;

import net.rocky.coresuite.CoreSuitePlugin;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Optional Geyser API adapter. Immutable cached packs are served by Geyser, not GitHub's HTTP headers. */
final class GeyserPackBridge implements AutoCloseable {
    private static final UUID AUDIO_ID = UUID.fromString("f80b764f-d08b-5aca-8b85-9fce65c85c00");
    private final CoreSuitePlugin plugin;
    private final Path cache;
    private final String repository;
    private final Class<?> packType, codecType;
    private final Method register, unregister, packUuid, unsubscribe;
    private final Object bus, owner;
    private volatile Object currentPack;
    private volatile boolean closed;
    private volatile PackManifest currentManifest;
    private BukkitTask task;
    private final AtomicBoolean warnedEvent = new AtomicBoolean();
    private final AtomicBoolean checking = new AtomicBoolean();

    static GeyserPackBridge start(CoreSuitePlugin plugin, Path cache, String repository, long period) throws Exception {
        Plugin geyser = Bukkit.getPluginManager().getPlugin("Geyser-Spigot");
        if (geyser == null || !geyser.isEnabled()) return null;
        GeyserPackBridge bridge = new GeyserPackBridge(plugin, cache, repository, geyser.getClass().getClassLoader());
        try {
            bridge.task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, bridge::refresh, 1L, period);
            return bridge;
        } catch (RuntimeException error) { bridge.close(); throw error; }
    }

    private GeyserPackBridge(CoreSuitePlugin plugin, Path cache, String repository, ClassLoader loader) throws Exception {
        this.plugin=plugin;this.cache=cache;this.repository=repository;
        Class<?> api=Class.forName("org.geysermc.geyser.api.GeyserApi",true,loader);
        Object instance=api.getMethod("api").invoke(null);
        if(instance==null) throw new IllegalStateException("Geyser API is not ready");
        packType=Class.forName("org.geysermc.geyser.api.pack.ResourcePack",true,loader);
        codecType=Class.forName("org.geysermc.geyser.api.pack.PackCodec",true,loader);
        Class<?> event=Class.forName("org.geysermc.geyser.api.event.bedrock.SessionLoadResourcePacksEvent",true,loader);
        Class<?> registrar=Class.forName("org.geysermc.geyser.api.event.EventRegistrar",true,loader);
        Class<?> ownedBus=Class.forName("org.geysermc.event.bus.OwnedEventBus",true,loader);
        register=event.getMethod("register",packType);unregister=event.getMethod("unregister",UUID.class);
        packUuid=packType.getMethod("uuid");
        bus=api.getMethod("eventBus").invoke(instance);owner=registrar.getMethod("of",Object.class).invoke(null,this);
        unsubscribe=ownedBus.getMethod("unregisterAll",Object.class);
        ownedBus.getMethod("subscribe",Object.class,Class.class,Consumer.class).invoke(bus,owner,event,(Consumer<Object>)this::onSession);
    }

    private void onSession(Object event) {
        Object pack=currentPack;
        if(closed || pack==null) return;
        try {
            // Replace only our stable audio-pack UUID, preserving fish textures and integrated packs.
            unregister.invoke(event,packUuid.invoke(pack));
            if (Boolean.FALSE.equals(register.invoke(event,pack))) throw new IllegalStateException("Geyser rejected the audio pack");
        } catch(Exception error) {
            if(warnedEvent.compareAndSet(false,true)) plugin.getLogger().warning("[Resource Pack] Geyser audio registration failed: "+error.getClass().getSimpleName());
        }
    }

    private void refresh() {
        if(closed || !checking.compareAndSet(false,true)) return;
        Path pointer=cache.resolve("last-verified.properties");
        try {
            Files.createDirectories(cache);
            if(currentManifest==null && Files.exists(pointer)) {
                try {
                    PackManifest saved=PackManifest.parse(Files.readAllBytes(pointer),repository,"meermc-bedrock-audio.mcpack");
                    if(Files.size(packPath(saved))!=saved.size())throw new java.io.IOException("Cached size mismatch");
                    byte[] data=Files.readAllBytes(packPath(saved));saved.verify(data,"manifest.json");
                    Object pack=loadPack(packPath(saved));
                    synchronized(this) { if(closed)return;currentPack=pack;currentManifest=saved; }
                    plugin.getLogger().info("[Resource Pack] Loaded cached Bedrock audio "+saved.version());
                } catch(Exception error) { plugin.getLogger().warning("[Resource Pack] Cached Bedrock audio could not be loaded; checking GitHub."); }
            }
            byte[] manifest=PackManifest.download(URI.create("https://github.com/"+repository+"/releases/latest/download/bedrock-manifest.properties"),16384);
            PackManifest candidate=PackManifest.parse(manifest,repository,"meermc-bedrock-audio.mcpack");
            if(candidate.equals(currentManifest))return;
            byte[] data=PackManifest.download(candidate.url(),candidate.size());candidate.verify(data,"manifest.json");
            if(closed)return;
            writeAtomic(packPath(candidate),data);
            Object pack=loadPack(packPath(candidate)); // Geyser validates the Bedrock manifest before activation.
            synchronized(this) {
                if(closed)return;
                writeAtomic(pointer,manifest);currentPack=pack;currentManifest=candidate;
            }
            plugin.getLogger().info("[Resource Pack] Verified Bedrock audio "+candidate.version()+"; Geyser will send it on the next connection.");
        } catch(Exception error) {
            if(!closed)plugin.getLogger().warning("[Resource Pack] Bedrock update unavailable; retaining cached audio. "+error.getClass().getSimpleName()+": "+error.getMessage());
        } finally { checking.set(false); }
    }

    private Path packPath(PackManifest manifest) { return cache.resolve(manifest.sha1()+".mcpack"); }
    private Object loadPack(Path file) throws Exception {
        Object pack=packType.getMethod("create",codecType).invoke(null,codecType.getMethod("path",Path.class).invoke(null,file));
        if (!AUDIO_ID.equals(packUuid.invoke(pack))) throw new java.io.IOException("Unexpected Bedrock audio pack UUID");
        return pack;
    }
    private static void writeAtomic(Path file,byte[] bytes) throws Exception {
        Path temporary=Files.createTempFile(file.getParent(),"pack-",".tmp");
        try {
            Files.write(temporary,bytes);
            try {Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException ignored){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}
        } finally {Files.deleteIfExists(temporary);}
    }

    @Override public synchronized void close() {
        closed=true;if(task!=null)task.cancel();currentPack=null;currentManifest=null;
        try {unsubscribe.invoke(bus,owner);}
        catch(Exception error){plugin.getLogger().warning("[Resource Pack] Could not detach Geyser audio listener: "+error.getClass().getSimpleName());}
    }
}
