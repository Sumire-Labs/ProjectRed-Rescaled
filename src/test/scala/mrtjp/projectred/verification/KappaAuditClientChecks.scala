package mrtjp.projectred.verification

import java.util.Arrays
import mrtjp.projectred.{ProjectRedExpansion, ProjectRedFabrication, ProjectRedIllumination}
import mrtjp.projectred.illumination.{LampBakery, LampRenderer, TileLamp}
import mrtjp.projectred.relocation.{FramePart, FrameRenderer}
import mrtjp.projectred.transmission.{FramedInsulatedRedAlloyPart, FramedRedAlloyWirePart, WireDef}
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.Locale
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.{RayTraceResult, Vec3d}
import net.minecraftforge.common.property.IExtendedBlockState

object KappaAuditClientChecks {
    private var setup:java.util.concurrent.Future[?] = null
    private var cleanup:java.util.concurrent.Future[?] = null
    private var waitingTicks = 0
    private val positions = new java.util.concurrent.atomic.AtomicReference[Vector[net.minecraft.util.math.BlockPos]]()
    private def machineStates =
        (0 until 13).map(ProjectRedExpansion.machine2.getStateFromMeta) ++
            (0 until 2).map(ProjectRedExpansion.machine1.getStateFromMeta) ++
            (0 until 3).map(ProjectRedFabrication.fabricationMachineBlock.getStateFromMeta) ++
            (0 until 2).map(ProjectRedFabrication.icBlock.getStateFromMeta)

    def run():Boolean = {
        val mc = Minecraft.getMinecraft
        val server = mc.getIntegratedServer
        if cleanup != null then {
            if !cleanup.isDone then return false
            cleanup.get()
            return true
        }
        if setup == null then {
            val id = mc.player.getUniqueID
            setup = server.addScheduledTask(new Runnable {
                def run():Unit = {
                    val player = server.getPlayerList.getPlayerByUUID(id)
                    val world = player.getServerWorld
                    val origin = player.getPosition.add(3,1,3)
                    val states = machineStates :+ ProjectRedIllumination.blockLamp.getDefaultState
                    val blocks = states.indices.map(i => origin.add((i%5)*2,0,(i/5)*2)).toVector
                    for (state,pos) <- states.zip(blocks) do {
                        world.setBlockToAir(pos)
                        world.setBlockState(pos,state,3)
                    }
                    positions.set(blocks)
                }
            })
            return false
        }
        if !setup.isDone then return false
        setup.get()
        waitingTicks += 1
        val blocks = positions.get()
        if !blocks.forall(pos => mc.world.getTileEntity(pos) != null) then {
            assert(waitingTicks < 200,"Particle fixtures did not synchronize to the client")
            return false
        }
        checkParticlesAndTranslations(blocks)
        val world = server.getWorld(0)
        cleanup = server.addScheduledTask(new Runnable {
            def run():Unit = blocks.foreach(world.setBlockToAir)
        })
        false
    }

    private def checkParticlesAndTranslations(blocks:Vector[net.minecraft.util.math.BlockPos]):Unit = {
        val mc = Minecraft.getMinecraft
        for language <- Seq("en_us", "ja_jp", "zh_cn") do {
            val locale = new Locale
            locale.loadLocaleDataFiles(mc.getResourceManager, Arrays.asList("en_us", language))
            val key = "gui.projectred.transportation.chip.filterModeLabel"
            val formatted = locale.formatMessage(key, Array[Object]("TEST"))
            assert(formatted != key && formatted.contains("TEST"), s"Missing routing translation for $language")
            val request = locale.formatMessage("message.projectred.request.success", Array[Object](Int.box(3), "TEST"))
            assert(request.contains("3") && request.contains("TEST"), s"Invalid request format for $language")
            assert(!request.contains("%") && !request.startsWith("message."))
        }

        val missing = mc.getTextureMapBlocks.getMissingSprite
        val frame = new FramePart
        assert(frame.getBrokenIcon(0) == FrameRenderer.getIcon && frame.getBrokenIcon(0) != missing)
        assert(new FramedRedAlloyWirePart().getBrokenIcon(0) == WireDef.RED_ALLOY.wireSprites(0))
        for colour <- 0 until 16 do {
            val wire = new FramedInsulatedRedAlloyPart
            wire.colour = colour.toByte
            for signal <- Seq(0,255) do {
                wire.signal = signal.toByte
                assert(wire.getBrokenIcon(0) == WireDef.INSULATED_WIRES(colour).wireSprites(0))
                assert(wire.getBrokenIcon(0) != missing && wire.getBounds == mrtjp.projectred.transmission.WireBoxes.fOBounds(6))
            }
        }

        for (state,pos) <- machineStates.zip(blocks) do {
            val block = state.getBlock
            assert(mc.world.getTileEntity(pos) != null, s"Client fixture has no tile for $state")
            val extended = block.getExtendedState(state,mc.world,pos).asInstanceOf[IExtendedBlockState]
            val bakery = block.asInstanceOf[codechicken.lib.model.bakery.IBakeryProvider].getBakery
                .asInstanceOf[codechicken.lib.model.bakery.generation.IBlockBakery]
            val sprite = bakery.getParticleTexture(extended)
            assert(sprite != null && sprite != missing, s"Missing machine particle for $state")
            val hit = new RayTraceResult(new Vec3d(pos.getX+0.5,pos.getY+1,pos.getZ+0.5),EnumFacing.UP,pos)
            assert(block.addHitEffects(state,mc.world,hit,mc.effectRenderer))
            assert(block.addDestroyEffects(mc.world,pos,mc.effectRenderer))
        }
        val pos = blocks.last
        val state = ProjectRedIllumination.blockLamp.getDefaultState
        val lamp = mc.world.getTileEntity(pos).asInstanceOf[TileLamp]
        for colour <- 0 until 16; on <- Seq(false,true) do {
            lamp.setShape(colour,on)
            lamp.powered = false
            val extended = LampBakery.handleState(state.asInstanceOf[IExtendedBlockState],mc.world,pos)
            assert(LampBakery.getParticleTexture(extended) == (if on then LampRenderer.iconsOn(colour) else LampRenderer.iconsOff(colour)))
            assert(state.getBlock.addDestroyEffects(mc.world,pos,mc.effectRenderer))
        }
        println("PROJECTRED_KAPPA_CLIENT_PASS: localized English/Japanese/Chinese text, machine particles and all lamp/insulated-wire colours")
    }
}
