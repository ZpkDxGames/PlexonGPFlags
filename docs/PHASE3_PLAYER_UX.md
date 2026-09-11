# PlexonGPFlags Phase 3 — Player UX Overhaul

## Frozen boundary

- Repository: `ZpkDxGames/PlexonGPFlags`
- Frozen Phase 2 branch: `phase2/1.1.0-policy-hardening`
- Exact Phase 2 candidate: `88cb5d6205766c66515fdc5d3f55ba1f78fe5033`
- Existing candidate: `v1.1.0-rc.1`
- Phase 3 branch: `phase3/player-ux-overhaul`
- Runtime certification: **NOT EXECUTED**

This phase changes player presentation and navigation. It does not create a second claim system, policy store, trust authority, teleport coordinator, visualizer, or persistence identity.

## Authority boundary

GriefPrevention remains authoritative for claim creation/removal, ownership, trust, boundaries, subdivisions, numeric claim identity, claim-block accounting and claim persistence.

PlexonGPFlags remains authoritative only for Plexon claim policy, claim-management UX, safe orchestration, diagnostics and sparse policy persistence.

The following Phase 2 authorities remain intact:

- `ClaimService`
- `FlagService`
- `FlagStore`
- `ClaimActionService`
- `PromptService`
- `TeleportService`
- `VisualizerService`
- `ProtectionListener`
- public PlexonGPFlags API/events
- legacy PlexonClaimFlags compatibility API
- optional PlexonCore lifecycle integration

## Old player journey

Phase 2 already provided functional native inventories:

1. Claim Hub
2. My Claims
3. Claim Dashboard
4. Claim Areas
5. Claim Flags
6. Trusted Players
7. Trust Level
8. Create Claim
9. Resize Direction / Resize
10. Teleport
11. Visualizer
12. Abandon confirmation

The main usability gaps were:

- numeric GriefPrevention IDs appeared in ordinary titles and abandonment text;
- flag mutation relied on undocumented left-click/right-click semantics;
- trusted-player cards removed access immediately when clicked;
- Dashboard Back returned to My Claims page 1 instead of the originating page;
- parent/subdivision navigation did not retain the conceptual origin;
- effective policy and sparse configured policy were described with implementation-oriented terms;
- stale configuration sessions were guarded, but individual claim ownership/bounds/parent identity were not captured in the inventory presentation token;
- creation-cost presentation duplicated the claim-block calculation in the GUI.

## New player journey

The Phase 3 product hierarchy is:

```text
Claims Home
├─ Current Claim -> Claim Dashboard
├─ My Claims -> Claim Dashboard
├─ Create Claim
└─ Claim Shovel

Claim Dashboard
├─ Rules -> Rule Details -> Allow / Block / Use Parent or Use Server Default
├─ Trusted Players -> Player Details -> Change Access / Remove Trust confirmation
├─ Claim Areas -> Main Claim / Subdivision -> Claim Dashboard
├─ Resize -> Direction -> Preview -> Confirm Resize
├─ Teleport
├─ Show Boundary
└─ Abandon Claim -> explicit confirmation
```

Feature-disabled controls remain absent.

## Claims Home

`Claims Home` is the ordinary entry point for `/gpflags`.

The Current Claim card has three meaningful states:

- manageable current claim: human claim card + direct dashboard action;
- outside a claim: explicit unavailable explanation;
- inside a claim the player cannot manage: explicit authority explanation.

No numeric claim ID is used as the primary identity.

## Claim presentation

`ClaimPresentation` builds immutable, non-authoritative screen data from the current GriefPrevention claim object.

Player-facing claim identity uses:

- `Main Claim` or `Subdivision N`;
- world;
- approximate center coordinates;
- width × length;
- area;
- subdivision count for main claims;
- whether the player is currently standing there.

Numeric GriefPrevention IDs remain hidden routing state only.

`ClaimService.areaLabel` is now intentionally player-facing (`Main Claim` / `Subdivision N`). Admin diagnostics and `/gpflags inspect` remain the technical surfaces where numeric IDs are still appropriate.

## My Claims

My Claims uses a bounded 28-entry content region per page and the Plexon navigation row:

- `45` Previous
- `48` Claims Home / Back
- `51` page/status
- `52` Close
- `53` Next

Selecting a claim creates a dashboard context containing the exact originating page. Dashboard -> Back restores that page instead of page 1.

No persistence write occurs during navigation.

## Claim Areas

Claim Areas presents one current authoritative snapshot consisting of the main claim plus its current GriefPrevention children.

The UI distinguishes:

- Main Claim
- Subdivision N
- currently selected area

`ClaimNavigationContext` carries the parent claim routing ID, original dashboard claim routing ID and area page. Selecting a subdivision opens its dashboard; Back returns to the same Claim Areas context; leaving Claim Areas returns to the originating dashboard, whose own Back still returns to the original Home/My Claims origin.

