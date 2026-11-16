# Kappa fork audit

The audit covers the complete net change from ProjectRed's 1.12 baseline
`11da8db1` to `kappa-maintainer/ProjectRed:1.12.x` at
`f55e7aab200dd5fe6ed66e758208ecb8e6480e5d`. GitHub's branch head was checked on
2026-11-17 and still pointed to that commit. The history contains 53 commits.
The comparison started from this fork's `1da24f93`.

The behavior reference remains ProjectRed 1.21.1 at
`e631f983c9b31c79f04deffa92ae5e68468326a0`. Kappa provides source material;
its build, dependency repositories and conflicting mechanics are not the target.

The JSON preserves the decisions at the end of that audit. The subsequent
[legacy save migration](LEGACY_SAVE_MIGRATION.md) implements the transport and
manufacturing retirements that were still pending in that snapshot.

## Coverage

[KAPPA_AUDIT.json](KAPPA_AUDIT.json) records every changed path after normalizing
the relocation of `src/mrtjp/projectred`, ComputerCraft API sources and `resources`.
The 403 entries comprise 187 ProjectRed source paths, 130 resources, 64 external
API source paths and 22 build/documentation paths. Unchanged files that only moved
directories are excluded. The JSON also records a decision for each of the 53
commits, including changes later reverted within Kappa's own history.

Review used the original patches, the current code, the pinned 1.21.1 source and
resource hashes. Whitespace-normalized source comparisons helped locate changes;
they do not prove equivalent behavior. Runtime checks exercise the affected paths.
The `compatibility-pending` decision identifies a reviewed deletion or progression
change that needs a save migration; those cases are listed explicitly below.

## Missing changes completed in this audit

| Area | Change | Evidence |
| --- | --- | --- |
| Pneumatic Tube placement | Remove the legacy `PayloadPipePart` cast in the item factory. The new tube extends `SubcorePipePart`; the old cast crashed when a player placed it. | Create every registered pipe variant through the item factory; place a Pneumatic Tube through `onItemUse` and check the multipart and item consumption. |
| Machine and lamp particles | Add hit/destroy hooks and bakery particle sprites to Expansion machines, the IC Workbench/Printer and ordinary lamps. Use explicit bakery sprites for non-cube fabrication models, avoiding CCL's empty face-sprite crash. Lamp sprites follow the actual color and lit state instead of Kappa's fixed white sprite. | Client checks all 20 machine variants, all 32 lamp color/states and actual hit/destroy callbacks. |
| Multipart particles | Add framed-wire hit effects, insulated-wire color sprites and direct access to the frame sprite. Suppress particles for the invisible moving-row placeholder. | Client checks frame and red-alloy sprites, all 16 insulated colors at both signal states and bounds. |
| Fabrication fallback | Add the missing `fabrication_machine` blockstate fallback for the existing dynamic renderer. | Distribution resources and client model/particle checks. |
| Localization | Port the GUI, routing-chip, tooltip and player-message translations, including the corrected Chinese inverted fallout-lamp keys. Add Japanese translations for the newly used keys. | Load English, Japanese and Chinese through the actual client locale loader and format parameterized messages. |
| Server messages | Send translation components, including item names, so clients resolve their own language. Preserve custom display names and `LocName` tags. | Dedicated-server regression checks and installed-server checks. |
| JEI API | Replace deprecated `BlankModPlugin` with `IModPlugin`. | Development client with JEI and installed server without JEI. |

## Decisions for the remaining differences

The evidence IDs below are used by the path inventory.

