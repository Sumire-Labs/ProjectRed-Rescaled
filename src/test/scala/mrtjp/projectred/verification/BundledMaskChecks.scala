package mrtjp.projectred.verification

import mrtjp.projectred.integration.{BundledGatePart, BusConverter, BusRandomizer}
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.world.{World,WorldServer}
import net.minecraft.util.math.BlockPos

object BundledMaskChecks {
    def run(world:WorldServer):Unit = {
        for mask <- Seq(0x8000,0x8081,0xFFFF) do {
            val randomizer = new BusRandomizer(new BundledGatePart)
            randomizer.mask = mask
            randomizer.output = mask
            val tag = new NBTTagCompound
            randomizer.save(tag)
            val restored = new BusRandomizer(new BundledGatePart)
            restored.load(tag)
            assert(restored.mask == mask && restored.output == mask, "Bundled randomizer sign-extended channel 15")
            if mask == 0x8000 then for _ <- 0 until 32 do
                assert(restored.calc1BitOut == 0x8000, "Randomizer selected an unavailable channel after reload")
        }

        val converter = new BusConverter(new BundledGatePart)
        converter.bIn = 0x8001
        converter.setBOut(0x8000)
        converter.rsIn = 15
        val tag = new NBTTagCompound
        converter.save(tag)
        val restored = new BusConverter(new BundledGatePart)
        restored.load(tag)
        assert(restored.bIn == 0x8001 && restored.bOut == 0x8000, "Bus converter truncated high channels when saving")
        assert(tag.hasKey("in0",2) && tag.hasKey("out0",2), "Bus converter did not save 16-bit masks")

        val legacy = new NBTTagCompound
        legacy.setByte("in",7.toByte)
        legacy.setByte("in0",0x80.toByte)
        legacy.setByte("out0",0x80.toByte)
        val legacyGate = new BundledGatePart
        legacyGate.schedTime = 123L
        val fromLegacy = new BusConverter(legacyGate)
        fromLegacy.load(legacy)
        assert(fromLegacy.bIn == 0x80 && fromLegacy.bOut == 0x80, "Legacy byte masks were sign-extended")
        val pending = new NBTTagCompound
        fromLegacy.save(pending)
        assert(pending.getBoolean("restoreOutput") && legacyGate.schedTime == -1L,
            "Legacy outputs were not marked for input resampling before pending work")
        val resumedGate = new BundledGatePart
        resumedGate.schedTime = 456L
        val resumed = new BusConverter(resumedGate)
        resumed.load(pending)
        val savedAgain = new NBTTagCompound
        resumed.save(savedAgain)
        assert(savedAgain.getBoolean("restoreOutput") && resumedGate.schedTime == -1L,
            "Saving before the first tick lost legacy-output repair state")

        val clock:World = world
        val originalTime = clock.getTotalWorldTime
        val pos = new BlockPos(8,80,4)
        class ProbeGate extends BundledGatePart {
            var power = 1
            override def getRedstoneInput(r:Int):Int = power
        }
        val probe = new ProbeGate
        legacy.setByte("subID",mrtjp.projectred.integration.GateDefinition.BusConverter.ordinal.toByte)
        legacy.setLong("schedTime",0L)
        probe.load(legacy)
        world.setBlockState(pos.down(),net.minecraft.init.Blocks.STONE.getDefaultState)
        try {
            codechicken.multipart.TileMultipart.addPart(world,pos,probe)
            val logic = probe.getLogic[BusConverter]
            probe.update()
            assert(logic.rsIn == 1, "Bus converter rounded a weak redwire input down to zero")
            assert(logic.bOut == 0x80, "Legacy converter changed outputs before resampling delay")
            val sampled = new NBTTagCompound
            logic.save(sampled)
            assert(!sampled.getBoolean("restoreOutput"), "Legacy converter repair remained pending")
            clock.getWorldInfo.setWorldTotalTime(originalTime+2)
            probe.update()
            assert(logic.bOut == 2, "Resampled converter failed to select channel 1")
            probe.power = 0
            logic.onChange(probe)
            clock.getWorldInfo.setWorldTotalTime(originalTime+4)
            probe.update()
            assert(logic.bOut == 1)
            assert(logic.cycleShape(probe))
            probe.onChange()
            assert(probe.schedTime == originalTime+6, "Zero-input mode change did not schedule output refresh")
            clock.getWorldInfo.setWorldTotalTime(originalTime+6)
            probe.update()
            assert(logic.bOut == 0, "Mode change retained bundled output")
        } finally {
            world.setBlockToAir(pos)
            world.setBlockToAir(pos.down())
            clock.getWorldInfo.setWorldTotalTime(originalTime)
        }
    }
}
