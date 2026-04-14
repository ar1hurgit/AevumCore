package me.ar1hurgit.aevumcore.modules.nickname;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChatTabCompleteEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.server.TabCompleteEvent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class NicknameListener implements Listener {

    private static final Set<String> TARGETED_COMMANDS = new HashSet<>(Arrays.asList(
            "/msg", "/tell", "/w", "/whisper",
            "/minecraft:msg", "/minecraft:tell", "/minecraft:w", "/minecraft:whisper"
    ));

    private final NicknameManager manager;

    public NicknameListener(NicknameManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        manager.handleJoin(event.getPlayer());

        String joinMessage = event.getJoinMessage();
        if (joinMessage != null) {
            String displayed = manager.getDisplayedName(event.getPlayer());
            event.setJoinMessage(joinMessage.replace(event.getPlayer().getName(), displayed));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        String quitMessage = event.getQuitMessage();
        if (quitMessage != null) {
            String displayed = manager.getDisplayedName(event.getPlayer());
            event.setQuitMessage(quitMessage.replace(event.getPlayer().getName(), displayed));
        }
        manager.handleQuit(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        manager.handleRespawn(player);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        manager.handleDeath(event.getEntity());
    }

    @EventHandler(ignoreCancelled = true)
    public void onChatTabComplete(PlayerChatTabCompleteEvent event) {
        java.util.List<String> rewritten = manager.rewriteCompletionsForViewer(
                event.getPlayer(),
                event.getChatMessage(),
                event.getTabCompletions()
        );
        event.getTabCompletions().clear();
        event.getTabCompletions().addAll(rewritten);
    }

    @EventHandler(ignoreCancelled = true)
    public void onServerTabComplete(TabCompleteEvent event) {
        if (!(event.getSender() instanceof Player player)) return;
        event.setCompletions(manager.rewriteCompletionsForViewer(player, event.getBuffer(), event.getCompletions()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String raw = event.getMessage();
        if (raw == null || raw.isBlank()) return;
        if (!raw.startsWith("/")) return;

        String[] split = raw.split(" ", 3);
        if (split.length < 2) return;

        String command = split[0].toLowerCase();
        if (!TARGETED_COMMANDS.contains(command)) return;

        String providedTarget = split[1];
        Player resolved = manager.resolveTargetForViewer(event.getPlayer(), providedTarget);
        if (resolved == null) return;
        if (providedTarget.equalsIgnoreCase(resolved.getName())) return;

        String rebuilt;
        if (split.length == 2) {
            rebuilt = split[0] + " " + resolved.getName();
        } else {
            rebuilt = split[0] + " " + resolved.getName() + " " + split[2];
        }
        event.setMessage(rebuilt);
    }
}
