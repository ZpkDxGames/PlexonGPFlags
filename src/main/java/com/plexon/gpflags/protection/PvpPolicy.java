package com.plexon.gpflags.protection;

/** Pure policy function for combining independently evaluated PvP claim restrictions. */
final class PvpPolicy {
    private PvpPolicy() { }

    static boolean shouldDeny(boolean victimBlocked, boolean victimBypass,
                              boolean attackerBlocked, boolean attackerBypass) {
        return victimBlocked && !victimBypass || attackerBlocked && !attackerBypass;
    }
}
