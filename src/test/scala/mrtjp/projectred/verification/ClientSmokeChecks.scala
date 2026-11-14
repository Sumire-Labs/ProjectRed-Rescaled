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
    private var serverAction:java.util.concurrent.Future[?] = null
    private val benchPosition = new java.util.concurrent.atomic.AtomicReference[net.minecraft.util.math.BlockPos]()
    private var craftSubmitted = false
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
            assert(rendered.size == stacks.size, s"Some item variants were not rendered: ${rendered.size}/${stacks.size}")
            assert(mrtjp.projectred.fabrication.RenderICPrinterItem.world == null &&
                mrtjp.projectred.fabrication.RenderICPrinterItem.entity == null, "IC Printer renderer retained a client world or entity")
            Files.writeString(Path.of(System.getProperty("projectred.client.result")), "PASS\n")
            println("PROJECTRED_CLIENT_PASS: "+rendered.size+" item variants rendered; IC Workbench opened/resized; Project Bench opened, crafted and closed out of range through the server")
        } finally mc.shutdown()
    }
    @SubscribeEvent def tick(event:ClientTickEvent):Unit = {
        if event.phase != Phase.END then return
        val mc = Minecraft.getMinecraft
        try {
            if failure != null then { finish(); return }
            state match {
                case 0 if mc.currentScreen.isInstanceOf[GuiMainMenu] =>
                    mc.gameSettings.pauseOnLostFocus = false
                    state = 1
                    mc.launchIntegratedServer("client-smoke", "ProjectRed Client Smoke", new WorldSettings(0L, GameType.CREATIVE, false, false, WorldType.FLAT))
                case 1 if mc.player != null && mc.world != null && mc.currentScreen == null =>
                    val all = NonNullList.create[ItemStack]()
                    ForgeRegistries.ITEMS.getValuesCollection.asScala.filter(i => i.getRegistryName.getNamespace.startsWith("projectred")).foreach(_.getSubItems(CreativeTabs.SEARCH, all))
                    stacks = all.asScala.toVector.filterNot(_.isEmpty)
                    assert(stacks.size > 100, "Creative variants were not registered")
                    gallery = new Gallery
                    mc.displayGuiScreen(gallery)
                    ticks = 0; state = 2
                case 2 =>
                    ticks += 1
                    assert(mc.currentScreen eq gallery, "Another screen interrupted the item gallery: "+
                        Option(mc.currentScreen).map(_.getClass.getName).getOrElse("no screen"))
                    val pageDrawn = (gallery.page*60 until math.min(stacks.size,(gallery.page+1)*60)).forall(rendered.contains)
                    if ticks % 10 == 0 && pageDrawn then {
                        if gallery.page == 0 then screenshot("projectred-items.png")
                        gallery.page += 1
                    }
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
                    if ticks >= 30 then {
                        val server = mc.getIntegratedServer
                        val playerId = mc.player.getUniqueID
                        serverAction = server.addScheduledTask(new Runnable {
                            def run():Unit = {
                                val player = server.getPlayerList.getPlayerByUUID(playerId)
                                val world = player.getServerWorld
                                val pos = player.getPosition.add(1,0,0)
                                world.setBlockState(pos, mrtjp.projectred.ProjectRedExpansion.machine2.getStateFromMeta(10), 3)
                                benchPosition.set(pos)
                            }
                        })
                        ticks = 0; state = 4
                    }
                case 4 =>
                    ticks += 1
                    if serverAction.isDone then {
                        serverAction.get()
                        val pos = benchPosition.get()
                        if pos != null && mc.world.getTileEntity(pos).isInstanceOf[mrtjp.projectred.expansion.TileProjectBench] then {
                            val server = mc.getIntegratedServer
                            val playerId = mc.player.getUniqueID
                            serverAction = server.addScheduledTask(new Runnable {
                                def run():Unit = {
                                    val player = server.getPlayerList.getPlayerByUUID(playerId)
                                    val tile = player.getServerWorld.getTileEntity(pos).asInstanceOf[mrtjp.projectred.expansion.TileProjectBench]
                                    player.inventory.setItemStack(ItemStack.EMPTY)
                                    tile.setInventorySlotContents(0,new ItemStack(net.minecraft.init.Blocks.LOG))
                                    assert(tile.onBlockActivated(player, 1), "Project Bench refused to open")
                                }
                            })
                            ticks = 0; state = 5
                        } else assert(ticks < 100, "Project Bench did not synchronize to the client")
                    }
                case 5 =>
                    ticks += 1
                    if serverAction.isDone then serverAction.get()
                    if mc.currentScreen.isInstanceOf[mrtjp.projectred.expansion.GuiProjectBench] then {
                        if !craftSubmitted then {
                            val result = mc.player.openContainer.getSlot(28).getStack
                            if !result.isEmpty && result.getItem == net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.PLANKS) then {
                                screenshot("projectred-project-bench.png")
                                val gui = mc.currentScreen.asInstanceOf[mrtjp.projectred.expansion.GuiProjectBench]
                                val slot = mc.player.openContainer.getSlot(28)
                                val x = gui.position.x+slot.xPos+8
                                val y = gui.position.y+slot.yPos+8
                                gui.mouseClicked(x,y,0)
                                gui.mouseReleased(x,y,0)
                                craftSubmitted = true
                                ticks = 0
                            } else assert(ticks < 100, "Project Bench recipe did not reach the client")
                        } else if ticks >= 10 then {
                            val server = mc.getIntegratedServer
                            val playerId = mc.player.getUniqueID
                            serverAction = server.addScheduledTask(new Runnable {
                                def run():Unit = {
                                    val player = server.getPlayerList.getPlayerByUUID(playerId)
                                    val held = player.inventory.getItemStack
                                    assert(held.getItem == net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.PLANKS) && held.getCount == 4,
                                        "Server did not accept the Project Bench craft")
                                    val tile = player.getServerWorld.getTileEntity(benchPosition.get()).asInstanceOf[mrtjp.projectred.expansion.TileProjectBench]
                                    assert(tile.getStackInSlot(0).isEmpty,"Project Bench craft did not consume its log")
                                    player.connection.setPlayerLocation(player.posX+20, player.posY, player.posZ, player.rotationYaw, player.rotationPitch)
                                }
                            })
                            ticks = 0; state = 6
                        }
                    } else assert(ticks < 100, "Project Bench server GUI did not reach the client")
                case 6 =>
                    ticks += 1
                    if serverAction.isDone then {
                        serverAction.get()
                        if ticks >= 10 && !mc.currentScreen.isInstanceOf[mrtjp.projectred.expansion.GuiProjectBench] then finish()
                        else assert(ticks < 100, "Project Bench GUI remained open after leaving its range")
                    }
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
