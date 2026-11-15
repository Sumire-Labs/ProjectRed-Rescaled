# ProjectRed 1.21.1 conformance

The target is the behavior and specifications of the newer ProjectRed for existing
content and future backports. Visual updates alone do not establish conformance.
Legacy behavior is not a reason to keep a conflicting mechanic. Preserve existing
identifiers and read old saves through compatibility code where possible.

The current comparison is pinned to `e631f983c9b31c79f04deffa92ae5e68468326a0`
on `Sumire-Labs/ProjectRed-Rescaled:1.21.1`. A future change of reference must be
recorded explicitly. Kappa's 1.12 port is an implementation reference; it does not
override the 1.21.1 specification or replace the Sumire dependency forks.

## Current comparison

| Area | Implemented comparison | Remaining work |
| --- | --- | --- |
| Auto Crafter | 100-tick work, 1100 power draw per powered tick, unpowered pause, cancellation on invalid ingredients/output, idle plan cycling, three front states and rotated rendering; newer inventory GUI | Review recipe and automation edge cases against the newer implementation; test representative old saves |
| IC Workbench | Newer engine/compiler and 0.5ms staged assembly, 20 gates, four I/O types, compile tree/problems, modern production targets and fabricated-gate runtime; existing IDs/drawings retained | Complete perspective/layer/interaction-zone frontend, newer model/overlay adapters and edit-gesture comparison; migrate already placed legacy runtimes. See IC_ENGINE_BACKPORT.md |
| Lithography | 4096 nm rough wafer, standard 16x16 die at 64 nm, 50% defects and 16 total dies in production | Advanced processes remain unimplemented upstream; track actual upstream availability. Newer manufacturing currently assumes 16x16 even for a larger design; the GUI estimates from actual dimensions, following upstream |
| Plotting / Packaging | Basic production chain and nominal work durations are ported | Audit recipes, side access, synchronization and all cancellation conditions against 1.21.1 |
| Pneumatic Tubes | Newer OBJ geometry and atlas, all 64 connection masks; basic routing and return-buffer delivery | Audit routing priority, insertion, bouncing, devices and timing against newer code. Legacy routed/pressure/resistance content still exists and needs an explicit migration decision |
| Smart Lamps | Mixed bundled-channel colour, brightness and six orientations | Compare all color math, light updates, recipes and drops with 1.21.1; Pulsar still needs its own Smart Lamp adapter |
| Decorative walls | Vanilla wall/gate connectivity and existing variants | Compare newer shape and placement rules within the 1.12 world API |
| Other existing content | No claim of full conformance | Audit gates/timers, wire propagation, machines, tools, recipes, world generation, relocation, inventories, drops, GUI controls and automation |

The old IC Printer, direct fabrication recipes and legacy transport remain
registered today. Their continued availability is an outstanding migration issue,
not an approved exception to this policy. Do not remove saved items or blocks
without a deliberate migration path.

## Required evidence for subsequent ports

- Record the newer source revision and affected behavior: timings, power, recipe
  and inventory rules, I/O, state changes, persistence and user controls.
- Describe the conversion of existing IDs and NBT. Separate platform API
  adaptations from observable behavior differences.
- Verify the affected behavior in development and in the installed distribution;
  inspect client rendering and actual GUI interactions for visual changes.
- Keep unresolved differences in this file. Build and smoke-test success do not
  mean the entire mod matches 1.21.1.
