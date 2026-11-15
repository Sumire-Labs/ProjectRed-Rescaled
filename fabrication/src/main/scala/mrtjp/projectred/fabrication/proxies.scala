/*
 * Copyright (c) 2015.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.projectred.fabrication
import java.lang.{Character as JC}

import codechicken.lib.model.ModelRegistryHelper
import codechicken.lib.model.bakery.{CCBakeryModel, ModelBakery}
import codechicken.lib.model.bakery.key.IBlockStateKeyGenerator
import codechicken.lib.render.item.map.MapRenderRegistry
import codechicken.lib.texture.TextureUtils
import codechicken.multipart.MultiPartRegistry
import codechicken.multipart.api.IPartFactory
import mrtjp.core.block.{ItemBlockCore, MultiTileBlock}
import mrtjp.core.gui.GuiHandler
import mrtjp.projectred.ProjectRedFabrication.*
import mrtjp.projectred.ProjectRedIntegration
import mrtjp.projectred.core.{IProxy, PartDefs}
import mrtjp.projectred.integration.{GateDefinition, RenderGate}
import net.minecraft.client.renderer.block.model.{ModelResourceLocation, ModelBakery as MCModelBakery}
import net.minecraft.client.renderer.block.statemap.StateMap.Builder
import net.minecraft.item.{Item, ItemStack}
import net.minecraft.util.ResourceLocation
import net.minecraftforge.client.model.ModelLoader
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.common.property.IExtendedBlockState
import net.minecraftforge.fml.client.registry.ClientRegistry
import net.minecraftforge.fml.common.registry.{ForgeRegistries, GameRegistry}
import net.minecraftforge.fml.relauncher.{Side, SideOnly}

class FabricationProxy_server extends IProxy with IPartFactory
{
    override def preinit(): Unit =
    {
        icBlock = new BlockICMachine(icMachineBakery)
        icBlock.setTranslationKey("projectred.fabrication.icMachine")
        ForgeRegistries.BLOCKS.register(icBlock.setRegistryName("ic_machine"))
        ForgeRegistries.ITEMS.register(new ItemBlockCore(icBlock).setRegistryName(icBlock.getRegistryName))
        icBlock.addTile(classOf[TileICWorkbench], 0)
        icBlock.addTile(classOf[TileICPrinter], 1)

        fabricationMachineBlock = new BlockFabricationMachine(fabricationMachineBakery)
        fabricationMachineBlock.setTranslationKey("projectred.fabrication.fabricationMachine")
        ForgeRegistries.BLOCKS.register(fabricationMachineBlock.setRegistryName("fabrication_machine"))
        ForgeRegistries.ITEMS.register(new ItemBlockCore(fabricationMachineBlock).setRegistryName(fabricationMachineBlock.getRegistryName))
        fabricationMachineBlock.addTile(classOf[TilePlottingTable],0)
        fabricationMachineBlock.addTile(classOf[TileLithographyTable],1)
        fabricationMachineBlock.addTile(classOf[TilePackagingTable],2)

        itemICBlueprint = new ItemICBlueprint
        itemICBlueprint.setTranslationKey("projectred.fabrication.icBlueprint")
        ForgeRegistries.ITEMS.register(itemICBlueprint.setRegistryName("ic_blueprint"))

        itemICChip = new ItemICChip
        itemICChip.setTranslationKey("projectred.fabrication.icChip")
        ForgeRegistries.ITEMS.register(itemICChip.setRegistryName("ic_chip"))

        itemRoughSiliconWafer = new ItemRoughSiliconWafer
        itemRoughSiliconWafer.setTranslationKey("projectred.fabrication.roughSiliconWafer")
        ForgeRegistries.ITEMS.register(itemRoughSiliconWafer.setRegistryName("rough_silicon_wafer"))
        itemBlankPhotomask = new ItemBlankPhotomask
        itemBlankPhotomask.setTranslationKey("projectred.fabrication.blankPhotomask")
        ForgeRegistries.ITEMS.register(itemBlankPhotomask.setRegistryName("blank_photomask"))
        itemPhotomaskSet = new ItemPhotomaskSet
        itemPhotomaskSet.setTranslationKey("projectred.fabrication.photomaskSet")
        ForgeRegistries.ITEMS.register(itemPhotomaskSet.setRegistryName("photomask_set"))
        itemValidDie = new ItemValidDie
        itemValidDie.setTranslationKey("projectred.fabrication.validDie")
        ForgeRegistries.ITEMS.register(itemValidDie.setRegistryName("valid_die"))
        itemInvalidDie = new ItemInvalidDie
        itemInvalidDie.setTranslationKey("projectred.fabrication.invalidDie")
        ForgeRegistries.ITEMS.register(itemInvalidDie.setRegistryName("invalid_die"))

        MultiPartRegistry.registerParts(this, Array(GateDefinition.typeICGate))
        MinecraftForge.EVENT_BUS.register(FabricationRecipes)
    }

    override def init(): Unit =
        GameRegistry.addSmelting(PartDefs.SILICON.makeStack,new ItemStack(itemRoughSiliconWafer),0f)

    override def postinit(): Unit =
    {
        //hook into gate part to add tooltip info
        ProjectRedIntegration.itemPartGate.infoBuilderFunc = {(stack, list) =>
            if stack.getItemDamage == GateDefinition.ICGate.meta then {
                import com.mojang.realmsclient.gui.ChatFormatting.*
                if !ItemICBlueprint.hasICInside(stack) then {
                    list.add(RED.toString + "INVALID: Craft by surrounding Printed IC with Circuit Plates")
                } else {
                    list.add(GRAY.toString+ItemICBlueprint.getICName(stack))
                }
            }
        }
    }

    /**
      * Create a new instance of the part with the specified type name identifier
      *
      * @param client If the part instance is for the client or the server
      */
    override def createPart(name:ResourceLocation, client:Boolean) = name match
    {
        case GateDefinition.typeICGate => new ICGatePart
        case _ => null
    }
}

