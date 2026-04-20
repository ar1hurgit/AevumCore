package me.ar1hurgit.aevumcore.modules.vanish;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import me.ar1hurgit.aevumcore.AevumCore;
import me.ar1hurgit.aevumcore.core.module.Module;
import me.ar1hurgit.aevumcore.modules.nickname.NicknameManager;
import me.ar1hurgit.aevumcore.modules.nickname.NicknameModule;
import me.ar1hurgit.aevumcore.storage.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VanishManager {

    private static final String MENU_TITLE = ChatColor.DARK_AQUA + "Vanish Menu";
    private static final double SOUND_FALLBACK_RADIUS = 2.4D;
    private static final double SOUND_ACTIVITY_RADIUS = 3.2D;
    private static final long SOUND_ACTIVITY_WINDOW_MS = 1800L;

    private final AevumCore plugin;
    private final DatabaseManager databaseManager;
    private final Set<UUID> vanishedPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> knownNames = new ConcurrentHashMap<>();
    private final Map<UUID, GameMode> guiModeBackup = new ConcurrentHashMap<>();
    private final Map<UUID, Map<Integer, UUID>> menuTargets = new ConcurrentHashMap<>();
    private final Set<UUID> skipSpectatorNextOpen = ConcurrentHashMap.newKeySet();
    private final Map<UUID, SoundMarker> recentSoundMarkers = new ConcurrentHashMap<>();
    private final VanishDataStore dataStore;

    private ProtocolManager protocolManager;
    private final List<PacketAdapter> protocolListeners = new ArrayList<>();
    private PacketType playerInfoRemovePacketType;

    public VanishManager(AevumCore plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.dataStore = new VanishDataStore(plugin, databaseManager);
    }

    public void enable() {
        loadData();
        setupProtocolLib();

        for (Player player : Bukkit.getOnlinePlayers()) {
            updateKnownName(player, player.getPlayerListName());
            if (isVanished(player.getUniqueId())) {
                applyRuntimeState(player, true);
                refreshVisibilityForTarget(player);
            }
        }
    }

    public void disable() {
        for (UUID uuid : new HashSet<>(vanishedPlayers)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                applyRuntimeState(player, false);
                showToEveryone(player);
            }
        }

        if (protocolManager != null) {
            for (PacketAdapter listener : protocolListeners) {
                protocolManager.removePacketListener(listener);
            }
        }
        protocolListeners.clear();

        saveDataSync();
        guiModeBackup.clear();
        menuTargets.clear();
        skipSpectatorNextOpen.clear();
        recentSoundMarkers.clear();
    }

    public boolean isVanished(UUID uuid) {
        return vanishedPlayers.contains(uuid);
    }

    public boolean canSeeVanished(CommandSender sender) {
        return sender.hasPermission("aevumcore.vanish.see");
    }

    public void toggleVanish(Player target, CommandSender actor) {
        setVanish(target, !isVanished(target.getUniqueId()), actor);
    }

    public void setVanish(Player target, boolean vanish, CommandSender actor) {
        UUID uuid = target.getUniqueId();
        updateKnownName(target, target.getPlayerListName());

        if (vanish) {
            vanishedPlayers.add(uuid);
        } else {
            vanishedPlayers.remove(uuid);
        }

        applyRuntimeState(target, vanish);
        refreshVisibilityForTarget(target);
        syncNicknameVisualState(target, vanish);
        saveData();

        if (actor != null) {
            String prefix = prefix();
            if (actor != target) {
                actor.sendMessage(prefix + ChatColor.GRAY + target.getName() + (vanish ? ChatColor.GREEN + " est maintenant vanish." : ChatColor.YELLOW + " n'est plus vanish."));
            }
            target.sendMessage(prefix + (vanish ? ChatColor.GREEN + " Vous etes maintenant en vanish." : ChatColor.YELLOW + " Vous n'etes plus en vanish."));
        }

        if (plugin.getConfig().getBoolean("vanish.notify-staff", true)) {
            notifyStaff(prefix() + ChatColor.AQUA + target.getName() + (vanish ? " est entre en vanish." : " est sorti du vanish."));
        }
    }

    public void handleJoin(Player player) {
        updateKnownName(player, player.getPlayerListName());

        if (isVanished(player.getUniqueId())) {
            applyRuntimeState(player, true);
            refreshVisibilityForTarget(player);
        }

        refreshVisibilityForViewer(player);
    }

    public void handleQuit(Player player) {
        UUID uuid = player.getUniqueId();
        updateKnownName(player, player.getPlayerListName());
        menuTargets.remove(uuid);
        guiModeBackup.remove(uuid);
        skipSpectatorNextOpen.remove(uuid);
        recentSoundMarkers.remove(uuid);
    }

    public boolean shouldHideJoinMessage(Player player) {
        return isVanished(player.getUniqueId()) && plugin.getConfig().getBoolean("vanish.hide-join-message", true);
    }

    public Set<UUID> getVanishedSnapshot() {
        return new HashSet<>(vanishedPlayers);
    }

    public String getKnownName(UUID uuid) {
        String cached = knownNames.get(uuid);
        if (cached != null) return cached;

        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        if (offline.getName() != null) {
            knownNames.put(uuid, offline.getName());
            return offline.getName();
        }
        return uuid.toString();
    }

    public void updateKnownName(Player player, String preferredName) {
        if (player == null) return;
        String cleaned = ChatColor.stripColor(preferredName);
        if (cleaned == null || cleaned.isBlank()) {
            cleaned = player.getName();
        }
        knownNames.put(player.getUniqueId(), cleaned);
    }

    public void refreshVisibility(Player target) {
        if (target == null) return;
        refreshVisibilityForTarget(target);
    }

    public boolean isVanishedName(String suggestion) {
        String clean = normalizeSuggestion(suggestion);
        if (clean.isEmpty()) return false;

        for (UUID uuid : vanishedPlayers) {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                if (online.getName().equalsIgnoreCase(clean)) {
                    return true;
                }

                String listName = ChatColor.stripColor(online.getPlayerListName());
                if (listName != null && listName.equalsIgnoreCase(clean)) {
                    return true;
                }
            }

            String name = getKnownName(uuid).toLowerCase(Locale.ROOT);
            if (name.equals(clean)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeSuggestion(String suggestion) {
        if (suggestion == null) return "";

        String clean = suggestion.trim();
        if (clean.startsWith("@")) {
            clean = clean.substring(1);
        }

        int spaceIdx = clean.indexOf(' ');
        if (spaceIdx >= 0) {
            clean = clean.substring(0, spaceIdx);
        }

        while (!clean.isEmpty() && !Character.isLetterOrDigit(clean.charAt(clean.length() - 1)) && clean.charAt(clean.length() - 1) != '_') {
            clean = clean.substring(0, clean.length() - 1);
        }

        return clean.toLowerCase(Locale.ROOT);
    }

    public void openMenu(Player viewer) {
        Inventory inventory = Bukkit.createInventory(viewer, 54, MENU_TITLE);
        Map<Integer, UUID> targets = new HashMap<>();

        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        online.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));

        int slot = 0;
        for (Player target : online) {
            if (slot >= inventory.getSize()) break;

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(target);
                meta.setDisplayName(ChatColor.GOLD + target.getName() + (isVanished(target.getUniqueId()) ? ChatColor.GRAY + " [VANISH]" : ""));

                Location loc = target.getLocation();
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Monde: " + ChatColor.AQUA + target.getWorld().getName());
                lore.add(ChatColor.GRAY + "Position: " + ChatColor.YELLOW + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
                lore.add(ChatColor.GRAY + "Etat: " + (isVanished(target.getUniqueId()) ? ChatColor.RED + "Vanish" : ChatColor.GREEN + "Visible"));
                lore.add(ChatColor.DARK_GRAY + "Cliquez pour vous teleporter");
                meta.setLore(lore);
            }

            ItemMeta itemMeta = meta;
            if (itemMeta != null) {
                head.setItemMeta(itemMeta);
            }

            inventory.setItem(slot, head);
            targets.put(slot, target.getUniqueId());
            slot++;
        }

        menuTargets.put(viewer.getUniqueId(), targets);
        markSkipSpectatorNextOpen(viewer);
        viewer.openInventory(inventory);
    }

    public boolean isMenu(String title) {
        return MENU_TITLE.equals(title);
    }

    public UUID getMenuTarget(UUID viewerUuid, int slot) {
        Map<Integer, UUID> map = menuTargets.get(viewerUuid);
        if (map == null) return null;
        return map.get(slot);
    }

    public void clearMenu(UUID viewerUuid) {
        menuTargets.remove(viewerUuid);
    }

    public boolean shouldUseSpectatorFor(InventoryType type) {
        if (type == InventoryType.PLAYER || type == InventoryType.CRAFTING) return false;
        return !"WORKBENCH".equalsIgnoreCase(type.name());
    }

    public void markSkipSpectatorNextOpen(Player player) {
        skipSpectatorNextOpen.add(player.getUniqueId());
    }

    public boolean consumeSkipSpectatorNextOpen(Player player) {
        return skipSpectatorNextOpen.remove(player.getUniqueId());
    }

    public void enterGuiSpectator(Player player) {
        UUID uuid = player.getUniqueId();
        guiModeBackup.putIfAbsent(uuid, player.getGameMode());

        if (player.getGameMode() != GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SPECTATOR);
        }
    }

    public void restoreGameModeAfterGui(Player player) {
        UUID uuid = player.getUniqueId();
        GameMode previous = guiModeBackup.remove(uuid);
        if (previous != null && player.getGameMode() != previous) {
            player.setGameMode(previous);
        }
    }

    public void markVanishSoundActivity(Player player, Location location) {
        if (!isVanished(player.getUniqueId()) || location == null) return;
        recentSoundMarkers.put(player.getUniqueId(), new SoundMarker(location.clone(), System.currentTimeMillis()));
    }

    private void applyRuntimeState(Player player, boolean vanish) {
        player.setSilent(vanish);
        player.setCanPickupItems(!vanish);
        player.setCollidable(!vanish);
        player.setGlowing(vanish);
    }

    private void refreshVisibilityForTarget(Player target) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(target)) continue;

            if (isVanished(target.getUniqueId()) && !canSeeVanished(viewer)) {
                viewer.hidePlayer(plugin, target);
                sendTabListRemoval(viewer, target);
            } else {
                viewer.showPlayer(plugin, target);
            }
        }
    }

    private void refreshVisibilityForViewer(Player viewer) {
        for (UUID uuid : vanishedPlayers) {
            Player vanishedOnline = Bukkit.getPlayer(uuid);
            if (vanishedOnline == null || vanishedOnline.equals(viewer)) continue;

            if (canSeeVanished(viewer)) {
                viewer.showPlayer(plugin, vanishedOnline);
            } else {
                viewer.hidePlayer(plugin, vanishedOnline);
                sendTabListRemoval(viewer, vanishedOnline);
            }
        }
    }

    private void showToEveryone(Player target) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(target)) {
                viewer.showPlayer(plugin, target);
            }
        }
    }

    private void notifyStaff(String message) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("aevumcore.vanish.notify") || canSeeVanished(online)) {
                online.sendMessage(message);
            }
        }
    }

    private void syncNicknameVisualState(Player player, boolean vanished) {
        Module module = plugin.getModuleManager().get("nickname");
        if (!(module instanceof NicknameModule nicknameModule) || !module.isEnabled()) {
            return;
        }

        NicknameManager nicknameManager = nicknameModule.getManager();
        if (nicknameManager == null) return;

        nicknameManager.refreshForVanishState(player, vanished);
    }

    private String prefix() {
        return ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("prefix", "&f[&bAevumCore&f]"));
    }

    private void setupProtocolLib() {
        if (plugin.getServer().getPluginManager().getPlugin("ProtocolLib") == null) {
            plugin.getLogger().warning("[Vanish] ProtocolLib absent, le filtrage packet vanish (tab/sons) est desactive.");
            return;
        }

        protocolManager = ProtocolLibrary.getProtocolManager();
        protocolListeners.clear();
        playerInfoRemovePacketType = resolveServerPacketType("PLAYER_INFO_REMOVE");

        List<PacketType> playerInfoPackets = resolveServerPacketTypes("PLAYER_INFO", "PLAYER_INFO_UPDATE", "PLAYER_INFO_REMOVE");
        if (!playerInfoPackets.isEmpty()) {
            PacketAdapter playerInfoListener = new PacketAdapter(plugin, ListenerPriority.HIGHEST, playerInfoPackets.toArray(new PacketType[0])) {
                @Override
                public void onPacketSending(PacketEvent event) {
                    if (canSeeVanished(event.getPlayer())) return;
                    filterPlayerInfoPacket(event);
                }
            };
            protocolManager.addPacketListener(playerInfoListener);
            protocolListeners.add(playerInfoListener);
        } else {
            plugin.getLogger().warning("[Vanish] Aucun packet PLAYER_INFO* trouve via ProtocolLib.");
        }

        List<PacketType> soundPackets = resolveServerPacketTypes("ENTITY_SOUND", "SOUND_EFFECT", "NAMED_SOUND_EFFECT");
        if (!soundPackets.isEmpty()) {
            PacketAdapter soundListener = new PacketAdapter(plugin, ListenerPriority.HIGHEST, soundPackets.toArray(new PacketType[0])) {
                @Override
                public void onPacketSending(PacketEvent event) {
                    filterSoundPacket(event);
                }
            };
            protocolManager.addPacketListener(soundListener);
            protocolListeners.add(soundListener);
        } else {
            plugin.getLogger().warning("[Vanish] Aucun packet SOUND* trouve via ProtocolLib.");
        }

        List<PacketType> commandSuggestionPackets = resolveServerPacketTypes("COMMAND_SUGGESTIONS", "TAB_COMPLETE");
        if (!commandSuggestionPackets.isEmpty()) {
            PacketAdapter suggestionListener = new PacketAdapter(plugin, ListenerPriority.HIGHEST, commandSuggestionPackets.toArray(new PacketType[0])) {
                @Override
                public void onPacketSending(PacketEvent event) {
                    if (canSeeVanished(event.getPlayer())) return;
                    filterCommandSuggestionsPacket(event);
                }
            };
            protocolManager.addPacketListener(suggestionListener);
            protocolListeners.add(suggestionListener);
        }
    }

    private List<PacketType> resolveServerPacketTypes(String... fieldNames) {
        List<PacketType> resolved = new ArrayList<>();
        for (String fieldName : fieldNames) {
            PacketType packetType = resolveServerPacketType(fieldName);
            if (packetType != null) {
                resolved.add(packetType);
            }
        }
        return resolved;
    }

    private PacketType resolveServerPacketType(String fieldName) {
        try {
            Field field = PacketType.Play.Server.class.getField(fieldName);
            Object value = field.get(null);
            if (value instanceof PacketType packetType) {
                return packetType;
            }
        } catch (NoSuchFieldException | IllegalAccessException exception) {
            logProtocolIssue("resolution du packet " + fieldName, exception);
        }
        return null;
    }

    private void filterPlayerInfoPacket(PacketEvent event) {
        filterPlayerInfoDataLists(event);
    }

    private void filterPlayerInfoDataLists(PacketEvent event) {
        StructureModifier<List<PlayerInfoData>> dataLists = event.getPacket().getPlayerInfoDataLists();
        for (int i = 0; i < dataLists.size(); i++) {
            List<PlayerInfoData> raw;
            try {
                raw = dataLists.read(i);
            } catch (RuntimeException exception) {
                logProtocolIssue("lecture des donnees tablist index " + i, exception);
                continue;
            }

            if (raw == null || raw.isEmpty()) continue;

            List<PlayerInfoData> filtered = new ArrayList<>(raw);
            filtered.removeIf(data -> data != null
                    && data.getProfile() != null
                    && data.getProfile().getUUID() != null
                    && isVanished(data.getProfile().getUUID()));

            if (filtered.size() != raw.size()) {
                try {
                    dataLists.write(i, filtered);
                } catch (RuntimeException exception) {
                    logProtocolIssue("ecriture des donnees tablist index " + i, exception);
                }
            }
        }
    }

    private void filterSoundPacket(PacketEvent event) {
        Player viewer = event.getPlayer();
        if (canSeeVanished(viewer)) return;

        Player source = resolveSourcePlayer(event);
        if (source != null) {
            if (!source.getUniqueId().equals(viewer.getUniqueId()) && isVanished(source.getUniqueId())) {
                event.setCancelled(true);
            }
            return;
        }

        Location packetLocation = resolvePacketLocation(event, viewer);
        if (packetLocation == null) return;
        if (isNearVanishedPlayer(packetLocation, viewer) || isNearRecentVanishSound(packetLocation, viewer)) {
            event.setCancelled(true);
        }
    }

    private Player resolveSourcePlayer(PacketEvent event) {
        String packetName = event.getPacketType().name();
        if (!packetName.contains("ENTITY_SOUND")) {
            return null;
        }

        int entityId;
        try {
            if (event.getPacket().getIntegers().size() <= 0) {
                return null;
            }
            entityId = event.getPacket().getIntegers().read(0);
        } catch (RuntimeException exception) {
            logProtocolIssue("lecture de l'entite source du packet son", exception);
            return null;
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getEntityId() == entityId) {
                return online;
            }
        }
        return null;
    }

    private Location resolvePacketLocation(PacketEvent event, Player viewer) {
        String packetName = event.getPacketType().name();
        if (packetName.contains("ENTITY_SOUND")) {
            return null;
        }

        try {
            if (event.getPacket().getDoubles().size() >= 3) {
                double x = event.getPacket().getDoubles().read(0);
                double y = event.getPacket().getDoubles().read(1);
                double z = event.getPacket().getDoubles().read(2);
                return new Location(viewer.getWorld(), x, y, z);
            }
        } catch (RuntimeException exception) {
            logProtocolIssue("lecture des coordonnees doubles du packet son", exception);
        }

        try {
            if (event.getPacket().getIntegers().size() >= 3) {
                double x = event.getPacket().getIntegers().read(0) / 8.0D;
                double y = event.getPacket().getIntegers().read(1) / 8.0D;
                double z = event.getPacket().getIntegers().read(2) / 8.0D;
                return new Location(viewer.getWorld(), x, y, z);
            }
        } catch (RuntimeException exception) {
            logProtocolIssue("lecture des coordonnees entieres du packet son", exception);
        }

        return null;
    }

    private boolean isNearVanishedPlayer(Location packetLocation, Player viewer) {
        double maxDistanceSquared = SOUND_FALLBACK_RADIUS * SOUND_FALLBACK_RADIUS;
        for (UUID uuid : vanishedPlayers) {
            if (uuid.equals(viewer.getUniqueId())) continue;

            Player vanished = Bukkit.getPlayer(uuid);
            if (vanished == null || !vanished.getWorld().equals(packetLocation.getWorld())) continue;

            if (vanished.getLocation().distanceSquared(packetLocation) <= maxDistanceSquared) {
                return true;
            }
        }
        return false;
    }

    private void filterCommandSuggestionsPacket(PacketEvent event) {
        StructureModifier<Suggestions> suggestionsModifier = event.getPacket().getModifier().withType(Suggestions.class);
        for (int i = 0; i < suggestionsModifier.size(); i++) {
            Suggestions suggestions;
            try {
                suggestions = suggestionsModifier.read(i);
            } catch (RuntimeException exception) {
                logProtocolIssue("lecture des suggestions de commande index " + i, exception);
                continue;
            }

            if (suggestions == null || suggestions.getList().isEmpty()) {
                continue;
            }

            List<Suggestion> filtered = new ArrayList<>(suggestions.getList());
            filtered.removeIf(suggestion -> suggestion == null || isVanishedName(suggestion.getText()));

            if (filtered.size() != suggestions.getList().size()) {
                try {
                    suggestionsModifier.write(i, new Suggestions(suggestions.getRange(), filtered));
                } catch (RuntimeException exception) {
                    logProtocolIssue("ecriture des suggestions de commande index " + i, exception);
                }
            }
        }
    }

    private void sendTabListRemoval(Player viewer, Player target) {
        if (protocolManager == null || playerInfoRemovePacketType == null) return;
        if (!viewer.isOnline() || !target.isOnline()) return;

        try {
            PacketContainer packet = protocolManager.createPacket(playerInfoRemovePacketType);
            packet.getUUIDLists().write(0, Collections.singletonList(target.getUniqueId()));
            protocolManager.sendServerPacket(viewer, packet);
        } catch (RuntimeException exception) {
            logProtocolIssue("envoi de retrait tablist pour " + target.getName(), exception);
        }
    }

    private boolean isNearRecentVanishSound(Location packetLocation, Player viewer) {
        long now = System.currentTimeMillis();
        double maxDistanceSquared = SOUND_ACTIVITY_RADIUS * SOUND_ACTIVITY_RADIUS;

        for (UUID uuid : vanishedPlayers) {
            if (uuid.equals(viewer.getUniqueId())) continue;

            SoundMarker marker = recentSoundMarkers.get(uuid);
            if (marker == null) continue;
            if (now - marker.timestamp() > SOUND_ACTIVITY_WINDOW_MS) continue;
            if (!marker.location().getWorld().equals(packetLocation.getWorld())) continue;

            if (marker.location().distanceSquared(packetLocation) <= maxDistanceSquared) {
                return true;
            }
        }

        return false;
    }

    private void loadData() {
        VanishDataStore.StoredState storedState = dataStore.load();
        vanishedPlayers.clear();
        vanishedPlayers.addAll(storedState.vanishedPlayers());
        knownNames.clear();
        knownNames.putAll(storedState.knownNames());
    }

    private void saveData() {
        databaseManager.runAsync(() -> dataStore.save(vanishedPlayers, knownNames))
                .exceptionally(throwable -> {
                    plugin.getLogger().severe("[Vanish] Echec sauvegarde SQL : " + throwable.getMessage());
                    return null;
                });
    }

    private void saveDataSync() {
        dataStore.save(vanishedPlayers, knownNames);
    }

    private void logProtocolIssue(String action, Exception exception) {
        plugin.getLogger().fine("[Vanish] Echec ProtocolLib lors de " + action + " : " + exception.getMessage());
    }

    private record SoundMarker(Location location, long timestamp) {
    }
}
