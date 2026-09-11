package com.plexon.gpflags.gui;

/**
 * Bounded player navigation state. Claim identities remain GriefPrevention IDs used only
 * for hidden routing/revalidation; this record never persists or replaces claim authority.
 */
record ClaimNavigationContext(
        Origin origin,
        int claimsPage,
        int areasPage,
        int trustPage,
        long areasParentClaimId,
        long areasReturnClaimId,
        boolean dashboardFromAreas
) {
    enum Origin { HOME, CLAIMS }

    ClaimNavigationContext {
        origin = origin == null ? Origin.HOME : origin;
        claimsPage = Math.max(1, claimsPage);
        areasPage = Math.max(1, areasPage);
        trustPage = Math.max(1, trustPage);
    }

    static ClaimNavigationContext home() {
        return new ClaimNavigationContext(Origin.HOME, 1, 1, 1, -1L, -1L, false);
    }

    static ClaimNavigationContext claims(int page) {
        return new ClaimNavigationContext(Origin.CLAIMS, page, 1, 1, -1L, -1L, false);
    }

    ClaimNavigationContext withClaimsPage(int page) {
        return new ClaimNavigationContext(origin, page, areasPage, trustPage,
                areasParentClaimId, areasReturnClaimId, dashboardFromAreas);
    }

    ClaimNavigationContext withTrustPage(int page) {
        return new ClaimNavigationContext(origin, claimsPage, areasPage, page,
                areasParentClaimId, areasReturnClaimId, dashboardFromAreas);
    }

    ClaimNavigationContext beginAreas(long parentClaimId, long returnClaimId) {
        return new ClaimNavigationContext(origin, claimsPage, 1, trustPage,
                parentClaimId, returnClaimId, false);
    }

    ClaimNavigationContext withAreasPage(int page) {
        return new ClaimNavigationContext(origin, claimsPage, page, trustPage,
                areasParentClaimId, areasReturnClaimId, false);
    }

    ClaimNavigationContext selectArea() {
        return new ClaimNavigationContext(origin, claimsPage, areasPage, trustPage,
                areasParentClaimId, areasReturnClaimId, true);
    }

    ClaimNavigationContext leaveAreas() {
        return new ClaimNavigationContext(origin, claimsPage, 1, trustPage,
                -1L, -1L, false);
    }
}