| ID | Decision |
| --- | --- |
| `template` | Use the existing CleanroomDevEnv-derived multi-project build, Java 25, Scala 3.10, Sumire dependencies and single runtime Jar. Kappa's Unimined configuration, Maven endpoints, split artifacts, compiler rewrite flags, version literals and CI files are not copied. AT rules remain under `META-INF`, as required by this build. |
| `syntax` | Scala 3 conversion, collections, mappings and initialization changes are present. Formatting, inferred return types, generated version constants, synchronized syntax and removed comments do not add gameplay features. The complete source set is compiled and exercised in the distribution checks. |
| `particles` | Machine/lamp bakery sprites, hit/destroy hooks, frame sprites and framed-wire hit effects are integrated. Explicit bakery sprites avoid empty model-face lookups for fabrication machines. Lamp particles use their actual color and lit state. Framed-wire bounds remain available on both sides. Invisible moving-row blocks suppress placeholder effects. |
| `identifiers` | Keep historical mod IDs, including `projectred-compat`, and existing multipart/item IDs. Do not copy Kappa's compatibility-mod rename or assign the new tube over old pressure-tube metadata 64. New Pneumatic Tubes remain metadata 66. |
| `optional-api` | ComputerCraft compatibility uses the same bundled-redstone entry points. Its API sources are compile-time stubs excluded from the runtime Jar. Additional unused Kappa API classes add no ProjectRed integration and are not bundled. This does not establish compatibility with every ComputerCraft fork. |
| `assets` | Adopted Kappa textures, sounds, recipes and fallback resources are present. Copper ore/ingot use the recorded official 1.21.1 assets; the texture provenance file records their hashes. Frame fallback has an extra inventory variant and an empty element list. |
| `localization` | All English and Chinese key/value pairs in the pinned Kappa language files are present after this audit. Existing newer strings are retained, and Japanese translations cover the newly used keys. This is not a translation review of every legacy locale. |
| `smart-lamp` | Smart Lamp, placement topology, channel input, rendering, inventory halo, recipe and persistence are ported. Shared color calculation and color-only light updates are retained. The connection-mask correction follows 1.21.1. Pulsar's separate Smart Lamp adapter remains outside ProjectRed. |
| `pneumatic` | Graph routing, payloads, device connections, round-robin exits, bounce, link effects and sounds are ported. Newer ordinary/backstuff priority and geometry supersede Kappa's deferred versions. Keep dirty tracking, bounded NBT transport, large counts, normal sided inventory wrappers and unloaded-chunk guards. |
| `legacy-transport` | Old transport IDs now load as modern Pneumatic Tubes. Payloads and recoverable components migrate, while original settings are archived. Old routing services and recipes are retired. Keeping the read aliases avoids the loss caused by deleting registrations. See LEGACY_SAVE_MIGRATION.md. |
| `production` | The five new items, three machines, work durations, power use, inventory rules, state appearances and GUI are ported. Use the newer 4096 nm wafer and fixed 16x16 production footprint. Do not adopt Kappa's 12288 nm/64x64 calibration or larger-drawing yield behavior. Advanced processes and wafer grades are unimplemented in the pinned upstream source. |
| `modern-ic` | The newer compiler/simulator, layered perspective editor and state migration supersede Kappa's legacy editor implementation. Old Printer/chip saves now migrate to Packaging, dies/blueprints and recovery archives. Single-step recipes are retired; old codecs remain readable. |
| `repairs` | Keep this fork's additional fixes for wire flags, numeric narrowing, gate state/timing, inventory rollback, barrel hiding, dense IC searches, authorization, client keys, large NBT and repeated loads. Kappa code must not undo those repairs. |

## Verification and limits

The regression suite covers the new item-placement path and server message
components. Client checks use the actual locale loader and particle callbacks,
then continue through the existing item gallery, Workbench, Smart Lamp,
production, pneumatic transport and machine appearance checks. The installed
Cleanroom server runs the same server regression suite with reobfuscated Jars
and without JEI.

Development server, graphical client and installed-server checks passed. The
client rendered all 482 creative item variants and completed the existing GUI
and transport checks. The new particle fixtures are placed on the integrated
server and synchronized to the client before testing. Auto Crafter and Plotting
Table screenshots were inspected. The installed run completed on 2026-11-17.

This audit resolves the identified omissions from the pinned Kappa fork. The
broader 1.21.1 behavior audit and historical real-save checks remain in [SPEC_PARITY.md](SPEC_PARITY.md)
and [IC_ENGINE_BACKPORT.md](IC_ENGINE_BACKPORT.md). Kappa's TODO file describes
unimplemented work; its entries are not completed features available to import.
