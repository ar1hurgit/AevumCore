package me.ar1hurgit.aevumcore.modules.report;

import me.ar1hurgit.aevumcore.AevumCore;
import me.ar1hurgit.aevumcore.core.module.Module;
import me.ar1hurgit.aevumcore.modules.nickname.NicknameManager;
import me.ar1hurgit.aevumcore.modules.nickname.NicknameModule;
import me.ar1hurgit.aevumcore.modules.report.gui.ReportMenu;
import me.ar1hurgit.aevumcore.storage.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class ReportManager {

    public static final String PLAYER_PERMISSION = "aevumcore.player.report";
    public static final String VIEW_PERMISSION = "aevumcore.report.view";
    public static final String OVERRIDE_PERMISSION = "aevumcore.report.override";

    private static final long REPORT_LIFETIME_MS = 24L * 60L * 60L * 1000L;
    private static final long CLEANUP_INTERVAL_TICKS = 20L * 60L;

    private final AevumCore plugin;
    private final DatabaseManager databaseManager;
    private final ReportMenu menu = new ReportMenu();

    private final Map<Long, ReportRecord> openReports = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastReportAtByPlayer = new ConcurrentHashMap<>();
    private final Set<UUID> pendingSubmitters = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> openMenuPages = new ConcurrentHashMap<>();
    private final Map<UUID, ReportMenu.RenderedPage> openMenuStates = new ConcurrentHashMap<>();
    private final AtomicLong nextReportId = new AtomicLong(1L);

    private volatile boolean ready = false;
    private ReportCleanupTask cleanupTask;

    public ReportManager(AevumCore plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    public void enable() {
        ready = false;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            createTablesIfNeeded();
            long cutoff = System.currentTimeMillis() - REPORT_LIFETIME_MS;
            purgeExpiredReports(cutoff);

            List<ReportRecord> loadedReports = loadOpenReports(cutoff);
            Map<UUID, Long> loadedCooldowns = loadCooldowns();
            long initialNextId = loadNextReportId();

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!plugin.isEnabled()) return;

                openReports.clear();
                for (ReportRecord record : loadedReports) {
                    openReports.put(record.id(), record);
                }

                lastReportAtByPlayer.clear();
                lastReportAtByPlayer.putAll(loadedCooldowns);

                nextReportId.set(initialNextId);
                ready = true;
                startCleanupTask();
            });
        });
    }

    public void disable() {
        ready = false;

        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }

        openMenuPages.clear();
        openMenuStates.clear();
        pendingSubmitters.clear();
        openReports.clear();
        lastReportAtByPlayer.clear();
    }

    public boolean isReady() {
        return ready;
    }

    public boolean canManage(CommandSender sender) {
        return !(sender instanceof Player) || sender.hasPermission(VIEW_PERMISSION);
    }

    public boolean isMenuTitle(String title) {
        return ReportMenu.TITLE.equals(title);
    }

    public void submitReport(Player reporter, ResolvedTarget target, String rawReason) {
        if (!isReady()) {
            reporter.sendMessage(prefix() + ChatColor.RED + " Le systeme de report se charge encore. Reessayez dans quelques secondes.");
            return;
        }

        String reason = sanitizeReason(rawReason);
        if (reason == null) {
            reporter.sendMessage(prefix() + ChatColor.RED + " Vous devez indiquer une raison.");
            return;
        }

        long cooldownSeconds = Math.max(0L, plugin.getConfig().getLong("report.cooldown", 0L));
        long now = System.currentTimeMillis();
        long remaining = getRemainingCooldownMillis(reporter.getUniqueId(), now, cooldownSeconds);
        if (remaining > 0L) {
            reporter.sendMessage(prefix() + ChatColor.RED + " Vous devez attendre " + ChatColor.YELLOW + formatDuration(remaining) + ChatColor.RED + " avant de pouvoir reporter a nouveau.");
            return;
        }

        if (!pendingSubmitters.add(reporter.getUniqueId())) {
            reporter.sendMessage(prefix() + ChatColor.RED + " Votre precedent report est encore en cours d'envoi.");
            return;
        }

        long reportId = nextReportId.getAndIncrement();
        long createdAt = System.currentTimeMillis();
        ReportRecord record = new ReportRecord(
                reportId,
                reporter.getUniqueId(),
                reporter.getName(),
                target.uuid(),
                target.name(),
                reason,
                createdAt,
                null,
                null,
                0L
        );

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String failureMessage = null;

            try (Connection con = databaseManager.getConnection()) {
                insertReport(con, record);
                updateCooldown(con, reporter.getUniqueId(), createdAt);
            } catch (SQLException exception) {
                failureMessage = exception.getMessage();
                plugin.getLogger().severe("[Report] Impossible d'envoyer le report #" + reportId + " : " + exception.getMessage());
            }

            String finalFailureMessage = failureMessage;
            Bukkit.getScheduler().runTask(plugin, () -> {
                pendingSubmitters.remove(reporter.getUniqueId());
                if (!plugin.isEnabled()) return;

                if (finalFailureMessage != null) {
                    if (reporter.isOnline()) {
                        reporter.sendMessage(prefix() + ChatColor.RED + " Impossible d'envoyer le report pour le moment.");
                    }
                    return;
                }

                openReports.put(record.id(), record);
                lastReportAtByPlayer.put(reporter.getUniqueId(), createdAt);

                if (reporter.isOnline()) {
                    reporter.sendMessage(buildConfirmation(record));
                }

                notifyStaff(record);
                refreshOpenMenus();
            });
        });
    }

    public void claimReport(Player staff, long reportId) {
        if (!isReady()) {
            staff.sendMessage(prefix() + ChatColor.RED + " Le systeme de report se charge encore.");
            return;
        }

        ReportRecord current = openReports.get(reportId);
        if (current == null) {
            staff.sendMessage(prefix() + ChatColor.RED + " Ce report n'existe pas ou a deja ete ferme.");
            return;
        }

        boolean override = staff.hasPermission(OVERRIDE_PERMISSION);
        if (current.isClaimedBy(staff.getUniqueId())) {
            staff.sendMessage(prefix() + ChatColor.YELLOW + " Ce report est deja pris en charge par vous.");
            return;
        }

        if (current.isClaimed() && !override) {
            staff.sendMessage(prefix() + ChatColor.RED + " Ce report est deja pris en charge par " + ChatColor.YELLOW + current.claimedByName() + ChatColor.RED + ".");
            return;
        }

        long claimAt = System.currentTimeMillis();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int updatedRows = 0;
            try (Connection con = databaseManager.getConnection();
                 PreparedStatement stmt = con.prepareStatement(buildClaimSql(override))) {
                stmt.setString(1, staff.getUniqueId().toString());
                stmt.setString(2, staff.getName());
                stmt.setLong(3, claimAt);
                stmt.setLong(4, reportId);
                updatedRows = stmt.executeUpdate();
            } catch (SQLException exception) {
                plugin.getLogger().severe("[Report] Impossible de prendre en charge le report #" + reportId + " : " + exception.getMessage());
            }

            int finalUpdatedRows = updatedRows;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!plugin.isEnabled()) return;

                if (finalUpdatedRows <= 0) {
                    if (staff.isOnline()) {
                        staff.sendMessage(prefix() + ChatColor.RED + " Le report n'est plus disponible.");
                    }
                    refreshOpenMenus();
                    return;
                }

                openReports.computeIfPresent(reportId, (id, record) -> record.withClaim(staff.getUniqueId(), staff.getName(), claimAt));

                if (staff.isOnline()) {
                    if (current.isClaimed() && !current.isClaimedBy(staff.getUniqueId())) {
                        staff.sendMessage(prefix() + ChatColor.GREEN + " Vous avez repris le report " + ChatColor.GOLD + "#" + reportId + ChatColor.GREEN + ".");
                    } else {
                        staff.sendMessage(prefix() + ChatColor.GREEN + " Vous avez pris en charge le report " + ChatColor.GOLD + "#" + reportId + ChatColor.GREEN + ".");
                    }
                }

                refreshOpenMenus();
            });
        });
    }

    public void closeReport(CommandSender actor, long reportId) {
        if (!isReady()) {
            actor.sendMessage(prefix() + ChatColor.RED + " Le systeme de report se charge encore.");
            return;
        }

        ReportRecord current = openReports.get(reportId);
        if (current == null) {
            actor.sendMessage(prefix() + ChatColor.RED + " Ce report n'existe pas ou a deja ete ferme.");
            return;
        }

        boolean override = !(actor instanceof Player) || actor.hasPermission(OVERRIDE_PERMISSION);
        UUID actorUuid = actor instanceof Player player ? player.getUniqueId() : null;
        String actorName = actor instanceof Player player ? player.getName() : "Console";

        if (!override) {
            if (!current.isClaimed()) {
                actor.sendMessage(prefix() + ChatColor.RED + " Ce report doit d'abord etre pris en charge.");
                return;
            }

            if (!current.isClaimedBy(actorUuid)) {
                actor.sendMessage(prefix() + ChatColor.RED + " Ce report est pris en charge par " + ChatColor.YELLOW + current.claimedByName() + ChatColor.RED + ".");
                return;
            }
        }

        long closedAt = System.currentTimeMillis();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int updatedRows = 0;
            try (Connection con = databaseManager.getConnection();
                 PreparedStatement stmt = con.prepareStatement(buildCloseSql(override))) {
                stmt.setString(1, actorUuid == null ? null : actorUuid.toString());
                stmt.setString(2, actorName);
                stmt.setLong(3, closedAt);
                stmt.setLong(4, reportId);
                if (!override && actorUuid != null) {
                    stmt.setString(5, actorUuid.toString());
                }
                updatedRows = stmt.executeUpdate();
            } catch (SQLException exception) {
                plugin.getLogger().severe("[Report] Impossible de fermer le report #" + reportId + " : " + exception.getMessage());
            }

            int finalUpdatedRows = updatedRows;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!plugin.isEnabled()) return;

                if (finalUpdatedRows <= 0) {
                    actor.sendMessage(prefix() + ChatColor.RED + " Le report n'est plus disponible.");
                    refreshOpenMenus();
                    return;
                }

                openReports.remove(reportId);
                actor.sendMessage(prefix() + ChatColor.GREEN + " Le report " + ChatColor.GOLD + "#" + reportId + ChatColor.GREEN + " a ete ferme.");
                refreshOpenMenus();
            });
        });
    }

    public void openMenu(Player viewer, int requestedPage) {
        if (!isReady()) {
            viewer.sendMessage(prefix() + ChatColor.RED + " Le systeme de report se charge encore.");
            return;
        }

        ReportMenu.RenderedPage renderedPage = menu.render(requestedPage, getSortedOpenReports());
        openMenuPages.put(viewer.getUniqueId(), renderedPage.page());
        openMenuStates.put(viewer.getUniqueId(), renderedPage);

        if (isMenuTitle(viewer.getOpenInventory().getTitle())) {
            Inventory topInventory = viewer.getOpenInventory().getTopInventory();
            topInventory.setContents(renderedPage.inventory().getContents());
        } else {
            viewer.openInventory(renderedPage.inventory());
        }
    }

    public void handleMenuClick(Player viewer, int rawSlot, ClickType click) {
        ReportMenu.RenderedPage page = openMenuStates.get(viewer.getUniqueId());
        if (page == null) return;

        if (rawSlot == ReportMenu.PREVIOUS_SLOT && page.hasPrevious()) {
            openMenu(viewer, page.page() - 1);
            return;
        }

        if (rawSlot == ReportMenu.NEXT_SLOT && page.hasNext()) {
            openMenu(viewer, page.page() + 1);
            return;
        }

        Long reportId = page.reportSlots().get(rawSlot);
        if (reportId == null) return;

        if (click.isLeftClick()) {
            claimReport(viewer, reportId);
            return;
        }

        if (click.isRightClick()) {
            closeReport(viewer, reportId);
        }
    }

    public void handleMenuClose(UUID viewerUuid) {
        openMenuPages.remove(viewerUuid);
        openMenuStates.remove(viewerUuid);
    }

    public void cleanupExpiredReportsAsync() {
        if (!isReady()) return;

        long cutoff = System.currentTimeMillis() - REPORT_LIFETIME_MS;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean sqlSuccess = purgeExpiredReports(cutoff);

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!plugin.isEnabled() || !sqlSuccess) return;

                boolean removed = openReports.entrySet().removeIf(entry -> entry.getValue().createdAt() < cutoff);
                if (removed) {
                    refreshOpenMenus();
                }
            });
        });
    }

    public ResolvedTarget resolveTarget(Player reporter, String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        NicknameManager nicknameManager = getNicknameManager();
        if (nicknameManager != null) {
            Player resolved = nicknameManager.resolveTargetForViewer(reporter, input);
            if (resolved != null) {
                return new ResolvedTarget(resolved.getUniqueId(), resolved.getName());
            }
        }

        Player online = Bukkit.getPlayerExact(input);
        if (online == null) {
            online = Bukkit.getPlayer(input);
        }
        if (online != null) {
            return new ResolvedTarget(online.getUniqueId(), online.getName());
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(input);
        if (!offline.isOnline() && !offline.hasPlayedBefore()) {
            return null;
        }

        String resolvedName = offline.getName() == null || offline.getName().isBlank() ? input : offline.getName();
        return new ResolvedTarget(offline.getUniqueId(), resolvedName);
    }

    public List<String> getTargetSuggestions(Player viewer, String token) {
        String loweredToken = token == null ? "" : token.toLowerCase(Locale.ROOT);
        TreeSet<String> suggestions = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        NicknameManager nicknameManager = getNicknameManager();

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(online) && !viewer.canSee(online)) continue;

            suggestions.add(online.getName());
            if (nicknameManager != null) {
                suggestions.add(nicknameManager.getDisplayedName(online));
            }
        }

        List<String> filtered = new ArrayList<>();
        for (String suggestion : suggestions) {
            if (suggestion.toLowerCase(Locale.ROOT).startsWith(loweredToken)) {
                filtered.add(suggestion);
            }
        }
        return filtered;
    }

    public List<String> getOpenReportIdSuggestions(String token) {
        String loweredToken = token == null ? "" : token.toLowerCase(Locale.ROOT);
        List<String> suggestions = new ArrayList<>();
        for (ReportRecord record : getSortedOpenReports()) {
            String id = String.valueOf(record.id());
            if (id.toLowerCase(Locale.ROOT).startsWith(loweredToken)) {
                suggestions.add(id);
            }
        }
        return suggestions;
    }

    private void startCleanupTask() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }

        cleanupTask = new ReportCleanupTask(this);
        cleanupTask.runTaskTimer(plugin, CLEANUP_INTERVAL_TICKS, CLEANUP_INTERVAL_TICKS);
    }

    private void refreshOpenMenus() {
        if (openMenuStates.isEmpty()) return;

        for (UUID viewerUuid : new ArrayList<>(openMenuStates.keySet())) {
            Player viewer = Bukkit.getPlayer(viewerUuid);
            if (viewer == null || !viewer.isOnline() || !isMenuTitle(viewer.getOpenInventory().getTitle())) {
                handleMenuClose(viewerUuid);
                continue;
            }

            int page = openMenuPages.getOrDefault(viewerUuid, 0);
            openMenu(viewer, page);
        }
    }

    private List<ReportRecord> getSortedOpenReports() {
        List<ReportRecord> reports = new ArrayList<>(openReports.values());
        reports.sort(Comparator
                .comparingLong(ReportRecord::createdAt)
                .thenComparingLong(ReportRecord::id)
                .reversed());
        return reports;
    }

    private void notifyStaff(ReportRecord record) {
        String message = buildNotification(record);
        boolean playSound = plugin.getConfig().getBoolean("report.notify-sound", true);

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.hasPermission(VIEW_PERMISSION)) continue;

            online.sendMessage(message);
            if (playSound) {
                online.playSound(online.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.2F);
            }
        }
    }

    private String buildNotification(ReportRecord record) {
        String template = plugin.getConfig().getString(
                "report.format-notification",
                "&c[Nouveau report] &e#{id} &7| &b{reporter} &7-> &6{target} &7: &f{reason}"
        );
        return prefix() + applyPlaceholders(template, record);
    }

    private String buildConfirmation(ReportRecord record) {
        String template = plugin.getConfig().getString(
                "report.format-confirmation",
                "&aVotre report &e#{id} &acontre &6{target} &aa bien ete envoye."
        );
        return prefix() + applyPlaceholders(template, record);
    }

    private String applyPlaceholders(String template, ReportRecord record) {
        String parsed = template
                .replace("{id}", String.valueOf(record.id()))
                .replace("{reporter}", safe(record.reporterName()))
                .replace("{target}", safe(record.targetName()))
                .replace("{reason}", safe(record.reason()));
        return color(parsed);
    }

    private void insertReport(Connection con, ReportRecord record) throws SQLException {
        try (PreparedStatement stmt = con.prepareStatement(
                "INSERT INTO reports (" +
                        "id, reporter_uuid, reporter_name, target_uuid, target_name, reason, created_at, " +
                        "claimed_by_uuid, claimed_by_name, claimed_at, closed_by_uuid, closed_by_name, closed_at" +
                        ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        )) {
            stmt.setLong(1, record.id());
            stmt.setString(2, record.reporterUuid().toString());
            stmt.setString(3, record.reporterName());
            stmt.setString(4, record.targetUuid() == null ? null : record.targetUuid().toString());
            stmt.setString(5, record.targetName());
            stmt.setString(6, record.reason());
            stmt.setLong(7, record.createdAt());
            stmt.setString(8, null);
            stmt.setString(9, null);
            stmt.setLong(10, 0L);
            stmt.setString(11, null);
            stmt.setString(12, null);
            stmt.setLong(13, 0L);
            stmt.executeUpdate();
        }
    }

    private void updateCooldown(Connection con, UUID reporterUuid, long lastReportAt) throws SQLException {
        boolean updated;
        try (PreparedStatement stmt = con.prepareStatement(
                "UPDATE report_cooldowns SET last_report_at = ? WHERE reporter_uuid = ?"
        )) {
            stmt.setLong(1, lastReportAt);
            stmt.setString(2, reporterUuid.toString());
            updated = stmt.executeUpdate() > 0;
        }

        if (!updated) {
            try (PreparedStatement stmt = con.prepareStatement(
                    "INSERT INTO report_cooldowns (reporter_uuid, last_report_at) VALUES (?, ?)"
            )) {
                stmt.setString(1, reporterUuid.toString());
                stmt.setLong(2, lastReportAt);
                stmt.executeUpdate();
            }
        }
    }

    private void createTablesIfNeeded() {
        try (Connection con = databaseManager.getConnection();
             PreparedStatement reportsStmt = con.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS reports (" +
                             "id BIGINT PRIMARY KEY," +
                             "reporter_uuid VARCHAR(36) NOT NULL," +
                             "reporter_name VARCHAR(64) NOT NULL," +
                             "target_uuid VARCHAR(36)," +
                             "target_name VARCHAR(64) NOT NULL," +
                             "reason TEXT NOT NULL," +
                             "created_at BIGINT NOT NULL," +
                             "claimed_by_uuid VARCHAR(36)," +
                             "claimed_by_name VARCHAR(64)," +
                             "claimed_at BIGINT NOT NULL DEFAULT 0," +
                             "closed_by_uuid VARCHAR(36)," +
                             "closed_by_name VARCHAR(64)," +
                             "closed_at BIGINT NOT NULL DEFAULT 0" +
                             ")"
             );
             PreparedStatement cooldownStmt = con.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS report_cooldowns (" +
                             "reporter_uuid VARCHAR(36) PRIMARY KEY," +
                             "last_report_at BIGINT NOT NULL" +
                             ")"
             )) {
            reportsStmt.executeUpdate();
            cooldownStmt.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().severe("[Report] Impossible de creer les tables SQL : " + exception.getMessage());
        }
    }

    private boolean purgeExpiredReports(long cutoff) {
        try (Connection con = databaseManager.getConnection();
             PreparedStatement stmt = con.prepareStatement("DELETE FROM reports WHERE created_at < ?")) {
            stmt.setLong(1, cutoff);
            stmt.executeUpdate();
            return true;
        } catch (SQLException exception) {
            plugin.getLogger().severe("[Report] Impossible de purger les reports expires : " + exception.getMessage());
            return false;
        }
    }

    private List<ReportRecord> loadOpenReports(long cutoff) {
        List<ReportRecord> reports = new ArrayList<>();

        try (Connection con = databaseManager.getConnection();
             PreparedStatement stmt = con.prepareStatement(
                     "SELECT id, reporter_uuid, reporter_name, target_uuid, target_name, reason, created_at, claimed_by_uuid, claimed_by_name, claimed_at " +
                             "FROM reports WHERE closed_at = 0 AND created_at >= ?"
             )) {
            stmt.setLong(1, cutoff);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                UUID reporterUuid = parseUuid(rs.getString("reporter_uuid"));
                if (reporterUuid == null) continue;

                reports.add(new ReportRecord(
                        rs.getLong("id"),
                        reporterUuid,
                        rs.getString("reporter_name"),
                        parseUuid(rs.getString("target_uuid")),
                        rs.getString("target_name"),
                        rs.getString("reason"),
                        rs.getLong("created_at"),
                        parseUuid(rs.getString("claimed_by_uuid")),
                        rs.getString("claimed_by_name"),
                        rs.getLong("claimed_at")
                ));
            }
        } catch (SQLException exception) {
            plugin.getLogger().severe("[Report] Impossible de charger les reports actifs : " + exception.getMessage());
        }

        return reports;
    }

    private Map<UUID, Long> loadCooldowns() {
        Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

        try (Connection con = databaseManager.getConnection();
             PreparedStatement stmt = con.prepareStatement("SELECT reporter_uuid, last_report_at FROM report_cooldowns")) {
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                UUID uuid = parseUuid(rs.getString("reporter_uuid"));
                if (uuid == null) continue;
                cooldowns.put(uuid, rs.getLong("last_report_at"));
            }
        } catch (SQLException exception) {
            plugin.getLogger().severe("[Report] Impossible de charger les cooldowns de reports : " + exception.getMessage());
        }

        return cooldowns;
    }

    private long loadNextReportId() {
        try (Connection con = databaseManager.getConnection();
             PreparedStatement stmt = con.prepareStatement("SELECT MAX(id) AS max_id FROM reports")) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getLong("max_id") + 1L;
            }
        } catch (SQLException exception) {
            plugin.getLogger().severe("[Report] Impossible de calculer le prochain identifiant de report : " + exception.getMessage());
        }

        return 1L;
    }

    private String buildClaimSql(boolean override) {
        if (override) {
            return "UPDATE reports SET claimed_by_uuid = ?, claimed_by_name = ?, claimed_at = ? WHERE id = ? AND closed_at = 0";
        }
        return "UPDATE reports SET claimed_by_uuid = ?, claimed_by_name = ?, claimed_at = ? WHERE id = ? AND closed_at = 0 AND claimed_by_uuid IS NULL";
    }

    private String buildCloseSql(boolean override) {
        if (override) {
            return "UPDATE reports SET closed_by_uuid = ?, closed_by_name = ?, closed_at = ? WHERE id = ? AND closed_at = 0";
        }
        return "UPDATE reports SET closed_by_uuid = ?, closed_by_name = ?, closed_at = ? WHERE id = ? AND closed_at = 0 AND claimed_by_uuid = ?";
    }

    private long getRemainingCooldownMillis(UUID uuid, long now, long cooldownSeconds) {
        if (cooldownSeconds <= 0L) return 0L;

        long lastReportAt = lastReportAtByPlayer.getOrDefault(uuid, 0L);
        long expiresAt = lastReportAt + (cooldownSeconds * 1000L);
        return Math.max(0L, expiresAt - now);
    }

    private NicknameManager getNicknameManager() {
        Module module = plugin.getModuleManager().get("nickname");
        if (!(module instanceof NicknameModule nicknameModule) || !module.isEnabled()) {
            return null;
        }
        return nicknameModule.getManager();
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String sanitizeReason(String rawReason) {
        if (rawReason == null) return null;

        String sanitized = ChatColor.stripColor(rawReason)
                .replace('\n', ' ')
                .replace('\r', ' ')
                .trim()
                .replaceAll("\\s{2,}", " ");
        return sanitized.isEmpty() ? null : sanitized;
    }

    private String safe(String value) {
        return value == null ? "inconnu" : value;
    }

    private String formatDuration(long millis) {
        long totalSeconds = Math.max(0L, millis / 1000L);
        long days = totalSeconds / 86400L;
        long hours = (totalSeconds % 86400L) / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        if (days > 0) return days + "j " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + seconds + "s";
        return seconds + "s";
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }

    private String prefix() {
        return color(plugin.getConfig().getString("prefix", "&f[&bAevumCore&f]"));
    }

    public record ResolvedTarget(UUID uuid, String name) {
    }
}
