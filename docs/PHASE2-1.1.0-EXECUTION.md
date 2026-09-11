# PlexonGPFlags 1.1.0 — Phase 2 execution specification

## Release boundary

- Rollback tag: `v1.0.1`
- Rollback SHA: `757f62fa52fdb6ffa718c57ab4515b60c5aa23f3`
- Phase 2 branch: `phase2/1.1.0-policy-hardening`
- Stable target: `1.1.0`
- First candidate: `1.1.0-rc.1`
- Classification until PlexonCraft certification: **RC RELEASED / RUNTIME PENDING**

The release is MINOR rather than MAJOR because GriefPrevention claim identity, the existing public APIs, the sparse `flags.yml` persistence contract, existing flag IDs, and ON/OFF/INHERIT behavior remain compatible. Phase 2 adds reliability, diagnostics, administrative inspection, validation, migration safety, and certification gates.

## Authority boundary

PlexonGPFlags is authoritative only for Plexon-provided claim flag policy and its editing UX. GriefPrevention remains authoritative for claim creation/removal, numeric claim identity, ownership, trust, geometry, and claim persistence. No parallel Plexon claim system may be introduced.

The deprecated PlexonClaimFlags / GriefPreventionAddon implementation must never run beside PlexonGPFlags. Compatibility classes/services may remain for consumers, but the deprecated plugin itself must not be installed or revived.

## Claim identity and authorization

The authoritative persistence key remains GriefPrevention's durable numeric claim ID (`Claim#getID`). Subclaims use their own GP claim IDs and may inherit from the GP parent claim. Claim resize must preserve the same logical flag record when GP preserves the ID.

Flag mutation is owner/admin-only as in v1.0.1. Ordinary Access/Container/Build/Manage trust does not itself grant PlexonGPFlags editing rights. Authorization must be rechecked at the mutation service and immediately before GUI mutation. Admin claims require the existing admin-claims permission.

GUI sessions must be bound to actor UUID, claim ID, a stable claim fingerprint, and configuration generation. Reload invalidates open PlexonGPFlags inventories. Missing/replaced claims or changed authorization must fail safely.

## Policy model

Existing stable flag IDs are retained. A `true` effective value continues to mean the described behavior is blocked. Existing three-state override semantics remain:

- `ON`: explicit block
- `OFF`: explicit allow
- `INHERIT`: no explicit override

Resolution order is deterministic:

1. explicit claim override;
2. parent override/default chain for subclaims when parent inheritance is enabled;
3. configured world default when present;
4. configured global default.

Bypass authorization is evaluated separately and never persisted as a flag value.

## Configuration and reload

Introduce an explicit configuration schema/version while accepting the v1.0.1 unversioned layout as schema 1. Reject future schema versions and wrong scalar/list/material/spawn-reason/flag IDs instead of silently coercing them. Unknown default flag IDs are errors.

Reload is transactional: parse and validate a candidate configuration first, validate `flags.yml` into a candidate snapshot, and only then publish the new runtime state. Failure retains the previous known-good settings/store. Successful reload increments configuration generation and closes PlexonGPFlags menus.

## Persistence and migration

`flags.yml` remains sparse and claim-ID keyed. Add an explicit schema marker. Missing marker is treated as the v1 legacy schema and upgraded on the next successful write. Future schema versions must fail closed before in-memory mutation or writes.

Legacy PlexonClaimFlags data import remains one-way and only occurs when the authoritative PlexonGPFlags store does not exist. Before import, create a backup copy. Unknown legacy flag IDs are reported and preserved/quarantined rather than silently becoming active. No migration path may require installing the deprecated plugin.

## Event and performance contracts

Protection listeners remain synchronous, in-memory, allocation-light, and `ignoreCancelled=true`. PlexonGPFlags never uncancels another protection plugin's denial. No YAML/database/network/PlaceholderAPI access, global claim scan, or task-per-event is allowed in hot protection paths.

Fix any verified semantic inconsistency found during audit without widening PlexonGPFlags into a replacement for GriefPrevention. Owner/admin bypass behavior must remain consistent across player-targeted restrictions.

## Administrative and diagnostic surface

Keep `/gpflags diagnostics` and extend it with schema/config generation, supported flag count, explicit override count, migration status, rejected mutation count, stale GUI count, reload success/failure, and lightweight enforcement counters. Add bounded admin inspection of a single claim/effective flag state. No unbounded mass claim mutation.

## Automated certification

Tests and static contracts must cover at minimum: stable IDs; override parsing; policy precedence; future-schema fail-closed; transactional reload structure; unknown config flags; legacy import backup/idempotency; owner/admin authorization at mutation service; GUI actor/generation/fingerprint binding; cancelled-event interoperability; hot-path I/O prohibition; exact candidate version; Java 25/class major 69; dependency non-shading; required resources; checksum; TEST_SUMMARY; PROVENANCE.

CI must run on `phase2/**` and PRs. Release publication is prerelease-only while runtime certification is unavailable. The RC tag must target the exact certified candidate commit directly and release assets must include the JAR, `SHA256SUMS.txt`, `TEST_SUMMARY.txt`, and `PROVENANCE.txt`.

## Runtime gate

Stable `v1.1.0` remains unpublished until PlexonCraft runtime certification covers upgrade preservation, legacy migration rehearsal, startup dependency handling, owner/admin/unauthorized edits, flag resolution, claim create/resize/delete/ownership changes, stale GUI, reload rollback, restart persistence, future-schema fail-close, all major protection flags, cancellation interoperability, Core integration, Spark/MSPT comparison, and at least a 30-minute soak with zero HIGH/CRITICAL defects.
