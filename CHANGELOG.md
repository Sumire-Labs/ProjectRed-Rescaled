# Changelog

## [Unreleased]

### Changed

- Migrate Minecraft 1.12.2 ProjectRed to Cleanroom, Java 25 and Scala 3.10.
- Use CleanroomDevEnv-derived builds and Cleanroom userdev mappings.
- Compile modules as Gradle subprojects and distribute one runtime jar.
- Use Sumire Labs MrTJPCore Rescaled and CBMultipart Rescaled dependencies.
- Add Maven/CurseForge/Modrinth release workflows and isolated integration verification.

### Fixed

- Preserve machine payload NBT, counts and queue order when loading.
- Recover wire flags after exceptions and release pooled world references on shutdown.
- Handle crafting chips without output items.
- Adapt tile actions and Relocation configuration arrays to MrTJPCore Rescaled.
- Preserve hidden barrel items and stop non-progressing insertion loops.
- Correct Cleanroom keyboard handling, IC Printer rendering and dynamic fallback models.
- Restore IC Workbench clipping state after tooltip errors.
- Align compatibility metadata/API with the historical `projectred-compat` ID.
- Prevent stale Scala Mod annotation versions after Git-version changes.
- Release temporary IC Printer renderer world/entity references.
- Execute IC scheduled events at their deadlines and preserve callback rescheduling.
- Close tile inventory windows when the player leaves range or the tile is removed.
- Avoid server stalls when compiling dense IC wire networks (upstream #1747).
- Preserve actual tool wear/NBT in crafting remainders and roll back unsuccessful crafts (upstream #1580).
- Use numeric narrowing rather than runtime casts when restoring bundled digital signals.
- Preserve all 16 bus channels through saves and repair legacy converter outputs after resampling.
- Refresh and persist Counter GUI edits and honor two-tick Timer/StateCell interval reductions.
- Finish Synchronizer pulses after reset and retain falling-edge feedback inputs.
