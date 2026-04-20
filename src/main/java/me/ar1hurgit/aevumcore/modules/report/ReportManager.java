package me.ar1hurgit.aevumcore.modules.report;

import me.ar1hurgit.aevumcore.AevumCore;
import me.ar1hurgit.aevumcore.core.text.DurationFormatter;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
    private final ReportRepository repository;
    private final ReportTargetResolver targetResolver;

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
        this.repository = new ReportRepository(databaseManager, plugin.getLogger());
        this.targetResolver = new ReportTargetResolver(plugin);
    }

    public void enable() {
        ready = false;

        long cutoff = System.currentTimeMillis() - REPORT_LIFETIME_MS;
        databaseManager.supplyAsync(() -> repository.loadBootstrapState(cutoff))
                .whenComplete((bootstrapState, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!plugin.isEnabled() || throwable != null) {
                        if (throwable != null) {
                            plugin.getLogger().severe("[Report] Initialisation impossible : " + throwable.getMessage());
                        }
                        return;
                    }

                    openReports.clear();
                    for (ReportRecord record : bootstrapState.openReports()) {
                        openReports.put(record.id(), record);
                    }

                    lastReportAtByPlayer.clear();
                    lastReportAtByPlayer.putAll(bootstrapState.cooldowns());

                    nextReportId.set(bootstrapState.nextReportId());
                    ready = true;
                    startCleanupTask();
                }));
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

        databaseManager.supplyAsync(() -> repository.storeNewReport(record))
                .whenComplete((success, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    pendingSubmitters.remove(reporter.getUniqueId());
                    if (!plugin.isEnabled()) return;

                    if (throwable != null || !Boolean.TRUE.equals(success)) {
                        if (throwable != null) {
                            plugin.getLogger().severe("[Report] Echec creation report #" + record.id() + " : " + throwable.getMessage());
                        }
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
                }));
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
        databaseManager.supplyAsync(() -> repository.claimReport(reportId, staff.getUniqueId(), staff.getName(), claimAt, override))
                .whenComplete((updatedRows, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!plugin.isEnabled()) return;

                    if (throwable != null || updatedRows == null || updatedRows <= 0) {
                        if (throwable != null) {
                            plugin.getLogger().severe("[Report] Echec prise en charge report #" + reportId + " : " + throwable.getMessage());
                        }
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
                }));
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
        databaseManager.supplyAsync(() -> repository.closeReport(reportId, actorUuid, actorName, closedAt, override))
                .whenComplete((updatedRows, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!plugin.isEnabled()) return;

                    if (throwable != null || updatedRows == null || updatedRows <= 0) {
                        if (throwable != null) {
                            plugin.getLogger().severe("[Report] Echec fermeture report #" + reportId + " : " + throwable.getMessage());
                        }
                        actor.sendMessage(prefix() + ChatColor.RED + " Le report n'est plus disponible.");
                        refreshOpenMenus();
                        return;
                    }

                    openReports.remove(reportId);
                    actor.sendMessage(prefix() + ChatColor.GREEN + " Le report " + ChatColor.GOLD + "#" + reportId + ChatColor.GREEN + " a ete ferme.");
                    refreshOpenMenus();
                }));
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
        databaseManager.supplyAsync(() -> repository.purgeExpiredReports(cutoff))
                .whenComplete((sqlSuccess, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!plugin.isEnabled() || throwable != null || !Boolean.TRUE.equals(sqlSuccess)) {
                        if (throwable != null) {
                            plugin.getLogger().severe("[Report] Echec purge reports expires : " + throwable.getMessage());
                        }
                        return;
                    }

                    boolean removed = openReports.entrySet().removeIf(entry -> entry.getValue().createdAt() < cutoff);
                    if (removed) {
                        refreshOpenMenus();
                    }
                }));
    }

    public ResolvedTarget resolveTarget(Player reporter, String input) {
        return targetResolver.resolveTarget(reporter, input);
    }

    public List<String> getTargetSuggestions(Player viewer, String token) {
        return targetResolver.getTargetSuggestions(viewer, token);
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

    private long getRemainingCooldownMillis(UUID uuid, long now, long cooldownSeconds) {
        return ReportCooldownPolicy.getRemainingCooldownMillis(
                lastReportAtByPlayer.getOrDefault(uuid, 0L),
                now,
                cooldownSeconds
        );
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
        return DurationFormatter.formatCompact(millis);
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
