# PlexonGPFlags 1.1.1

Release-engineering patch over the stable 1.1.0 gameplay line.

## What changed

- Preserves all PlexonGPFlags 1.1.0 runtime behavior and the cross-claim PvP bypass correctness fix.
- Generates `SHA256SUMS.txt` with the release JAR basename rather than a build-directory path.
- Downloads the published GitHub Release assets and verifies their checksum directly in the download directory.
- Retains exact-current-`main` publication, the full 51-test/Javadoc/distribution contract, Java 25 / Paper 26.2 validation, dependency isolation and provenance evidence.

No claim schema, flag schema, command, permission, API, GriefPrevention authority, persistence or player-facing behavior changes are introduced by this patch.

Live PlexonCraft runtime certification remains a separate operational follow-up and may remain `NOT_EXECUTED` in release provenance.

Rollback baseline remains `v1.0.1` / `757f62fa52fdb6ffa718c57ab4515b60c5aa23f3`.
