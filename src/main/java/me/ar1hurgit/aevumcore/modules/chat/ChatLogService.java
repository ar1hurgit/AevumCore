package me.ar1hurgit.aevumcore.modules.chat;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class ChatLogService {

    private static final DateTimeFormatter LOG_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final JavaPlugin plugin;
    private final Object logLock = new Object();
    private File chatLogFile;

    public ChatLogService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void append(String category, String actor, String content) {
        ensureLogFile();
        if (chatLogFile == null) {
            return;
        }

        String line = "[" + LOG_DATE_FORMAT.format(Instant.now()) + "] "
                + "[" + safe(category) + "] "
                + safe(actor) + " : "
                + safe(content) + System.lineSeparator();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            synchronized (logLock) {
                try {
                    Files.writeString(
                            chatLogFile.toPath(),
                            line,
                            StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.APPEND
                    );
                } catch (IOException exception) {
                    plugin.getLogger().warning("[Chat] Impossible d'ecrire le log chat: " + exception.getMessage());
                }
            }
        });
    }

    private void ensureLogFile() {
        if (chatLogFile != null) {
            return;
        }

        try {
            File folder = new File(plugin.getDataFolder(), "logs");
            if (!folder.exists()) {
                folder.mkdirs();
            }
            chatLogFile = new File(folder, "chat.log");
            if (!chatLogFile.exists()) {
                chatLogFile.createNewFile();
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("[Chat] Impossible de preparer chat.log : " + exception.getMessage());
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
