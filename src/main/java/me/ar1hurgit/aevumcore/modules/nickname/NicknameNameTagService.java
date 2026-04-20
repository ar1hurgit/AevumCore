package me.ar1hurgit.aevumcore.modules.nickname;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NicknameNameTagService {

    private static final String HIDDEN_NAMETAG_TEAM = "ac_nick_hide";
    private static final String NAME_TAG_ENTITY_TAG = "aevumcore_nickname_tag";
    private static final String NAME_TAG_OWNER_PREFIX = "aevumcore_nick_owner_";

    private final Map<UUID, UUID> nameTagStandByPlayer = new ConcurrentHashMap<>();

    public void clearAll() {
        for (UUID uuid : new ArrayList<>(nameTagStandByPlayer.keySet())) {
            removeNameTag(uuid);
        }

        Team team = getOrCreateHiddenNameTagTeam();
        if (team == null) {
            return;
        }

        for (String entry : new ArrayList<>(team.getEntries())) {
            team.removeEntry(entry);
        }
    }

    public void cleanupManagedNameTags() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                if (entity instanceof ArmorStand && entity.getScoreboardTags().contains(NAME_TAG_ENTITY_TAG)) {
                    entity.remove();
                }
            }
        }
    }

    public void ensureHiddenRealName(Player player) {
        Team team = getOrCreateHiddenNameTagTeam();
        if (team != null && !team.hasEntry(player.getName())) {
            team.addEntry(player.getName());
        }
    }

    public void removeHiddenRealName(Player player) {
        Team team = getOrCreateHiddenNameTagTeam();
        if (team != null && team.hasEntry(player.getName())) {
            team.removeEntry(player.getName());
        }
    }

    public void ensureNameTag(Player player, String nickname) {
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

    public void removeNameTag(UUID playerUuid) {
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

    private Team getOrCreateHiddenNameTagTeam() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager() == null ? null : Bukkit.getScoreboardManager().getMainScoreboard();
        if (scoreboard == null) {
            return null;
        }

        Team team = scoreboard.getTeam(HIDDEN_NAMETAG_TEAM);
        if (team == null) {
            team = scoreboard.registerNewTeam(HIDDEN_NAMETAG_TEAM);
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        }
        return team;
    }

    private boolean isManagedNameTag(Entity entity, UUID ownerUuid, UUID standUuid) {
        if (!(entity instanceof ArmorStand)) {
            return false;
        }
        if (standUuid != null && standUuid.equals(entity.getUniqueId())) {
            return true;
        }

        Set<String> tags = entity.getScoreboardTags();
        return tags.contains(NAME_TAG_ENTITY_TAG) && tags.contains(ownerTag(ownerUuid));
    }

    private boolean looksLikeLegacyNameTagPassenger(Entity entity) {
        if (!(entity instanceof ArmorStand stand)) {
            return false;
        }
        return stand.isMarker()
                && stand.isInvisible()
                && stand.isSilent()
                && stand.isCustomNameVisible();
    }

    private String ownerTag(UUID ownerUuid) {
        return NAME_TAG_OWNER_PREFIX + ownerUuid;
    }
}
