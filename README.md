# PlexonGPFlags

**PlexonGPFlags 1.1.0** is the stable GriefPrevention claim-policy and player-management companion for PlexonCraft. GriefPrevention remains authoritative for claim identity, boundaries, ownership, trust and claim persistence; PlexonGPFlags owns Plexon flag policy, editing UX, diagnostics and compatible sparse flag persistence.

Repository/source/release closure is independent from live PlexonCraft rollout. Runtime certification may still be recorded as `NOT_EXECUTED`; that is deployment evidence, not a blocker for the verified GitHub stable artifact.

## Runtime

- Paper `26.2.build.121-stable`
- Java 25 (class major 69)
- GriefPrevention 16.18.7+; required
- PlexonCore 2.0.4 compatible/optional
- Config schema 2
- Flag-store schema 2

**Never install PlexonGPFlags together with the deprecated PlexonClaimFlags / GriefPreventionAddon plugin.** PlexonGPFlags keeps compatibility API classes for existing consumers, but the deprecated plugin itself is not a runtime dependency and must not be active.

## Claims and authorization

GriefPrevention's numeric `Claim#getID()` is the authoritative stored flag-record identity. Ordinary player-facing claim UX uses Main Claim / Subdivision presentation rather than exposing numeric IDs.

Flag edits are authorized at the mutation service. Existing policy remains owner/admin-only; ordinary GriefPrevention Access, Container, Build or Manage trust does not implicitly grant PlexonGPFlags flag-edit permission. Administrative claims require `plexongpflags.adminclaims`.

GUI mutations re-resolve the stored claim ID and re-check authorization. PlexonGPFlags inventories are also bound to the actor and configuration generation, so a successful reload invalidates old inventory sessions.

PvP protection evaluates the victim-side and attacker-side claim independently. If either claim blocks PvP, the attacker must have the existing bypass/ownership exemption for that same blocking claim; an exemption for one claim cannot accidentally bypass another claim's policy.

## Stable flags and resolution

A flag value of **ON** means PlexonGPFlags blocks the described behavior. Stable IDs remain:

`natural-mobs`, `spawner-mobs`, `pvp`, `building`, `interactions`, `containers`, `explosions`, `fire`, `crop-trampling`, `mob-griefing`.

Explicit state is three-state: `ON`, `OFF`, or `INHERIT`. Effective policy resolves in this order:

1. explicit claim override;
2. parent policy for subclaims when inheritance is enabled;
3. sparse world default keyed by Bukkit world UUID;
4. global default.

Bypass is authorization policy, not a persisted flag value.

## Player UX

`/gpflags` provides the first-party Claims Home, My Claims, Current Claim and Claim Dashboard flow. The UI includes explicit rule editing, trusted-player details/access changes, claim creation affordability preview, resize preview/confirmation, warmup teleport, selected-claim boundary display and destructive abandon confirmation.

The UI does not become a second claim authority: claim mutation remains orchestrated against GriefPrevention, while `FlagService` / `FlagStore` remain the only Plexon flag-policy mutation and persistence authorities.

## Configuration and reload

`config.yml` schema 2 is strictly validated before runtime publication. Wrong scalar/list types, unknown default flag IDs, invalid materials/spawn reasons, unsafe legacy-folder paths, unknown world-default flag IDs, and unavailable configured world UUIDs reject startup/reload instead of being silently accepted.

`/gpflags reload` prepares both the candidate configuration and `flags.yml` snapshot before publishing. A rejected reload retains the previous known-good runtime. Successful reloads increment the configuration generation and close existing PlexonGPFlags menus.

World defaults are optional and sparse:

```yaml
world-defaults:
  01234567-89ab-cdef-0123-456789abcdef:
    pvp: true
    explosions: true
```

Changing defaults does not rewrite every claim record.

## Persistence and migration

`flags.yml` remains sparse and keyed by GriefPrevention claim ID. Schema 2 is written on successful persistence. An unversioned v1.0.1 store is accepted as legacy schema 1. A future schema version fails closed before it can be applied or rewritten.

Unknown stored flag IDs are quarantined rather than becoming active. On a first migration from `plugins/PlexonClaimFlags/flags.yml`, PlexonGPFlags requires the authoritative store to be absent, creates a backup under `migration-backups/`, imports the legacy file once, writes `legacy-import.complete`, and never requires the deprecated plugin to be installed.

Keep a copy of the complete `plugins/PlexonGPFlags/` directory before production upgrades.

## Commands

```text
/gpflags
/gpflags claim
/gpflags claims
/gpflags flags
/gpflags set <flag> <on|off|inherit>
/gpflags inspect [claimId]
/gpflags diagnostics
/gpflags reload
```

`inspect`, `diagnostics`, and `reload` require admin permission. Compatibility aliases remain `/claimhelp`, `/claimsflags`, `/claimflags`, and `/cf`.

## Diagnostics and performance

Diagnostics are bounded and do not scan all GriefPrevention claims. They report plugin/Core/GP state, config generation/schema, store schema, definition/record/override counts, sparse world-default count, quarantined flags, migration state, persistence state, last reload result, rejected mutations, stale GUI actions, protection decision/denial counters, pending teleports and API registration.

Protection listeners are synchronous and use the in-memory store only. They do not perform YAML/database/network/PlaceholderAPI access, schedule one task per event, or scan all claims. All enforcement handlers use `ignoreCancelled=true`; PlexonGPFlags never uncancels another protection plugin's denial.

## Public APIs

The existing `com.plexon.gpflags.api.PlexonGPFlagsAPI` contract remains available. The `net.plexon.claimflags.*` compatibility API/event facade remains packaged for consumers migrating from PlexonClaimFlags 1.1.x. Neither facade exposes mutable persistence objects.

## Upgrade and rollback

Upgrade from `v1.0.1` with the deprecated addon absent. Existing stable flag IDs and sparse claim associations are retained. For rollback, stop the server, restore the backed-up plugin data if needed, and reinstall `v1.0.1` at commit `757f62fa52fdb6ffa718c57ab4515b60c5aa23f3`.

The live validation matrix remains in `docs/RUNTIME-CERTIFICATION-1.1.0.md` as an operational follow-up for deployment evidence.

## Build and release verification

With JDK 25 and Gradle 9.1.0, provision the pinned PlexonCore 2.0.4 API artifact and run:

```bash
gradle clean check javadoc
```

Stable output: `build/libs/PlexonGPFlags-1.1.0.jar`.

GitHub CI verifies accepted Phase 3 ancestry, all tests with zero failures/errors/skips, Java class major 69, Paper 26.2 metadata, hot-path restrictions, required public compatibility classes, dependency isolation, SHA-256 integrity and provenance. The stable publisher accepts only the exact current `main` commit and rebuilds the artifact before publishing `v1.1.0`.
