# ProjectRed 1.21.1 conformance

The target is the behavior and specifications of the newer ProjectRed for existing
content and future backports. Visual updates alone do not establish conformance.
Legacy behavior is not a reason to keep a conflicting mechanic. Preserve existing
identifiers and read old saves through compatibility code where possible.

The current comparison is pinned to `e631f983c9b31c79f04deffa92ae5e68468326a0`
on `Sumire-Labs/ProjectRed-Rescaled:1.21.1`. A future change of reference must be
recorded explicitly. Kappa's 1.12 port is an implementation reference; it does not
override the 1.21.1 specification or replace the Sumire dependency forks.

[KAPPA_AUDIT.md](KAPPA_AUDIT.md) records the complete pinned Kappa comparison.
Its identified omissions are addressed separately from the wider conformance
work below.

## Current comparison

| Area | Implemented comparison | Remaining work |
| --- | --- | --- |
| Auto Crafter | 100-tick work, 1100 power draw per powered tick, unpowered pause, cancellation on invalid ingredients/output, idle plan cycling, three front states and rotated rendering; newer inventory GUI | Review recipe and automation edge cases against the newer implementation; test representative old saves |
| IC Workbench | Newer backend plus perspective camera, layer navigation/storage, direct interaction zones, freehand wires and drag placement; supported placed legacy runtimes migrate with preserved state and backups | Artwork/model comparison, real-save checks for other legacy codecs, and manual conversion of legacy-only components/conflicting drivers. See IC_ENGINE_BACKPORT.md |
| Lithography | 4096 nm rough wafer, standard 16x16 die at 64 nm, 50% defects and 16 total dies in production | Advanced processes remain unimplemented upstream; track actual upstream availability. Newer manufacturing currently assumes 16x16 even for a larger design; the GUI estimates from actual dimensions, following upstream |
| Plotting / Packaging | Basic production chain and nominal work durations; old Printer/chip saves migrate into Packaging and recovery archives, with legacy direct recipes removed | Continue recipe, side-access and cancellation comparisons; exercise historical real saves |
| Pneumatic Tubes | Newer geometry, routing and return-buffer delivery; all nine old pipe types load as modern tubes. Filtered Importers migrate to ordinary Importers with recovery data. New tube recipe and device transfers follow the pinned source | Continue full routing/insertion/bouncing audits and larger-network checks; test additional historical codecs and inventories |
| Smart Lamps | Mixed bundled-channel colour, brightness and six orientations | Compare all color math, light updates, recipes and drops with 1.21.1; Pulsar still needs its own Smart Lamp adapter |
| Decorative walls | Vanilla wall/gate connectivity and existing variants | Compare newer shape and placement rules within the 1.12 world API |
| Other existing content | No claim of full conformance | Audit gates/timers, wire propagation, machines, tools, recipes, world generation, relocation, inventories, drops, GUI controls and automation |

Old registrations now serve as save readers, aliases and retired configuration
items. They do not run the old routing or direct IC manufacturing systems. The
conversion map, recovery steps and behavior changes are described in
[LEGACY_SAVE_MIGRATION.md](LEGACY_SAVE_MIGRATION.md). Keep old identifiers readable
when subsequent ports retire content.

## Required evidence for subsequent ports

- Record the newer source revision and affected behavior: timings, power, recipe
  and inventory rules, I/O, state changes, persistence and user controls.
- Describe the conversion of existing IDs and NBT. Separate platform API
  adaptations from observable behavior differences.
- Verify the affected behavior in development and in the installed distribution;
  inspect client rendering and actual GUI interactions for visual changes.
- Keep unresolved differences in this file. Build and smoke-test success do not
  mean the entire mod matches 1.21.1.
