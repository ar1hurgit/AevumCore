package me.ar1hurgit.aevumcore.modules.report;

import me.ar1hurgit.aevumcore.AevumCore;
import me.ar1hurgit.aevumcore.core.module.Module;
import me.ar1hurgit.aevumcore.modules.nickname.NicknameManager;
import me.ar1hurgit.aevumcore.modules.nickname.NicknameModule;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

public class ReportTargetResolver {

    private final AevumCore plugin;

    public ReportTargetResolver(AevumCore plugin) {
        this.plugin = plugin;
    }

    public ReportManager.ResolvedTarget resolveTarget(Player reporter, String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        NicknameManager nicknameManager = getNicknameManager();
        if (nicknameManager != null) {
            Player resolved = nicknameManager.resolveTargetForViewer(reporter, input);
            if (resolved != null) {
                return new ReportManager.ResolvedTarget(resolved.getUniqueId(), resolved.getName());
            }
        }

        Player online = Bukkit.getPlayerExact(input);
        if (online == null) {
            online = Bukkit.getPlayer(input);
        }
        if (online != null) {
            return new ReportManager.ResolvedTarget(online.getUniqueId(), online.getName());
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(input);
        if (!offline.isOnline() && !offline.hasPlayedBefore()) {
            return null;
        }

        String resolvedName = offline.getName() == null || offline.getName().isBlank() ? input : offline.getName();
        return new ReportManager.ResolvedTarget(offline.getUniqueId(), resolvedName);
    }

    public List<String> getTargetSuggestions(Player viewer, String token) {
        String loweredToken = token == null ? "" : token.toLowerCase(Locale.ROOT);
        TreeSet<String> suggestions = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        NicknameManager nicknameManager = getNicknameManager();

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(online) && !viewer.canSee(online)) {
                continue;
            }

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

    private NicknameManager getNicknameManager() {
        Module module = plugin.getModuleManager().get("nickname");
        if (!(module instanceof NicknameModule nicknameModule) || !module.isEnabled()) {
            return null;
        }
        return nicknameModule.getManager();
    }
}
