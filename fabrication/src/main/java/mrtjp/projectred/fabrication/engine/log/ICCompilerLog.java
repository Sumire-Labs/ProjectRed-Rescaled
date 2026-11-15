// Ported from ProjectRed 1.21.1 e631f983. Client rendering is adapted separately.
package mrtjp.projectred.fabrication.engine.log;
import mrtjp.projectred.fabrication.engine.*;
import static mrtjp.projectred.fabrication.engine.EngineData.*;

import codechicken.lib.data.MCDataInput;
import codechicken.lib.data.MCDataOutput;
import mrtjp.fengine.TileCoord;
import mrtjp.fengine.api.ICStepThroughAssembler;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTBase;

import java.util.*;

import static mrtjp.fengine.api.ICStepThroughAssembler.AssemblerStepType.CHECK_OPEN_TILE_MAPS;

import static mrtjp.projectred.fabrication.engine.EngineCompiler.*;

public class ICCompilerLog implements ICStepThroughAssembler.EventReceiver {

    private final EngineCompiler stateMachine;

    // Compile steps tracking
    private final CompileTree compileTree = new CompileTree();
    private final List<Integer> currentPath = new LinkedList<>();
    private int completedSteps = 0; //TODO ideally this would be a feature in the compile tree object

    // Compile problems
    private final List<CompileProblem> problems = new ArrayList<>();
    private int warningCount = 0;
    private int errorCount = 0;

    private final List<Runnable> treeChangedListeners = new LinkedList<>();

    public ICCompilerLog(EngineCompiler stateMachine) {
        this.stateMachine = stateMachine;
    }

    public void save(NBTTagCompound tag) {
        tag.setInteger(KEY_COMPLETED_STEPS, completedSteps);
        tag.setIntArray(KEY_CURRENT_PATH, currentPath.stream().mapToInt(i -> i).toArray());
        compileTree.save(tag);

        NBTTagList list = new NBTTagList();
        for (CompileProblem problem : problems) {
            NBTTagCompound problemTag = new NBTTagCompound();
            problemTag.setByte("_type", (byte) problem.type.getID());
            problem.save(problemTag);
            list.appendTag(problemTag);
        }
        tag.setTag(KEY_PROBLEMS_LIST, list);
        tag.setInteger(KEY_WARNING_COUNT, warningCount);
        tag.setInteger(KEY_ERROR_COUNT, errorCount);
    }

    public void load(NBTTagCompound tag) {
        clear();
        completedSteps = tag.getInteger(KEY_COMPLETED_STEPS);
        currentPath.clear();
        currentPath.addAll(Arrays.stream(tag.getIntArray(KEY_CURRENT_PATH)).boxed().toList());
        compileTree.load(tag);

        NBTTagList list = tag.getTagList(KEY_PROBLEMS_LIST, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound problemTag = list.getCompoundTagAt(i);
            CompileProblem problem = CompileProblemType.createById(problemTag.getByte("_type") & 0xFF);
            problem.load(problemTag);
            addProblemInternal(problem);
        }
    }

    public void writeDesc(MCDataOutput out) {
        compileTree.writeDesc(out);
        out.writeInt(completedSteps);
        out.writeInt(currentPath.size());
        for (int i : currentPath) out.writeInt(i);

        out.writeVarInt(problems.size());
        for (CompileProblem problem : problems) {
            out.writeByte(problem.type.getID());
            problem.writeDesc(out);
        }
    }

    public void readDesc(MCDataInput in) {
        clear();
        compileTree.readDesc(in);
        completedSteps = in.readInt();
        int size = in.readInt();
        for (int i = 0; i < size; i++) currentPath.add(in.readInt());

        size = in.readVarInt();
        for (int i = 0; i < size; i++) {
            CompileProblem problem = CompileProblemType.createById(in.readUByte());
            problem.readDesc(in);
            addProblemInternal(problem);
        }
    }

    public void clear() {
        compileTree.clear();
        currentPath.clear();
        completedSteps = 0;
        problems.clear();
        warningCount = 0;
        errorCount = 0;
        notifyListeners();
    }

    public int getTotalSteps() {
        return compileTree.size();
    }

    public int getCompletedSteps() {
        return completedSteps;
    }

