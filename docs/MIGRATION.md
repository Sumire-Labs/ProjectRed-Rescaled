# Cleanroom / Scala 3.10 migration

## Baseline and provenance

- ProjectRed baseline: `11da8db1` on the legacy Minecraft 1.12 branch.
- The source migration uses kappa-maintainer/ProjectRed at
  `3a7b284b336df13bbc71858e061248154f5b300c` as a syntax and mapping reference.
  This predates its pneumatic replacement, Smart Lamp and Fabrication production chain.
- Dependencies and tooling use the Sumire Labs forks and CleanroomDevEnv.
  There is no dependency on kappa's Maven endpoints or Unimined build.
- Template-derived build support comes from Sumire-Labs/MrTJPCore-Rescaled at
  `9563657719ed3deb9c72895517902caf84ec9244` (CleanroomDevEnv-derived).
- Dynamic model fallbacks follow kappa-maintainer/ProjectRed `fd83f325371aec527c19de4c69a26cc7e2f7e3fc`.
- Payload NBT type correction follows GTNewHorizons/ProjectRed
  `904cceb6a57e8941d0ae2527baea12f1f2392aac`.
- Crafting-chip null-output handling follows GTNewHorizons/ProjectRed
  `101fa3017a83c01be611b3ede4a0e98239924b9f`.
- Wire flag restoration adapts upstream ProjectRed
  `e631f983c9b31c79f04deffa92ae5e68468326a0`, preserving nested previous states.
- Propagation reference cleanup follows the applicable 1.12 portion of
  GTNewHorizons/ProjectRed `fc69ec35274a4e2e2cd61e0e79cf52d32a7e08e3`.

## Architecture

Eleven internal Gradle projects compile in dependency order. The root collects their
class outputs and shared assets and reobfuscates one runtime jar. Internal jars are
not publication artifacts. Existing logical mod IDs are retained, including the actual
historical `projectred-compat` entrypoint. Its metadata and
`ProjectRedAPI.modIDCompatibility` now agree with that entrypoint.

Version constants are generated before Scala compilation so annotations and TASTy
agree. The bundle preserves ten mod metadata entries, Scala TASTy, MIT notices and
`META-INF/projectred-relocation_at.cfg` with its `FMLAT` manifest attribute.
ComputerCraft API stubs are compile inputs only, excluded from distribution and sources.

An incremental build across a Git-version change exposed stale Scala 3 annotation
constants in Zinc outputs. Scala compile tasks now track the version explicitly and
fully compile affected modules, retaining Gradle's task and build caches. Distribution
verification also checks the version constant in every logical mod entrypoint.

The root exports only external dependencies to its children. No child depends on
the root bundle's classes. Each module uses Cleanroom userdev and the same ATs.
Root development runs include every module's class output.

## MrTJPCore Rescaled integration

The Sumire fork is the authoritative API, including its changed behavior. The initial
server run exposed a Relocation mismatch: the migration reference passed IndexedSeq
to `ModConfig.put`, while the current library supports typed arrays. Relocation now
passes arrays directly.

Client tile actions opt in through `readFromClient`, separate from server-to-client
`read`. AutoCrafter, Project Bench and Filtered Importer require an open inventory
container referring to the tile, a loaded matching tile, permission to modify the
block, and a nearby non-spectator sender. Project Bench cannot clear another player's
grid by sending their entity ID.

IC Workbench actions also require an active watcher. Client streams accept editing
operations and tile controls, not simulation snapshots. New-board messages send a name
and validated dimensions instead of client-supplied tile/simulation descriptions.
Matching client/server builds are required for this changed internal packet format.
Names are bounded and edits/renames mark the tile dirty.

Large IC item share tags and multipart descriptions use a compressed transport
envelope. The receiver restores the full tag before item or circuit code sees it.
Item/world saves retain the original NBT format, and small/legacy tags still read
directly. Decoding is bounded to one MiB compressed, four MiB expanded and sixteen MiB
of NBT allocation accounting. Client and server must both support the envelope.

The barrel's overridden extraction preserves its hidden item; insertion stops if no
items are accepted. These need ProjectRed-side handling even though the library's
base wrappers already contain fixes.

## Other fixes

- Correct the compound-list tag type used to load machine payloads.
- Preserve payload FIFO order and clear queue entries before repeated loads.
- Restore wire power/connection flags with `finally`, preserving nested states.
- Release world/parent/caller references from pooled runs; clear pools on server stop.
- Ignore excess crafting promises when a chip has no output item.
- Use legacy GUI key codes and the client Shift helper on Cleanroom.
- Adapt IC Printer overrides/rendering to current userdev API names and release its
  temporary world/entity references after rendering.
- Restore IC Workbench scissor state even if tooltip rendering throws.
- Add valid fallbacks for dynamically rendered machines/moving blocks and frame particles.

## Verification

`verifyDistribution` checks ten unique mod entries, matching versions, module classes,
Java 25 bytecode, Scala TASTy, AT/manifest and license notices. It rejects unresolved
metadata tokens and bundled test/API-stub classes.

The server fixture checks module loading, payload counts/NBT/FIFO/repeated load,
Project Bench inventory NBT, open/closed/distant tile authorization, vanilla-powered
multipart wire placement and serialization, nested/exceptional wire flags, and pool
cleanup. The fixture runs in development and on installed Cleanroom using SRG jars.
The installed-server check omits JEI to verify its optional nature.

The client fixture creates a flat world, renders every creative item variant,
checks that each resolves a model, and opens/resizes the IC Workbench with screenshots.
These checks do not establish complete saved-world or third-party modpack compatibility.

Subsequent issue reproductions, repairs and expanded coverage are recorded in
[the review log](REVIEW_LOG.md) and [issue triage](ISSUE_TRIAGE.md).

Pinned dependency sources in `.github/scripts/verify.ps1` are built as Scala 3.10
fixtures. Only the temporary CBMultipart copy adjusts its Scalar version declaration.
The actual sibling repository is untouched. The Scalar fixture uses its pinned actual
language adapter with Scala 3.10 libraries. Release workflows never publish fixtures.

## Remaining release work

- Publish intended Scala 3.10 MrTJPCore / CBMultipart / Scalar releases and update pins;
  current development coordinates are not all available remotely.
- Set ProjectRed's CF/MR IDs and enabled destination secrets.
- Exercise copied real saves containing active logistics, ICs, moving frames, barrel
  contents and retrogen flags; check third-party inventory capability behavior.
- Exercise gate timing and larger logistics/IC networks beyond smoke coverage.
- Select later feature backports separately. Smart Lamp, new pneumatic routing and
  Plotting/Lithography/Packaging Tables are not included in this baseline migration.
  Old Pressure/Resistance Tube registrations, the IC Printer and the single-step IC
  recipe are preserved.
