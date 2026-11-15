/*
 * Copyright (c) 2015.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.projectred.expansion

import codechicken.lib.colour.EnumColour
import codechicken.lib.data.{MCDataInput, MCDataOutput}
import codechicken.lib.gui.GuiDraw
import codechicken.lib.model.bakery.SimpleBlockRenderer
import codechicken.lib.texture.TextureUtils
import codechicken.lib.vec.uv.{MultiIconTransformation, UVTransformation}
import mrtjp.core.gui.*
import mrtjp.core.inventory.{TInventory, TInventoryCapablilityTile}
import mrtjp.core.vec.{Point, Size}
import mrtjp.projectred.ProjectRedExpansion
import net.minecraft.client.renderer.texture.{TextureAtlasSprite, TextureMap}
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.{IContainerListener, ISidedInventory}
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.math.BlockPos
import net.minecraft.util.{EnumFacing, ResourceLocation}
import net.minecraft.world.IBlockAccess
import net.minecraftforge.common.property.IExtendedBlockState
import net.minecraftforge.fml.relauncher.{Side, SideOnly}
import org.lwjgl.input.Keyboard

class TileAutoCrafter extends TileMachine with TPoweredMachine with TInventory with ISidedInventory with TGuiMachine with TInventoryCapablilityTile
{
    var planSlot = 0
    var isCharged = false
    var isWorking = false
    var workRemaining = 0
    var workMax = 0
    private var idleTicksOnPlan = 0

    def visualState:Int = if !isCharged then 0 else if isWorking then 2 else 1

    private var recipeNeedsRefresh = true
    val craftHelper = new CraftingResultTestHelper

    override def save(tag:NBTTagCompound): Unit =
    {
        super.save(tag)
        saveInv(tag)
        tag.setInteger("remaining_work",workRemaining)
        tag.setInteger("total_work",workMax)
        tag.setBoolean("working",isWorking)
        tag.setBoolean("charging",isCharged)
        tag.setByte("plan_slot",planSlot.toByte)
    }

    override def load(tag:NBTTagCompound): Unit =
    {
        super.load(tag)
        clear()
        loadInv(tag)
        planSlot = math.max(0,math.min(8,tag.getByte("plan_slot")&255))
        workMax = math.max(0,math.min(100,tag.getInteger("total_work")))
        workRemaining = math.max(0,math.min(workMax,tag.getInteger("remaining_work")))
        isCharged = if tag.hasKey("charging") then tag.getBoolean("charging") else cond.canWork
        isWorking = tag.getBoolean("working") && workRemaining > 0
        recipeNeedsRefresh = true
    }

    override def writeDesc(out:MCDataOutput):Unit = {
        super.writeDesc(out)
        out.writeBoolean(isCharged).writeBoolean(isWorking)
    }

    override def readDesc(in:MCDataInput):Unit = {
        super.readDesc(in)
        isCharged = in.readBoolean()
        isWorking = in.readBoolean()
    }

    override def read(in:MCDataInput,key:Int):Unit = key match {
        case 3 =>
            isCharged = in.readBoolean()
            isWorking = in.readBoolean()
            markRender()
        case _ => super.read(in,key)
    }

    override def readFromClient(in:MCDataInput, key:Int, player:EntityPlayer):Unit =
        if key == 2 && mrtjp.projectred.core.TileActions.hasOpenInventory(this, player) then cyclePlanSlot()

    def sendCyclePlanSlot(): Unit =
    {
        writeStream(2).sendToServer()
    }

    def getWorkDuration = 100
    def progressScaled(scale:Int):Int = if workMax == 0 then 0 else scale*(workMax-workRemaining)/workMax

    override def getBlock = ProjectRedExpansion.machine2

    override def doesOrient = false
    override def doesRotate = true

    /**
     * 0 until 9 - Plans
     * 9 until 27 - Storage
     */
    override protected val storage = Array.fill(27)(ItemStack.EMPTY)//new Array[ItemStack](27)

    override def getInventoryStackLimit = 64

    override def getName = "auto_bench"

    override def getDisplayName = super.getDisplayName
    override def canExtractItem(slot:Int, item:ItemStack, side:EnumFacing) = 9 until 27 contains slot
    override def canInsertItem(slot:Int, item:ItemStack, side:EnumFacing) = 9 until 27 contains slot
    override def getSlotsForFace(side:EnumFacing) = (9 until 27).toArray

    override def updateServer(): Unit =
    {
        super.updateServer()
        val oldCharged = isCharged
        val oldWorking = isWorking
        isCharged = cond.canWork
        if !isWorking && canStartOrContinueWork then {
            isWorking = true
            workRemaining = getWorkDuration
            workMax = workRemaining
        }
        if !canStartOrContinueWork then {
            isWorking = false
            workRemaining = 0
            workMax = 0
        } else {
            if cond.canWork then {
                cond.drawPower(1100)
                workRemaining -= 1
                super.markDirty()
            }
            if workRemaining <= 0 then {
                isWorking = false
                workRemaining = 0
                workMax = 0
                tryCraft()
                cyclePlanSlot()
            }
        }
        if idleTicksOnPlan > 10 then cyclePlanSlot()
        if isCharged != oldCharged || isWorking != oldWorking then {
            writeStream(3).writeBoolean(isCharged).writeBoolean(isWorking).sendToChunk(this)
            super.markDirty()
        }
    }

    private def canStartOrContinueWork:Boolean = {
        if recipeNeedsRefresh then { refreshRecipe(); recipeNeedsRefresh = false }
        val possible = if craftHelper.recipe == null then false else {
            craftHelper.loadStorage((9 until 27).map(getStackInSlot).toArray,true)
            craftHelper.consumeAndCraftToStorage(world,64)
        }
        if !possible then idleTicksOnPlan += 1
        possible
    }

    def cyclePlanSlot(): Unit =
    {
        val start = planSlot
        while { planSlot = (planSlot+1)%9
        ; planSlot != start && getStackInSlot(planSlot).isEmpty} do ()
        if planSlot != start then { recipeNeedsRefresh = true; idleTicksOnPlan = 0; markDirty() }
    }

    def refreshRecipe(): Unit =
    {
        craftHelper.clear()

        val plan = getStackInSlot(planSlot)
        if !plan.isEmpty && ItemPlan.hasRecipeInside(plan) then {
            val inputs = ItemPlan.loadPlanInputs(plan)

            craftHelper.loadInputs(inputs)
            craftHelper.findRecipeFromInputs(world)
        }
    }

    override def markDirty(): Unit =
    {
        super.markDirty()
        recipeNeedsRefresh = true
    }

    def tryCraft():Boolean =
    {
        if recipeNeedsRefresh then {
            refreshRecipe()
            recipeNeedsRefresh = false
        }

        if craftHelper.recipe != null then {
            craftHelper.loadStorage((9 until 27).map(getStackInSlot).toArray, true)
            if craftHelper.consumeAndCraftToStorage(world, 64) then {
                craftHelper.unloadStorage(this, {_ + 9})
                return true
            }
        }

        false
    }

    override def onBlockRemoval(): Unit =
    {
        super.onBlockRemoval()
        dropInvContents(world, getPos)
    }

    override def openGui(player:EntityPlayer): Unit =
    {
        GuiAutoCrafter.open(player, createContainer(player), _.writePos(getPos))
    }

    override def createContainer(player: EntityPlayer): ContainerAutoCrafter = new ContainerAutoCrafter(player, this)
}

