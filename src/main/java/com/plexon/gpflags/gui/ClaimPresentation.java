package com.plexon.gpflags.gui;

import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.Location;

import java.util.Objects;
import java.util.UUID;

/** Immutable, non-authoritative claim presentation snapshots built from GriefPrevention. */
final class ClaimPresentation {
    private ClaimPresentation() {}

    record Card(String label, String relation, String world, int centerX, int centerZ,
                int width, int length, long area, int subdivisions, boolean current) {}

    record Token(long claimId, UUID ownerId, Long parentId, UUID worldId,
                 int x1, int z1, int x2, int z2) {
        static Token from(Claim claim) {
            Location a = claim.getLesserBoundaryCorner();
            Location b = claim.getGreaterBoundaryCorner();
            Long parent = claim.parent == null ? null : claim.parent.getID();
            return new Token(Objects.requireNonNull(claim.getID()), claim.getOwnerID(), parent,
                    a.getWorld().getUID(), a.getBlockX(), a.getBlockZ(), b.getBlockX(), b.getBlockZ());
        }

        boolean matches(Claim claim) {
            if (claim == null || !claim.inDataStore || claim.getID() == null) return false;
            Location a = claim.getLesserBoundaryCorner();
            Location b = claim.getGreaterBoundaryCorner();
            Long parent = claim.parent == null ? null : claim.parent.getID();
            return claimId == claim.getID()
                    && Objects.equals(ownerId, claim.getOwnerID())
                    && Objects.equals(parentId, parent)
                    && worldId.equals(a.getWorld().getUID())
                    && x1 == a.getBlockX() && z1 == a.getBlockZ()
                    && x2 == b.getBlockX() && z2 == b.getBlockZ();
        }
    }

    static Card card(Claim claim, boolean current) {
        Location a = claim.getLesserBoundaryCorner();
        Location b = claim.getGreaterBoundaryCorner();
        int width = b.getBlockX() - a.getBlockX() + 1;
        int length = b.getBlockZ() - a.getBlockZ() + 1;
        String relation = claim.parent == null ? "Main Claim" : subdivisionLabel(claim);
        return new Card(relation, relation, a.getWorld().getName(),
                (a.getBlockX() + b.getBlockX()) / 2, (a.getBlockZ() + b.getBlockZ()) / 2,
                width, length, (long) width * length,
                claim.parent == null ? claim.children.size() : 0, current);
    }

    static String label(Claim claim) {
        if (claim == null) return "Claim";
        return claim.parent == null ? "Main Claim" : subdivisionLabel(claim);
    }

    static String subdivisionLabel(Claim claim) {
        if (claim == null || claim.parent == null) return "Subdivision";
        int index = claim.parent.children.indexOf(claim);
        return index < 0 ? "Subdivision" : "Subdivision " + (index + 1);
    }
}
