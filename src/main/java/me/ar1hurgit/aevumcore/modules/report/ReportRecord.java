package me.ar1hurgit.aevumcore.modules.report;

import java.util.UUID;

public record ReportRecord(
        long id,
        UUID reporterUuid,
        String reporterName,
        UUID targetUuid,
        String targetName,
        String reason,
        long createdAt,
        UUID claimedByUuid,
        String claimedByName,
        long claimedAt
) {

    public boolean isClaimed() {
        return claimedByUuid != null;
    }

    public boolean isClaimedBy(UUID uuid) {
        return uuid != null && uuid.equals(claimedByUuid);
    }

    public ReportRecord withClaim(UUID staffUuid, String staffName, long claimTimestamp) {
        return new ReportRecord(
                id,
                reporterUuid,
                reporterName,
                targetUuid,
                targetName,
                reason,
                createdAt,
                staffUuid,
                staffName,
                claimTimestamp
        );
    }
}
