package com.plexon.gpflags.gui;

import com.plexon.gpflags.flag.FlagOverride;

/** Converts sparse policy state into player-facing behavior/source language. */
record FlagPresentation(String behavior, String source, String resetLabel, String resetDescription) {
    static FlagPresentation of(boolean effectiveBlocked, Boolean explicit, boolean subdivision,
                               boolean parentInheritanceEnabled) {
        String behavior = effectiveBlocked ? "Blocked" : "Allowed";
        String source;
        if (explicit != null) source = "Custom Override";
        else if (subdivision && parentInheritanceEnabled) source = "Inherited from parent";
        else source = "Server Default";
        String resetLabel = subdivision && parentInheritanceEnabled ? "Use Parent" : "Use Server Default";
        String resetDescription = subdivision && parentInheritanceEnabled
                ? "Remove this claim's override and follow the main claim."
                : "Remove this claim's override and follow the server setting.";
        return new FlagPresentation(behavior, source, resetLabel, resetDescription);
    }

    static FlagOverride allowOverride() { return FlagOverride.OFF; }
    static FlagOverride blockOverride() { return FlagOverride.ON; }
    static FlagOverride resetOverride() { return FlagOverride.INHERIT; }
}
