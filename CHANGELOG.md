# Changelog

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