No boundary geometry is copied into a persistent Plexon model.

## Dashboard

Claim Dashboard answers:

- where am I managing?
- what rules apply?
- who has access?
- what can I change?
- what will each action do?

Available controls are generated from existing feature flags:

- Rules
- Trusted Players
- Claim Areas
- Resize (main claims only)
- Teleport
- Show Boundary
- Abandon Claim (main claims only)

Teleport lore exposes the configured warmup and configured movement/damage cancellation behavior before the player clicks.

Show Boundary explicitly refers to the selected claim only.

## Rules and Rule Details

The ten stable Phase 2 flag IDs are unchanged.

The Rules list is read-only navigation. Selecting a rule opens Rule Details; card clicks no longer mutate policy and right-click is not a hidden reset mechanism.

Rule Details exposes three explicit actions:

- **Allow** -> existing `FlagOverride.OFF`
- **Block** -> existing `FlagOverride.ON`
- **Use Parent** for a subdivision when parent inheritance is enabled, otherwise **Use Server Default** -> existing sparse `FlagOverride.INHERIT`

The player-facing state model separates:

```text
Current behavior: Allowed | Blocked
This claim: Custom Override | Inherited from parent | Server Default
```

The UI never explains policy as `FlagOverride.ON`, `FlagOverride.OFF`, `INHERIT`, `EXPLICIT`, null or sparse-key absence.

Before each flag mutation, the GUI re-resolves the hidden GriefPrevention claim ID, verifies the immutable claim token, verifies management authority, uses a single-submit guard and then delegates to `FlagService.set`.

`FlagService` and `FlagStore` remain the only policy mutation/persistence authorities.

## Trusted Players

Trusted-player cards are now navigation, not immediate revocation actions.

Flow:

```text
Trusted Players
-> Player Details
   -> Change Access
      -> Access / Container / Build / Manage
   -> Remove Trust
      -> explicit confirmation
```

Only real GriefPrevention trust levels are offered:

- Access
- Container
- Build
- Manage

Existing UUID trust keys remain hidden. Online UUID-backed entries use the online player name; offline UUID-backed entries receive bounded generic display labels rather than exposing raw UUIDs. No per-entry task or persistent name cache is added.

Changing/removing trust continues to delegate to `ClaimActionService`, which re-resolves the claim and verifies management authority before invoking GriefPrevention.

## Add Player and prompts

`PromptService` remains one central AsyncChat listener with one shared expiry sweep while prompts exist.

Prompt messages now state:

- expected input;
- `cancel` behavior;
- configured timeout.

The Add Player callback captures a claim token and navigation context. Before continuing to access-level selection, it re-resolves the claim and validates ownership/parent/world/bounds plus management authority. A stale prompt therefore cannot continue against a no-longer-valid claim.

## Create Claim

Preset and custom-size behavior remains backed by `ClaimActionService` and GriefPrevention.

`ClaimActionService.CreatePreview` centralizes the same size/claim-block calculation used immediately before creation. Cards display:

- side length;
- required claim blocks;
- available claim blocks;
- possible / insufficient state.

The GUI no longer owns a parallel creation-cost calculation.

Creation itself still calls GriefPrevention `dataStore.createClaim` through `ClaimActionService.createSquare`.

## Resize

Resize remains top-level-claim only.

Flow:

```text
Resize
-> North / South / East / West
-> bounded offset controls
-> ResizePreview
-> Confirm Resize
```

The GUI displays `ClaimActionService.ResizePreview`; it does not reproduce minimum-area, child-boundary or maximum-expansion mathematics.

The claim token is revalidated before each resize-navigation action and before confirmation. The final mutation remains `ClaimActionService.resize` -> GriefPrevention `resizeClaim`.

## Teleport

`TeleportService` remains the single shared teleport warmup coordinator. No GUI scheduler was added.

The dashboard shows the selected claim, warmup and configured cancellation behavior. Clicking delegates to `TeleportService.request`.

The service still revalidates the claim/authority before warmup and again before execution. Completion feedback now uses player-facing claim labels rather than numeric claim IDs.

## Visualizer

`VisualizerService` is unchanged.

It remains:

- selected-claim only;
- one shared ticker;
- bounded by configured particle budget;
- free of global claim scans.

The player action is explicitly named `Show Boundary`.

## Abandon Claim

Abandon remains destructive and retains explicit confirmation.

The confirmation identifies the claim by world/center/size and states that the claim will be removed. If subdivisions currently exist, their relationship to the main claim is stated before confirmation.

A claim-token revalidation and single-submit guard occur before the existing `ClaimActionService.delete` path.

Numeric IDs are not used as the player-facing confirmation identity.

## Navigation and stale lifecycle

