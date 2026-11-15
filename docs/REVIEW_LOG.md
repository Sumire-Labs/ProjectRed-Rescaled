# Autonomous review

## User direction

This review phase ended when the user returned. They then authorized Smart Lamp,
the Fabrication production chain and pneumatic transport backports. Feature work
continues from this reviewed baseline and is documented in MIGRATION.md.

New feature backports are deferred until the user returns and says they are home.
Discuss Smart Lamp, the pneumatic replacement, Fabrication production machines and
newer-version module changes then. Until that message, review the implemented migration,
reproduce defects, fix them, verify and commit. Do not publish releases or expand the
feature scope. If a problem cannot be resolved with reasonable confidence, record the
reproduction, attempted fixes and remaining uncertainty before stopping.

## Review focus

- Fabrication editing packets and scheduled events after the MrTJPCore API migration.
- Queue/inventory save behavior and overridden wrapper contracts.
- Version generation, aggregate jar contents and module dependencies.
- Repeatable verification and release workflows, including clean/repeated runs.

Findings and verification results are appended with each repair commit.

## IC scheduled events

Reproduced `IC event fired before its deadline` in a running development server.
The scheduler compared deadlines in reverse. It also removed entries after executing
callbacks, discarding a callback's new schedule, and retained schedules at removed
component positions.

Corrected the comparison, snapshot due entries, consume each old entry before its
callback, and cancel entries when a component is removed/replaced. Added in-game
checks for early/on-time/late delivery, exactly-once execution, self-rescheduling and
replacement-component isolation. Development and installed-distribution server
verification both passed through `.github/scripts/verify.ps1 -Production`.

## Tile container lifetime and distance

Reproduced `Distant Project Bench inventory container remained usable`. Custom tile
packet guards did not protect vanilla window clicks because the inherited library
container always allowed interaction. Added a shared tile-bound container and applied
it to the machine, Project Bench, Filtered Importer, IC Printer and IC Workbench windows.
Portable/item containers remain independent of tile coordinates.

In-game checks cover valid nearby access, leaving/returning to range and block removal.
The client check now opens an actual server-side Project Bench, waits for block sync,
verifies that its GUI reaches the client, then teleports the player away and checks
that the server closes the window. That client verification passed alongside all
472 item variants and the IC Workbench render checks.

## Dense IC wire search — upstream #1747

The reported 32x32 dense blueprint was reproduced with 900 wire tiles. The original
channel search spent over 60 seconds in `List.contains` and triggered the server
watchdog. Its repeated pending-list scans/copies and per-direction duplicate states
are replaced with a queue and a set of discovered position/colour/propagation-group
states. Every port in a group is still recorded, and crossed groups remain distinct.

The same channel-search fixture takes about 45–63 ms in development after the repair.
Checks cover separated white/black bundled channels, crossed propagation groups,
agreement between topology/channel searches, all 3,600 dense-network ports, complete
input/output simulation and the same simulation after NBT save/reload. Timings describe
this fixture, not a general performance guarantee.

## Crafting remainders — upstream #1580

Reproduced incorrect tool wear using a recipe whose planned tool has damage 5 and
the actual stored tool has damage 37 plus identifying NBT. The returned tool was based
on the plan. Ingredient matching now keeps independent copies of actual accepted
ingredients for remainder calculation, adapting the intent of upstream d2957e7.
Crafting restores storage/grid state when ingredients, output capacity or recipe
evaluation fail, including shared stack references from `copy=false` storage loads.

Development and installed servers passed checks for actual wear/NBT, two successive
crafts, missing ingredients, partial output insertion and a throwing remainder recipe.
The graphical test also performed a real client/server Project Bench log-to-planks
craft and verified server consumption/result counts before the out-of-range close.
The item gallery now advances only after its page has rendered, and the isolated
client fixture disables pause-on-focus-loss to prevent background verification stalls.

## IDE output in subprojects

The IDE generated `bin/main` bytecode directories in the new modules. The legacy
root-only `/bin/` exclusion did not cover them. Ignore module `bin` output directories
alongside module `build` output; no generated binaries were added to the repository.

