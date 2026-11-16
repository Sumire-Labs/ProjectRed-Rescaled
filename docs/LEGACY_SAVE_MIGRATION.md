# Legacy transport and fabrication saves

The behavior reference is ProjectRed 1.21.1 at
`e631f983c9b31c79f04deffa92ae5e68468326a0`. Old registrations remain as save
readers and recovery carriers. They no longer provide the old transport or
single-step IC manufacturing systems.

## Conversion map

| Saved content | Result |
| --- | --- |
| Pressure and Resistance Tubes | Pneumatic Tubes, with the newer geometry, routing, speed and weight. |
| Basic/routed/interface/request/firewall pipes, network valves and latency pipes | Pneumatic Tubes. Old filters, routing state, UUIDs, delays and other settings remain in the recovery snapshot. |
| Pipe item metadata 0, 1, 2, 3, 4, 32, 33, 64 and 65 | Pipe metadata 66. Stack counts and item NBT are preserved. |
| Routing chips and router utility | Readable retired items. Their registrations and saved settings remain, but their GUIs, reset recipe and routing actions are disabled. |
| Filtered Importer and its item (Expansion machine2 metadata 3) | Ordinary Item Importer (metadata 1), implementing the newer Transposer behavior. Its buffer is retained; filter items and old settings go into a recovery archive. |
| Placed IC Printer | Packaging Table. A compatible printed chip goes into the die slot. Remaining ingredients, blueprint, blank chip and other stored items go into a recovery archive in the output slot. |
| IC Printer item | Packaging Table item. Carried block-entity inventory data follows the same conversion. |
| Normal printed IC chip | Valid die when its circuit builds with the newer engine; otherwise a blueprint retaining its design. |
| Creative printed IC chip | Blueprint. It does not grant free dies or fabricated gates. |
| Blank IC chip | Rough silicon wafer. |

Existing fabricated IC gates use the separate running-state conversion described
in [IC_ENGINE_BACKPORT.md](IC_ENGINE_BACKPORT.md).

## Collecting saved contents

Take the recovery archive from the converted Packaging Table's output. Right-click
the archive to transfer its contents into your inventory. Items that do not fit
remain in the archive, including in creative mode. Repeated use does not issue
the same contents twice. An empty archive still retains its recovery snapshot.

Use an empty hand on a converted Filtered Importer to collect its archive. If it
is broken before collection, the archive drops with its recovered contents.
Use an empty hand on a migrated tube to collect saved interface chips and any
redstone material. Uncollected items and settings remain attached when that tube
is picked up and placed again; its old in-flight payloads are not replayed.

The archive is a migration carrier using the retained IC-chip registration at
metadata 2. It has no crafting recipe or creative-tab entry. Over network
connections, its item description contains a stable summary; the full inventory
and snapshot remain authoritative on the server.

## Behavior after conversion

Pneumatic transport uses the newer ordinary-destination/backstuff priority and
speed 12 on its 0..255 progress scale. Existing cargo keeps its stack, NBT and
normalized position; the new network reevaluates its destination. It may choose
a different destination because old routing, color filters, valve restrictions,
resistance weights and request/crafting-chip services no longer run. Layouts
that depended on those controls need to be rebuilt for the newer transport.

Device output goes to a tube or air, following 1.21.1. Direct device-to-inventory
export is removed. Connect a tube to reach an adjacent destination inventory.
Item Importer extraction follows the pinned Transposer's slot order, sided
inventory rules and item-handler access. Sided inventories use the upstream
full-stack extraction behavior; the item-handler path requests one item. Entity
suction is unimplemented in that pinned upstream source and is disabled here.
Queues retain the working activation wake-up and 4/16-tick retry behavior.

The old Printer stops before consuming additional resources. Its partial work
does not shorten the new production process. Collect the recovery archive, supply
the required interface/corner plates and power, then use the normal 400-tick
Packaging Table job. Unprinted designs continue through Plotting, Lithography
and Packaging; the old Printer, chip and direct-gate recipes are removed.

The Pneumatic Tube recipe now matches 1.21.1: three rows of copper/glass pane/copper
produce eight tubes. Automatic save conversion replaces the earlier pressure-tube
upgrade recipe.

## Persistence

Forge's `projectred-core` data fixer, version 1, visits saved chunks, players,
entities, item stacks and nested compound/list data, including unfamiliar
container/capability layouts that contain ordinary serialized item stacks.
Inventory, container-open, entity-item and periodic player checks handle legacy
items imported after the initial conversion. Multipart aliases also convert
through CBMultipart's actual saved-part factory.

Tile conversion prepares and loads the replacement before clearing the old
inventory or replacing its block. Standard block/tile synchronization delivers
the replacement description after its block change, so a client holding the old
Printer or Filtered Importer does not decode the new format with the old reader.
Tube save readers accept old IDs, then write
the modern ID and payload format. Repeated loads and conversions do not replay
recovery contents or duplicate cargo. A bounded compilation cache avoids
recompiling identical printed designs during bulk inventory conversion; each
result owns its design and recovery data.

Snapshots live under `projectred_legacy`, with a version, kind and gzip-encoded
original NBT. `LegacySaveMigration.original` reads a snapshot with a 32 MiB
expansion limit. Snapshot contents are never treated as live inventory and are
not recursively converted. Recovery inventories use integer counts; device
buffers also store extended counts and backstuff state. Old buffers without a
backstuff flag are treated conservatively as backstuffed while nonempty.

Unreadable buffer records remain serialized in their original order and pause
automatic export. Breaking the device yields a recovery archive for those
records; known buffered stacks drop normally. Missing item providers must be
restored before their records can become usable stacks. A count already lost
by an older byte-count save format cannot be inferred from the surviving byte.

## Verification

Development checks exercise all nine transport aliases, mixed wire/tube multipart
NBT, nested Forge fixes, in-flight item delivery, exact recovery counts, repeated
loads, a full creative inventory, unreadable records and 1000-item buffers.
Printer checks cover interrupted work, design preservation, die validation,
creative-chip conversion, subsequent powered packaging and persistence.

The graphical client first observes the old Printer and Filtered Importer before
their conversion, then opens the converted
Packaging Table, transfers the recovery item through actual GUI clicks and
collects its contents through a use packet. A vanilla item-codec round trip
supplies the summary used by that GUI transaction because integrated local
channels otherwise skip serialization. Screenshots were inspected.

Development server, graphical client and installed Cleanroom server checks pass;
the installed test omits JEI. Client checks also reject logged task exceptions
after completion. All 459 currently exposed creative item variants render.

Representative real saves from other historical forks and private capability
schemas still need validation. The known native inventory schemas are
`icprinteritems` and `filtered importeritems`; snapshots retain unrecognized
fields for recovery. This migration does not establish full 1.21.1 conformance
for every other machine, recipe or world-generation feature.
