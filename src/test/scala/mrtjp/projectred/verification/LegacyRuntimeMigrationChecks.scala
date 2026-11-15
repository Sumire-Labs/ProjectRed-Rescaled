package mrtjp.projectred.verification

import mrtjp.core.vec.{Point,Size}
import mrtjp.projectred.fabrication.*
import net.minecraft.nbt.NBTTagCompound

object LegacyRuntimeMigrationChecks {
    private def legacy(kind:ICGateDefinition.ICGateDef,roles:Map[Int,Boolean],shape:Int=0):ICGateLogic={
        val map=new ICTileMapContainer;map.size=Size(16,16)
        val gate=new SequentialGateICTile;gate.preparePlacement(0,kind.ordinal);gate.setShape(shape)
        gate.bindTileMap(map);gate.bindPos(Point(8,8));map.tiles((8,8))=gate
        if kind==ICGateDefinition.Counter then {val c=gate.getLogic[Counter];c.value=4;c.incr=2;c.decr=3}
        for (side,input)<-roles do {
            val p=side match {case 0=>Point(8,0);case 1=>Point(15,8);case 2=>Point(8,15);case 3=>Point(0,8)}
            val io=new IOGateICTile;io.preparePlacement(side,ICGateDefinition.IOSimple.ordinal);io.setShape(if input then 0 else 1)
            io.bindTileMap(map);io.bindPos(p);map.tiles((p.x,p.y))=io
            val points=side match {
                case 0=>(1 to 7).map(y=>Point(8,y));case 1=>(9 to 14).map(x=>Point(x,8))
                case 2=>(9 to 14).map(y=>Point(8,y));case 3=>(1 to 7).map(x=>Point(x,8))
            }
            if !(kind==ICGateDefinition.Sequencer && input) then
                for p<-points do {val w=new AlloyWireICTile;w.bindTileMap(map);w.bindPos(p);map.tiles((p.x,p.y))=w}
        }
        for tile<-map.tiles.values.collect{case t:TConnectableICTile=>t} do {
            var mask=0
            for side<-0 until 4 do map.getTile(tile.pos.offset(side)) match {
                case other:TConnectableICTile if tile.canConnectTile(other,side) && other.canConnectTile(tile,(side+2)%4) => mask|=1<<side
                case _ =>
            }
            tile.connMap=mask.toByte
        }
        val stack=mrtjp.projectred.integration.GateDefinition.ICGate.makeStack
        ItemICBlueprint.saveICToGate(map,stack)
        val part=new ICGatePart;part.assertLogic()
        val logic=part.getLogicIC
        ItemICBlueprint.loadTileMap(logic.tmap,stack)
        val (ri,ro,bi,bo)=ItemICBlueprint.getGateMasks(stack)
        logic.ri=ri;logic.ro=ro;logic.bi=bi;logic.bo=bo;logic.connmodes=ItemICBlueprint.getConnModes(stack)
        logic.sim.delegate=null;logic.sim.recompileSimulation(logic.tmap)
        part.getLogicIC
    }
    private def input(old:ICGateLogic,side:Int,value:Int):Unit={old.sim.setInput(side,value);old.sim.onInputChanged(1<<side);old.sim.repropagate()}
    private def ticks(old:ICGateLogic,n:Int):Unit=for _<-0 until n do {old.sim.advanceTime(1);old.sim.repropagate()}
    private def restore(old:ICGateLogic,pending:Long = -1): (ICGateLogic,NBTTagCompound)={
        for r<-0 until 4 do old.setOut(r,old.sim.iostate(r)>>>16)
        val saved=new NBTTagCompound;old.save(saved);saved.setLong("schedTime",pending)
        val part=new ICGatePart;part.assertLogic();part.getLogicIC.load(saved)
        val loaded=part.getLogicIC
        assert(loaded.modernSim!=null,s"Legacy conversion failed: ${loaded.legacyMigrationStatus}")
        assert(loaded.legacyRuntimeBackup==saved,"Original legacy runtime was not backed up")
        for r<-0 until 4 do assert((loaded.modernSim.getOutput(r)&65535)==(old.sim.iostate(r)>>>16),s"Migration changed output side $r")
        (loaded,saved)
    }
    private def set(logic:ICGateLogic,side:Int,value:Int):Unit={val sim=logic.modernSim;sim.setInput(side,value.toShort);sim.pushInputs(1<<side);sim.simulate();sim.pullOutputs()}
    private def advance(logic:ICGateLogic,n:Int):Unit=for _<-0 until n do {val sim=logic.modernSim;sim.progressTime(1);sim.pushTime();sim.simulate();sim.pullOutputs()}
    def run(world:net.minecraft.world.WorldServer):Unit={
        val counter=legacy(ICGateDefinition.Counter,Map(0->false,1->true,2->false,3->true))
        for _<-0 until 2 do {input(counter,1,1);input(counter,1,0)}
        input(counter,1,1) // Value is ten, with the increment input still high.
        val oldCounter=counter.tmap.getTile(Point(8,8)).asInstanceOf[SequentialGateICTile].getLogic[Counter]
        assert(counter.sim.simEngine.getRegVal[Int](oldCounter.valueReg)==10)
        val (counting,_)=restore(counter)
        set(counting,3,1)
        assert(counting.modernSim.getOutput(0)==0 && counting.modernSim.getOutput(2)==0,"Counter value or previous input edges were reset")
        set(counting,1,0);set(counting,3,0);set(counting,1,1)
        assert(counting.modernSim.getOutput(0)==0,"Counter resumed at the wrong nonzero value")
        set(counting,1,0);set(counting,1,1)
        assert(counting.modernSim.getOutput(0)==1,"Counter did not retain its value across migration")
        val legacyItem=mrtjp.projectred.integration.GateDefinition.ICGate.makeStack
        ItemICBlueprint.saveICToGate(counter.tmap,legacyItem)
        val placedAgain=new ICGatePart;placedAgain.assertLogic();ICGateLogic.constructICLogic(placedAgain.getLogicIC,legacyItem)
        assert(placedAgain.getLogicIC.modernSim!=null,"An old inventory IC returned to the legacy engine on placement")
        val mismatched=new NBTTagCompound;counter.save(mismatched)
        mismatched.setByte(s"reg[${SEIntegratedCircuit.REG_OUT(0,0)}]",0.toByte)
        val rejected=new ICGatePart;rejected.assertLogic();rejected.getLogicIC.load(mismatched)
        assert(rejected.getLogicIC.modernSim==null && rejected.getLogicIC.legacyMigrationStatus.nonEmpty && rejected.getLogicIC.legacyRuntimeBackup!=null,
            "An inconsistent legacy register layout was accepted without a recovery copy")
        val timer=legacy(ICGateDefinition.Timer,Map(0->false,2->true))
        ticks(timer,20)
        val (timing,_)=restore(timer,123)
        assert(timing.migrationOutputUntil==123)
        advance(timing,16);assert(timing.modernSim.getOutput(0)==0,"Timer phase was shortened during migration")
        advance(timing,1);assert(timing.modernSim.getOutput(0)==1,"Timer remaining time was not preserved")
        advance(timing,2);assert(timing.modernSim.getOutput(0)==0)
        for (kind,shape)<-Seq((ICGateDefinition.SRLatch,2),(ICGateDefinition.ToggleLatch,0)) do {
            val latch=legacy(kind,Map(0->false,1->true,2->false,3->true),shape)
            input(latch,1,1);input(latch,1,0)
            val (loaded,_)=restore(latch)
            advance(loaded,1)
            assert((loaded.modernSim.getOutput(0)&65535)==(latch.sim.iostate(0)>>>16),s"$kind lost its latched state")
        }
        for kind<-Seq(ICGateDefinition.Pulse,ICGateDefinition.Repeater,ICGateDefinition.Sequencer,ICGateDefinition.Randomizer) do {
            val circuit=legacy(kind,Map(0->false,2->true))
            input(circuit,2,1);ticks(circuit,1)
            val (loaded,_)=restore(circuit)
            advance(loaded,1)
            val saved=new NBTTagCompound;loaded.save(saved)
            val again=new ICGatePart;again.assertLogic();again.getLogicIC.load(saved)
            assert(again.getLogicIC.modernSim!=null && again.getLogicIC.legacyRuntimeBackup!=null)
        }
        val transparent=legacy(ICGateDefinition.TransparentLatch,Map(0->false,1->false,2->true,3->true))
        input(transparent,3,1);input(transparent,2,1);input(transparent,2,0)
        val (locked,_)=restore(transparent)
        set(locked,3,0);assert(locked.modernSim.getOutput(0)==1,"Transparent latch lost locked data")
        set(locked,2,1);assert(locked.modernSim.getOutput(0)==0)
        val stateCell=legacy(ICGateDefinition.StateCell,Map(0->false,1->true,2->true,3->false))
        input(stateCell,2,1);input(stateCell,2,0);ticks(stateCell,10)
        val (running,_)=restore(stateCell)
        advance(running,27);assert(running.modernSim.getOutput(0)==0)
        advance(running,1);assert(running.modernSim.getOutput(0)==1,"State cell lost its running interval")
        val sync=legacy(ICGateDefinition.Synchronizer,Map(0->false,1->true,2->true,3->true))
        input(sync,1,1)
        val (armed,_)=restore(sync)
        set(armed,3,1);assert(armed.modernSim.getOutput(0)==1,"Synchronizer lost its armed input")
        advance(armed,2);assert(armed.modernSim.getOutput(0)==0)
        val unsupported=legacy(ICGateDefinition.Counter,Map(0->false,1->true,2->false,3->true))
        val lever=new LeverICTile;lever.bindTileMap(unsupported.tmap);lever.bindPos(Point(2,2));unsupported.tmap.tiles((2,2))=lever
        unsupported.sim.recompileSimulation(unsupported.tmap)
        val original=new NBTTagCompound;unsupported.save(original)
        val incompatible=new ICGatePart;incompatible.assertLogic();incompatible.getLogicIC.load(original)
        assert(incompatible.getLogicIC.legacyMigrationStatus.nonEmpty && incompatible.getLogicIC.sim.simEngine!=null && incompatible.getLogicIC.tmap.getTile(Point(2,2)).isInstanceOf[LeverICTile],
            "Unsupported legacy circuitry was silently discarded or reset")
        val unknown=original.copy();unknown.getTagList("tiles",10).getCompoundTagAt(0).setByte("id",127.toByte)
        val unreadable=new ICGatePart;unreadable.assertLogic();unreadable.getLogicIC.load(unknown)
        val kept=new NBTTagCompound;unreadable.getLogicIC.save(kept)
        assert(unreadable.getLogicIC.modernDisabled && kept.getTagList("tiles",10)==unknown.getTagList("tiles",10) && kept.hasKey("legacy_runtime_backup"),
            "An unrecognized legacy tile ID caused saved data to be lost")
        val unknownItem=mrtjp.projectred.integration.GateDefinition.ICGate.makeStack
        val itemData=new NBTTagCompound;itemData.setTag("tilemap",unknown.copy());unknownItem.setTagCompound(itemData)
        val unknownPlacement=new ICGatePart;unknownPlacement.assertLogic();ICGateLogic.constructICLogic(unknownPlacement.getLogicIC,unknownItem)
        assert(unknownPlacement.getLogicIC.modernDisabled && unknownPlacement.getLogicIC.legacyRuntimeBackup!=null,
            "An unrecognized old inventory IC could not retain its source on placement")
        val pulse=legacy(ICGateDefinition.Pulse,Map(0->false,2->true))
        input(pulse,2,1)
        val clock:net.minecraft.world.World=world;val time=clock.getTotalWorldTime
        val snapshot=new NBTTagCompound;pulse.save(snapshot)
        snapshot.setByte("subID",mrtjp.projectred.integration.GateDefinition.ICGate.ordinal.toByte)
        snapshot.setLong("schedTime",time+1)
        class Probe extends ICGatePart {override def getRedstoneInput(r:Int):Int=if r==2 then 255 else 0}
        val probe=new Probe;probe.load(snapshot)
        val pos=new net.minecraft.util.math.BlockPos(44,80,24)
        world.setBlockState(pos.down(),net.minecraft.init.Blocks.STONE.getDefaultState)
        try {
            codechicken.multipart.TileMultipart.addPart(world,pos,probe)
            probe.update();assert(probe.getLogicIC.out(0)==0,"Migration exposed a pending output too early")
            clock.getWorldInfo.setWorldTotalTime(time+1);probe.update()
            assert(probe.getLogicIC.out(0)==1,"Migration lost a pending pulse")
            clock.getWorldInfo.setWorldTotalTime(time+2);probe.update();assert(probe.getLogicIC.out(0)==1)
            clock.getWorldInfo.setWorldTotalTime(time+3);probe.update();assert(probe.getLogicIC.out(0)==0,"Migration stretched or retriggered a pending pulse")
        } finally {world.setBlockToAir(pos);world.setBlockToAir(pos.down());clock.getWorldInfo.setWorldTotalTime(time)}
        println("PROJECTRED_LEGACY_MIGRATION_PASS: live counter values/edges, latch memory, timer phase, pending output and repeat-load backup")
    }
}