`ClaimNavigationContext` is a bounded immutable routing model containing only current-screen navigation information:

- root origin (Claims Home or My Claims);
- My Claims page;
- Claim Areas page;
- Trusted Players page;
- hidden parent/origin claim IDs required to return through Claim Areas;
- whether a selected-area dashboard should Back into Claim Areas.

It is not persistent and is not a claim-domain replacement.

`ClaimPresentation.Token` captures claim identity at screen creation:

- GriefPrevention claim ID;
- owner UUID;
- parent claim ID;
- world UUID;
- X/Z bounds.

Before a mutation or context-sensitive navigation, the current GriefPrevention claim is re-resolved and matched against that token. Claim deletion, ownership transfer, parent change or boundary change rejects the stale action and returns the player to Claims Home/My Claims.

Phase 2 `MenuSessionGuard` remains in place as the separate actor/configuration-generation guard.

## Performance safeguards

Phase 3 adds no repeating GUI redraw task.

The menu layer contains no:

- per-player repeating GUI task;
- menu tick refresh;
- global `getClaims()` scan;
- file I/O;
- disk write merely for navigation;
- task per trust entry;
- task per flag;
- unbounded GUI cache.

Screens use one bounded claim/trust/rule snapshot where practical. Player profile resolution is not performed once per trust entry on every redraw; offline UUID-backed entries use a bounded generic label unless the player is currently online.

Existing Prompt, Teleport and Visualizer shared coordinators remain bounded and stop when idle.

## Protection hot path

`ProtectionListener` is not modified by Phase 3.

The inherited verification continues to require:

```text
cheap event rejection
-> required GriefPrevention lookup
-> in-memory effective flag lookup
-> decision
```

The build rejects filesystem/config/scheduler operations in that protection source and rejects a global visualizer claim scan.

## Persistence / migration / compatibility

No database or flag-schema migration is added.

Preserved unchanged:

- schema 2;
- stable flag IDs;
- claim-ID keyed sparse persistence;
- ON/OFF/INHERIT semantics;
- parent/subclaim inheritance;
- world/global defaults;
- transactional reload candidate validation;
- future-schema fail-close;
- legacy PlexonClaimFlags import;
- source backup + import marker;
- public and legacy APIs/events;
- optional PlexonCore lifecycle.

`config.yml` and `messages.yml` defaults are updated additively for player wording. Existing production GUI title templates containing `#%claim%` are sanitized at render time so numeric identity is not leaked even before an operator adopts the new default title strings.

## CI boundary

Phase 2's workflow only ran pushes for `phase2/**` and pull requests targeting `main`. Phase 3 extends the workflow triggers to:

- push: `phase3/**`
- pull request base: `phase2/**`

The verification body itself remains intact: Java 25, Paper 26.2 dependency, exact PlexonCore 2.0.4 provisioning/checksum, full Gradle checks/Javadocs, hot-path checks, distribution dependency isolation, class major 69, test summary and candidate checksum artifact.

## Operator-ready runtime plan

Runtime remains pending until performed on PlexonCraft. Validate:

1. `/gpflags` Claims Home.
2. Claims Home outside any claim.
3. Claims Home while standing in an owned claim.
4. My Claims pagination.
5. exact My Claims page -> Dashboard -> Back restoration.
6. main-claim presentation.
7. subdivision presentation.
8. Claim Areas parent/subdivision navigation and Back context.
9. Rules effective Allowed state.
10. Rules effective Blocked state.
11. Custom Override presentation.
12. subdivision parent-inheritance presentation.
13. server-default presentation.
14. explicit Allow mutation.
15. explicit Block mutation.
16. subdivision Use Parent/reset mutation.
17. top-level Use Server Default/reset mutation.
18. rapid repeated rule clicks.
19. trusted-player list/details.
20. add player prompt, cancel and timeout.
21. Access / Container / Build / Manage changes.
22. Remove Trust confirmation/cancel.
23. small/medium/large creation presets.
24. insufficient claim blocks.
25. custom-size prompt/cancel/timeout.
26. resize North/South/East/West and preview.
27. subdivision resize restriction.
28. teleport warmup, movement cancellation and damage cancellation.
29. selected-claim visualizer and particle budget.
30. abandon confirmation/cancel with subdivisions.
31. stale GUI after claim deletion.
32. stale GUI after ownership transfer.
33. stale GUI after boundary/parent change.
34. stale Add Player prompt after claim change.
35. sparse flag persistence across normal restart.
36. legacy flag preservation/import path.
37. GriefPrevention protection interoperability.
38. optional PlexonCore lifecycle/diagnostics.
39. repeated GUI navigation under Spark/MSPT.
40. protection-event path under Spark/MSPT.
41. integrated >=30-minute ecosystem soak.

Do not infer runtime PASS from CI.