class ContainerAutoCrafter(player:EntityPlayer, tile:TileAutoCrafter) extends ContainerPoweredMachine(tile)
{
    {
        for ((x, y), i) <- GuiLib.createSlotGrid(44, 22, 3, 3, 0, 0).zipWithIndex do
        {
            val s = new Slot3(tile, i, x, y)
            s.canPlaceDelegate = {s =>
                s.getItem.isInstanceOf[ItemPlan] && ItemPlan.hasRecipeInside(s)
            }
            addSlotToContainer(s)
        }

        for ((x, y), i) <- GuiLib.createSlotGrid(8, 80, 9, 2, 0, 0).zipWithIndex do
            addSlotToContainer(new Slot3(tile, i+9, x, y))

        addPlayerInv(player, 8, 130)
    }

    var slot = -1
    private var lastRemaining = -1
    private var lastTotal = -1

    override def detectAndSendChanges(): Unit =
    {
        super.detectAndSendChanges()
        import scala.jdk.CollectionConverters.*
        for i <- listeners.asScala do
        {
            val ic = i

            if slot != tile.planSlot then ic.sendWindowProperty(this, 3, tile.planSlot)
            if lastRemaining != tile.workRemaining then ic.sendWindowProperty(this,4,tile.workRemaining)
            if lastTotal != tile.workMax then ic.sendWindowProperty(this,5,tile.workMax)
        }
        slot = tile.planSlot
        lastRemaining = tile.workRemaining
        lastTotal = tile.workMax
    }

