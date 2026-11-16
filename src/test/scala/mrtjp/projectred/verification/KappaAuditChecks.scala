package mrtjp.projectred.verification

import mrtjp.core.item.ItemKey
import mrtjp.projectred.{ProjectRedCore, ProjectRedTransportation}
import mrtjp.projectred.transportation.{PipeDefs, SubcorePipePart, TransportationSPH}
import codechicken.lib.vec.Vector3
import codechicken.multipart.BlockMultipart
import net.minecraft.init.Blocks
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.text.{TextComponentString, TextComponentTranslation}
import net.minecraft.util.{EnumActionResult, EnumFacing, EnumHand}
import net.minecraft.util.math.BlockPos
import net.minecraft.world.WorldServer
import net.minecraftforge.common.util.FakePlayerFactory

object KappaAuditChecks {
    def run(world:WorldServer):Unit = {
        val stack = new ItemStack(ProjectRedCore.itemPart)
        val component = TransportationSPH.nameOf(ItemKey.get(stack))
        assert(component.isInstanceOf[TextComponentTranslation], "Server resolved a mod item name before sending it")
        assert(component.asInstanceOf[TextComponentTranslation].getKey == stack.getTranslationKey+".name")

        stack.setStackDisplayName("Custom chip name")
        val named = TransportationSPH.nameOf(ItemKey.get(stack))
        assert(named.isInstanceOf[TextComponentString] && named.getUnformattedText == "Custom chip name")

        val display = new NBTTagCompound
        display.setString("LocName", "item.projectred.core.screwdriver.name")
        stack.getTagCompound.setTag("display", display)
        val before = stack.getTagCompound.copy()
        val localized = TransportationSPH.nameOf(ItemKey.get(stack))
        assert(localized.asInstanceOf[TextComponentTranslation].getKey == display.getString("LocName"))
        assert(stack.getTagCompound == before, "Request message changed the requested item's data")

        val player = FakePlayerFactory.getMinecraft(world)
        assert(new mrtjp.projectred.transmission.FramedRedAlloyWirePart().getBounds ==
            mrtjp.projectred.transmission.WireBoxes.fOBounds(6), "Framed-wire bounds are unavailable on the server")
        val item = ProjectRedTransportation.itemPartPipe
        val pos = new BlockPos(12,80,-12)
        for definition <- PipeDefs.values do {
            val part = item.newPart(definition.makeStack,player,world,pos,1,new Vector3(0.5,1,0.5))
            assert(part.isInstanceOf[mrtjp.projectred.transportation.pneumatics.part.PneumaticTubePart] && part.asInstanceOf[SubcorePipePart].meta == 66,
                s"Pipe item did not create metadata ${definition.meta}")
        }
        val held = player.getHeldItemMainhand
        val creative = player.capabilities.isCreativeMode
        try {
            world.setBlockState(pos.down(),Blocks.STONE.getDefaultState,3)
            world.setBlockToAir(pos)
            player.capabilities.isCreativeMode = false
            val tubes = PipeDefs.PNEUMATICTUBE.makeStack
            tubes.setCount(2)
            player.setHeldItem(EnumHand.MAIN_HAND,tubes)
            val result = item.onItemUse(player,world,pos.down(),EnumHand.MAIN_HAND,EnumFacing.UP,0.5F,1F,0.5F)
            assert(result == EnumActionResult.SUCCESS && tubes.getCount == 1, "Pneumatic tube placement failed or consumed the wrong count")
            assert(BlockMultipart.getPart(world,pos,6).isInstanceOf[mrtjp.projectred.transportation.pneumatics.part.PneumaticTubePart],
                "Pneumatic item placed the wrong multipart")
        } finally {
            player.setHeldItem(EnumHand.MAIN_HAND,held)
            player.capabilities.isCreativeMode = creative
            world.setBlockToAir(pos)
            world.setBlockToAir(pos.down())
        }
        println("PROJECTRED_KAPPA_AUDIT_PASS: pipe item creation/placement and translated/custom-name server messages")
    }
}
