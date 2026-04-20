package me.ar1hurgit.aevumcore.modules.lastconnection;

import me.ar1hurgit.aevumcore.AevumCore;
import me.ar1hurgit.aevumcore.core.command.CommandBindings;
import me.ar1hurgit.aevumcore.core.module.AbstractModule;
import me.ar1hurgit.aevumcore.storage.data.SessionData;
import me.ar1hurgit.aevumcore.storage.database.DatabaseManager;
import org.bukkit.Bukkit;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class LastConnectionModule extends AbstractModule {

    private final AevumCore plugin;
    private final DatabaseManager databaseManager;
    private final LastConnectionRepository repository;
    private final Map<QueryKey, CachedPlayerStats> statsCache = new ConcurrentHashMap<>();

    private LastConnectionManager manager;
    private LastConnectionListener listener;
    private volatile boolean ready;

    public LastConnectionModule(AevumCore plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.repository = new LastConnectionRepository(databaseManager);
    }

    @Override
    public String getName() {
        return "lastconnection";
    }

    @Override
    protected void onEnable() {
        if (!plugin.getConfig().getBoolean("lastconnection.enabled", true)) {
            return;
        }

        ready = false;
        databaseManager.runAsync(repository::createTableIfNeeded)
                .whenComplete((unused, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!plugin.isEnabled() || throwable != null) {
                        ready = false;
                        if (throwable != null) {
                            plugin.getLogger().severe("[LastConnection] Erreur lors de la creation de la table player_sessions : " + throwable.getMessage());
                        }
                        return;
                    }

                    manager = new LastConnectionManager();
                    listener = new LastConnectionListener(this, manager);
                    plugin.getServer().getPluginManager().registerEvents(listener, plugin);

                    LastConnectionCommand command = new LastConnectionCommand(plugin, this);
                    CommandBindings.bind(plugin, "lastconnexion", command, command);

                    ready = true;
                    Bukkit.getLogger().info(plugin.getConfig().getString("prefix", "[AevumCore]") + " LastConnection module enabled");
                }));
    }

    public boolean isReady() {
        return ready;
    }

    public long getCurrentSessionDuration(UUID uuid, long now) {
        if (!ready || manager == null) {
            return 0L;
        }

        return manager.getCurrentSessionDuration(uuid, now);
    }

    public void fetchRecentSessionsAsync(UUID uuid, int limit, Consumer<List<SessionData>> callback) {
        fetchPlayerStatsAsync(uuid, limit, stats -> callback.accept(stats == null ? null : stats.recentSessions()));
    }

    public void fetchTotalPlaytimeAsync(UUID uuid, Consumer<Long> callback) {
        if (!ready) {
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(null));
            return;
        }

        fetchPlayerStatsAsync(uuid, 1, stats -> {
            if (stats == null) {
                callback.accept(null);
                return;
            }

            callback.accept(stats.totalPlaytime() + getCurrentSessionDuration(uuid, System.currentTimeMillis()));
        });
    }

    public void fetchPlayerStatsAsync(UUID uuid, int limit, Consumer<LastConnectionRepository.PlayerStats> callback) {
        if (!ready) {
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(null));
            return;
        }

        int safeLimit = Math.max(1, limit);
        long now = System.currentTimeMillis();
        QueryKey cacheKey = new QueryKey(uuid, safeLimit);
        CachedPlayerStats cached = statsCache.get(cacheKey);
        if (cached != null && (now - cached.loadedAt()) < getCacheDurationMillis()) {
            callback.accept(cached.stats());
            return;
        }

        databaseManager.supplyAsync(() -> repository.fetchPlayerStats(uuid, safeLimit))
                .whenComplete((stats, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (throwable != null) {
                        plugin.getLogger().severe("[LastConnection] Erreur lors de la lecture des sessions de " + uuid + " : " + throwable.getMessage());
                        callback.accept(null);
                        return;
                    }

                    statsCache.put(cacheKey, new CachedPlayerStats(stats, System.currentTimeMillis()));
                    callback.accept(stats);
                }));
    }

    public void persistSessionAsync(LastConnectionManager.SessionSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }

        databaseManager.runAsync(() -> repository.insertSession(snapshot))
                .whenComplete((unused, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (throwable != null) {
                        plugin.getLogger().severe("[LastConnection] Erreur lors de l'enregistrement de la session : " + throwable.getMessage());
                    }
                    invalidateCache(snapshot.uuid());
                }));
    }

    public void invalidateCache(UUID uuid) {
        statsCache.entrySet().removeIf(entry -> entry.getKey().uuid().equals(uuid));
    }

    private long getCacheDurationMillis() {
        return Math.max(0, plugin.getConfig().getInt("lastconnection.cache-duration", 60)) * 1000L;
    }

    @Override
    protected void onDisable() {
        ready = false;
        statsCache.clear();
        listener = null;
        manager = null;
        Bukkit.getLogger().info(plugin.getConfig().getString("prefix", "[AevumCore]") + " LastConnection module disabled");
    }

    private record QueryKey(UUID uuid, int limit) {
    }

    private record CachedPlayerStats(LastConnectionRepository.PlayerStats stats, long loadedAt) {
    }
}
