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
