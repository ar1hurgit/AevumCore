package me.ar1hurgit.aevumcore.modules.nickname;

import me.ar1hurgit.aevumcore.AevumCore;
import me.ar1hurgit.aevumcore.core.module.Module;
import me.ar1hurgit.aevumcore.modules.vanish.VanishManager;
import me.ar1hurgit.aevumcore.modules.vanish.VanishModule;
import me.ar1hurgit.aevumcore.storage.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class NicknameManager {

    private final AevumCore plugin;
    private final DatabaseManager databaseManager;
    private final NicknameRepository repository;
    private final NicknameNameTagService nameTagService;

    private final Map<UUID, String> nicknameByUuid = new ConcurrentHashMap<>();
    private final Map<String, UUID> uuidByNicknameLower = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastChangeByUuid = new ConcurrentHashMap<>();
    private final Set<UUID> loadingPlayers = ConcurrentHashMap.newKeySet();

    public NicknameManager(AevumCore plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.repository = new NicknameRepository(databaseManager, plugin.getLogger());
        this.nameTagService = new NicknameNameTagService();
    }

    public void enable() {
        databaseManager.supplyAsync(() -> {
            repository.initializeSchema();
            return repository.loadAllNicknames();
        }).whenComplete((loadedNicknames, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!plugin.isEnabled()) {
                return;
            }

            if (throwable != null) {
                plugin.getLogger().severe("[Nickname] Erreur d'initialisation : " + throwable.getMessage());
                return;
            }

            applyLoadedNicknames(loadedNicknames);
            nameTagService.cleanupManagedNameTags();
            applyToOnlinePlayers();
        }));
    }

    public void disable() {
        nameTagService.clearAll();
        nicknameByUuid.clear();
        uuidByNicknameLower.clear();
        lastChangeByUuid.clear();
        loadingPlayers.clear();
    }

    public void handleJoin(Player player) {
        String cached = nicknameByUuid.get(player.getUniqueId());
        if (cached != null) {
            applyDisplay(player, cached);
            return;
        }

        if (!loadingPlayers.add(player.getUniqueId())) return;

        databaseManager.supplyAsync(() -> repository.loadPlayer(player.getUniqueId()))
                .whenComplete((playerNickname, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    loadingPlayers.remove(player.getUniqueId());
                    if (!plugin.isEnabled()) {
                        return;
                    }

                    if (throwable != null) {
                        plugin.getLogger().severe("[Nickname] Erreur chargement joueur " + player.getUniqueId() + " : " + throwable.getMessage());
                        return;
                    }

                    String finalNickname = sanitizeNickname(playerNickname.nickname());
                    if (finalNickname != null) {
                        nicknameByUuid.put(player.getUniqueId(), finalNickname);
                        uuidByNicknameLower.put(finalNickname.toLowerCase(Locale.ROOT), player.getUniqueId());
                        applyDisplay(player, finalNickname);
                    }
                    lastChangeByUuid.put(player.getUniqueId(), playerNickname.lastChange());
                }));
    }

    public void handleQuit(Player player) {
        loadingPlayers.remove(player.getUniqueId());
        nameTagService.removeNameTag(player.getUniqueId());
    }

    public void handleDeath(Player player) {
        if (player == null) return;
        nameTagService.removeNameTag(player.getUniqueId());
    }

    public void handleRespawn(Player player) {
        if (player == null) return;

        // Re-apply display a couple ticks after respawn so the player is at final respawn location.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            String nickname = nicknameByUuid.get(player.getUniqueId());
            if (nickname == null) {
                resetDisplay(player);
                return;
            }

            applyDisplay(player, nickname);
        }, 2L);
    }

    public void setNickname(Player player, String requestedNickname) {
        String nickname = sanitizeNickname(requestedNickname);
        if (nickname == null) {
            player.sendMessage(prefix() + ChatColor.RED + " Pseudo invalide.");
            return;
        }

        int maxLength = Math.max(3, plugin.getConfig().getInt("nickname.max-length", 16));
        if (nickname.length() > maxLength) {
            player.sendMessage(prefix() + ChatColor.RED + " Le pseudo est trop long (max " + maxLength + ").");
            return;
        }

        String allowedChars = plugin.getConfig().getString("nickname.allowed-chars", "A-Za-z0-9_");
        if (!nickname.matches("[" + allowedChars + "]+")) {
            player.sendMessage(prefix() + ChatColor.RED + " Le pseudo contient des caracteres interdits.");
            return;
        }

        boolean clearNickname = nickname.equalsIgnoreCase(player.getName());
        long now = System.currentTimeMillis();

        if (!player.hasPermission("aevumcore.nickname.bypass.cooldown")) {
            int cooldown = Math.max(0, plugin.getConfig().getInt("nickname.cooldown", 60));
            long last = lastChangeByUuid.getOrDefault(player.getUniqueId(), 0L);
            long remaining = (cooldown * 1000L) - (now - last);
            if (remaining > 0) {
                long seconds = (remaining + 999) / 1000;
                player.sendMessage(prefix() + ChatColor.RED + " Vous devez attendre " + seconds + "s avant de rechanger de pseudo.");
                return;
            }
        }

        databaseManager.supplyAsync(() -> clearNickname
                        ? (repository.clearNickname(player.getUniqueId())
                        ? NicknameRepository.SaveResult.SUCCESS
                        : NicknameRepository.SaveResult.ERROR)
                        : repository.saveNickname(player.getUniqueId(), nickname, now))
                .whenComplete((result, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!plugin.isEnabled()) {
                        return;
                    }

                    if (throwable != null || result == NicknameRepository.SaveResult.ERROR) {
                        player.sendMessage(prefix() + ChatColor.RED + " Erreur SQL lors du changement de pseudo.");
                        if (throwable != null) {
                            plugin.getLogger().severe("[Nickname] Echec async sur " + player.getUniqueId() + " : " + throwable.getMessage());
                        }
                        return;
                    }

                    if (result == NicknameRepository.SaveResult.ALREADY_TAKEN) {
                        player.sendMessage(prefix() + ChatColor.RED + " Ce pseudo est deja utilise.");
                        return;
                    }

                    String previous = nicknameByUuid.remove(player.getUniqueId());
                    if (previous != null) {
                        uuidByNicknameLower.remove(previous.toLowerCase(Locale.ROOT));
                    }

                    if (clearNickname) {
                        resetDisplay(player);
                        player.sendMessage(prefix() + ChatColor.YELLOW + " Votre pseudo affiche a ete reinitialise.");
                    } else {
                        nicknameByUuid.put(player.getUniqueId(), nickname);
                        uuidByNicknameLower.put(nickname.toLowerCase(Locale.ROOT), player.getUniqueId());
                        applyDisplay(player, nickname);
                        player.sendMessage(prefix() + ChatColor.GREEN + " Nouveau pseudo affiche: " + ChatColor.GOLD + nickname);
                    }

                    lastChangeByUuid.put(player.getUniqueId(), now);
                }));
    }

    public void findRealNameByDisplayed(String displayedName, Consumer<String> callback) {
        String normalized = sanitizeNickname(displayedName);
        if (normalized == null) {
            callback.accept(null);
            return;
        }

        UUID uuid = uuidByNicknameLower.get(normalized.toLowerCase(Locale.ROOT));
        if (uuid != null) {
            callback.accept(resolveRealName(uuid));
            return;
        }

        databaseManager.supplyAsync(() -> repository.findUuidByNickname(normalized))
                .whenComplete((foundUuid, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (throwable != null) {
                        plugin.getLogger().severe("[Nickname] Erreur recherche pseudo " + normalized + " : " + throwable.getMessage());
                        callback.accept(null);
                        return;
                    }

                    callback.accept(foundUuid == null ? null : resolveRealName(foundUuid));
                }));
    }

    public List<String> getKnownDisplayedNames() {
        List<String> names = new ArrayList<>(nicknameByUuid.values());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public String getDisplayedName(Player player) {
        String nickname = nicknameByUuid.get(player.getUniqueId());
        return nickname == null ? player.getName() : nickname;
    }

    public List<String> rewriteCompletionsForViewer(Player viewer, String buffer, Collection<String> completions) {
        if (viewer == null || completions == null) {
            return completions == null ? new ArrayList<>() : new ArrayList<>(completions);
        }

        List<String> rewritten = new ArrayList<>(completions);
        String token = extractCurrentToken(buffer).toLowerCase(Locale.ROOT);
        for (Player target : Bukkit.getOnlinePlayers()) {
            String real = target.getName();
            String displayed = getDisplayedName(target);

            if (isHiddenFromViewer(viewer, target)) {
                rewritten.removeIf(entry -> entry.equalsIgnoreCase(real) || entry.equalsIgnoreCase(displayed));
                continue;
            }

            if (displayed.equalsIgnoreCase(real)) continue;

            rewritten.removeIf(entry -> entry.equalsIgnoreCase(real));

            if (token.isEmpty() || displayed.toLowerCase(Locale.ROOT).startsWith(token)) {
                boolean alreadyPresent = rewritten.stream().anyMatch(entry -> entry.equalsIgnoreCase(displayed));
                if (!alreadyPresent) {
                    rewritten.add(displayed);
                }
            }
        }

        rewritten.sort(String.CASE_INSENSITIVE_ORDER);
        return rewritten;
    }

    public Player resolveTargetForViewer(Player viewer, String input) {
        String normalized = sanitizeNickname(input);
        if (normalized == null) return null;

        Player direct = Bukkit.getPlayerExact(normalized);
        if (direct != null && !isHiddenFromViewer(viewer, direct)) {
            return direct;
        }

        direct = Bukkit.getPlayer(normalized);
        if (direct != null && !isHiddenFromViewer(viewer, direct)) {
            return direct;
        }

        UUID byNickname = uuidByNicknameLower.get(normalized.toLowerCase(Locale.ROOT));
        if (byNickname == null) return null;

        Player target = Bukkit.getPlayer(byNickname);
        if (target == null) return null;
        if (isHiddenFromViewer(viewer, target)) return null;
        return target;
    }

    public void refreshForVanishState(Player player, boolean vanished) {
        if (player == null) return;

        if (vanished) {
            nameTagService.removeNameTag(player.getUniqueId());
            return;
        }

        String nickname = nicknameByUuid.get(player.getUniqueId());
        if (nickname == null) return;

        nameTagService.ensureHiddenRealName(player);
        nameTagService.ensureNameTag(player, nickname);
    }

    private void applyLoadedNicknames(NicknameRepository.LoadedNicknames loadedNicknames) {
        nicknameByUuid.clear();
        uuidByNicknameLower.clear();
        lastChangeByUuid.clear();

        lastChangeByUuid.putAll(loadedNicknames.lastChanges());

        for (Map.Entry<UUID, String> entry : loadedNicknames.nicknames().entrySet()) {
            String nickname = sanitizeNickname(entry.getValue());
            if (nickname == null) {
                continue;
            }

            nicknameByUuid.put(entry.getKey(), nickname);
            uuidByNicknameLower.put(nickname.toLowerCase(Locale.ROOT), entry.getKey());
        }
    }

    private void applyToOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            String nickname = nicknameByUuid.get(player.getUniqueId());
            if (nickname != null) {
                applyDisplay(player, nickname);
            } else {
                resetDisplay(player);
            }
        }
    }

    private void applyDisplay(Player player, String nickname) {
        String sanitized = sanitizeNickname(nickname);
        if (sanitized == null) {
            resetDisplay(player);
            return;
        }

        player.setDisplayName(sanitized);
        player.setCustomName(sanitized);
        player.setCustomNameVisible(true);
        player.setPlayerListName(sanitized);

        nameTagService.ensureHiddenRealName(player);
        refreshForVanishState(player, isVanished(player));
        refreshNameplateForViewers(player);
        syncWithVanish(player, sanitized);
    }

    private void resetDisplay(Player player) {
        String real = player.getName();
        player.setDisplayName(real);
        player.setCustomName(null);
        player.setCustomNameVisible(false);
        player.setPlayerListName(real);
        nameTagService.removeHiddenRealName(player);
        nameTagService.removeNameTag(player.getUniqueId());
        refreshNameplateForViewers(player);
        syncWithVanish(player, real);
    }

    private void refreshNameplateForViewers(Player player) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(player)) continue;
            if (!viewer.canSee(player)) continue;

            viewer.hidePlayer(plugin, player);
            viewer.showPlayer(plugin, player);
        }
    }

    private void syncWithVanish(Player player, String shownName) {
        VanishManager vanishManager = getVanishManager();
        if (vanishManager == null) return;

        vanishManager.updateKnownName(player, shownName);
        vanishManager.refreshVisibility(player);
    }

    private boolean isHiddenFromViewer(Player viewer, Player target) {
        if (viewer == null || target == null) return true;
        if (viewer.equals(target)) return false;

        VanishManager vanishManager = getVanishManager();
        if (vanishManager != null
                && vanishManager.isVanished(target.getUniqueId())
                && !vanishManager.canSeeVanished(viewer)) {
            return true;
        }

        return !viewer.canSee(target);
    }

    private boolean isVanished(Player player) {
        VanishManager vanishManager = getVanishManager();
        return vanishManager != null && vanishManager.isVanished(player.getUniqueId());
    }

    private VanishManager getVanishManager() {
        Module module = plugin.getModuleManager().get("vanish");
        if (!(module instanceof VanishModule vanishModule) || !module.isEnabled()) {
            return null;
        }
        return vanishModule.getManager();
    }

    private String resolveRealName(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        if (offlinePlayer.getName() != null) return offlinePlayer.getName();

        return uuid.toString();
    }

    private String sanitizeNickname(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        return trimmed;
    }

    private String extractCurrentToken(String buffer) {
        if (buffer == null || buffer.isBlank()) return "";
        String trimmed = buffer.trim();
        int spaceIndex = trimmed.lastIndexOf(' ');
        if (spaceIndex < 0) return trimmed;
        return trimmed.substring(spaceIndex + 1);
    }

    private String prefix() {
        return ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("prefix", "&f[&bAevumCore&f]"));
    }
}
