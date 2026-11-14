# Autonomous review

## User direction

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
