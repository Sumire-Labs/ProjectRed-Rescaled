package mrtjp.projectred.verification

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.{GuiMainMenu, GuiScreen}
import net.minecraft.client.renderer.RenderHelper
import net.minecraft.creativetab.CreativeTabs
import net.minecraft.item.ItemStack
import net.minecraft.util.{NonNullList, ScreenShotHelper}
import net.minecraft.world.{GameType, WorldSettings, WorldType}
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent.{ClientTickEvent, Phase}
import net.minecraftforge.fml.common.registry.ForgeRegistries
import mrtjp.projectred.fabrication.{GuiICWorkbench, TileICWorkbench}
import mrtjp.core.vec.Size
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

object ClientSmokeChecks {
    private var state = 0
    private var ticks = 0
    private var stacks = Vector.empty[ItemStack]
    private var failure:Throwable = null
    private var gallery:Gallery = null
    private var rendered = Set.empty[Int]
    def start():Unit = MinecraftForge.EVENT_BUS.register(this)
    private def screenshot(name:String):Unit = {
        val mc = Minecraft.getMinecraft
        ScreenShotHelper.saveScreenshot(mc.gameDir, name, mc.displayWidth, mc.displayHeight, mc.getFramebuffer)
    }
    private def finish():Unit = {
        val mc = Minecraft.getMinecraft
        MinecraftForge.EVENT_BUS.unregister(this)
        try {
            if failure != null then throw failure
            assert(rendered.size == stacks.size, "Some item variants were not rendered")
            assert(mrtjp.projectred.fabrication.RenderICPrinterItem.world == null &&
                mrtjp.projectred.fabrication.RenderICPrinterItem.entity == null, "IC Printer renderer retained a client world or entity")
            Files.writeString(Path.of(System.getProperty("projectred.client.result")), "PASS\n")
            println("PROJECTRED_CLIENT_PASS: "+rendered.size+" item variants rendered; IC Workbench opened and resized")
        } finally mc.shutdown()
    }
    @SubscribeEvent def tick(event:ClientTickEvent):Unit = {
        if event.phase != Phase.END then return
        val mc = Minecraft.getMinecraft
        try {
            if failure != null then { finish(); return }
            state match {
                case 0 if mc.currentScreen.isInstanceOf[GuiMainMenu] =>
                    state = 1
                    mc.launchIntegratedServer("client-smoke", "ProjectRed Client Smoke", new WorldSettings(0L, GameType.CREATIVE, false, false, WorldType.FLAT))
                case 1 if mc.player != null && mc.world != null =>
                    val all = NonNullList.create[ItemStack]()
                    ForgeRegistries.ITEMS.getValuesCollection.asScala.filter(i => i.getRegistryName.getNamespace.startsWith("projectred")).foreach(_.getSubItems(CreativeTabs.SEARCH, all))
                    stacks = all.asScala.toVector.filterNot(_.isEmpty)
                    assert(stacks.size > 100, "Creative variants were not registered")
                    gallery = new Gallery
                    mc.displayGuiScreen(gallery)
                    ticks = 0; state = 2
                case 2 =>
                    ticks += 1
                    if ticks == 10 then screenshot("projectred-items.png")
                    if ticks % 10 == 0 then gallery.page += 1
                    if gallery.page * 60 >= stacks.size then {
                        val tile = new TileICWorkbench
                        tile.setWorld(mc.world)
                        tile.hasBP = true
                        tile.editor.tileMapContainer.size = Size(16,16)
                        mc.displayGuiScreen(new GuiICWorkbench(tile))
                        ticks = 0; state = 3
                    }
                case 3 =>
                    ticks += 1
                    if ticks == 15 then {
                        assert(mc.currentScreen.isInstanceOf[GuiICWorkbench])
                        screenshot("projectred-ic-workbench.png")
                        mc.currentScreen.setWorldAndResolution(mc, mc.currentScreen.width+40, mc.currentScreen.height+20)
                    }
                    if ticks >= 30 then finish()
                case _ =>
            }
        } catch { case error:Throwable => failure = error; finish() }
    }
    class Gallery extends GuiScreen {
        var page = 0
        override def doesGuiPauseGame():Boolean = false
        override def drawScreen(mouseX:Int, mouseY:Int, frame:Float):Unit = {
            drawDefaultBackground()
            drawString(fontRenderer, "ProjectRed Rescaled: item render checks "+page, 12, 10, 0xFFFFFF)
            RenderHelper.enableGUIStandardItemLighting()
            try {
                for index <- page*60 until math.min(stacks.size,(page+1)*60) do {
                    val stack = stacks(index)
                    val model = mc.getRenderItem.getItemModelWithOverrides(stack, mc.world, mc.player)
                    assert(model != mc.getRenderItem.getItemModelMesher.getModelManager.getMissingModel, "Missing item model: "+stack)
                    mc.getRenderItem.renderItemAndEffectIntoGUI(stack, 12+(index%10)*28, 32+((index%60)/10)*28)
                    rendered += index
                }
            } catch { case error:Throwable => failure = error }
            finally RenderHelper.disableStandardItemLighting()
        }
    }
}