## Production verification launcher

The standalone production test used PATH's Java even when JAVA_HOME selected a
different JDK for Gradle, and had no process deadline. It now uses JAVA_HOME when
set, passes arguments through ProcessStartInfo.ArgumentList, limits the server run
to 180 seconds by default, and caches installers by Cleanroom version.

A one-second timeout probe terminated its child and left no stale PASS marker. A
normal run then passed with PATH deliberately pointing to Java 26 and JAVA_HOME to
Java 25; the actual Cleanroom server log confirmed Java 25.0.4.1.

## Numeric narrowing in the Scala 3 port

A bundled-gate reload reproduced Integer-to-Byte ClassCastException in
`BundledCommons.unpackDigital`. The conditional integer expression used a type cast
instead of numeric conversion. Replaced the port's Byte/Short numeric casts with
explicit `toByte`/`toShort` conversions. Regression checks cover all-off, low/high and
mixed channels, all 16 channels, packing round trips and clearing a reused buffer.
Development and installed-distribution verification passed.

## Bundled masks — GTNH PR #100

Reproduced sign extension of channel 15 in BusRandomizer and byte-sized storage of
BusConverter's 16-bit masks. Adapted the corresponding GTNH repair: unsigned short
loads, short mask storage, unsigned legacy-byte decoding and persisted legacy-output
resampling before pending output work. Also adopted its redwire rounding and explicit
two-tick refresh for mode changes with zero inputs.

Tests cover high/mixed/all channel masks, repeated random selection, old byte data,
resaving before the first repair tick, a placed converter with a weak redwire input,
the resampling delay and a zero-input mode switch. The remaining portions of PR #100
are not represented as completed by this change.

The checks passed both in development and using installed SRG distribution jars.

## Verification dependency repository isolation

The fixture bootstrap published Scala 3.10 builds under the dependency projects'
normal Maven coordinates into the user's Maven Local repository. This could affect
other workspaces expecting those coordinates' ordinary builds. Verification now
passes a task-local `maven.repo.local` to dependency publication, root builds and the
standalone smoke stage. Its path is recorded in fixtures.json for manual client runs.

A fresh isolated repository successfully built and passed both server modes. The
hashes and modification times of two existing global Maven dependency jars were
checked before/after and remained unchanged.

## Counter and timer settings — GTNH PR #100

A placed Counter reproduced an unscheduled output after its GUI value changed.
Adapted the relevant setting fixes: schedule output reevaluation after value/maximum
edits, clamp against the validated maximum, mark changed settings for saving, and
compare the output nibble rather than input/output bits together. Timer and StateCell
compare the requested interval against their exposed interval (including the two-tick
offset); Sequencer interval edits also mark the tile dirty.

In-game checks cover output changes after two ticks, unchanged-value maximum edits,
invalid maximum clamping, redundant notification suppression, and dirty-chunk marking
for value/step/interval edits. The Fabrication IC timer code was inspected separately:
its interval comparison and markSave calls were already correct, so it was not patched
by analogy with the different older GTNH IC implementation.

Development and installed-distribution server verification both passed.

## Synchronizer reset and feedback — GTNH PR #100

