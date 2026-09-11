# PlexonGPFlags 1.1.0

Stable GitHub repository closure of the accepted Phase 2/Phase 3 PlexonGPFlags line.

## Highlights

- Unified Claims Home / My Claims / Current Claim / Claim Dashboard player UX.
- Explicit rule configuration with effective-policy and inheritance/source visibility.
- Trusted-player details and access management while GriefPrevention remains the trust authority.
- Claim creation affordability preview, resize preview/confirmation, warmup teleport, selected-claim boundary display and abandon confirmation.
- Ten stable protection flags with ON/OFF/INHERIT semantics, parent/subclaim inheritance, sparse world defaults and schema-2 persistence.
- Transactional reload, stale-menu protection, bounded diagnostics, atomic persistence and safe legacy PlexonClaimFlags import.
- Cross-claim PvP correctness fix: victim-side and attacker-side blocking policies now evaluate bypass/ownership against their own claim independently.

## Compatibility and authority

GriefPrevention remains authoritative for claim identity, ownership, boundaries, trust, claim-block accounting and claim persistence. PlexonGPFlags owns only Plexon flag policy, UX/orchestration, diagnostics and sparse flag persistence.

The deprecated PlexonClaimFlags / GriefPreventionAddon plugin must not run alongside PlexonGPFlags. Compatibility API/event classes remain packaged for migrating consumers.

Target: Paper 26.2, Java 25, required external GriefPrevention, optional external PlexonCore 2.0.4. Config and flag-store schemas remain 2.

## Verification

The stable publisher rebuilds and verifies the exact final `main` commit, requires a non-empty all-green test suite, verifies Java class major 69, hot-path restrictions, required public/compatibility classes, dependency isolation, SHA-256 integrity and published release bytes.

Live PlexonCraft runtime certification is a separate operational follow-up and may remain `NOT_EXECUTED` in release provenance.

Rollback baseline: `v1.0.1` / `757f62fa52fdb6ffa718c57ab4515b60c5aa23f3`.
