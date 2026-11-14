package mrtjp.projectred.verification

import net.minecraftforge.fml.common.{FMLCommonHandler, Loader, Mod}
import net.minecraftforge.fml.common.event.FMLServerStartedEvent
import java.nio.file.{Files, Path}

@Mod(modid="projectred_smoke", name="ProjectRed regression checks", version="1", dependencies="required-after:projectred-core;required-after:projectred-expansion;required-after:projectred-fabrication")
class ServerSmokeMod {
    @Mod.EventHandler def loaded(event:net.minecraftforge.fml.common.event.FMLLoadCompleteEvent):Unit =
        if System.getProperty("projectred.client.result") != null then
            Class.forName("mrtjp.projectred.verification.ClientSmokeChecks").getMethod("start").invoke(null)
    @Mod.EventHandler def started(event:FMLServerStartedEvent):Unit = {
        val result = System.getProperty("projectred.smoke.result")
        if result == null then return
        val server = FMLCommonHandler.instance().getMinecraftServerInstance
        try {
            RegressionChecks.run(server.getWorld(0))
            ICScheduleChecks.run(server.getWorld(0))
            ICWireNetChecks.run()
            LargeICPacketChecks.run(server.getWorld(0))
            CraftingRemainderChecks.run(server.getWorld(0))
            BundledSignalChecks.run()
            BundledMaskChecks.run(server.getWorld(0))
            GateSettingChecks.run(server.getWorld(0))
            SynchronizerChecks.run(server.getWorld(0))
            ArrayCellPulseChecks.run(server.getWorld(0))
            Files.writeString(Path.of(result), "PASS\n")
            println("PROJECTRED_SMOKE_PASS: all modules, payload NBT/FIFO, inventory NBT, tile actions, wire exception recovery")
        } finally server.initiateShutdown()
    }
}
