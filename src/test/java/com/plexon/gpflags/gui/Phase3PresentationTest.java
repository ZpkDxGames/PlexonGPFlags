package com.plexon.gpflags.gui;

import com.plexon.gpflags.flag.FlagOverride;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase3PresentationTest {
    @Test void homeContextIsBounded() {
        ClaimNavigationContext context = ClaimNavigationContext.home();
        assertEquals(ClaimNavigationContext.Origin.HOME, context.origin());
        assertEquals(1, context.claimsPage());
        assertEquals(1, context.areasPage());
        assertEquals(1, context.trustPage());
        assertFalse(context.dashboardFromAreas());
    }

    @Test void claimsContextClampsAndPreservesPage() {
        assertEquals(1, ClaimNavigationContext.claims(0).claimsPage());
        assertEquals(4, ClaimNavigationContext.claims(4).claimsPage());
    }

    @Test void trustPageIsPreservedWithoutChangingClaimsPage() {
        ClaimNavigationContext context = ClaimNavigationContext.claims(3).withTrustPage(2);
        assertEquals(3, context.claimsPage());
        assertEquals(2, context.trustPage());
    }

    @Test void areasContextRemembersParentAndOriginatingDashboard() {
        ClaimNavigationContext context = ClaimNavigationContext.claims(2).beginAreas(41L, 42L);
        assertEquals(41L, context.areasParentClaimId());
        assertEquals(42L, context.areasReturnClaimId());
        assertEquals(2, context.claimsPage());
    }

    @Test void selectedAreaMarksDashboardAsReturningToAreas() {
        ClaimNavigationContext context = ClaimNavigationContext.home().beginAreas(11L, 12L).selectArea();
        assertTrue(context.dashboardFromAreas());
        assertEquals(11L, context.areasParentClaimId());
    }

    @Test void leavingAreasRestoresRootNavigationAndDropsAreaIds() {
        ClaimNavigationContext context = ClaimNavigationContext.claims(5).beginAreas(11L, 12L).selectArea().leaveAreas();
        assertEquals(ClaimNavigationContext.Origin.CLAIMS, context.origin());
        assertEquals(5, context.claimsPage());
        assertEquals(-1L, context.areasParentClaimId());
        assertEquals(-1L, context.areasReturnClaimId());
        assertFalse(context.dashboardFromAreas());
    }

    @Test void effectiveAllowedIsPlayerFacing() {
        FlagPresentation state = FlagPresentation.of(false, null, false, true);
        assertEquals("Allowed", state.behavior());
        assertEquals("Server Default", state.source());
    }

    @Test void effectiveBlockedIsPlayerFacing() {
        FlagPresentation state = FlagPresentation.of(true, null, false, true);
        assertEquals("Blocked", state.behavior());
        assertEquals("Server Default", state.source());
    }

    @Test void explicitStateIsCalledCustomOverride() {
        assertEquals("Custom Override", FlagPresentation.of(true, Boolean.TRUE, false, true).source());
        assertEquals("Custom Override", FlagPresentation.of(false, Boolean.FALSE, true, true).source());
    }

    @Test void subdivisionWithoutOverrideExplainsParentInheritance() {
        FlagPresentation state = FlagPresentation.of(false, null, true, true);
        assertEquals("Inherited from parent", state.source());
        assertEquals("Use Parent", state.resetLabel());
    }

    @Test void topLevelResetUsesServerDefaultLanguage() {
        FlagPresentation state = FlagPresentation.of(false, Boolean.FALSE, false, true);
        assertEquals("Use Server Default", state.resetLabel());
        assertTrue(state.resetDescription().contains("server setting"));
    }

    @Test void subdivisionWithoutParentInheritanceUsesServerDefaultLanguage() {
        FlagPresentation state = FlagPresentation.of(true, null, true, false);
        assertEquals("Server Default", state.source());
        assertEquals("Use Server Default", state.resetLabel());
    }

    @Test void allowBlockAndResetMapToExistingTriStateAuthority() {
        assertEquals(FlagOverride.OFF, FlagPresentation.allowOverride());
        assertEquals(FlagOverride.ON, FlagPresentation.blockOverride());
        assertEquals(FlagOverride.INHERIT, FlagPresentation.resetOverride());
    }
}
