package com.plexon.gpflags.protection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectionListenerTest {
    @Test
    void attackerClaimBypassDoesNotGetCheckedAgainstVictimClaim() {
        assertFalse(ProtectionListener.shouldDenyPvp(false, false, true, true));
    }

    @Test
    void victimClaimStillDeniesWhenOnlyAttackerClaimIsBypassed() {
        assertTrue(ProtectionListener.shouldDenyPvp(true, false, true, true));
    }

    @Test
    void eitherUnbypassedBlockingClaimDeniesPvp() {
        assertTrue(ProtectionListener.shouldDenyPvp(true, false, false, false));
        assertTrue(ProtectionListener.shouldDenyPvp(false, false, true, false));
    }

    @Test
    void bothBlockingClaimsMustBeBypassedToAllowPvp() {
        assertFalse(ProtectionListener.shouldDenyPvp(true, true, true, true));
        assertTrue(ProtectionListener.shouldDenyPvp(true, true, true, false));
    }
}
