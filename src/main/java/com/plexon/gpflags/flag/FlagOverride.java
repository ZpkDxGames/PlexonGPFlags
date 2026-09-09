package com.plexon.gpflags.flag;

import java.util.Locale;
import java.util.Optional;

public enum FlagOverride {
    INHERIT(null),
    ON(Boolean.TRUE),
    OFF(Boolean.FALSE);

    private final Boolean explicit;

    FlagOverride(Boolean explicit) { this.explicit = explicit; }

    public Boolean explicitValue() { return explicit; }

    public static FlagOverride fromExplicit(Boolean value) {
        return value == null ? INHERIT : value ? ON : OFF;
    }

    public static Optional<FlagOverride> parse(String input) {
        if (input == null) return Optional.empty();
        return switch (input.trim().toLowerCase(Locale.ROOT)) {
            case "on", "true", "enable", "enabled" -> Optional.of(ON);
            case "off", "false", "disable", "disabled" -> Optional.of(OFF);
            case "inherit", "inherited", "default", "reset" -> Optional.of(INHERIT);
            default -> Optional.empty();
        };
    }
}
