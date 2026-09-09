# PlexonGPFlags

**PlexonGPFlags 1.0.0** is the unified GriefPrevention control panel for PlexonCraft. It replaces the separate PlexonClaimFlags addon and the need for a second claim-GUI plugin with one Paper-native plugin.

## Runtime

- Paper 26.2
- Java 25
- GriefPrevention 16.18.7+ (required)
- PlexonCore optional; detected for diagnostics without becoming a runtime requirement

## One UI for claims and flags

`/gpflags` opens the claim hub. Compatibility commands `/claimhelp`, `/claimsflags`, `/claimflags`, and `/cf` are retained.

Players can:

- create centered claims from presets or chat-entered sizes;
- request a golden claim shovel with a cooldown;
- browse top-level claims with pagination;
- open a claim dashboard;
- configure protection flags on main claims and subdivisions;
- inherit parent flag values on subdivisions;
- manage GriefPrevention trust levels;
- resize claims north/south/east/west with cost previews;
- teleport to a selected claim with movement/damage cancellation;
- preview only the selected claim boundary with bounded particles;
- abandon claims behind a confirmation screen.

## Flags

When a flag is **ON**, PlexonGPFlags prevents that behavior in the claim:

- `natural-mobs`
- `spawner-mobs`
- `pvp`
- `building`
- `interactions`
- `containers`
- `explosions`
- `fire`
- `crop-trampling`
- `mob-griefing`

GriefPrevention remains authoritative for ownership, claim boundaries, trust, subdivisions, claim blocks, overlap validation, and persistent claim IDs.

## Performance model

- protection listeners read an in-memory flag map only;
- no database;
- no disk reads/writes in gameplay protection listeners;
- no recurring global claim scan;
- the particle visualizer renders only explicitly selected claims;
- teleport cancellation uses one central pending-player map/listener;
- GUI pages render only visible claims;
- flag persistence uses atomic temporary-file replacement on infrequent mutations.

## Migration from PlexonClaimFlags

On first start, if `plugins/PlexonGPFlags/flags.yml` is missing, PlexonGPFlags checks the old `plugins/PlexonClaimFlags/flags.yml` and imports it. Keep the old plugin folder until the first successful startup has been verified.

Remove the old PlexonClaimFlags JAR before enabling PlexonGPFlags so both plugins do not enforce the same events.

## Commands

```text
/gpflags
/gpflags claim
/gpflags claims
/gpflags flags
/gpflags set <flag> <on|off|inherit>
/gpflags reload
/gpflags diagnostics
```

Compatibility: `/claimhelp`, `/claimsflags`, `/claimflags`, `/cf`.

## Build

```bash
gradle clean check
```

Output: `build/libs/PlexonGPFlags-1.0.0.jar`.

## Attribution

The claim-management UX is independently implemented for PlexonGPFlags and was informed by the public feature set of GriefPreventionEasyGUI by hope61. No InvUI or bStats runtime code is bundled. See `THIRD_PARTY_NOTICES.md`.
