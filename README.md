# PlexonGPFlags

**PlexonGPFlags 1.1.0-rc.1** is the Phase 2 release candidate for the PlexonCraft GriefPrevention claim-flags product. It remains a GriefPrevention companion: GriefPrevention owns claim identity, boundaries, ownership, trust and claim persistence; PlexonGPFlags owns only Plexon flag policy, editing UX, diagnostics and compatible flag persistence.

> Runtime certification is **NOT EXECUTED**. Do not promote this candidate to stable until the PlexonCraft runtime matrix is complete.

## Runtime

- Paper `26.2.build.121-stable`
- Java 25 (class major 69)
- GriefPrevention 16.18.7+; required
- PlexonCore 2.0.4 compatible/optional
- Config schema 2
- Flag-store schema 2

**Never install PlexonGPFlags together with the deprecated PlexonClaimFlags / GriefPreventionAddon plugin.** PlexonGPFlags keeps compatibility API classes for existing consumers, but the deprecated plugin itself is not a runtime dependency and must not be active.

## Claims and authorization

GriefPrevention's numeric `Claim#getID()` is the authoritative flag-record identity. The plugin does not create a parallel claim ID or duplicate GriefPrevention ownership.

Flag edits are authorized at the mutation service. Existing policy remains owner/admin-only; ordinary GriefPrevention Access, Container, Build or Manage trust does not implicitly grant PlexonGPFlags flag-edit permission. Administrative claims require `plexongpflags.adminclaims`.

GUI mutations re-resolve the stored claim ID and re-check authorization. PlexonGPFlags inventories are additionally bound to the actor and configuration generation, so a successful reload invalidates old inventory sessions.

## Stable flags and resolution

A flag value of **ON** means PlexonGPFlags blocks the described behavior. Stable IDs are unchanged:

`natural-mobs`, `spawner-mobs`, `pvp`, `building`, `interactions`, `containers`, `explosions`, `fire`, `crop-trampling`, `mob-griefing`.

Explicit state is three-state: `ON`, `OFF`, or `INHERIT`. Effective policy resolves predictably:

1. explicit claim override;
2. parent policy for subclaims when inheritance is enabled;
3. sparse world default keyed by Bukkit world UUID;
4. global default.

Bypass is authorization policy, not a persisted flag value.

## Configuration and reload

`config.yml` schema 2 is strictly validated before runtime publication. Wrong scalar/list types, unknown default flag IDs, invalid materials/spawn reasons, unsafe legacy-folder paths, unknown world-default flag IDs, and unavailable configured world UUIDs reject startup/reload instead of being silently accepted.

`/gpflags reload` prepares both the candidate configuration and `flags.yml` snapshot before publishing. A rejected reload restores the previous known-good runtime. Successful reloads increment the configuration generation and close existing PlexonGPFlags menus.

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

Keep a copy of the entire `plugins/PlexonGPFlags/` directory before upgrading.

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

## Diagnostics

Diagnostics are intentionally bounded and do not scan all GriefPrevention claims. They report plugin/Core/GP state, config generation/schema, store schema, definition/record/override counts, sparse world-default count, quarantined flags, migration state, persistence state, last reload result, rejected mutations, stale GUI actions, protection decision/denial counters, pending teleports, and API registration.

## Performance contract

Protection listeners are synchronous and use the in-memory store only. They do not perform YAML/database/network/PlaceholderAPI access, schedule one task per event, or scan all claims. All enforcement handlers use `ignoreCancelled=true`; PlexonGPFlags never uncancels another protection plugin's denial.

## Public APIs

The existing `com.plexon.gpflags.api.PlexonGPFlagsAPI` contract remains available. The `net.plexon.claimflags.*` compatibility API/event facade remains packaged for consumers migrating from PlexonClaimFlags 1.1.x. Neither facade exposes mutable persistence objects.

## Upgrade and rollback

Upgrade from `v1.0.1` with the deprecated addon absent. Existing stable flag IDs and sparse claim associations are retained. If startup or runtime certification fails, stop the server, restore the backed-up plugin data if needed, and reinstall `v1.0.1` at commit `757f62fa52fdb6ffa718c57ab4515b60c5aa23f3`.

Stable `v1.1.0` remains blocked until the runtime checklist in `docs/RUNTIME-CERTIFICATION-1.1.0.md` is completed with zero HIGH/CRITICAL defects.

## Build

```bash
gradle clean check javadoc
```

Candidate output: `build/libs/PlexonGPFlags-1.1.0-rc.1.jar`.