class FabricationProxy_client extends FabricationProxy_server
{
    val icWorkbenchGui = 12
    val icPrinterGui = 13
    val plottingTableGui = 14
    val lithographyTableGui = 15
    val packagingTableGui = 16

    @SideOnly(Side.CLIENT)
    override def preinit(): Unit =
    {
        super.preinit()

        fabricationMachineBakery.registerSubBakery(0,RenderPlottingTable,tableStateKey)
        fabricationMachineBakery.registerSubBakery(1,RenderLithographyTable,tableStateKey)
        fabricationMachineBakery.registerSubBakery(2,RenderPackagingTable,tableStateKey)
        ModelLoader.setCustomStateMapper(fabricationMachineBlock,new Builder().ignore(MultiTileBlock.TILE_INDEX).build())
        val tableLocation = new ModelResourceLocation(fabricationMachineBlock.getRegistryName,"normal")
        val tableItem = Item.getItemFromBlock(fabricationMachineBlock)
        MCModelBakery.registerItemVariants(tableItem,tableLocation)
        ModelLoader.setCustomMeshDefinition(tableItem,_ => tableLocation)
        ModelRegistryHelper.register(tableLocation,new CCBakeryModel())
        TextureUtils.addIconRegister(fabricationMachineBakery.registerKeyGens(fabricationMachineBlock))

        import BlockICMachine.*

        icMachineBakery.registerSubBakery(0, RenderICWorkbench, new IBlockStateKeyGenerator {
            override def generateKey(state: IExtendedBlockState):String = {
                val hasBP = state.getValue(UNLISTED_HAS_BP_PROPERTY)
                state.getBlock.getRegistryName.toString + s",bp=$hasBP"
            }
        })

        icMachineBakery.registerSubBakery(1, RenderICPrinter, new IBlockStateKeyGenerator {
            override def generateKey(state:IExtendedBlockState) = {
                val rot = state.getValue(UNLISTED_ROTATION_PROPERTY)
                state.getBlock.getRegistryName.toString + s",rot=$rot"
            }
        })

        {
            val model = new CCBakeryModel()
            val regLoc = icBlock.getRegistryName
            val normalLoc = new ModelResourceLocation(regLoc, "normal")
            val wrappedLoc = new ModelResourceLocation(regLoc, "printer_wrapped")
            ModelLoader.setCustomStateMapper(icBlock, new Builder().ignore(MultiTileBlock.TILE_INDEX).build())
            ModelLoader.setCustomMeshDefinition(Item.getItemFromBlock(icBlock), stack => if stack.getMetadata == 1 then wrappedLoc else normalLoc)
            ModelRegistryHelper.register(normalLoc, model)
            ModelRegistryHelper.register(wrappedLoc, RenderICPrinterItem)
            TextureUtils.addIconRegister(icMachineBakery.registerKeyGens(icBlock))
        }

        registerModelType(itemICBlueprint, "projectred:fabrication/items", "ic_blueprint")
        MapRenderRegistry.registerMapRenderer(itemICBlueprint, ItemRenderICBlueprint)

        registerModelType(itemICChip, "projectred:fabrication/items", Array("ic_active", "ic_inert"), { stack =>
            if ItemICBlueprint.hasICInside(stack) then "ic_active" else "ic_inert" })

        ClientRegistry.bindTileEntitySpecialRenderer(classOf[TileICPrinter], RenderICPrinterDynamic)

        registerModelType(itemRoughSiliconWafer,"projectred:fabrication/items","rough_silicon_wafer")
        registerModelType(itemBlankPhotomask,"projectred:fabrication/items","blank_photomask")
        registerModelType(itemPhotomaskSet,"projectred:fabrication/items","photomask_set")
        registerModelType(itemValidDie,"projectred:fabrication/items","valid_die")
        registerModelType(itemInvalidDie,"projectred:fabrication/items","invalid_die")

        GuiHandler.register(GuiICWorkbench, icWorkbenchGui)
        GuiHandler.register(GuiICPrinter, icPrinterGui)
        GuiHandler.register(GuiPlottingTable)
        GuiHandler.register(GuiLithographyTable)
        GuiHandler.register(GuiPackagingTable)

        RenderGate.hotswap(new RenderICGate, GateDefinition.ICGate.ordinal)
    }

