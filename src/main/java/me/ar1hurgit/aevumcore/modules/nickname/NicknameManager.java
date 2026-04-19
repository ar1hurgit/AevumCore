package me.ar1hurgit.aevumcore.modules.nickname;

import me.ar1hurgit.aevumcore.AevumCore;
import me.ar1hurgit.aevumcore.core.module.Module;
import me.ar1hurgit.aevumcore.modules.vanish.VanishManager;
import me.ar1hurgit.aevumcore.modules.vanish.VanishModule;
import me.ar1hurgit.aevumcore.storage.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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

    private static final String HIDDEN_NAMETAG_TEAM = "ac_nick_hide";
    private static final String NAME_TAG_ENTITY_TAG = "aevumcore_nickname_tag";
    private static final String NAME_TAG_OWNER_PREFIX = "aevumcore_nick_owner_";

    private final AevumCore plugin;
    private final DatabaseManager databaseManager;

    private final Map<UUID, String> nicknameByUuid = new ConcurrentHashMap<>();
    private final Map<String, UUID> uuidByNicknameLower = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastChangeByUuid = new ConcurrentHashMap<>();
    private final Set<UUID> loadingPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, UUID> nameTagStandByPlayer = new ConcurrentHashMap<>();

    public NicknameManager(AevumCore plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    public void enable() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            createTableIfNeeded();
            loadAllFromDatabase();
            Bukkit.getScheduler().runTask(plugin, () -> {
                cleanupManagedNameTags();
                applyToOnlinePlayers();
            });
        });
    }

    public void disable() {
        for (UUID uuid : new ArrayList<>(nameTagStandByPlayer.keySet())) {
            removeNameTag(uuid);
        }
        Team team = getOrCreateHiddenNameTagTeam();
        if (team != null) {
            for (String entry : new ArrayList<>(team.getEntries())) {
                team.removeEntry(entry);
            }
        }
        nicknameByUuid.clear();
        uuidByNicknameLower.clear();
        lastChangeByUuid.clear();
        loadingPlayers.clear();
        nameTagStandByPlayer.clear();
    }

    public void handleJoin(Player player) {
        String cached = nicknameByUuid.get(player.getUniqueId());
        if (cached != null) {
            applyDisplay(player, cached);
            return;
        }

        if (!loadingPlayers.add(player.getUniqueId())) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String nickname = null;
            long lastChange = 0L;

            try (Connection con = databaseManager.getConnection();
                 PreparedStatement stmt = con.prepareStatement("SELECT nickname, last_change FROM player_nicknames WHERE uuid = ?")) {
                stmt.setString(1, player.getUniqueId().toString());
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    nickname = rs.getString("nickname");
                    lastChange = rs.getLong("last_change");
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[Nickname] Erreur chargement joueur " + player.getUniqueId() + " : " + e.getMessage());
            }

            final String finalNickname = sanitizeNickname(nickname);
            final long finalLastChange = lastChange;

            Bukkit.getScheduler().runTask(plugin, () -> {
                loadingPlayers.remove(player.getUniqueId());

                if (finalNickname != null) {
                    nicknameByUuid.put(player.getUniqueId(), finalNickname);
                    uuidByNicknameLower.put(finalNickname.toLowerCase(Locale.ROOT), player.getUniqueId());
                    applyDisplay(player, finalNickname);
                }
                lastChangeByUuid.put(player.getUniqueId(), finalLastChange);
            });
        });
    }

    public void handleQuit(Player player) {
        loadingPlayers.remove(player.getUniqueId());
        removeNameTag(player.getUniqueId());
    }

    public void handleDeath(Player player) {
        if (player == null) return;
        removeNameTag(player.getUniqueId());
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

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection con = databaseManager.getConnection()) {
                if (!clearNickname && isNicknameTakenByOther(con, player.getUniqueId(), nickname)) {
                    Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(prefix() + ChatColor.RED + " Ce pseudo est deja utilise."));
                    return;
                }

                if (clearNickname) {
                    try (PreparedStatement delete = con.prepareStatement("DELETE FROM player_nicknames WHERE uuid = ?")) {
                        delete.setString(1, player.getUniqueId().toString());
                        delete.executeUpdate();
                    }
                } else {
                    boolean updated;
                    try (PreparedStatement update = con.prepareStatement("UPDATE player_nicknames SET nickname = ?, last_change = ? WHERE uuid = ?")) {
                        update.setString(1, nickname);
                        update.setLong(2, now);
                        update.setString(3, player.getUniqueId().toString());
                        updated = update.executeUpdate() > 0;
                    }

                    if (!updated) {
                        try (PreparedStatement insert = con.prepareStatement("INSERT INTO player_nicknames (uuid, nickname, last_change) VALUES (?, ?, ?)")) {
                            insert.setString(1, player.getUniqueId().toString());
                            insert.setString(2, nickname);
                            insert.setLong(3, now);
                            insert.executeUpdate();
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[Nickname] Erreur changement pseudo " + player.getUniqueId() + " : " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(prefix() + ChatColor.RED + " Erreur SQL lors du changement de pseudo."));
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
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
            });
        });
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

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            UUID found = null;

            try (Connection con = databaseManager.getConnection();
                 PreparedStatement stmt = con.prepareStatement("SELECT uuid FROM player_nicknames WHERE LOWER(nickname) = LOWER(?)")) {
                stmt.setString(1, normalized);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    found = UUID.fromString(rs.getString("uuid"));
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[Nickname] Erreur recherche realname: " + e.getMessage());
            }

            UUID finalFound = found;
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(finalFound == null ? null : resolveRealName(finalFound)));
        });
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
            removeNameTag(player.getUniqueId());
            return;
        }

        String nickname = nicknameByUuid.get(player.getUniqueId());
        if (nickname == null) return;

        ensureHiddenRealName(player);
        ensureNameTag(player, nickname);
    }

    private boolean isNicknameTakenByOther(Connection con, UUID requester, String nickname) throws SQLException {
        try (PreparedStatement stmt = con.prepareStatement("SELECT uuid FROM player_nicknames WHERE LOWER(nickname) = LOWER(?)")) {
            stmt.setString(1, nickname);
            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) return false;
            String uuid = rs.getString("uuid");
            return !requester.toString().equalsIgnoreCase(uuid);
        }
    }

    private void loadAllFromDatabase() {
        nicknameByUuid.clear();
        uuidByNicknameLower.clear();

        try (Connection con = databaseManager.getConnection();
             PreparedStatement stmt = con.prepareStatement("SELECT uuid, nickname, last_change FROM player_nicknames")) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                String nickname = sanitizeNickname(rs.getString("nickname"));
                long lastChange = rs.getLong("last_change");

                lastChangeByUuid.put(uuid, lastChange);
                if (nickname == null) continue;

                nicknameByUuid.put(uuid, nickname);
                uuidByNicknameLower.put(nickname.toLowerCase(Locale.ROOT), uuid);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[Nickname] Erreur chargement global des pseudos : " + e.getMessage());
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

    private void createTableIfNeeded() {
        try (Connection con = databaseManager.getConnection();
             PreparedStatement stmt = con.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS player_nicknames (" +
                             "uuid VARCHAR(36) PRIMARY KEY," +
                             "nickname VARCHAR(64)," +
                             "last_change BIGINT NOT NULL DEFAULT 0" +
                             ")")) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[Nickname] Erreur creation table player_nicknames : " + e.getMessage());
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

        ensureHiddenRealName(player);
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
        removeHiddenRealName(player);
        removeNameTag(player.getUniqueId());
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

    private void ensureHiddenRealName(Player player) {
        Team team = getOrCreateHiddenNameTagTeam();
        if (team == null) return;
        if (!team.hasEntry(player.getName())) {
            team.addEntry(player.getName());
        }
    }

    private void removeHiddenRealName(Player player) {
        Team team = getOrCreateHiddenNameTagTeam();
        if (team != null && team.hasEntry(player.getName())) {
            team.removeEntry(player.getName());
        }
    }

    private Team getOrCreateHiddenNameTagTeam() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager() == null ? null : Bukkit.getScoreboardManager().getMainScoreboard();
        if (scoreboard == null) return null;

        Team team = scoreboard.getTeam(HIDDEN_NAMETAG_TEAM);
        if (team == null) {
            team = scoreboard.registerNewTeam(HIDDEN_NAMETAG_TEAM);
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        }
        return team;
    }

    private void ensureNameTag(Player player, String nickname) {
        removeNameTag(player.getUniqueId());

        World world = player.getWorld();
        ArmorStand stand = (ArmorStand) world.spawnEntity(player.getLocation(), EntityType.ARMOR_STAND);
        stand.setMarker(true);
        stand.setInvisible(true);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        stand.setBasePlate(false);
        stand.setCustomName(nickname);
        stand.setCustomNameVisible(true);
        stand.addScoreboardTag(NAME_TAG_ENTITY_TAG);
        stand.addScoreboardTag(ownerTag(player.getUniqueId()));

        player.addPassenger(stand);
        nameTagStandByPlayer.put(player.getUniqueId(), stand.getUniqueId());
    }

    private void removeNameTag(UUID playerUuid) {
        UUID standUuid = nameTagStandByPlayer.remove(playerUuid);
        Player owner = Bukkit.getPlayer(playerUuid);
        if (owner != null) {
            for (Entity passenger : new ArrayList<>(owner.getPassengers())) {
                if (isManagedNameTag(passenger, playerUuid, standUuid) || looksLikeLegacyNameTagPassenger(passenger)) {
                    passenger.remove();
                }
            }
        }

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                if (isManagedNameTag(entity, playerUuid, standUuid)) {
                    entity.remove();
                }
            }
        }
    }

    private void cleanupManagedNameTags() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                if (!(entity instanceof ArmorStand)) continue;
                if (!entity.getScoreboardTags().contains(NAME_TAG_ENTITY_TAG)) continue;
                entity.remove();
            }
        }
    }

    private boolean isManagedNameTag(Entity entity, UUID ownerUuid, UUID standUuid) {
        if (!(entity instanceof ArmorStand)) return false;
        if (standUuid != null && standUuid.equals(entity.getUniqueId())) return true;

        Set<String> tags = entity.getScoreboardTags();
        return tags.contains(NAME_TAG_ENTITY_TAG) && tags.contains(ownerTag(ownerUuid));
    }

    private boolean looksLikeLegacyNameTagPassenger(Entity entity) {
        if (!(entity instanceof ArmorStand stand)) return false;
        return stand.isMarker()
                && stand.isInvisible()
                && stand.isSilent()
                && stand.isCustomNameVisible();
    }

    private String ownerTag(UUID ownerUuid) {
        return NAME_TAG_OWNER_PREFIX + ownerUuid;
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
