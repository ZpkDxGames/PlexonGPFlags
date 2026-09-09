package net.plexon.claimflags.api;

/** @deprecated Use com.plexon.gpflags.api.PlexonGPFlagsAPI. */
@Deprecated(forRemoval = false)
public record ClaimFlagView(String id, String displayName, String iconMaterial, boolean defaultValue) {}
