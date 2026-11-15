# IC engine and Workbench backport

## References and distribution

- ProjectRed 1.21.1: `e631f983c9b31c79f04deffa92ae5e68468326a0`.
- Runtime library: `io.github.mrtjp:fabrication-engine:0.1.0-alpha-19`, embedded
  in the single distribution jar. Its published SHA-256 is
  `bc489cd8bedd1146b754394a475f599d8df173d010e04402eb3f1bc59f5823a0`.
- The step-through assembler adapter follows fabrication-engine source
  `c630983d53032f3c54265015770dfb67d3cb37fb`. Assembly passes are unchanged;
  its per-tile console tracing is guarded by the ProjectRed debug log level.
- Verification uses Sumire MrTJPCore at `ff920de16d5c7a790ef44048cb5e4f8dc0766a33`
  and CBMultipart at `d2e34dd80112b39a2af30aa1abe35e69e802b646`. Both already
  declare Scala 3.10. No fixture source patch or sibling checkout modification
  is needed. The latter includes the client random-tick registration fix that
  long client sessions require.

## Implemented path

Workbench now compiles and simulates with the newer engine. The port includes
the 20 logic gates, four I/O types, red/insulated/bundled wires, byte registers,
64-bit simulation time, static I/O register layout and typed compiler problems.
Compilation runs with the newer 0.5ms work budget per server tick. Progress,
the compile tree and problems are synchronized with viewers. Tree/problem rows
can focus their corresponding location in the editor.

Full-width Bundled Bus I/O has an appended editor ID and a channel control GUI.
Existing editor IDs are unchanged. The palette offers the newer supported
components. The frontend now uses the 1.21.1 top-down perspective camera, 70-degree
vertical FOV, cursor-directed zoom, WASD panning, animated layer navigation and
interaction-zone definitions. The 1.12 wire/gate sprites are composited through
a renderer adapter; the newer solid gate artwork is not substituted wholesale.
Rotation/reflection icons and interaction labels come from the pinned reference.

The same camera calculation drives rendering and picking. Layers above/below
the selected layer fade during transitions. Upper and negative layers persist
in drawing NBT and descriptions; vertical wire connections use the new engine.
Layer bounds belong to the drawing. As upstream, a new design starts on one
layer; browsing outside its bounds does not permit placement there.

Counter/Timer adjustments, gate rotation/reflection, I/O direction and signal
controls use the newer interaction zones and server-side actions. Placement uses
the newer drag rotation rule, and wires follow visited cells instead of being
restricted to an axial segment. Problem/tree rows retain their layer when
focusing the camera. The full-width bus test popup remains an additional control
for the reference's unfinished bus-input toggle.

Production validates with the new compiler. Modern blueprints retain a compiled
target tied to a canonical copy of their source drawing. Modified source cannot
reuse that target. Photomasks, dies, printed ICs and fabricated gates copy it
without sharing mutable NBT. New fabricated gates use the newer simulator and
latch external inputs for two ticks before propagating them, matching 1.21.1.
Time-driven output changes are published on their simulation tick.

## Save and protocol adaptations

The original 1.12 drawing remains available for editing and conversion. Supported
gate settings, wire colours, I/O direction and frequencies map to the new engine.
Legacy-only Lever, Button, Cell and Dec Randomizer components remain in the
drawing and produce a located conversion error. They are not silently deleted.
An uncompiled or failed Workbench export cannot enter the new production path.

World saves keep the new simulation and compiler state. A partial assembly is
restarted from its saved drawing. Incompatible compiled formats cannot silently
fall back to the old simulator.

Previously placed supported legacy ICs migrate when loaded. The retained legacy
reader reconstructs terminal/register associations; migration maps semantic
state to the new registers. This preserves counter values and previous input
edges, latch memory, timer/state-cell phase, pending pulses, synchronizer state
and system time. Committed values are imported without computing gates and
creating artificial edges. A two-tick transition transfers the old output
pipeline latency to the new clock origin, preserving pending physical pulses;
subsequent events follow the newer input-latching rules.

The original legacy runtime is kept in `legacy_runtime_backup`. Recalculation on
a separate simulation checks observable I/O before accepting the conversion.
Legacy-only components, conflicting drivers or incompatible register layouts
retain the old compatibility runtime with a conversion reason. Their drawing
and saved state are not reset. These cases require Workbench conversion. Old
circuits without external I/O can migrate their runtime, while normal Workbench
fabrication still enforces the newer I/O validation.
If the retained reader cannot decode a drawing at all, the IC stops with a
reason and preserves its complete original logic data instead of losing the
multipart during loading. Old inventory ICs also enter the new runtime on
placement when their layout can be verified.

Minecraft 1.12 NBT strings have a 65535-byte modified-UTF limit. Engine JSON is
therefore stored as gzip byte arrays with bounded expansion; upstream plain JSON
strings can also be read. Item and multipart descriptions continue to use the
existing large-NBT transport envelope.

Compile-tree lengths, paths, register lists and child counts use matching VarInt
encodings. This also fixes the reference's byte/VarInt mismatch for gate/remap
counts and supports trees with more than 255 children. Buffered incremental
events are flushed before complete snapshots; new viewers do not replay edits
that precede their snapshot.

## Repairs made during the port

- Use explicit I/O copy gates instead of renaming shared/static drivers to one
  output register. Direct I/O connections and fan-out retain every output pin.
- Avoid sixteen equivalent redstone path searches while retaining insulated
  colour selection and the sixteen distinct bundled ports.
- Clear old compiler-log state when loading another saved log.
- Keep per-tile assembler traces at debug level.
- Fix the native bundled-colour I/O renderer's cast to an unrelated Analog I/O
  logic class.
- Fix initial workflow detection for drawings previously compiled by the old
  engine, and snapshot ordering when opening or completing compilation.

## Verification and remaining work

Regression fixtures exercise perspective/picking round trips, layer transitions,
positive/negative-layer NBT, layered descriptions, vertical signal paths, and
upper-layer-only drawings. Client tests use actual Counter zones, bus channel
controls, layer navigation, upper-layer erasure and a bent freehand wire path.
Migration fixtures seed non-default running states and verify value/edge/phase
continuation, pending physical pulse timing, repeated saves, incompatible layouts
and preservation of legacy-only components.

- Continue artwork/model comparisons, including the exact newer Bundled Bus mesh.
- Exercise copies of real saves from other historical forks/codecs; the original
  runtime backup remains necessary when a layout cannot be verified.
- Complete migration of legacy direct fabrication routes and the IC Printer.

See [SPEC_PARITY.md](SPEC_PARITY.md) for the wider behavior audit.