    @SideOnly(Side.CLIENT)
    private def tableStateKey:IBlockStateKeyGenerator = new IBlockStateKeyGenerator {
        override def generateKey(state:IExtendedBlockState):String = {
            val meta = state.getBlock.getMetaFromState(state)
            val rotation = state.getValue(BlockFabricationMachine.UNLISTED_ROTATION_PROPERTY)
            val charged = state.getValue(BlockFabricationMachine.UNLISTED_CHARGED_PROPERTY)
            val working = state.getValue(BlockFabricationMachine.UNLISTED_WORKING_PROPERTY)
            state.getBlock.getRegistryName.toString+s",meta=$meta,rot=$rotation,charged=$charged,working=$working"
        }
    }

    @SideOnly(Side.CLIENT)
    def registerModelType(item:Item, jsonLocation:String, typeValue:String): Unit =
    {
        registerModelType(item, 0, jsonLocation, typeValue)
    }

    @SideOnly(Side.CLIENT)
    def registerModelType(item:Item, meta:Int, jsonLocation:String, typeValue:String): Unit =
    {
        val modelLoc = new ModelResourceLocation(jsonLocation, "type=" + typeValue)
        ModelLoader.setCustomModelResourceLocation(item, meta, modelLoc)
    }

    @SideOnly(Side.CLIENT)
    def registerModelType(item:Item, jsonLocation:String, names:Array[String], typeValue:ItemStack => String): Unit =
    {
        MCModelBakery.registerItemVariants(item, names.map { n => new ModelResourceLocation(jsonLocation, s"type=$n") }*)
        ModelLoader.setCustomMeshDefinition(item, (s: ItemStack) => new ModelResourceLocation(jsonLocation, "type=" + typeValue(s)))
    }
}

object FabricationProxy extends FabricationProxy_client
