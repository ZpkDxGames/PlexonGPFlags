package net.plexon.claimflags.api;

/** @deprecated Use com.plexon.gpflags.flag.FlagOverride. */
@Deprecated(forRemoval = false)
public enum FlagOverride {
    ON, OFF, INHERIT;

    public Boolean explicitValue() {
        return switch (this) {
            case ON -> Boolean.TRUE;
            case OFF -> Boolean.FALSE;
            case INHERIT -> null;
        };
    }

    public static FlagOverride fromExplicit(Boolean value) {
        if (value == null) return INHERIT;
        return value ? ON : OFF;
    }
}
