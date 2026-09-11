# PlexonGPFlags 1.1.x — PlexonCraft runtime certification matrix

Current deployment evidence state: **NOT EXECUTED**.

This matrix is an operational follow-up for validating the latest stable `v1.1.1` artifact on PlexonCraft. Repository/source/release closure is established independently by GitHub CI and the exact-`main` stable publication workflow; this checklist does not block the verified stable release.

Execute the following against the exact published stable JAR:

- representative v1.0.1 upgrade and existing override preservation
- legacy PlexonClaimFlags data import rehearsal, backup, marker and idempotency
- startup with supported GriefPrevention
- startup failure/disable behavior without required GriefPrevention
- startup refusal with deprecated PlexonClaimFlags / GriefPreventionAddon active
- owner claim editor and each GriefPrevention trust level
- unauthorized, admin and admin-claim authorization behavior
- explicit ON, explicit OFF and INHERIT resolution plus world/default precedence
- cross-claim PvP where attacker/victim occupy different claims and bypass/ownership applies to only one side
- claim creation, resize, deletion, ownership transfer and trust change
- stale GUI after reload, claim deletion and authorization change
- repeated successful reload with no listener multiplication
- invalid reload retains previous known-good runtime
- persistence restart reconstruction and future config/store schema fail-close
- all ten supported flag enforcement paths
- already-cancelled event interoperability and other protection plugin coexistence
- public and legacy API/event compatibility and PlexonCore 2.0.4 integration
- Spark/MSPT comparison against v1.0.1 under representative interactions
- at least 30 minutes soak
- zero HIGH/CRITICAL defects

Record exact server/Paper/Java/GP/Core versions, stable tag/SHA, JAR SHA-256, Spark evidence and any deviations when executing this matrix.
