package mrtjp.projectred.transportation

import codechicken.lib.model.ModelRegistryHelper
import codechicken.lib.packet.PacketCustom
import codechicken.lib.texture.TextureUtils
import codechicken.microblock.MicroMaterialRegistry
import codechicken.multipart.MultiPartRegistry
import codechicken.multipart.api.IPartFactory
import mrtjp.core.gui.GuiHandler
import mrtjp.projectred.ProjectRedTransportation.*
import mrtjp.projectred.core.IProxy
import mrtjp.projectred.transportation.pneumatics.part.PneumaticTubePart
import net.minecraft.client.renderer.block.model.ModelResourceLocation
import net.minecraft.util.ResourceLocation
import net.minecraftforge.client.model.ModelLoader
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.registry.ForgeRegistries
import net.minecraftforge.fml.relauncher.{Side, SideOnly}

class TransportationProxy_server extends IProxy with IPartFactory
{
    val guiIDInterfacePipe = 5
    val guiIDFirewallPipe = 6
    val guiIDRoutingChips = 7

    override def preinit(): Unit =
    {
        LegacyTransportMigration.init()
        itemPartPipe = new ItemPartPipe
        itemPartPipe.setTranslationKey("projectred.transportation.pipe")
        ForgeRegistries.ITEMS.register(itemPartPipe.setRegistryName("pipe"))

        itemRoutingChip = new ItemRoutingChip
        itemRoutingChip.setTranslationKey("projectred.transportation.routingChip")
        ForgeRegistries.ITEMS.register(itemRoutingChip.setRegistryName("routing_chip"))

        itemRouterUtility = new ItemRouterUtility
        itemRouterUtility.setTranslationKey("projectred.transportation.routerUtility")
        ForgeRegistries.ITEMS.register(itemRouterUtility.setRegistryName("router_utility"))

        MultiPartRegistry.registerParts(this, PipeDefs.values.map{_.partname}.toArray)
        MinecraftForge.EVENT_BUS.register(PneumaticSounds)
    }

    override def init(): Unit =
    {
    }

    override def postinit(): Unit =
    {
        PacketCustom.assignHandler(TransportationSPH.channel, TransportationSPH)

    }

    import mrtjp.projectred.transportation.PipeDefs.*
    override def createPart(name:ResourceLocation, client:Boolean) = name match
    {
        case legacy if LegacyTransportMigration.oldParts(legacy.toString) => new PneumaticTubePart(legacy.toString)
        case PNEUMATICTUBE.partname => new PneumaticTubePart
        case _ => null
    }
}

class TransportationProxy_client extends TransportationProxy_server
{
    @SideOnly(Side.CLIENT)
    override def preinit(): Unit =
    {
        super.preinit()
        PneumaticTubeRenderer.prepare()

        ModelRegistryHelper.registerItemRenderer(itemPartPipe, PipeItemRenderer)

        for i <- RoutingChipDefs.values do
            i.setCustomModelResourceLocations()

        ModelLoader.setCustomModelResourceLocation(itemRouterUtility, 0,
            new ModelResourceLocation("projectred:mechanical/tools", "type=router_utility"))
        TextureUtils.addIconRegister(RenderPipe)
    }

    @SideOnly(Side.CLIENT)
    override def init(): Unit =
    {
        super.init()
        MicroMaterialRegistry.registerHighlightRenderer(PipeRSHighlightRenderer)
        MicroMaterialRegistry.registerHighlightRenderer(PipeColourHighlightRenderer)
    }

    @SideOnly(Side.CLIENT)
    override def postinit(): Unit =
    {
        super.postinit()
        PacketCustom.assignHandler(TransportationCPH.channel, TransportationCPH)

        GuiHandler.register(GuiInterfacePipe, guiIDInterfacePipe)
        GuiHandler.register(GuiFirewallPipe, guiIDFirewallPipe)
        GuiHandler.register(GuiChipConfig, guiIDRoutingChips)
    }
}

object TransportationProxy extends TransportationProxy_client
