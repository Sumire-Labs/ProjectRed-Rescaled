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


public class NoOutputsError extends CompileProblem {

    public NoOutputsError() {
        super(CompileProblemType.NO_OUTPUTS, CompileProblemSeverity.ERROR);
    }

    public void save(NBTTagCompound tag) {
    }

    public void load(NBTTagCompound tag) {
    }

    public void writeDesc(MCDataOutput out) {
    }

    public void readDesc(MCDataInput in) {
    }

}
