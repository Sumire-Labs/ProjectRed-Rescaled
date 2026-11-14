package mrtjp.projectred.verification

import mrtjp.projectred.expansion.CraftingResultTestHelper
import mrtjp.core.inventory.SimpleInventory
import net.minecraft.init.Items
import net.minecraft.inventory.InventoryCrafting
import net.minecraft.item.ItemStack
import net.minecraft.item.crafting.IRecipe
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.NonNullList
import net.minecraft.world.World
import net.minecraftforge.registries.IForgeRegistryEntry

object CraftingRemainderChecks {
    private class Recipe(tool:Boolean) extends IForgeRegistryEntry.Impl[IRecipe] with IRecipe {
        def matches(inv:InventoryCrafting, world:World):Boolean =
            !inv.getStackInSlot(0).isEmpty && !inv.getStackInSlot(1).isEmpty &&
            inv.getStackInSlot(0).getItem == (if tool then Items.IRON_PICKAXE else Items.IRON_INGOT) &&
            inv.getStackInSlot(1).getItem == Items.IRON_INGOT
        def canFit(width:Int,height:Int):Boolean = width*height >= 2
        def getRecipeOutput:ItemStack = new ItemStack(Items.PAPER)
        def getCraftingResult(inv:InventoryCrafting):ItemStack = getRecipeOutput
        override def getRemainingItems(inv:InventoryCrafting):NonNullList[ItemStack] = {
            val remaining = NonNullList.withSize(9,ItemStack.EMPTY)
            if tool then {
                val used = inv.getStackInSlot(0).copy()
                used.setCount(1)
                used.setItemDamage(used.getItemDamage+1)
                remaining.set(0,used)
            }
            remaining
        }
    }
    private def inputs(first:ItemStack,second:ItemStack):Array[ItemStack] =
        Array(first,second) ++ Array.fill(7)(ItemStack.EMPTY)
    def run(world:World):Unit = {
        // Model upstream #1580 without depending on IC2: the plan and actual tool have different wear/NBT.
        val planTool = new ItemStack(Items.IRON_PICKAXE)
        planTool.setItemDamage(5)
        val realTool = new ItemStack(Items.IRON_PICKAXE)
        realTool.setItemDamage(37)
        val toolTag = new NBTTagCompound
        toolTag.setString("source","actual-storage")
        realTool.setTagCompound(toolTag)
        val helper = new CraftingResultTestHelper
        helper.setRecipe(new Recipe(true))
        helper.loadInputs(inputs(planTool,new ItemStack(Items.IRON_INGOT)))
        helper.loadStorage(Array(realTool,new ItemStack(Items.IRON_INGOT)),true)
        val (result,remaining) = helper.consumeAndCraft(world)
        assert(!result.isEmpty)
        assert(remaining.get(0).getItemDamage == 38, "Crafting remainder used the plan tool's durability")
        assert(remaining.get(0).getTagCompound == toolTag, "Crafting remainder lost the actual ingredient's NBT")

        val incomplete = new CraftingResultTestHelper
        incomplete.setRecipe(new Recipe(false))
        incomplete.loadInputs(inputs(new ItemStack(Items.IRON_INGOT),new ItemStack(Items.IRON_INGOT)))
        incomplete.loadStorage(Array(new ItemStack(Items.IRON_INGOT)),true)
        assert(incomplete.consumeAndCraft(world)._1.isEmpty)
        val after = new SimpleInventory(1)
        incomplete.unloadStorage(after,slot => slot)
        assert(after.getStackInSlot(0).getCount == 1, "Failed crafting consumed part of its ingredients")

        val blocked = new CraftingResultTestHelper
        blocked.setRecipe(new Recipe(true))
        blocked.loadInputs(inputs(planTool,new ItemStack(Items.IRON_INGOT)))
        blocked.loadStorage(Array(realTool,new ItemStack(Items.IRON_INGOT,64)),true)
        assert(!blocked.consumeAndCraftToStorage(world,64), "Craft unexpectedly fit its output and tool remainder")
        val blockedAfter = new SimpleInventory(2)
        blocked.unloadStorage(blockedAfter,slot => slot)
        assert(ItemStack.areItemStacksEqual(blockedAfter.getStackInSlot(0),realTool), "Failed output insertion lost the tool")
        assert(blockedAfter.getStackInSlot(1).getItem == Items.IRON_INGOT && blockedAfter.getStackInSlot(1).getCount == 64,
            "Failed output insertion consumed ingredients")

        val retry = new CraftingResultTestHelper
        retry.setRecipe(new Recipe(true))
        retry.loadInputs(inputs(planTool,new ItemStack(Items.IRON_INGOT)))
        retry.loadStorage(Array(realTool,new ItemStack(Items.IRON_INGOT,2),ItemStack.EMPTY),true)
        assert(retry.consumeAndCraftToStorage(world,64))
        assert(retry.consumeAndCraftToStorage(world,64))
        val twice = new SimpleInventory(3)
        retry.unloadStorage(twice,slot => slot)
        val twiceStacks = (0 until 3).map(twice.getStackInSlot)
        assert(twiceStacks.filter(_.getItem == Items.PAPER).map(_.getCount).sum == 2)
        val returnedTool = twiceStacks.find(_.getItem == Items.IRON_PICKAXE).get
        assert(returnedTool.getItemDamage == 39 && returnedTool.getTagCompound == toolTag, "Repeated crafts reset tool wear or NBT")

        val sentinel = new IllegalStateException("remainder failure")
        val throwing = new CraftingResultTestHelper
        throwing.setRecipe(new Recipe(true) {
            override def getRemainingItems(inv:InventoryCrafting):NonNullList[ItemStack] = throw sentinel
        })
        throwing.loadInputs(inputs(planTool,new ItemStack(Items.IRON_INGOT)))
        val aliasedTool = realTool.copy()
        val aliasedMaterial = new ItemStack(Items.IRON_INGOT)
        throwing.loadStorage(Array(aliasedTool,aliasedMaterial),false)
        try {
            throwing.consumeAndCraft(world)
            assert(false,"Recipe failure was ignored")
        } catch { case error:IllegalStateException => assert(error eq sentinel) }
        assert(aliasedTool.getCount == 1 && aliasedMaterial.getCount == 1, "A recipe exception consumed aliased ingredients")
    }
}
