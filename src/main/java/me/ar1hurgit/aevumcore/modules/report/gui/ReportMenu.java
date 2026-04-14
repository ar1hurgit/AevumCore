package me.ar1hurgit.aevumcore.modules.report.gui;

import me.ar1hurgit.aevumcore.modules.report.ReportRecord;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReportMenu {

    public static final String TITLE = ChatColor.DARK_RED + "Reports Staff";
    public static final int INVENTORY_SIZE = 54;
    public static final int PAGE_SIZE = 45;
    public static final int PREVIOUS_SLOT = 45;
    public static final int INFO_SLOT = 49;
    public static final int NEXT_SLOT = 53;

    public RenderedPage render(int requestedPage, List<ReportRecord> reports) {
        int totalPages = Math.max(1, (int) Math.ceil(reports.size() / (double) PAGE_SIZE));
        int page = Math.max(0, Math.min(requestedPage, totalPages - 1));
        int startIndex = page * PAGE_SIZE;
        int endIndex = Math.min(reports.size(), startIndex + PAGE_SIZE);

        Inventory inventory = Bukkit.createInventory(null, INVENTORY_SIZE, TITLE);
        Map<Integer, Long> reportSlots = new HashMap<>();

        int slot = 0;
        for (int index = startIndex; index < endIndex; index++) {
            ReportRecord record = reports.get(index);
            inventory.setItem(slot, buildReportItem(record));
            reportSlots.put(slot, record.id());
            slot++;
        }

        if (reports.isEmpty()) {
            inventory.setItem(22, item(
                    Material.BARRIER,
                    ChatColor.RED + "Aucun report actif",
                    List.of(
                            ChatColor.GRAY + "Les nouveaux signalements",
                            ChatColor.GRAY + "apparaitront ici."
                    )
            ));
        }

        if (page > 0) {
            inventory.setItem(PREVIOUS_SLOT, item(
                    Material.ARROW,
                    ChatColor.YELLOW + "Page precedente",
                    List.of(ChatColor.GRAY + "Cliquez pour revenir a la page precedente.")
            ));
        }

        inventory.setItem(INFO_SLOT, item(
                Material.BOOK,
                ChatColor.GOLD + "Informations",
                List.of(
                        ChatColor.GRAY + "Page: " + ChatColor.YELLOW + (page + 1) + ChatColor.GRAY + "/" + ChatColor.YELLOW + totalPages,
                        ChatColor.GRAY + "Reports actifs: " + ChatColor.YELLOW + reports.size(),
                        ChatColor.GRAY + "Clic gauche: " + ChatColor.GREEN + "prendre en charge",
                        ChatColor.GRAY + "Clic droit: " + ChatColor.RED + "fermer"
                )
        ));

        if (page + 1 < totalPages) {
            inventory.setItem(NEXT_SLOT, item(
                    Material.ARROW,
                    ChatColor.YELLOW + "Page suivante",
                    List.of(ChatColor.GRAY + "Cliquez pour afficher la page suivante.")
            ));
        }

        return new RenderedPage(inventory, reportSlots, page, totalPages);
    }

    private ItemStack buildReportItem(ReportRecord record) {
        Material material = record.isClaimed() ? Material.GREEN_WOOL : Material.RED_WOOL;
        String status = record.isClaimed()
                ? ChatColor.GREEN + "Pris par " + ChatColor.YELLOW + record.claimedByName()
                : ChatColor.RED + "En attente";

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "ID: " + ChatColor.GOLD + "#" + record.id());
        lore.add(ChatColor.GRAY + "Reporte par: " + ChatColor.AQUA + record.reporterName());
        lore.add(ChatColor.GRAY + "Joueur signale: " + ChatColor.YELLOW + record.targetName());
        lore.add(ChatColor.GRAY + "Statut: " + status);
        lore.add(ChatColor.GRAY + "Age: " + ChatColor.YELLOW + formatAge(System.currentTimeMillis() - record.createdAt()));
        lore.add(ChatColor.DARK_GRAY + " ");
        lore.add(ChatColor.GRAY + "Raison:");
        lore.addAll(wrapReason(record.reason()));
        lore.add(ChatColor.DARK_GRAY + " ");
        lore.add(ChatColor.DARK_GRAY + "Gauche: prendre en charge");
        lore.add(ChatColor.DARK_GRAY + "Droite: fermer");

        return item(
                material,
                ChatColor.GOLD + "#" + record.id() + ChatColor.GRAY + " - " + ChatColor.RED + record.targetName(),
                lore
        );
    }

    private List<String> wrapReason(String reason) {
        List<String> lines = new ArrayList<>();
        String remaining = reason == null ? "" : reason.trim();
        if (remaining.isEmpty()) {
            lines.add(ChatColor.GRAY + "- " + ChatColor.WHITE + "Aucune raison");
            return lines;
        }

        int maxLineLength = 32;
        while (!remaining.isEmpty() && lines.size() < 6) {
            if (remaining.length() <= maxLineLength) {
                lines.add(ChatColor.GRAY + "- " + ChatColor.WHITE + remaining);
                remaining = "";
                continue;
            }

            int splitAt = remaining.lastIndexOf(' ', maxLineLength);
            if (splitAt <= 0) {
                splitAt = maxLineLength;
            }

            String line = remaining.substring(0, splitAt).trim();
            lines.add(ChatColor.GRAY + "- " + ChatColor.WHITE + line);
            remaining = remaining.substring(splitAt).trim();
        }

        if (!remaining.isEmpty()) {
            lines.add(ChatColor.GRAY + "- " + ChatColor.WHITE + "...");
        }
        return lines;
    }

    private String formatAge(long ageMillis) {
        long totalSeconds = Math.max(0L, ageMillis / 1000L);
        long days = totalSeconds / 86400L;
        long hours = (totalSeconds % 86400L) / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        if (days > 0) return days + "j " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + seconds + "s";
        return seconds + "s";
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public record RenderedPage(Inventory inventory, Map<Integer, Long> reportSlots, int page, int totalPages) {
        public boolean hasPrevious() {
            return page > 0;
        }

        public boolean hasNext() {
            return page + 1 < totalPages;
        }
    }
}
