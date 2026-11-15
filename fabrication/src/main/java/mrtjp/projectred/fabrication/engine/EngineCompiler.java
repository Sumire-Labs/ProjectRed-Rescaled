package mrtjp.projectred.fabrication.engine;

import codechicken.lib.data.MCDataOutput;
import mrtjp.fengine.TileCoord;
import mrtjp.fengine.api.ICStepThroughAssembler;
import mrtjp.projectred.fabrication.engine.log.*;
import java.util.*;
import java.util.function.Function;

/** 1.21.1 assembly passes, problem checks and simulation, with its 0.5ms tick budget. */
public final class EngineCompiler {
    public static final int KEY_COMPILER_LOG_CLEARED=1, KEY_COMPILER_LOG_NODE_ADDED=2,
        KEY_COMPILER_LOG_NODE_EXECUTED=3, KEY_COMPILER_LOG_PROBLEM_ADDED=4;
    public static final long COMPILE_BUDGET_NANOS = 500_000L;
    private final EngineContext editor;
    private final ICCompilerLog log = new ICCompilerLog(this);
    private final ICSimulationContainer simulation = new ICSimulationContainer();
    private ICStepThroughAssembler assembler;
    private int state;
    private long lastTick = -1;
    private String flatMap = PRFabricationEngine.EMPTY_FLAT_MAP_SERIALIZED;
    public EngineCompiler(EngineContext editor) { this.editor=editor; }
    public int getState() { return state; }
    public boolean isSimulating() { return state==3; }
    public long getSimSystemTime() { return simulation.getSystemTime(); }
    public ICCompilerLog getCompilerLog() { return log; }
    public ICSimulationContainer getSimulation() { return simulation; }
    public String getFlatMap() { return flatMap; }
    public MCDataOutput getStateMachineStream(int key) { return editor.compilerStream(key); }
    public void markEdited() { state=1; assembler=null; lastTick=-1; }
    public void startCompile() {
        if (state!=1) return;
        state=2;
        log.clearAndSend();
        assembler=PRFabricationEngine.instance.newStepThroughAssembler();
        assembler.setEventReceiver(log);
        assembler.addTileMap(editor.getTileMap(),Collections.emptyMap());
        var ioTiles=editor.getTileMap().getIOTiles();
        int mask=0;
        for (var io:ioTiles) mask|=(io.isInputIOMode()?1:2)<<(io.getIOSide()*2);
        for(int side=0;side<4;side++) {
            final int current=side;
            var tiles=ioTiles.stream().filter(t -> t.getIOSide()==current).toList();
            List<TileCoord> positions=tiles.stream().map(t -> ((BaseTile)t).getPos()).toList();
            if (((mask>>(side*2))&3)==3) log.addProblem(new IODirectionMismatchError(positions));
            if (tiles.stream().map(IIOConnectionTile::getInterfaceType).filter(t -> t!=ICInterfaceType.NC).distinct().count()>1)
                log.addProblem(new IOTypeMismatchError(positions));
        }
        if ((mask&0x55)==0) log.addProblem(new NoInputsError());
        if ((mask&0xAA)==0) log.addProblem(new NoOutputsError());
    }
    public void tick(long time) {
        if(state==2) {
            long deadline=System.nanoTime()+COMPILE_BUDGET_NANOS;
            while(!assembler.isDone() && System.nanoTime()<deadline) assembler.stepIn();
            if(assembler.isDone()) {
                var result=assembler.result();
                flatMap=PRFabricationEngine.instance.serializeFlatMap(result);
                simulation.setSystemTime(0);
                simulation.setFlatMap(result);
                state=log.getErrorCount()==0?3:4;
                assembler=null; lastTick=time;
                editor.notifySimulation(15);
            }
        } else if(state==3) {
            simulation.progressTime(lastTick<0?1:Math.max(0,time-lastTick));
            lastTick=time;
            simulation.pushTime(); simulation.simulate();
            editor.notifySimulation(simulation.pullOutputs());
        }
    }
    public void onInputRegistersChanged(int side,Function<Short,Short> change) {
        if(!isSimulating()) return;
        short value=change.apply(simulation.getInput(side));
        if(value!=simulation.getInput(side)) {
            simulation.setInput(side,value);simulation.pushInputs(1<<side);simulation.simulate();
            editor.notifySimulation(simulation.pullOutputs());
        }
    }
    public void save(net.minecraft.nbt.NBTTagCompound tag) {
        tag.setByte("state",(byte)state);
        tag.setInteger("compile_format",PRFabricationEngine.COMPILE_FORMAT);
        EngineData.putJson(tag,"flat_map",flatMap);
        var simTag=new net.minecraft.nbt.NBTTagCompound(); simulation.save(simTag); tag.setTag("sim_cont",simTag);
        var logTag=new net.minecraft.nbt.NBTTagCompound(); log.save(logTag); tag.setTag("compiler_log",logTag);
    }
    public void load(net.minecraft.nbt.NBTTagCompound tag) {
        assembler=null; lastTick=-1;
        state=tag.getByte("state");
        if(state==2) state=1; // A partial assembler restarts from the persisted drawing.
        flatMap=EngineData.getJson(tag,"flat_map");
        log.load(tag.getCompoundTag("compiler_log"));
        if(tag.getInteger("compile_format")!=PRFabricationEngine.COMPILE_FORMAT) { state=1; return; }
        if(state==3 || state==4) {
            var simTag=tag.getCompoundTag("sim_cont");
            try {
                if(PRFabricationEngine.instance.deserializeSimulation(EngineData.getJson(simTag,"simulation"))==null) state=1;
                else simulation.load(simTag);
            } catch(RuntimeException e) { EngineContext.LOGGER.warn("Recompilation required for saved IC simulation",e); state=1; }
        }
    }
}
