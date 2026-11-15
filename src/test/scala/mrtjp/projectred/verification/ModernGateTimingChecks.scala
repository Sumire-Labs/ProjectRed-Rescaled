package mrtjp.projectred.verification

import mrtjp.core.vec.{Point,Size}
import mrtjp.projectred.fabrication.*
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.math.BlockPos
import net.minecraft.world.{World,WorldServer}

object ModernGateTimingChecks {
    def run(world:WorldServer):Unit = {
        val map=new ICTileMapContainer
        map.size=Size(16,16)
        for (y,rotation,shape) <- Seq((0,0,0),(15,2,1)) do {
            val tile=new IOGateICTile
            tile.preparePlacement(rotation,ICGateDefinition.IOSimple.ordinal);tile.setShape(shape)
            tile.bindTileMap(map);tile.bindPos(Point(8,y));map.tiles += ((8,y)->tile)
        }
        for y <- 1 to 14 do {
            val tile=new AlloyWireICTile
            tile.bindTileMap(map);tile.bindPos(Point(8,y));map.tiles += ((8,y)->tile)
        }
        val blueprint=new ItemStack(mrtjp.projectred.ProjectRedFabrication.itemICBlueprint)
        ItemICBlueprint.saveTileMap(map,blueprint)
        assert(ICData.canFabricate(blueprint))
        val item=mrtjp.projectred.integration.GateDefinition.ICGate.makeStack
        ItemICBlueprint.copyToGate(blueprint,item)
        val template=new ICGatePart
        template.assertLogic();ICGateLogic.constructICLogic(template.getLogicIC,item)
        val tag=new NBTTagCompound
        template.getLogicIC.save(tag)
        tag.setByte("subID",mrtjp.projectred.integration.GateDefinition.ICGate.ordinal.toByte)
        tag.setLong("schedTime",-1L)
        tag.setTag("itemTag",item.getTagCompound.copy())
        class Probe extends ICGatePart {
            var powered=false
            override def getRedstoneInput(r:Int):Int = if r==0 && powered then 255 else 0
        }
        val probe=new Probe
        probe.load(tag)
        val clock:World=world
        val time=clock.getTotalWorldTime
        val pos=new BlockPos(42,80,24)
        world.setBlockState(pos.down(),net.minecraft.init.Blocks.STONE.getDefaultState)
        try {
            codechicken.multipart.TileMultipart.addPart(world,pos,probe)
            val logic=probe.getLogicIC
            assert(logic.modernSim!=null)
            probe.powered=true;logic.onChange(probe)
            assert(probe.schedTime==time+2 && logic.modernSim.getOutput(2)==0,"IC inputs were not latched for two ticks")
            clock.getWorldInfo.setWorldTotalTime(time+1);probe.update()
            assert(logic.out(2)==0,"Modern IC exposed an input before its scheduled tick")
            clock.getWorldInfo.setWorldTotalTime(time+2);probe.update()
            assert(logic.out(2)==1,"Modern IC did not publish its latched input at tick two")
            probe.powered=false;logic.onChange(probe)
            probe.powered=true;logic.onChange(probe)
            clock.getWorldInfo.setWorldTotalTime(time+4);probe.update()
            assert(logic.out(2)==1,"Modern IC propagated an input pulse shorter than its latch delay")
        } finally {
            world.setBlockToAir(pos);world.setBlockToAir(pos.down())
            clock.getWorldInfo.setWorldTotalTime(time)
        }
    }
}
