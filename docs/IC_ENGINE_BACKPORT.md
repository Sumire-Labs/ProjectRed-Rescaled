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
components. Native wire/gate graphics are still used by the frontend adapter;
the complete newer viewport and interaction-zone renderer are not yet ported.

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
fall back to the old simulator. Previously placed legacy gates still have a
compatibility reader; migrating their running state remains a separate task.

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

## Remaining frontend and migration work

- Replace the native two-dimensional editor view with the newer perspective
  viewport, layer navigation and interaction zones, including the exact newer
  Bundled Bus model and overlays.
- Finish comparing all edit gestures and settings controls with the newer tools.
- Convert the running state of already placed legacy ICs, including explicit
  handling for legacy-only components and start-value settings.
- Complete migration of legacy direct fabrication routes and the IC Printer.

The backend port and smoke tests do not establish complete Workbench frontend
conformance. See [SPEC_PARITY.md](SPEC_PARITY.md) for the wider audit.
