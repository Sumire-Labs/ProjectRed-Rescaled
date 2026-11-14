# Upstream and fork issue review

New feature requests remain deferred until the user returns. Issue status alone is
not evidence of a fix; compare discussion, source and reproducible behavior in 1.12.

| Source | Finding | Current assessment |
| --- | --- | --- |
| [Upstream #1747](https://github.com/MrTJP/ProjectRed/issues/1747) | Dense IC blueprint wire searches freeze/lag | Reproduced a 60-second server watchdog shutdown on 900 wires. Queue/set traversal reduces the channel-search fixture to roughly 45–63 ms; colour isolation, crossed groups, complete IO simulation and NBT reload pass. |
| [Upstream #1736](https://github.com/MrTJP/ProjectRed/issues/1736) | Project Table crash and inventory loss | Closed after the reporter traced likely Placebo FastShapelessRecipe interaction; requires specific reproduction, not assumed fixed by closure. |
| [Upstream #1580](https://github.com/MrTJP/ProjectRed/issues/1580) | IC2 tool durability changes incorrectly in Project Bench | Recipe remainder calculation still uses template inputs; candidate for a synthetic durability/NBT recipe regression. |
| [Upstream #1316](https://github.com/MrTJP/ProjectRed/issues/1316) | Large IC crashes/custom payload size | Duplicate of #1257; examine CCL packet compression/splitting and the actual sender, rather than applying unrelated crash-log frames. |
| [GTNH PR #100](https://github.com/GTNewHorizons/ProjectRed/pull/100) | Counter, synchronizer, timer, bus mask persistence and rounding | 1.7.10 patch; assess corresponding 1.12 logic and reproduce before adapting. |
| [GTNH PR #103](https://github.com/GTNewHorizons/ProjectRed/pull/103) | Opt-in client tile actions after MrTJPCore restrictions | Corresponding migration is already implemented; use the patch to audit remaining packet keys. |
| [GTNH PR #68](https://github.com/GTNewHorizons/ProjectRed/pull/68) | Array gate pulse behavior | Links an upstream fix; candidate for a timing regression on the current 1.12 gate implementation. |
| [GTNH modpack #25041](https://github.com/GTNewHorizons/GT-New-Horizons-Modpack/issues/25041) | Item Importer loses its internal buffer on shutdown | Compound-list type fix and payload round-trip checks are already present here. |
| [GTNH modpack #27540](https://github.com/GTNewHorizons/GT-New-Horizons-Modpack/issues/27540) | Ore Dictionary matching conflates processing stages | Current MrTJPCore compares exact shared OreDict IDs; not enough evidence to reproduce the reported GregTech-specific aliasing in this fork. |

GitHub search returned no standalone GTNH/ProjectRed issues; its merged PRs and
the modpack's Project Red issue label provide the useful reports. The queried
ACGaming/ProjectRed repository was not searchable, so no conclusions are drawn from it.