    public List<CompileProblem> getProblems() {
        return problems;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public int getWarningCount() {
        return warningCount;
    }

    public void addTreeChangedListener(Runnable listener) {
        treeChangedListeners.add(listener);
    }

    private void notifyListeners() {
        for (Runnable listener : treeChangedListeners) {
            listener.run();
        }
    }

    //region ICStepThroughAssembler.EventReceiver

    public void onStepAdded(ICStepThroughAssembler.AssemblerStepDescriptor descriptor) {
        CompileTreeNode node = compileTree.findOrCreateNode(descriptor.getTreePath());
        node.step = descriptor.getStepType();

        sendNodeAdded(node, descriptor.getTreePath());
        notifyListeners();
    }

    public void onStepExecuted(ICStepThroughAssembler.AssemblerStepResult result) {
        CompileTreeNode node = compileTree.findOrCreateNode(result.getTreePath());
        node.tileCoords.addAll(result.getTileCoords());
        node.registerIds.addAll(result.getRegisterIds());
        node.gateIds.addAll(result.getGateIds());
        node.registerRemaps.putAll(result.getRemappedRegisterIds());

        currentPath.clear();
        currentPath.addAll(result.getTreePath());
        completedSteps++;

        sendNodeExecuted(node, result.getTreePath());
        notifyListeners();
    }
    //endregion

    //region Compile-time logging
    public void clearAndSend() {
        clear();
        sendClear();
    }

    public void addProblem(CompileProblem problem) {
        addProblemInternal(problem);
        sendProblemAdded(problem);
    }
    //endregion

    //region Packet handling
    public void readLogStream(MCDataInput in, int key) {
        switch (key) {
            case KEY_COMPILER_LOG_CLEARED -> clear();
            case KEY_COMPILER_LOG_NODE_ADDED -> readNodeAdded(in);
            case KEY_COMPILER_LOG_NODE_EXECUTED -> readNodeExecuted(in);
            case KEY_COMPILER_LOG_PROBLEM_ADDED -> readProblemAdded(in);
            default -> throw new IllegalArgumentException("Unknown compiler stream key: " + key);
        }
    }
    //endregion

    //region Server-side utilities
    private void sendClear() {
        stateMachine.getStateMachineStream(KEY_COMPILER_LOG_CLEARED);
    }

    private void sendNodeAdded(CompileTreeNode node, List<Integer> treePath) {
        MCDataOutput out = stateMachine.getStateMachineStream(KEY_COMPILER_LOG_NODE_ADDED);
        out.writeVarInt(treePath.size());
        for (int i : treePath) {
            out.writeVarInt(i);
        }
        out.writeByte(node.step.ordinal());
    }

    private void sendNodeExecuted(CompileTreeNode node, List<Integer> treePath) {
        MCDataOutput out = stateMachine.getStateMachineStream(KEY_COMPILER_LOG_NODE_EXECUTED);
        out.writeVarInt(treePath.size());
        for (int i : treePath) {
            out.writeVarInt(i);
        }
        node.write(out);
    }

    private void sendProblemAdded(CompileProblem problem) {
        MCDataOutput out = stateMachine.getStateMachineStream(KEY_COMPILER_LOG_PROBLEM_ADDED);
        out.writeByte(problem.type.ordinal());
        problem.writeDesc(out);
    }

    private void addProblemInternal(CompileProblem problem) {
        problems.add(problem);
        switch (problem.severity) {
            case WARNING -> warningCount++;
            case ERROR -> errorCount++;
        }
        notifyListeners();
    }
    //endregion

    //region Client-side utilities
    private void readNodeAdded(MCDataInput in) {
        int pathLength = in.readVarInt();
        List<Integer> path = new ArrayList<>(pathLength);
        for (int i = 0; i < pathLength; i++) {
            path.add(in.readVarInt());
        }
        CompileTreeNode node = compileTree.findOrCreateNode(path);
        node.step = ICStepThroughAssembler.AssemblerStepType.values()[in.readUByte()];
        notifyListeners();
    }

    private void readNodeExecuted(MCDataInput in) {
        int pathLength = in.readVarInt();
        List<Integer> path = new ArrayList<>(pathLength);
        for (int i = 0; i < pathLength; i++) {
            path.add(in.readVarInt());
        }
        CompileTreeNode node = compileTree.findOrCreateNode(path);
        node.read(in);

        currentPath.clear();
        currentPath.addAll(path);
        completedSteps++;
        notifyListeners();
    }

    private void readProblemAdded(MCDataInput in) {
        CompileProblem problem = CompileProblemType.createById(in.readUByte());
        problem.readDesc(in);
        addProblemInternal(problem);
    }

    public List<CompileTreeNode> getCurrentStack() {
        return compileTree.getStack(currentPath);
    }

    public List<CompileTreeNode> getRootNodes() {
        return compileTree.rootNodes;
    }

    public int getProgressScaled(int scale) {
        int size = compileTree.size();
        return size == 0 ? 0 : completedSteps * scale / size;
    }
    //endregion

    public static class CompileTreeNode {

        public final List<CompileTreeNode> children = new ArrayList<>();

        public ICStepThroughAssembler.AssemblerStepType step = CHECK_OPEN_TILE_MAPS;
        public final List<TileCoord> tileCoords = new ArrayList<>();
        public final List<Integer> registerIds = new ArrayList<>();
        public final List<Integer> gateIds = new ArrayList<>();
        public final Map<Integer, Integer> registerRemaps = new HashMap<>();

        public void save(NBTTagCompound tag) {
            tag.setByte("step", (byte) step.ordinal());

            NBTTagList tileCoordsTag = new NBTTagList();
            for (TileCoord coord : tileCoords) {
                NBTTagCompound coordTag = new NBTTagCompound();
                coordTag.setByte("x", (byte) coord.x);
                coordTag.setByte("y", (byte) coord.y);
                coordTag.setByte("z", (byte) coord.z);
                tileCoordsTag.appendTag(coordTag);
            }
            tag.setTag("tileCoords", tileCoordsTag);

            tag.setIntArray("registerIds", registerIds.stream().mapToInt(i -> i).toArray());
            tag.setIntArray("gateIds", gateIds.stream().mapToInt(i -> i).toArray());

            NBTTagList remapsTag = new NBTTagList();
            for (Map.Entry<Integer, Integer> entry : registerRemaps.entrySet()) {
                NBTTagCompound remapTag = new NBTTagCompound();
                remapTag.setInteger("k", entry.getKey());
                remapTag.setInteger("v", entry.getValue());
                remapsTag.appendTag(remapTag);
            }
            tag.setTag("registerRemaps", remapsTag);

            NBTTagList childrenTag = new NBTTagList();
            for (CompileTreeNode child : children) {
                NBTTagCompound childTag = new NBTTagCompound();
                child.save(childTag);
                childrenTag.appendTag(childTag);
            }
            tag.setTag("children", childrenTag);
        }

        public void load(NBTTagCompound tag) {
            step = ICStepThroughAssembler.AssemblerStepType.values()[tag.getByte("step")];

            // Note: this is only called on fresh instance. No need to clear lists

            NBTTagList tileCoordsTag = tag.getTagList("tileCoords", 10);
            for (NBTBase coordTag : tileCoordsTag) {
                NBTTagCompound coordTagCompound = (NBTTagCompound) coordTag;
                tileCoords.add(new TileCoord(
                        coordTagCompound.getByte("x"),
                        coordTagCompound.getByte("y"),
                        coordTagCompound.getByte("z")
                ));
            }

            registerIds.addAll(Arrays.stream(tag.getIntArray("registerIds")).boxed().toList());
            gateIds.addAll(Arrays.stream(tag.getIntArray("gateIds")).boxed().toList());

            NBTTagList remapsTag = tag.getTagList("registerRemaps", 10);
            for (NBTBase remapTag : remapsTag) {
                NBTTagCompound remapTagCompound = (NBTTagCompound) remapTag;
                registerRemaps.put(remapTagCompound.getInteger("k"), remapTagCompound.getInteger("v"));
            }

            children.clear();
            NBTTagList childrenTag = tag.getTagList("children", 10);
            for (NBTBase childTag : childrenTag) {
                CompileTreeNode child = new CompileTreeNode();
                child.load((NBTTagCompound) childTag);
                children.add(child);
            }
        }

        public void write(MCDataOutput out) {
            out.writeByte(step.ordinal());
            out.writeVarInt(tileCoords.size());
            for (TileCoord coord : tileCoords) {
                out.writeByte(coord.x).writeByte(coord.y).writeByte(coord.z);
            }
            out.writeVarInt(registerIds.size());
            for (int i : registerIds) {
                out.writeVarInt(i);
            }
            out.writeVarInt(gateIds.size());
            for (int i : gateIds) {
                out.writeVarInt(i);
            }
            out.writeVarInt(registerRemaps.size());
            registerRemaps.forEach((k, v) -> {
                out.writeVarInt(k);
                out.writeVarInt(v);
            });
        }

        public void read(MCDataInput in) {
            step = ICStepThroughAssembler.AssemblerStepType.values()[in.readUByte()];
            int size = in.readVarInt();
            tileCoords.clear();
            for (int i = 0; i < size; i++) {
                tileCoords.add(new TileCoord(in.readByte(), in.readByte(), in.readByte()));
            }
            size = in.readVarInt();
            registerIds.clear();
            for (int i = 0; i < size; i++) {
                registerIds.add(in.readVarInt());
            }
            size = in.readVarInt();
            gateIds.clear();
            for (int i = 0; i < size; i++) {
                gateIds.add(in.readVarInt());
            }
            size = in.readVarInt();
            registerRemaps.clear();
            for (int i = 0; i < size; i++) {
                registerRemaps.put(in.readVarInt(), in.readVarInt());
            }
        }

        protected void writeDesc(MCDataOutput out) {
            write(out);
            out.writeVarInt(children.size());
            for (CompileTreeNode child : children) {
                child.writeDesc(out);
            }
        }

        protected void readDesc(MCDataInput in) {
            read(in);
            children.clear();
            int size = in.readVarInt();
            for (int i = 0; i < size; i++) {
                CompileTreeNode child = new CompileTreeNode();
                child.readDesc(in);
                children.add(child);
            }
        }

        public int countRegIdsInSubtree() {
            //TODO cache
            int count = registerIds.size();
            for (CompileTreeNode child : children) {
                count += child.countRegIdsInSubtree();
            }
            return count;
        }

        public int countGateIdsInSubtree() {
            //TODO cache
            int count = gateIds.size();
            for (CompileTreeNode child : children) {
                count += child.countGateIdsInSubtree();
            }
            return count;
        }

        public int countRemapsInSubtree() {
            //TODO cache
            int count = registerRemaps.size();
            for (CompileTreeNode child : children) {
                count += child.countRemapsInSubtree();
            }
            return count;
        }

        public boolean isEmpty() {
            return registerIds.isEmpty() && gateIds.isEmpty() && registerRemaps.isEmpty();
        }

        public void getPositionsInTree(List<TileCoord> pList) {
            if (!isEmpty()) {
                pList.addAll(tileCoords);
                for (CompileTreeNode child : children) {
                    child.getPositionsInTree(pList);
                }
            }
        }
    }

    public static class CompileTree {

        private final List<CompileTreeNode> rootNodes = new ArrayList<>();

        private int size = 0;

        /**
         * Find the node at the given path, creating empty nodes when necessary
         */
        public CompileTreeNode findOrCreateNode(List<Integer> path) {
            Iterator<Integer> pathIterator = path.iterator();

            int ri = pathIterator.next();
            CompileTreeNode node = ri < rootNodes.size() ? rootNodes.get(ri) : null;
            if (node == null) {
                node = new CompileTreeNode();
                rootNodes.add(ri, node);
                size++;
            }

            while (pathIterator.hasNext()) {
                int i = pathIterator.next();
                CompileTreeNode next = i < node.children.size() ? node.children.get(i) : null;
                if (next == null) {
                    next = new CompileTreeNode();
                    node.children.add(i, next);
                    size++;
                }
                node = next;
            }
            return node;
        }

        public List<CompileTreeNode> getStack(List<Integer> path) {
            if (path.isEmpty()) return Collections.emptyList();

            List<CompileTreeNode> stack = new ArrayList<>(path.size());
            Iterator<Integer> pathIterator = path.iterator();

            int ri = pathIterator.next();
            CompileTreeNode node = ri < rootNodes.size() ? rootNodes.get(ri) : null;
            if (node == null) {
                return stack;
            }
            stack.add(node);

            while (pathIterator.hasNext()) {
                int i = pathIterator.next();
                CompileTreeNode next = i < node.children.size() ? node.children.get(i) : null;
                if (next == null) {
                    return stack;
                }
                stack.add(next);
                node = next;
            }
            return stack;
        }

        public int size() {
            return size;
        }

        public void clear() {
            rootNodes.clear();
            size = 0;
        }

        public void save(NBTTagCompound tag) {
            tag.setInteger("size", size);
            NBTTagList rootNodesTag = new NBTTagList();
            for (CompileTreeNode rootNode : rootNodes) {
                NBTTagCompound rootNodeTag = new NBTTagCompound();
                rootNode.save(rootNodeTag);
                rootNodesTag.appendTag(rootNodeTag);
            }
            tag.setTag("rootNodes", rootNodesTag);
        }

        public void load(NBTTagCompound tag) {
            size = tag.getInteger("size");
            rootNodes.clear();
            NBTTagList rootNodesTag = tag.getTagList("rootNodes", 10);
            for (NBTBase rootNodeTag : rootNodesTag) {
                CompileTreeNode rootNode = new CompileTreeNode();
                rootNode.load((NBTTagCompound) rootNodeTag);
                rootNodes.add(rootNode);
            }
        }

        public void writeDesc(MCDataOutput out) {
            out.writeVarInt(size);
            out.writeVarInt(rootNodes.size());
            for (CompileTreeNode node : rootNodes) {
                node.writeDesc(out);
            }
        }

        public void readDesc(MCDataInput in) {
            size = in.readVarInt();
            int size = in.readVarInt();
            rootNodes.clear();
            for (int i = 0; i < size; i++) {
                CompileTreeNode node = new CompileTreeNode();
                node.readDesc(in);
                rootNodes.add(node);
            }
        }
    }
}
