# Changelog

## 1.1.0-rc.1 — Phase 2 candidate

### Compatible policy hardening
- Preserves GriefPrevention numeric claim identity, ten stable flag IDs, sparse claim overrides, public API signatures and ON/OFF/INHERIT semantics.
- Adds strict configuration schema validation and sparse UUID-keyed world defaults.
- Adds flag-store schema 2 with future-schema fail-closed behavior and unknown-ID quarantine.
- Makes reload candidate-first and rollback-safe; successful reloads invalidate old GUI generations.
- Adds actor/config-generation GUI session protection while retaining claim-ID and authorization revalidation.
- Backs up and marks one-way legacy PlexonClaimFlags data import.
- Refuses startup when the deprecated PlexonClaimFlags / GriefPreventionAddon plugin is active.
- Adds bounded `/gpflags inspect` and expanded diagnostics/observability counters.
- Normalizes owner/admin crop-trampling bypass with other player-targeted flag protections.
- Expands Java 25, dependency non-shading, hot-path, provenance, checksum and prerelease CI gates.

### Release state
- Candidate only; runtime certification NOT EXECUTED.
- Stable `v1.1.0` remains unpublished until the PlexonCraft runtime matrix passes.

## 1.0.0

### Unified product
- Introduced PlexonGPFlags as the single PlexonCraft GriefPrevention companion.
- Merged the previous PlexonClaimFlags behavior with a native claim-management GUI workflow.
- Added compatibility command aliases for the previous flag addon and `/claimhelp` GUI entry point.

### Claim management
- Claim hub, paginated claim list and claim dashboard.
- Auto-claim presets/custom size.
- Golden shovel cooldown.
- Trust management.
- Directional resize with preview/validation.
- Warmup teleport with cancellation.
- Selected-claim particle visualization.
- Confirmed abandon flow.

### Flags
- Preserved ten existing protection flags.
- Preserved main/subclaim inheritance.
- Preserved in-memory protection decisions and atomic YAML persistence.
- Added automatic legacy `flags.yml` import.

### Performance
- No global claim visualizer scan.
- No per-teleport listener registration.
- Native Paper inventories; no shaded GUI framework.
- Cached runtime protection material/reason sets.
