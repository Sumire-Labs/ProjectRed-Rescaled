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