    override def updateProgressBar(id:Int, bar:Int) = id match
    {
        case 3 => tile.planSlot = bar
        case 4 => tile.workRemaining = bar
        case 5 => tile.workMax = bar
        case _ => super.updateProgressBar(id, bar)
    }

    override def doMerge(stack:ItemStack, from:Int):Boolean =
    {
        if 0 until 9 contains from then //plan slots
        {
            if tryMergeItemStack(stack, 36, 63, false) then return true //to player inv
            if tryMergeItemStack(stack, 27, 36, false) then return true //to hotbar
        }
        else if 9 until 27 contains from then //storage
        {
            if stack.getItem.isInstanceOf[ItemPlan] then
                if tryMergeItemStack(stack, 0, 9, false) then return true //merge to plan

            if tryMergeItemStack(stack, 27, 36, true) then return true //to hotbar reversed
            if tryMergeItemStack(stack, 36, 63, true) then return true //to player inv reversed
        }
        else if 27 until 63 contains from then //player inventory
        {
            if stack.getItem.isInstanceOf[ItemPlan] then {
                if tryMergeItemStack(stack, 0, 9, false) then return true //merge to plan
            } else
                if tryMergeItemStack(stack, 9, 27, false) then return true //merge to storage
        }

        false
    }
}

class GuiAutoCrafter(tile:TileAutoCrafter, c:ContainerAutoCrafter) extends NodeGui(c, 176, 212)
{
    {
        val cycle = new IconButtonNode {
            override def drawButton(mouseover:Boolean): Unit = {
                TextureUtils.changeTexture(GuiAutoCrafter.background)
                drawTexturedModalRect(position.x, position.y, 176, 0, 14, 14)
            }
        }
        cycle.position = Point(126, 23)
        cycle.size = Size(14, 14)
        cycle.clickDelegate = {() => tile.sendCyclePlanSlot()}
        addChild(cycle)
    }

    override def drawBack_Impl(mouse:Point, rframe:Float): Unit =
    {
        TextureUtils.changeTexture(GuiAutoCrafter.background)
        GuiDraw.drawTexturedModalRect(0, 0, 0, 0, size.width, size.height)

        if tile.cond.canWork then
            GuiDraw.drawTexturedModalRect(16, 16, 177, 18, 7, 9)
        GuiLib.drawVerticalTank(16, 26, 177, 27, 7, 48, tile.cond.getChargeScaled(48))

        if tile.cond.flow == -1 then
            GuiDraw.drawTexturedModalRect(27, 16, 185, 18, 7, 9)
        GuiLib.drawVerticalTank(27, 26, 185, 27, 7, 48, tile.cond.getFlowScaled(48))

        val plan = tile.getStackInSlot(tile.planSlot)
        if !plan.isEmpty && ItemPlan.hasRecipeInside(plan) then
            ItemDisplayNode.renderItem(Point(125, 40), Size(16, 16), zPosition, true, ItemPlan.loadPlanOutput(plan))

        GuiDraw.drawTexturedModalRect(100,41,193,23,tile.progressScaled(22)+1,14)

        GuiDraw.drawString("Auto Crafting Bench", 8, 6, EnumColour.GRAY.argb, false)
        GuiDraw.drawString("Inventory", 8, 120, EnumColour.GRAY.argb, false)
    }

