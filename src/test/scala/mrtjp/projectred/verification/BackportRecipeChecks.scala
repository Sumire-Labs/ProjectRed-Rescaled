package mrtjp.projectred.verification

import mrtjp.projectred.ProjectRedFabrication.*
import mrtjp.projectred.ProjectRedIllumination
import mrtjp.projectred.core.PartDefs
import mrtjp.projectred.transportation.PipeDefs
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.{Container,InventoryCrafting}
import net.minecraft.item.ItemStack
import net.minecraft.item.crafting.FurnaceRecipes
import net.minecraft.util.ResourceLocation
import net.minecraft.world.WorldServer
import net.minecraftforge.fml.common.registry.ForgeRegistries
import scala.jdk.CollectionConverters.*

object BackportRecipeChecks {
    def run(world:WorldServer):Unit = {
        val targets = Seq(
            "projectred-illumination:illumar_smart_lamp" -> new ItemStack(ProjectRedIllumination.itemBlockSmartLamp),
            "projectred-fabrication:blank_photomask" -> new ItemStack(itemBlankPhotomask),
            "projectred-fabrication:plotting_table" -> new ItemStack(fabricationMachineBlock,1,0),
            "projectred-fabrication:lithography_table" -> new ItemStack(fabricationMachineBlock,1,1),
            "projectred-fabrication:packaging_table" -> new ItemStack(fabricationMachineBlock,1,2),
            "projectred-transportation:tube/pneumatic_tube" -> PipeDefs.PNEUMATICTUBE.makeStack
        )
        for (name,expected) <- targets do {
            val recipe = ForgeRegistries.RECIPES.getValue(new ResourceLocation(name))
            assert(recipe != null,s"Missing modern recipe: $name")
            assert(recipe.getRecipeOutput.isItemEqual(expected),s"Recipe $name has the wrong output")
        }
        val upgrade = ForgeRegistries.RECIPES.getValue(new ResourceLocation("projectred-transportation:tube/pneumatic_tube"))
        val inventory = new InventoryCrafting(new Container {
            def canInteractWith(player:EntityPlayer):Boolean = true
        },3,3)
        for i <- 0 until 9 do inventory.setInventorySlotContents(i,
            if i%3 == 1 then new ItemStack(net.minecraft.init.Blocks.GLASS_PANE) else PartDefs.COPPERINGOT.makeStack)
        assert(upgrade.matches(inventory,world) && upgrade.getCraftingResult(inventory).getItemDamage == 66 &&
            upgrade.getCraftingResult(inventory).getCount == 8,"Pneumatic Tube recipe differs from 1.21.1")
        assert(FurnaceRecipes.instance().getSmeltingResult(PartDefs.SILICON.makeStack).getItem == itemRoughSiliconWafer,
            "Rough silicon wafer smelting was not registered")
        for name <- Seq("projectred-fabrication:ic_printer","projectred-fabrication:ic_chip","projectred-fabrication:ic_gate",
            "projectred-transportation:chip_reset","projectred-transportation:tube/pressure_tube",
            "projectred-transportation:tube/pressure_resistance_tube","projectred-transportation:pipe/item_transport_pipe",
            "projectred-expansion:device/filtered_importer") do
            assert(ForgeRegistries.RECIPES.getValue(new ResourceLocation(name)) == null,s"Retired recipe remains: $name")
        for recipe <- ForgeRegistries.RECIPES.getValuesCollection.asScala do {
            val out = recipe.getRecipeOutput
            if !out.isEmpty then {
                assert(out.getItem != itemICChip,"A recipe still creates legacy chips or recovery archives")
                assert(out.getItem != mrtjp.projectred.ProjectRedTransportation.itemRoutingChip &&
                    out.getItem != mrtjp.projectred.ProjectRedTransportation.itemRouterUtility,"A recipe still creates retired routing equipment")
                if out.getItem == mrtjp.projectred.ProjectRedTransportation.itemPartPipe then
                    assert(out.getItemDamage == 66,"A recipe still creates an old transport pipe")
            }
        }
    }
}
