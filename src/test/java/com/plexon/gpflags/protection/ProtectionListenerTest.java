package com.plexon.gpflags.protection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectionListenerTest {
    @Test
    void attackerClaimBypassDoesNotGetCheckedAgainstVictimClaim() {
        assertFalse(PvpPolicy.shouldDeny(false, false, true, true));
    }

    @Test
    void victimClaimStillDeniesWhenOnlyAttackerClaimIsBypassed() {
        assertTrue(PvpPolicy.shouldDeny(true, false, true, true));
    }

    @Test
    void eitherUnbypassedBlockingClaimDeniesPvp() {
        assertTrue(PvpPolicy.shouldDeny(true, false, false, false));
        assertTrue(PvpPolicy.shouldDeny(false, false, true, false));
    }

    @Test
    void bothBlockingClaimsMustBeBypassedToAllowPvp() {
        assertFalse(PvpPolicy.shouldDeny(true, true, true, true));
        assertTrue(PvpPolicy.shouldDeny(true, true, true, false));
    }
}
