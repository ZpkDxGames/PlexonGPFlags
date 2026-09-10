# PlexonGPFlags 1.1.0-rc.1 — Security and abuse review

Status: **PASS — no known HIGH/CRITICAL source blockers at candidate preparation.**

This review is source/static only. It is not a substitute for the required PlexonCraft runtime certification.

## Reviewed boundaries

- Claim authority: GriefPrevention remains authoritative; policy is keyed by GP numeric claim ID.
- Mutation authorization: `FlagService` rechecks `ClaimService.canManage` on the primary thread. GUI controls are not the security boundary.
- Trust escalation: ordinary Access/Container/Build/Manage trust does not grant PlexonGPFlags flag mutation under current policy.
- Admin claims require the dedicated admin-claims permission; bypass remains separate from persisted flag state.
- Stale GUI: click/drag routing cancels item movement; claim identity/authorization are re-resolved before mutation; actor/config-generation mismatch is rejected by `MenuSessionGuard`.
- Cross-claim mutation: menus retain GP claim IDs, not display titles or slots, and resolve them again before mutation.
- Persistence: writes use temporary-file replacement and in-memory rollback. Future schemas are rejected before application.
- Malformed/unknown data: strict config types are rejected; unknown persisted flag IDs are quarantined instead of activated.
- Legacy migration: runs only when the authoritative store is absent, creates a backup and completion marker, validates the legacy folder, and does not require the old plugin.
- Dual implementation: startup is refused if deprecated PlexonClaimFlags / GriefPreventionAddon is active.
- Event interoperability: protection handlers use `ignoreCancelled=true` and never call `setCancelled(false)`.
- Hot paths: distribution checks reject disk/config parsing, PlaceholderAPI and scheduler calls in `ProtectionListener`; no global claim scan is introduced.

## Runtime-only gates still required

Ownership transfer, claim deletion/recreation, trust changes, real migration files, third-party protection interoperability, permission-plugin behavior, failed reload recovery, Spark/MSPT behavior and soak stability remain runtime gates. Any discovered HIGH/CRITICAL defect blocks stable promotion and requires a new RC boundary.
