# Changelog

## 1.1.1 — Stable release verification patch

- Preserves the complete `1.1.0` gameplay/runtime source line, including the cross-claim PvP correctness fix.
- Changes stable release checksum generation to store the JAR basename rather than a build-directory path.
- Makes post-publication verification work directly against assets downloaded from GitHub Releases.
- Keeps exact-current-`main` publication, full tests/Javadocs/distribution validation, SHA-256 evidence and provenance requirements unchanged.

## 1.1.0 — Stable

### Player UX and policy management
- Promotes the accepted Phase 2/Phase 3 claim-policy and player-UX lineage to stable without changing the GriefPrevention authority boundary.
- Keeps the Claims Home → My Claims / Current Claim → Claim Dashboard flow and player-facing Main Claim / Subdivision presentation.
- Keeps explicit Rules → Rule Details actions for Allow / Block / Use Parent or Use Server Default.
- Keeps trusted-player details/access changes, claim creation affordability preview, resize preview/confirmation, warmup teleport, selected-claim boundary display and abandon confirmation.

### Reliability and correctness
- Fixes cross-claim PvP bypass evaluation so victim-side and attacker-side PvP restrictions each check bypass/ownership against the same claim that supplied the blocking policy.
- Preserves strict schema validation, sparse world defaults, future-schema fail-close behavior, unknown flag quarantine, transactional reload and GUI generation invalidation.
- Preserves atomic rollback-safe `flags.yml` persistence and one-way backed-up legacy PlexonClaimFlags import.

### Compatibility and performance
- Preserves GriefPrevention numeric claim identity, ten stable flag IDs, ON/OFF/INHERIT semantics and parent/subclaim inheritance.
- Preserves the public PlexonGPFlags API/event and the packaged `net.plexon.claimflags.*` compatibility facade.
- Keeps protection hot paths in-memory with no file/network/PlaceholderAPI access, no per-event scheduler handoff and no global claim scan.
- Keeps Java 25 / Paper 26.2 and optional external PlexonCore 2.0.4 integration.

### Release engineering
- Replaces version-specific RC publishers with one exact-`main` stable release workflow.
- Stable publication rebuilds/tests/Javadocs the final source, requires a non-empty all-green test suite, verifies distribution isolation and Java class major 69, generates SHA-256/provenance evidence and verifies the published release bytes.
- Live PlexonCraft runtime certification remains a separate deployment/operations follow-up and may be recorded as `NOT_EXECUTED` without blocking verified GitHub stable publication.

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

### Historical release state
- Runtime certification was not executed during the RC campaign.

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
