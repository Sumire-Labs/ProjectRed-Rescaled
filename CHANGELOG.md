# Changelog

## [Unreleased]

### Added

- Add the newer perspective Workbench camera, animated layer navigation, direct interaction zones and freehand wire placement.
- Migrate supported placed legacy ICs to the new simulator with running state, phase, input edges and pending pulses preserved; retain recovery copies and compatibility reasons.

- Port the 1.21.1 IC compiler and simulator, including 20 gates, staged compilation and synchronized compile trees/problems.
- Add full-width Bundled Bus I/O and channel controls to the Workbench.

- Add Illumar Smart Lamps with mixed bundled-channel colours, variable brightness and six placement orientations.
- Add Plotting, Lithography and Packaging Tables for IC production, with photomasks, rough silicon wafers and dies.
- Add Pneumatic Tubes with cached graph routing, item-flow rendering, link particles and sounds. Existing Pressure/Resistance Tubes remain available.

### Changed

- Use modern compiled targets throughout IC production and new fabricated-gate runtimes, retaining legacy drawing readers.
- Persist large engine JSON as bounded compressed NBT and preserve two-tick IC input latching.
- Update integration fixtures to the Sumire Scala 3.10 revisions and their client random-tick fix.

- Align Auto Crafter work time, power consumption, plan cycling, GUI and appearance states with 1.21.1.
- Adapt the newer IC Workbench tabs and tool palette, with manual/automatic compilation and compile diagnostics.
- Use the newer Pneumatic Tube geometry and atlas for all connection shapes.
- Align basic lithography with the newer 4096nm wafer and fixed 16x16 production footprint.

- Backport 71 updated 1.21.1 textures for machines, mineral blocks, materials, tools, gem armor, backpacks and the IC perfboard.

- Migrate Minecraft 1.12.2 ProjectRed to Cleanroom, Java 25 and Scala 3.10.
- Use CleanroomDevEnv-derived builds and Cleanroom userdev mappings.
- Compile modules as Gradle subprojects and distribute one runtime jar.
- Use Sumire Labs MrTJPCore Rescaled and CBMultipart Rescaled dependencies.
- Add Maven/CurseForge/Modrinth release workflows and isolated integration verification.

### Fixed

- Keep layered drawings, vertical wire connections and layer-specific edits intact across saves and synchronization.
- Restore IC drawing alpha and GL state during layer transitions; keep analog zero-input levels consistent with the newer implementation.

- Route blocked pneumatic deliveries into backstuff buffers after checking ordinary destinations.

- Let decorative walls join vanilla walls and fence gates while retaining their existing variants and save IDs.

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
- Preserve boundary-tick AND Cell/Stacking Latch pulses and migrate legacy input snapshots.
- Clamp IC Counter starting values to the validated maximum when editing its settings.
- Compress large IC data in transit so blueprints, printed chips and placed gates fit the NBT network limits.