    override def drawFront_Impl(mouse:Point, rframe:Float): Unit =
    {
        if net.minecraft.client.gui.GuiScreen.isShiftKeyDown then
            GuiProjectBench.drawPlanOutputOverlay(c.slots)

        TextureUtils.changeTexture(GuiAutoCrafter.background)

        val Point(sx, sy) = Point(18, 18).multiply(tile.planSlot%3, tile.planSlot/3).add(44, 22).subtract(3)
        GuiDraw.drawTexturedModalRect(sx, sy, 193, 0, 22, 22)
    }
}

object GuiAutoCrafter extends TGuiFactory
{
    val background = new ResourceLocation("projectred", "textures/gui/auto_bench.png")

    override def getID = ExpansionProxy.autoCrafterGui

    @SideOnly(Side.CLIENT)
    override def buildGui(player:EntityPlayer, data:MCDataInput) =
    {
        player.world.getTileEntity(data.readPos()) match
        {
            case t:TileAutoCrafter => new GuiAutoCrafter(t, t.createContainer(player))
            case _ => null
        }
    }
}

object RenderAutoCrafter extends SimpleBlockRenderer
{
    import org.apache.commons.lang3.tuple.Triple

    var bottom:TextureAtlasSprite = scala.compiletime.uninitialized
    var top:TextureAtlasSprite = scala.compiletime.uninitialized
    var side1:TextureAtlasSprite = scala.compiletime.uninitialized
    var side2:TextureAtlasSprite = scala.compiletime.uninitialized

    var iconT:UVTransformation = scala.compiletime.uninitialized
    private var icons:Array[UVTransformation] = scala.compiletime.uninitialized

    override def handleState(state:IExtendedBlockState,world:IBlockAccess,pos:BlockPos):IExtendedBlockState =
        world.getTileEntity(pos) match {
            case tile:TileAutoCrafter => state
                .withProperty(BlockProperties.UNLISTED_CHARGED_PROPERTY,java.lang.Boolean.valueOf(tile.isCharged))
                .withProperty(BlockProperties.UNLISTED_WORKING_PROPERTY,java.lang.Boolean.valueOf(tile.isWorking))
                .withProperty(BlockProperties.UNLISTED_ROTATION_PROPERTY,Integer.valueOf(tile.rotation))
            case _ => state
        }

    override def getWorldTransforms(state: IExtendedBlockState) = {
        val charged = state.getValue(BlockProperties.UNLISTED_CHARGED_PROPERTY).booleanValue
        val working = state.getValue(BlockProperties.UNLISTED_WORKING_PROPERTY).booleanValue
        val rotation = state.getValue(BlockProperties.UNLISTED_ROTATION_PROPERTY)
        Triple.of(0,rotation,icons(if !charged then 0 else if working then 2 else 1))
    }
    override def getItemTransforms(stack: ItemStack) = Triple.of(0, 0, iconT)
    override def shouldCull() = true

    def getIcon(side:Int, meta:Int) = side match
    {
        case 0 => bottom
        case 1 => top
        case _ => side1
    }

    override def registerIcons(reg:TextureMap): Unit =
    {
        bottom = reg.registerSprite(new ResourceLocation("projectred:blocks/mechanical/autobench/bottom"))
        top = reg.registerSprite(new ResourceLocation("projectred:blocks/mechanical/autobench/top"))
        side1 = reg.registerSprite(new ResourceLocation("projectred:blocks/mechanical/autobench/side1"))
        side2 = reg.registerSprite(new ResourceLocation("projectred:blocks/mechanical/autobench/side2"))

        iconT = new MultiIconTransformation(bottom, top, side1, side2, side2, side2)
        val charged = reg.registerSprite(new ResourceLocation("projectred:blocks/mechanical/autobench/front_charged"))
        val working = reg.registerSprite(new ResourceLocation("projectred:blocks/mechanical/autobench/front_working"))
        icons = Array(iconT,
            new MultiIconTransformation(bottom,top,charged,side2,side2,side2),
            new MultiIconTransformation(bottom,top,working,side2,side2,side2))
    }
}
