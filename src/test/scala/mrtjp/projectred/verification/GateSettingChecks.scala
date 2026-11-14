package mrtjp.projectred.verification

import codechicken.multipart.TileMultipart
import mrtjp.projectred.integration.{SequentialGatePart, GateDefinition, Counter, ITimerGuiLogic}
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.math.BlockPos
import net.minecraft.world.{World,WorldServer}

object GateSettingChecks {
    def run(world:WorldServer):Unit = {
        val clock:World = world
        val originalTime = clock.getTotalWorldTime
        val pos = new BlockPos(8,80,8)
        class Probe extends SequentialGatePart {
            var notifications = 0
            override def getRedstoneInput(r:Int):Int = 0
            override def onOutputChange(mask:Int):Unit = { notifications += 1; super.onOutputChange(mask) }
        }
        def place(definition:GateDefinition.GateDef):Probe = {
            val tag = new NBTTagCompound
            tag.setByte("subID",definition.ordinal.toByte)
            tag.setLong("schedTime",-1L)
            tag.setInteger("max",10)
            tag.setInteger("inc",1)
            tag.setInteger("dec",1)
            tag.setInteger("pmax",38)
            tag.setLong("pelapsed",-1L)
            val gate = new Probe
            gate.load(tag)
            world.setBlockState(pos.down(),net.minecraft.init.Blocks.STONE.getDefaultState)
            TileMultipart.addPart(world,pos,gate)
            gate
        }
        try {
            val gate = place(GateDefinition.Counter)
            val counter = gate.getLogic[Counter]
            counter.scheduledTick(gate)
            gate.schedTime = -1L
            val counterChunk = clock.getChunk(pos)
            counterChunk.setModified(false)
            counter.setCounterValue(gate,1)
            assert(gate.schedTime == originalTime+2, "Counter GUI edit did not schedule output refresh")
            assert(counterChunk.needsSaving(false), "Counter value edit did not mark its chunk for saving")
            clock.getWorldInfo.setWorldTotalTime(originalTime+2)
            gate.update()
            assert((gate.state>>4) == 0, "Counter output retained the old zero-value signal")
            counter.setCounterMax(gate,1)
            assert(gate.schedTime == originalTime+4, "Counter maximum edit did not refresh an unchanged value")
            clock.getWorldInfo.setWorldTotalTime(originalTime+4)
            gate.update()
            assert((gate.state>>4) == 1)
            counter.max = 5
            counter.value = 4
            counter.setCounterMax(gate,-3)
            assert(counter.max == 1 && counter.value == 1, "Counter value was clamped against raw rather than validated maximum")
            counter.value = 2
            counter.max = 5
            gate.setState(8)
            gate.notifications = 0
            counter.scheduledTick(gate)
            assert(gate.notifications == 0, "Counter notified output changes when only input bits differed")
            counterChunk.setModified(false)
            counter.setCounterIncr(gate,2)
            assert(counterChunk.needsSaving(false), "Counter increment edit was not saved")
            counterChunk.setModified(false)
            counter.setCounterDecr(gate,3)
            assert(counterChunk.needsSaving(false), "Counter decrement edit was not saved")
            world.setBlockToAir(pos)

            for definition <- Seq(GateDefinition.Timer,GateDefinition.StateCell,GateDefinition.Sequencer) do {
                val timerGate = place(definition)
                val timer = timerGate.getLogic[ITimerGuiLogic]
                val requested = timer.getTimerMax-2
                val chunk = clock.getChunk(pos)
                chunk.setModified(false)
                assert(!chunk.needsSaving(false), "Timer persistence fixture started dirty")
                timer.setTimerMax(timerGate,requested)
                assert(timer.getTimerMax == requested, "Timer/state-cell ignored a two-tick interval decrease")
                assert(chunk.needsSaving(false), "Timer interval edit did not mark its chunk for saving")
                world.setBlockToAir(pos)
            }
        } finally {
            world.setBlockToAir(pos)
            world.setBlockToAir(pos.down())
            clock.getWorldInfo.setWorldTotalTime(originalTime)
        }
    }
}