Reproduced a placed Synchronizer remaining high after reset during an active pulse.
Retain the pulse-termination bit when clearing input latches, publish pulse state
before output callbacks and clear it before falling-edge feedback can latch the next
inputs. A placed-gate test covers reset during the pulse and new inputs delivered
through the output-notification callback. The existing 1.12 two-tick start delay is
explicitly checked and retained (the older fork's different start delay was not copied).
The Fabrication IC Synchronizer uses a separate register-based state machine and was
not patched as though it were the older fork's bit-state implementation.

Reset and callback-feedback checks passed in development and installed-distribution servers.

## Default task selection and internal archive names

Reproduced Gradle implicit-dependency validation failures with the plain `runServer`
selector used by the generated IDE configurations. Disabled child run tasks still
resolved child native-extraction inputs into the shared directory. Child native
extractors now delegate preparation to the root and the root run tasks declare their
ordering; IDE configurations explicitly select root tasks. Plain `runServer` and
distribution verification then passed together.

Internal jars also used the Jar task's `name` instead of the project name in their
archive basename. Capturing the module name outside the task receiver produces
distinct names such as `projectred-core-internal`; public distribution names are unchanged.

## Array-cell sampled pulses — GTNH PR #68 / upstream PR #1855

Reproduced AND Cell dropping a top-wire pulse whose falling-edge notification arrived
on the pending output tick. Scheduled work read the already-updated raw wire strength
instead of the input sampled when the work was queued. AND Cell and Stacking Latch now
evaluate the sampled redwire/input masks, then sample fresh input for subsequent work.
Tests cover the boundary-tick pulse, termination and Stacking Latch enable/hold/clear.

An `arrayInputSnapshot` NBT marker distinguishes new sampled-state saves. Legacy
saves reconstruct the new input bits from their saved wire strength; new saves preserve
their explicit snapshot even if raw strength differs. Both paths have regression checks.
Only the array-gate bug fix is adapted; Smart Lamp and other features in upstream PR
#1855 remain deferred as requested.

Boundary pulses, latch behavior and both NBT formats passed in development and
installed-distribution verification.

## Client GUI event path

The Project Bench client test now calls NodeGui's public mouse press/release entrypoints
at the actual output-slot position, rather than invoking PlayerController.windowClick
directly. The server accepted the craft with exactly one log consumed and four planks
returned; leaving range still closed the window. All 472 item variants and the IC
Workbench render/resize checks passed alongside it.

The fixture also waits for the initial terrain/position handshake to close its loading
screen before opening the item gallery, avoiding a late login screen-close packet
interrupting the renderer assertions.

## Clean checkout verification

A separate checkout at `6a5b325e` passed development-server, installed-distribution
and graphical client verification with the ProjectRed Gradle build cache disabled.
The checkout had fresh build output, test worlds and its own Maven fixture repository.
The client rendered all 472 variants and passed the IC Workbench and Project Bench
GUI checks. External dependency downloads and dependency build caches were reused.

## IC Counter maximum edits

Reviewing the separate Fabrication implementation found the same raw-maximum clamp
in its Counter starting-value setting. A regression test reproduced a start value of
7 becoming 0 when a requested maximum of -3 was validated to 1. Clamp the start value
against that validated maximum, preserving a start value of 1. The test also checks
save/recompile notifications, the 32767 upper bound and settings saved through NBT.
Development and installed-distribution server verification passed.

## Large IC transport limits

Following upstream #1257/#1316, a 64x64 fixture with 3,844 Counter tiles exposed a
different size failure: a 426,829-byte IC item exceeded Minecraft's two-MiB NBT
allocation accounting limit when read from a PacketBuffer. The editor description
round-trip passed at 103,838 raw bytes; this does not reproduce the original report's
32767-byte failure.

Large Blueprint, Printed IC and gate item share tags now use a compressed transport
envelope. Multipart gate descriptions use the same codec. Receivers restore the
original tag, and item/world saves retain their existing compound format. Small and
legacy uncompressed tags are accepted directly. Compression and expansion have
explicit bounds, including separate NBT allocation accounting for the decoder.

The gate fixture now uses 73,406 bytes in transit. Regression checks cover all three
item types, preservation of source tags, complete tile/state round-trips, multipart
descriptions and saved-gate reloads. Invalid gzip data and expansion beyond four MiB
are rejected. Development and installed-distribution server checks passed.

The graphical client also received the large IC in its inventory, opened its actual
server-side Workbench, rendered the circuit, placed it through the normal right-click
path and received its multipart description. The server confirmed all 3,844 circuit
tiles after placement. The 472-item gallery and existing GUI checks passed alongside it.

The original #1257 save was not available. Its exact failure remains unconfirmed.

## 1.21.1 behavior policy and visual follow-up

The user extended the newer-version behavior/specification target to all existing
content and future backports. Earlier decisions to retain conflicting legacy
mechanics are superseded; the current differences are tracked in SPEC_PARITY.md.
This pass does not establish full conformance for the whole mod.

Auto Crafter now uses 100-tick powered jobs, the newer work cost, unpowered pause,
ingredient/output cancellation and idle plan cycling. Charge/work state and rotation
are included in rendering and the bakery key. Its newer GUI synchronizes work
progress and accepts plan-cycle clicks through the tile-bound container. Old cycle
timer NBT is accepted without losing plans or storage; partial new jobs resume.

Workbench now uses the newer information/edit/compile atlases with a responsive
layout and grouped tool palette. Manual/automatic compilation, the default
20-component automatic limit, compiler state and missing-input/output diagnostics
are connected to the native compiler. Simulation input is rejected while the
circuit is uncompiled, stale or failed. The new staged engine/compiler and complete
newer editor still need a separate implementation and old-circuit migration.

The Pneumatic Tube renderer uses the newer OBJ/atlas for placed tubes and items.
The old CCL parser needs OBJ groups instead of object declarations; vertices,
faces and UV coordinates are otherwise unchanged. Checks cover bounds and UVs
for every one of the 64 connection masks and render actual synchronized item flow.

Basic lithography now uses the newer 4096nm wafer, standard 16x16 defect calibration
and fixed 16x16 production footprint. The old independent yield calibration is gone.
The newer GUI estimate can be negative for oversized designs; manufacturing uses
the fixed footprint, matching the pinned upstream implementation and its TODO.

Verification passed in development and in an installed Cleanroom server without
JEI. Tests cover full work duration, cancellation, idle plan selection, unpowered
pause, NBT conversion/resume, manual/automatic compilation, size limits, failed
designs and description packets. The graphical client renders all 482 creative
variants, opens/resizes the actual Workbench, switches all tabs and selects a tool
through its palette, shows three Auto Crafter states and clicks its plan-cycle
button. Screenshots were inspected. This caught and fixed a missing rotation state
and a popup background that covered its own child buttons.

## New IC backend and Workbench controls

The Workbench and new fabrication targets now use the pinned 1.21.1 engine.
The port includes all 20 gate implementations, four I/O types, wire pathfinding,
byte-register simulation and a staged compiler with the 0.5ms tick budget.
Full-width Bundled Bus I/O has channel controls. Existing IDs remain stable.
Legacy-only components stay in the drawing and report conversion errors.

Typed problems, progress and the compile tree are synchronized with viewers.
Tests exposed byte/VarInt mismatches, insufficient path/child count widths and
full-snapshot ordering problems. Pending events now precede snapshots, including
when viewers join. Awaiting manual compilation no longer dirties the chunk or
sends its state every tick. The bundled-colour renderer also needed a cast fix.

Explicit I/O copy gates preserve direct connections and fan-out instead of
renaming shared static registers. Redstone searches avoid duplicate colour ports;
all insulated colours and sixteen bundled channels retain their signal paths.
Assembler per-tile tracing is debug-only.

Compiled targets are tied to their drawing snapshot and copied through production.
New placed ICs use the new simulator, retain the two-tick input latch and publish
time-driven changes on their simulation tick. Large engine JSON uses bounded
gzip byte arrays instead of 1.12's size-limited NBT strings. Unknown runtime
formats stop safely; unrecognized Workbench engine data is backed up while its
original drawing remains available for recompilation.

Development server/client and installed Cleanroom server checks passed. Coverage
includes all gate implementations compiling/reloading, redstone and all insulated
colours, full-width buses, I/O fan-out, large JSON persistence, source invalidation,
unknown formats, an actual multipart gate's input-latch timing, legacy drawing
conversion, simulation reload and compile-tree transport. The client manually
compiles the 3,844-component fixture through 23,076 steps, inspects the synchronized
tree, renders all four I/O types and toggles bus channel 15 through the real GUI.
Screenshots were inspected.

Dependency fixtures now use the existing Sumire Scala 3.10 revisions, including
CBMultipart's client random-tick registration fix. No adjacent checkouts were
modified. The engine library is contained in the runtime jar and verified by hash.
The complete newer viewport/interaction-zone frontend and stateful conversion of
already placed legacy ICs remain open; see IC_ENGINE_BACKPORT.md.
