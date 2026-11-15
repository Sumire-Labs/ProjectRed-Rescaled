// Ported from ProjectRed 1.21.1 e631f983. Client rendering is adapted separately.
package mrtjp.projectred.fabrication.engine.log;
import mrtjp.projectred.fabrication.engine.*;
import static mrtjp.projectred.fabrication.engine.EngineData.*;

import codechicken.lib.data.MCDataInput;
import codechicken.lib.data.MCDataOutput;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.vec.Vector3;
import mrtjp.fengine.TileCoord;
import net.minecraft.nbt.NBTTagCompound;

import java.util.List;

public abstract class CompileProblem {

    public final CompileProblemType type;
    public final CompileProblemSeverity severity;

    public CompileProblem(CompileProblemType type, CompileProblemSeverity severity) {
        this.type = type;
        this.severity = severity;
    }

    public abstract void save(NBTTagCompound tag);

    public abstract void load(NBTTagCompound tag);

    public abstract void writeDesc(MCDataOutput out);

    public abstract void readDesc(MCDataInput in);

}
