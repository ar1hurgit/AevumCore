package me.ar1hurgit.aevumcore.modules.chat;

import me.ar1hurgit.aevumcore.AevumCore;
import me.ar1hurgit.aevumcore.core.command.CommandBindings;
import me.ar1hurgit.aevumcore.core.module.AbstractModule;
import me.ar1hurgit.aevumcore.core.module.Module;
import me.ar1hurgit.aevumcore.modules.nickname.NicknameManager;
import me.ar1hurgit.aevumcore.modules.nickname.NicknameModule;
import me.ar1hurgit.aevumcore.storage.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatModule extends AbstractModule {

    private final AevumCore plugin;
    private final DatabaseManager databaseManager;
    private final ChatRoyalMailRepository royalMailRepository;
    private final ChatLogService chatLogService;
    private final Object royalNotifLock = new Object();
    private final Set<UUID> spyEnabledPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> hrpMaskedPlayers = ConcurrentHashMap.newKeySet();

    private volatile long lastRoyalNotifAt = 0L;

    public ChatModule(AevumCore plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        this.royalMailRepository = new ChatRoyalMailRepository(plugin.getDatabaseManager(), plugin.getLogger());
        this.chatLogService = new ChatLogService(plugin);
    }

    @Override
    public String getName() {
        return "chat";
    }

    @Override
    protected void onEnable() {
        if (!getBoolean("enabled", true)) {
            return;
        }

        createMailboxTableAsync();

        ChatListener listener = new ChatListener(plugin, this);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        MessageCommand messageCommand = new MessageCommand(this);
        CommandBindings.bind(plugin, "msg", messageCommand, messageCommand);
        CommandBindings.bind(plugin, "tell", messageCommand, messageCommand);
        CommandBindings.bind(plugin, "w", messageCommand, messageCommand);

        StaffChatCommand staffChatCommand = new StaffChatCommand(this);
        CommandBindings.bind(plugin, "staffchat", staffChatCommand);

        AnnounceCommand announceCommand = new AnnounceCommand(this);
        CommandBindings.bind(plugin, "annonce", announceCommand, announceCommand);
        CommandBindings.bind(plugin, "anrp", announceCommand);
        CommandBindings.bind(plugin, "anhrp", announceCommand);

        SpyCommand spyCommand = new SpyCommand(this);
        CommandBindings.bind(plugin, "spy", spyCommand, spyCommand);

        HrpCommand hrpCommand = new HrpCommand(this);
        CommandBindings.bind(plugin, "hrp", hrpCommand, hrpCommand);

        Bukkit.getLogger().info(plugin.getConfig().getString("prefix", "[AevumCore]") + " Chat module enabled");
    }

    @Override
    protected void onDisable() {
        spyEnabledPlayers.clear();
        hrpMaskedPlayers.clear();
        Bukkit.getLogger().info(plugin.getConfig().getString("prefix", "[AevumCore]") + " Chat module disabled");
    }

    public void handlePublicChat(Player sender, String rawMessage) {
        if (sender == null || rawMessage == null) {
            return;
        }

        String trimmed = rawMessage.trim();
        if (trimmed.isEmpty()) {
            return;
        }

        if (isHrpMasked(sender.getUniqueId())) {
            sender.sendMessage(color(getString("message-hrp-mask-blocked", "&cVous etes en HRP mask. Faites /hrp unmask pour reparler.")));
            return;
        }

        String displayName = getDisplayName(sender);
        String senderPrefix = "";

        if (trimmed.startsWith("*")) {
            String action = trimmed.substring(1).trim();
            if (action.isEmpty()) {
                sender.sendMessage(prefix() + ChatColor.RED + " Message RP vide.");
                return;
            }

            Map<String, String> placeholders = basePlaceholders(sender, displayName, senderPrefix);
            placeholders.put("action", applyMentions(action));
            placeholders.put("message", applyMentions(action));

            String template = getString("format-rp", "&d* {player} {action} *");
            String formatted = format(template, placeholders);

            Bukkit.broadcastMessage(formatted);
            logLine("RP", sender.getName(), ChatColor.stripColor(action));
            return;
        }

        Map<String, String> placeholders = basePlaceholders(sender, displayName, senderPrefix);
        placeholders.put("message", applyMentions(rawMessage));

        String template = getString("format-hrp", "&7[HRP] {player}&8: &f{message}");
        String formatted = format(template, placeholders);
        for (Player recipient : Bukkit.getOnlinePlayers()) {
            if (isHrpMasked(recipient.getUniqueId())) {
                continue;
            }
            recipient.sendMessage(formatted);
        }

        logLine("HRP", sender.getName(), ChatColor.stripColor(rawMessage));
    }

    public String buildJoinMessage(Player player) {
        String template = getString("message-connexion", "&a[+] {player}");
        if (template == null || template.isBlank()) {
            return null;
        }

        return format(template, basePlaceholders(player, getDisplayName(player), ""));
    }

    public String buildQuitMessage(Player player) {
        String template = getString("message-deconnexion", "&c[-] {player}");
        if (template == null || template.isBlank()) {
            return null;
        }

        return format(template, basePlaceholders(player, getDisplayName(player), ""));
    }

    public String resolveSenderName(CommandSender sender) {
        if (sender instanceof Player player) {
            return getDisplayName(player);
        }
        return sender.getName();
    }

    public String getDisplayName(Player player) {
        NicknameManager nicknameManager = getNicknameManager();
        if (nicknameManager == null) {
            return player.getName();
        }
        return nicknameManager.getDisplayedName(player);
    }

    public Player resolveOnlineTarget(CommandSender sender, String input) {
        NicknameManager nicknameManager = getNicknameManager();
        if (sender instanceof Player viewer && nicknameManager != null) {
            Player resolved = nicknameManager.resolveTargetForViewer(viewer, input);
            if (resolved != null) {
                return resolved;
            }
        }

        Player target = Bukkit.getPlayerExact(input);
        if (target != null) {
            return target;
        }

        return Bukkit.getPlayer(input);
    }

    public List<String> getOnlineNameSuggestions(CommandSender sender, String token) {
        String loweredToken = token == null ? "" : token.toLowerCase(Locale.ROOT);
        NicknameManager nicknameManager = getNicknameManager();
        TreeSet<String> suggestions = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        if (sender instanceof Player viewer) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!viewer.equals(online) && !viewer.canSee(online)) {
                    continue;
                }
                suggestions.add(online.getName());
                if (nicknameManager != null) {
                    suggestions.add(nicknameManager.getDisplayedName(online));
                }
            }
        } else {
            for (Player online : Bukkit.getOnlinePlayers()) {
                suggestions.add(online.getName());
                if (nicknameManager != null) {
                    suggestions.add(nicknameManager.getDisplayedName(online));
                }
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

    public void broadcastAnnouncementLines(List<String> templates, Map<String, String> placeholders) {
        if (templates == null || templates.isEmpty()) {
            return;
        }
        for (String template : templates) {
            Bukkit.broadcastMessage(format(template, placeholders));
        }
    }

    public void broadcastSimpleAnnouncement(String category, String templateKey, String defaultTemplate, Map<String, String> placeholders) {
        List<String> lines = getFormatLines(templateKey, defaultTemplate);
        broadcastAnnouncementLines(lines, placeholders);
        logLine(category, placeholders.getOrDefault("sender", "system"), placeholders.getOrDefault("message", ""));
    }

    public long consumeRoyalNotifCooldown(CommandSender sender) {
        if (sender.hasPermission("aevumcore.chat.announce.royaume.notif.bypasscooldown")) {
            return 0L;
        }

        long cooldownMillis = Math.max(0, getInt("annonce-notif-cooldown", 300)) * 1000L;
        if (cooldownMillis <= 0L) {
            return 0L;
        }

        synchronized (royalNotifLock) {
            long now = System.currentTimeMillis();
            long remaining = (lastRoyalNotifAt + cooldownMillis) - now;
            if (remaining > 0L) {
                return remaining;
            }
            lastRoyalNotifAt = now;
            return 0L;
        }
    }

    public int sendRoyalDecree(CommandSender sender, String message) {
        String senderName = resolveSenderName(sender);
        long createdAt = System.currentTimeMillis();

        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        Set<UUID> onlineUuids = new LinkedHashSet<>();
        for (Player online : onlinePlayers) {
            onlineUuids.add(online.getUniqueId());
        }

        LinkedHashSet<UUID> recipients = new LinkedHashSet<>();
        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
            // Exclut les joueurs qui n'avaient jamais rejoint le serveur au moment du decret.
            if (offlinePlayer.hasPlayedBefore() || offlinePlayer.isOnline()) {
                recipients.add(offlinePlayer.getUniqueId());
            }
        }
        recipients.addAll(onlineUuids);

        databaseManager.runAsync(() -> royalMailRepository.storeRoyalDecree(senderName, sanitizeMessage(message), createdAt, recipients, onlineUuids))
                .exceptionally(throwable -> {
                    plugin.getLogger().severe("[Chat] Echec stockage decret royal : " + throwable.getMessage());
                    return null;
                });

        for (Player onlinePlayer : onlinePlayers) {
            sendRoyalDecreeLetter(onlinePlayer, senderName, message, createdAt);
        }

        logLine("DECRET", senderName, ChatColor.stripColor(message));
        return recipients.size();
    }

    public void deliverPendingRoyalDecrees(Player player) {
        if (player == null) {
            return;
        }

        UUID uuid = player.getUniqueId();
        databaseManager.supplyAsync(() -> royalMailRepository.loadPendingRoyalDecrees(uuid))
                .whenComplete((pending, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (throwable != null) {
                        plugin.getLogger().severe("[Chat] Echec lecture boite royale " + uuid + " : " + throwable.getMessage());
                        return;
                    }

                    if (pending.isEmpty() || !player.isOnline()) {
                        return;
                    }

                    List<String> deliveredIds = new ArrayList<>();
                    for (ChatRoyalMailRepository.RoyalMailEntry entry : pending) {
                        sendRoyalDecreeLetter(player, entry.senderName(), entry.content(), entry.createdAt());
                        deliveredIds.add(entry.mailId());
                    }

                    if (!deliveredIds.isEmpty()) {
                        markRoyalMailDeliveredAsync(deliveredIds);
                    }
                }));
    }

    public Sound getRpAnnouncementSound() {
        String rawSound = getString("annonce-rp-son", "BLOCK_BELL_USE");
        try {
            return Sound.valueOf(rawSound.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return Sound.BLOCK_BELL_USE;
        }
    }

    public String applyMentions(String rawMessage) {
        if (rawMessage == null) {
            return "";
        }
        if (!getBoolean("mentions", true)) {
            return rawMessage;
        }

        String mentionTemplate = getString("mentions-format", "&e@{name}&r");
        if (mentionTemplate == null || mentionTemplate.isBlank()) {
            mentionTemplate = "{name}";
        }

        LinkedHashSet<String> knownNames = new LinkedHashSet<>();
        NicknameManager nicknameManager = getNicknameManager();
        for (Player online : Bukkit.getOnlinePlayers()) {
            knownNames.add(online.getName());
            if (nicknameManager != null) {
                knownNames.add(nicknameManager.getDisplayedName(online));
            }
        }

        List<String> sortedNames = new ArrayList<>(knownNames);
        sortedNames.removeIf(Objects::isNull);
        sortedNames.removeIf(String::isBlank);
        sortedNames.sort((left, right) -> Integer.compare(right.length(), left.length()));

        String rendered = rawMessage;
        for (String name : sortedNames) {
            rendered = highlightWholeWord(rendered, name, mentionTemplate);
        }
        return rendered;
    }

    public void logLine(String category, String actor, String content) {
        if (!getBoolean("log-chat", false)) {
            return;
        }
        chatLogService.append(category, actor, content);
    }

    public boolean setSpyEnabled(UUID playerUuid, boolean enabled) {
        if (playerUuid == null) {
            return false;
        }
        if (enabled) {
            spyEnabledPlayers.add(playerUuid);
            return true;
        }
        spyEnabledPlayers.remove(playerUuid);
        return false;
    }

    public boolean toggleSpy(UUID playerUuid) {
        if (isSpyEnabled(playerUuid)) {
            spyEnabledPlayers.remove(playerUuid);
            return false;
        }
        spyEnabledPlayers.add(playerUuid);
        return true;
    }

    public boolean isSpyEnabled(UUID playerUuid) {
        return playerUuid != null && spyEnabledPlayers.contains(playerUuid);
    }

    public boolean setHrpMasked(UUID playerUuid, boolean masked) {
        if (playerUuid == null) {
            return false;
        }
        if (masked) {
            hrpMaskedPlayers.add(playerUuid);
            return true;
        }
        hrpMaskedPlayers.remove(playerUuid);
        return false;
    }

    public boolean isHrpMasked(UUID playerUuid) {
        return playerUuid != null && hrpMaskedPlayers.contains(playerUuid);
    }

    public void broadcastSpyPrivateMessage(CommandSender sender, Player target, String rawMessage) {
        if (target == null || rawMessage == null || rawMessage.isBlank()) {
            return;
        }

        String senderName = resolveSenderName(sender);
        String targetName = getDisplayName(target);

        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("sender", senderName);
        placeholders.put("target", targetName);
        placeholders.put("message", applyMentions(rawMessage));

        String template = getString("format-spy", "&8[SPY] {sender} &7-> &8{target}&7: &f{message}");
        String formatted = format(template, placeholders);

        UUID senderUuid = sender instanceof Player player ? player.getUniqueId() : null;
        UUID targetUuid = target.getUniqueId();

        for (Player online : Bukkit.getOnlinePlayers()) {
            UUID viewerUuid = online.getUniqueId();
            if (viewerUuid.equals(targetUuid) || (senderUuid != null && viewerUuid.equals(senderUuid))) {
                continue;
            }
            if (!online.hasPermission("aevumcore.chat.spy") || !isSpyEnabled(viewerUuid)) {
                continue;
            }
            online.sendMessage(formatted);
        }
    }

    public String rewriteMessageWithDisplayNames(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return rawMessage;
        }

        NicknameManager nicknameManager = getNicknameManager();
        if (nicknameManager == null) {
            return rawMessage;
        }

        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        onlinePlayers.sort((left, right) -> Integer.compare(right.getName().length(), left.getName().length()));

        String rendered = rawMessage;
        for (Player online : onlinePlayers) {
            String realName = online.getName();
            String displayed = nicknameManager.getDisplayedName(online);
            if (displayed == null || displayed.isBlank() || displayed.equals(realName)) {
                continue;
            }
            rendered = replaceWholeWord(rendered, realName, displayed);
        }
        return rendered;
    }

    public String getString(String key, String defaultValue) {
        String chatPath = "chat." + key;
        if (plugin.getConfig().contains(chatPath)) {
            return plugin.getConfig().getString(chatPath, defaultValue);
        }
        return plugin.getConfig().getString(key, defaultValue);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String chatPath = "chat." + key;
        if (plugin.getConfig().contains(chatPath)) {
            return plugin.getConfig().getBoolean(chatPath, defaultValue);
        }
        return plugin.getConfig().getBoolean(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        String chatPath = "chat." + key;
        if (plugin.getConfig().contains(chatPath)) {
            return plugin.getConfig().getInt(chatPath, defaultValue);
        }
        return plugin.getConfig().getInt(key, defaultValue);
    }

    public List<String> getFormatLines(String key, String defaultLine) {
        List<String> lines = getStringList(key);
        if (lines.isEmpty()) {
            String single = getString(key, defaultLine);
            if (single == null || single.isBlank()) {
                return Collections.emptyList();
            }
            return Collections.singletonList(single);
        }
        return lines;
    }

    public String format(String template, Map<String, String> placeholders) {
        String rendered = template == null ? "" : template;
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                rendered = rendered.replace("{" + entry.getKey() + "}", safe(entry.getValue()));
            }
        }
        return color(rendered);
    }

    public String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }

    public String prefix() {
        return color(plugin.getConfig().getString("prefix", "&f[&bAevumCore&f]")) + ChatColor.RESET;
    }

    private Map<String, String> basePlaceholders(Player sender, String displayName, String senderPrefix) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("player", safe(displayName));
        placeholders.put("sender", safe(displayName));
        placeholders.put("prefix", safe(senderPrefix));
        placeholders.put("real_player", sender == null ? "" : sender.getName());
        return placeholders;
    }

    private List<String> getStringList(String key) {
        String chatPath = "chat." + key;
        if (plugin.getConfig().contains(chatPath) && plugin.getConfig().isList(chatPath)) {
            return new ArrayList<>(plugin.getConfig().getStringList(chatPath));
        }
        if (plugin.getConfig().contains(key) && plugin.getConfig().isList(key)) {
            return new ArrayList<>(plugin.getConfig().getStringList(key));
        }
        return new ArrayList<>();
    }

    private NicknameManager getNicknameManager() {
        Module module = plugin.getModuleManager().get("nickname");
        if (!(module instanceof NicknameModule nicknameModule) || !module.isEnabled()) {
            return null;
        }
        return nicknameModule.getManager();
    }

    private void createMailboxTableAsync() {
        databaseManager.runAsync(royalMailRepository::initializeSchema)
                .exceptionally(throwable -> {
                    plugin.getLogger().severe("[Chat] Echec initialisation mailbox royale : " + throwable.getMessage());
                    return null;
                });
    }

    private void markRoyalMailDeliveredAsync(List<String> mailIds) {
        databaseManager.runAsync(() -> royalMailRepository.markDelivered(mailIds))
                .exceptionally(throwable -> {
                    plugin.getLogger().severe("[Chat] Echec marquage courrier royal lu : " + throwable.getMessage());
                    return null;
                });
    }

    private void sendRoyalDecreeLetter(Player target, String senderName, String message, long createdAt) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("player", getDisplayName(target));
        placeholders.put("sender", safe(senderName));
        placeholders.put("message", safe(message));
        placeholders.put("date", formatDate(createdAt));

        List<String> lines = getFormatLines(
                "format-annonce-royaume-decret",
                "&6[Decret Royal] &e{message}"
        );

        for (String line : lines) {
            target.sendMessage(format(line, placeholders));
        }
    }

    private String highlightWholeWord(String source, String target, String mentionTemplate) {
        if (source == null || source.isEmpty() || target == null || target.isEmpty()) {
            return source;
        }

        Pattern pattern = Pattern.compile(
                "(?i)(?<![A-Za-z0-9_])" + Pattern.quote(target) + "(?![A-Za-z0-9_])"
        );

        Matcher matcher = pattern.matcher(source);
        StringBuffer rendered = new StringBuffer();
        while (matcher.find()) {
            String matched = matcher.group();
            String replacement = mentionTemplate.replace("{name}", matched);
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    private String replaceWholeWord(String source, String target, String replacementValue) {
        if (source == null || source.isEmpty() || target == null || target.isEmpty()) {
            return source;
        }

        Pattern pattern = Pattern.compile(
                "(?i)(?<![A-Za-z0-9_])" + Pattern.quote(target) + "(?![A-Za-z0-9_])"
        );

        Matcher matcher = pattern.matcher(source);
        StringBuffer rendered = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(replacementValue));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    private String formatDate(long timestamp) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(
                    getString("annonce-date-format", "dd/MM/yyyy HH:mm"),
                    Locale.ROOT
            ).withZone(ZoneId.systemDefault());
            return formatter.format(Instant.ofEpochMilli(timestamp));
        } catch (IllegalArgumentException exception) {
            return DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ROOT)
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.ofEpochMilli(timestamp));
        }
    }

    private String sanitizeMessage(String message) {
        if (message == null) {
            return "";
        }
        return message.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
