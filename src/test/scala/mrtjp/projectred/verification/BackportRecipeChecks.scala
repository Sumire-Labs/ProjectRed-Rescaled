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

object BackportRecipeChecks {
    def run(world:WorldServer):Unit = {
        val targets = Seq(
            "projectred-illumination:illumar_smart_lamp" -> new ItemStack(ProjectRedIllumination.itemBlockSmartLamp),
            "projectred-fabrication:blank_photomask" -> new ItemStack(itemBlankPhotomask),
            "projectred-fabrication:plotting_table" -> new ItemStack(fabricationMachineBlock,1,0),
            "projectred-fabrication:lithography_table" -> new ItemStack(fabricationMachineBlock,1,1),
            "projectred-fabrication:packaging_table" -> new ItemStack(fabricationMachineBlock,1,2),
            "projectred-transportation:tube/pneumatic_tube" -> PipeDefs.PNEUMATICTUBE.makeStack,
            "projectred-transportation:tube/pressure_tube" -> PipeDefs.PRESSURETUBE.makeStack
        )
        for (name,expected) <- targets do {
            val recipe = ForgeRegistries.RECIPES.getValue(new ResourceLocation(name))
            assert(recipe != null,s"Missing backport/legacy recipe: $name")
            assert(recipe.getRecipeOutput.isItemEqual(expected),s"Recipe $name has the wrong output")
        }
        val upgrade = ForgeRegistries.RECIPES.getValue(new ResourceLocation("projectred-transportation:tube/pneumatic_tube"))
        val inventory = new InventoryCrafting(new Container {
            def canInteractWith(player:EntityPlayer):Boolean = true
        },2,1)
        inventory.setInventorySlotContents(0,PipeDefs.PRESSURETUBE.makeStack)
        inventory.setInventorySlotContents(1,new ItemStack(net.minecraft.init.Items.IRON_NUGGET))
        assert(upgrade.matches(inventory,world) && upgrade.getCraftingResult(inventory).getItemDamage == 66,
            "Pneumatic Tube upgrade recipe did not accept its ingredients")
        assert(FurnaceRecipes.instance().getSmeltingResult(PartDefs.SILICON.makeStack).getItem == itemRoughSiliconWafer,
            "Rough silicon wafer smelting was not registered")
        assert(ForgeRegistries.RECIPES.getValue(new ResourceLocation("projectred-fabrication:ic_printer")) != null,
            "Legacy IC Printer recipe disappeared")
    }
}
