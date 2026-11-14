package mrtjp.projectred.verification

import codechicken.lib.data.{MCDataOutput, MCDataOutputWrapper}
import mrtjp.core.vec.{Point, Size}
import mrtjp.projectred.fabrication.*
import net.minecraft.world.{World, WorldServer}

object ICScheduleChecks {
    def run(world:WorldServer):Unit = {
        val clock:World = world
        val originalTime = clock.getTotalWorldTime
        var editor:ICTileMapEditor = null
        val network = new IICTileEditorNetwork {
            def getIC:ICTileMapEditor = editor
            def getEditorWorld:World = world
            def getICStreamOf(key:Int):MCDataOutput = new MCDataOutputWrapper(new java.io.ByteArrayOutputStream)
            def getTileStream(pos:Point):MCDataOutput = new MCDataOutputWrapper(new java.io.ByteArrayOutputStream)
            def isRemote:Boolean = false
            def markSave():Unit = ()
        }
        editor = new ICTileMapEditor(network)
        editor.tileMapContainer.size = Size(16,16)
        editor.tick() // Initialize the empty simulation before adding a callback probe.

        class Probe extends LeverICTile {
            var fired = 0
            var repeat = false
            override def scheduledTick():Unit = {
                fired += 1
                if repeat && fired == 1 then scheduleTick(3)
            }
        }
        def add(point:Point):Probe = {
            val probe = new Probe
            probe.bindEditor(editor)
            probe.bindPos(point)
            editor.tileMapContainer.tiles += ((point.x,point.y) -> probe)
            probe
        }
        try {
            val first = add(Point(2,2))
            first.scheduleTick(5)
            editor.tick()
            assert(first.fired == 0, "IC event fired before its deadline")
            clock.getWorldInfo.setWorldTotalTime(originalTime+4)
            editor.tick()
            assert(first.fired == 0, "IC event fired one tick early")
            clock.getWorldInfo.setWorldTotalTime(originalTime+5)
            editor.tick()
            assert(first.fired == 1, "IC event did not fire at its deadline")
            editor.tick()
            assert(first.fired == 1, "IC event fired more than once")

            val repeat = add(Point(3,3))
            repeat.repeat = true
            repeat.scheduleTick(1)
            clock.getWorldInfo.setWorldTotalTime(originalTime+6)
            editor.tick()
            assert(repeat.fired == 1)
            clock.getWorldInfo.setWorldTotalTime(originalTime+9)
            editor.tick()
            assert(repeat.fired == 2, "A callback's new schedule was discarded")

            val removed = add(Point(4,4))
            removed.scheduleTick(1)
            editor.removeTile(removed.pos)
            val replacement = add(Point(4,4))
            clock.getWorldInfo.setWorldTotalTime(originalTime+10)
            editor.tick()
            assert(removed.fired == 0, "Removed IC component received a scheduled event")
            assert(replacement.fired == 0, "Replacement IC component inherited a removed component's schedule")

            val late = add(Point(5,5))
            late.scheduleTick(2)
            clock.getWorldInfo.setWorldTotalTime(originalTime+15)
            editor.tick()
            assert(late.fired == 1, "An overdue IC event was discarded")
        } finally clock.getWorldInfo.setWorldTotalTime(originalTime)
    }
}
