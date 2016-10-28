/*
 *  Copyright 2016 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.model.lowlevel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.teavm.common.DominatorTree;
import org.teavm.common.Graph;
import org.teavm.common.GraphUtils;
import org.teavm.model.BasicBlock;
import org.teavm.model.Incoming;
import org.teavm.model.Instruction;
import org.teavm.model.MethodReference;
import org.teavm.model.Phi;
import org.teavm.model.Program;
import org.teavm.model.TextLocation;
import org.teavm.model.TryCatchBlock;
import org.teavm.model.TryCatchJoint;
import org.teavm.model.ValueType;
import org.teavm.model.Variable;
import org.teavm.model.instructions.BinaryBranchingCondition;
import org.teavm.model.instructions.BinaryBranchingInstruction;
import org.teavm.model.instructions.CloneArrayInstruction;
import org.teavm.model.instructions.ConstructArrayInstruction;
import org.teavm.model.instructions.ConstructInstruction;
import org.teavm.model.instructions.ConstructMultiArrayInstruction;
import org.teavm.model.instructions.DoubleConstantInstruction;
import org.teavm.model.instructions.ExitInstruction;
import org.teavm.model.instructions.FloatConstantInstruction;
import org.teavm.model.instructions.InitClassInstruction;
import org.teavm.model.instructions.IntegerConstantInstruction;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.JumpInstruction;
import org.teavm.model.instructions.LongConstantInstruction;
import org.teavm.model.instructions.NullConstantInstruction;
import org.teavm.model.instructions.RaiseInstruction;
import org.teavm.model.instructions.SwitchInstruction;
import org.teavm.model.instructions.SwitchTableEntry;
import org.teavm.model.util.DefinitionExtractor;
import org.teavm.model.util.ProgramUtils;
import org.teavm.runtime.ExceptionHandling;
import org.teavm.runtime.ShadowStack;

public class ExceptionHandlingShadowStackContributor {
    private ManagedMethodRepository managedMethodRepository;
    private List<CallSiteDescriptor> callSites;
    private BasicBlock defaultExceptionHandler;
    private MethodReference method;
    private Program program;
    private DominatorTree dom;
    private BasicBlock[] variableDefinitionPlaces;
    private Phi[] jointPhis;
    private boolean hasExceptionHandlers;

    public ExceptionHandlingShadowStackContributor(ManagedMethodRepository managedMethodRepository,
            List<CallSiteDescriptor> callSites, MethodReference method, Program program) {
        this.managedMethodRepository = managedMethodRepository;
        this.callSites = callSites;
        this.method = method;
        this.program = program;

        Graph cfg = ProgramUtils.buildControlFlowGraph(program);
        dom = GraphUtils.buildDominatorTree(cfg);
        variableDefinitionPlaces = ProgramUtils.getVariableDefinitionPlaces(program);
        jointPhis = new Phi[program.variableCount()];
    }

    public boolean contribute() {
        int[] blockMapping = new int[program.basicBlockCount()];
        for (int i = 0; i < blockMapping.length; ++i) {
            blockMapping[i] = i;
        }

        List<Phi> allPhis = new ArrayList<>();
        int blockCount = program.basicBlockCount();
        for (int i = 0; i < blockCount; ++i) {
            allPhis.addAll(program.basicBlockAt(i).getPhis());
        }

        for (int i = 0; i < blockCount; ++i) {
            BasicBlock block = program.basicBlockAt(i);

            if (block.getExceptionVariable() != null) {
                InvokeInstruction catchCall = new InvokeInstruction();
                catchCall.setType(InvocationType.SPECIAL);
                catchCall.setMethod(new MethodReference(ExceptionHandling.class, "catchException",
                        Throwable.class));
                catchCall.setReceiver(block.getExceptionVariable());
                block.getInstructions().add(0, catchCall);
                block.setExceptionVariable(null);
            }

            int newIndex = contributeToBasicBlock(block);
            if (newIndex != i) {
                blockMapping[i] = newIndex;
                hasExceptionHandlers = true;
            }
        }

        for (Phi phi : allPhis) {
            for (Incoming incoming : phi.getIncomings()) {
                int mappedSource = blockMapping[incoming.getSource().getIndex()];
                incoming.setSource(program.basicBlockAt(mappedSource));
            }
        }

        return hasExceptionHandlers;
    }

    private int contributeToBasicBlock(BasicBlock block) {
        List<Instruction> instructions = block.getInstructions();

        int[] currentJointSources = new int[program.variableCount()];
        int[] jointReceiverMap = new int[program.variableCount()];
        Arrays.fill(currentJointSources, -1);
        for (TryCatchBlock tryCatch : block.getTryCatchBlocks()) {
            for (TryCatchJoint joint : tryCatch.getJoints()) {
                for (Variable sourceVar : joint.getSourceVariables()) {
                    BasicBlock sourceVarDefinedAt = variableDefinitionPlaces[sourceVar.getIndex()];
                    if (dom.dominates(sourceVarDefinedAt.getIndex(), block.getIndex())) {
                        currentJointSources[joint.getReceiver().getIndex()] = sourceVar.getIndex();
                        break;
                    }
                }
                for (Variable sourceVar : joint.getSourceVariables()) {
                    jointReceiverMap[sourceVar.getIndex()] = joint.getReceiver().getIndex();
                }
            }
        }

        DefinitionExtractor defExtractor = new DefinitionExtractor();
        List<BasicBlock> blocksToClearHandlers = new ArrayList<>();
        blocksToClearHandlers.add(block);

        for (int i = 0; i < instructions.size(); ++i) {
            Instruction insn = instructions.get(i);

            if (isCallInstruction(insn)) {
                BasicBlock next;
                boolean last = false;
                if (insn instanceof RaiseInstruction) {
                    InvokeInstruction raise = new InvokeInstruction();
                    raise.setMethod(new MethodReference(ExceptionHandling.class, "throwException", Throwable.class,
                            void.class));
                    raise.setType(InvocationType.SPECIAL);
                    raise.getArguments().add(((RaiseInstruction) insn).getException());
                    raise.setLocation(insn.getLocation());
                    instructions.set(i, raise);
                    next = null;
                } else if (i < instructions.size() - 1 && instructions.get(i + 1) instanceof JumpInstruction) {
                    next = ((JumpInstruction) instructions.get(i + 1)).getTarget();
                    instructions.remove(i + 1);
                    last = true;
                } else {
                    next = program.createBasicBlock();
                    next.getTryCatchBlocks().addAll(ProgramUtils.copyTryCatches(block, program));
                    blocksToClearHandlers.add(next);

                    List<Instruction> remainingInstructions = instructions.subList(i + 1, instructions.size());
                    List<Instruction> instructionsToMove = new ArrayList<>(remainingInstructions);
                    remainingInstructions.clear();
                    next.getInstructions().addAll(instructionsToMove);
                }

                CallSiteDescriptor callSite = new CallSiteDescriptor(callSites.size());
                callSites.add(callSite);
                List<Instruction> pre = setLocation(getInstructionsBeforeCallSite(callSite), insn.getLocation());
                List<Instruction> post = getInstructionsAfterCallSite(block, next, callSite, currentJointSources);
                post = setLocation(post, insn.getLocation());
                instructions.addAll(instructions.size() - 1, pre);
                instructions.addAll(post);
                hasExceptionHandlers = true;

                if (next == null || last) {
                    break;
                }
                block = next;
                instructions = block.getInstructions();
                i = -1;
            }

            insn.acceptVisitor(defExtractor);
            for (Variable definedVar : defExtractor.getDefinedVariables()) {
                int jointReceiver = jointReceiverMap[definedVar.getIndex()];
                currentJointSources[jointReceiver] = definedVar.getIndex();
            }
        }

        for (BasicBlock blockToClear : blocksToClearHandlers) {
            blockToClear.getTryCatchBlocks().clear();
        }

        return block.getIndex();
    }

    private boolean isCallInstruction(Instruction insn) {
        if (insn instanceof InitClassInstruction || insn instanceof ConstructInstruction
                || insn instanceof ConstructArrayInstruction || insn instanceof ConstructMultiArrayInstruction
                || insn instanceof CloneArrayInstruction || insn instanceof RaiseInstruction) {
            return true;
        } else if (insn instanceof InvokeInstruction) {
            return managedMethodRepository.isManaged(((InvokeInstruction) insn).getMethod());
        }
        return false;
    }

    private List<Instruction> setLocation(List<Instruction> instructions, TextLocation location) {
        if (location != null) {
            for (Instruction instruction : instructions) {
                instruction.setLocation(location);
            }
        }
        return instructions;
    }

    private List<Instruction> getInstructionsBeforeCallSite(CallSiteDescriptor callSite) {
        List<Instruction> instructions = new ArrayList<>();

        Variable idVariable = program.createVariable();
        IntegerConstantInstruction idInsn = new IntegerConstantInstruction();
        idInsn.setConstant(callSite.getId());
        idInsn.setReceiver(idVariable);
        instructions.add(idInsn);

        InvokeInstruction registerInsn = new InvokeInstruction();
        registerInsn.setMethod(new MethodReference(ShadowStack.class, "registerCallSite", int.class, void.class));
        registerInsn.setType(InvocationType.SPECIAL);
        registerInsn.getArguments().add(idVariable);
        instructions.add(registerInsn);

        return instructions;
    }

    private List<Instruction> getInstructionsAfterCallSite(BasicBlock block, BasicBlock next,
            CallSiteDescriptor callSite, int[] currentJointSources) {
        Program program = block.getProgram();
        List<Instruction> instructions = new ArrayList<>();

        Variable handlerIdVariable = program.createVariable();
        InvokeInstruction getHandlerIdInsn = new InvokeInstruction();
        getHandlerIdInsn.setMethod(new MethodReference(ShadowStack.class, "getExceptionHandlerId", int.class));
        getHandlerIdInsn.setType(InvocationType.SPECIAL);
        getHandlerIdInsn.setReceiver(handlerIdVariable);
        instructions.add(getHandlerIdInsn);

        SwitchInstruction switchInsn = new SwitchInstruction();
        switchInsn.setCondition(handlerIdVariable);

        if (next != null) {
            SwitchTableEntry continueExecutionEntry = new SwitchTableEntry();
            continueExecutionEntry.setCondition(callSite.getId());
            continueExecutionEntry.setTarget(next);
            switchInsn.getEntries().add(continueExecutionEntry);
        }

        boolean defaultExists = false;
        int nextHandlerId = callSite.getId();
        for (TryCatchBlock tryCatch : block.getTryCatchBlocks()) {
            ExceptionHandlerDescriptor handler = new ExceptionHandlerDescriptor(++nextHandlerId,
                    tryCatch.getExceptionType());
            callSite.getHandlers().add(handler);

            if (tryCatch.getExceptionType() == null) {
                defaultExists = true;
                switchInsn.setDefaultTarget(tryCatch.getHandler());
            } else {
                SwitchTableEntry catchEntry = new SwitchTableEntry();
                catchEntry.setTarget(tryCatch.getHandler());
                catchEntry.setCondition(handler.getId());
                switchInsn.getEntries().add(catchEntry);
            }

            for (TryCatchJoint joint : tryCatch.getJoints()) {
                Phi phi = getJointPhi(joint);
                Incoming incoming = new Incoming();
                incoming.setSource(block);
                int value = currentJointSources[joint.getReceiver().getIndex()];
                incoming.setValue(program.variableAt(value));
                phi.getIncomings().add(incoming);
            }
        }

        if (!defaultExists) {
            switchInsn.setDefaultTarget(getDefaultExceptionHandler());
        }

        if (switchInsn.getEntries().size() == 1) {
            SwitchTableEntry entry = switchInsn.getEntries().get(0);

            IntegerConstantInstruction singleTestConstant = new IntegerConstantInstruction();
            singleTestConstant.setConstant(entry.getCondition());
            singleTestConstant.setReceiver(program.createVariable());
            instructions.add(singleTestConstant);

            BinaryBranchingInstruction branching = new BinaryBranchingInstruction(BinaryBranchingCondition.EQUAL);
            branching.setConsequent(entry.getTarget());
            branching.setAlternative(switchInsn.getDefaultTarget());
            branching.setFirstOperand(switchInsn.getCondition());
            branching.setSecondOperand(singleTestConstant.getReceiver());
            instructions.add(branching);
        } else {
            instructions.add(switchInsn);
        }

        return instructions;
    }

    private BasicBlock getDefaultExceptionHandler() {
        if (defaultExceptionHandler == null) {
            defaultExceptionHandler = program.createBasicBlock();
            Variable result = createReturnValueInstructions(defaultExceptionHandler.getInstructions());
            ExitInstruction exit = new ExitInstruction();
            exit.setValueToReturn(result);
            defaultExceptionHandler.getInstructions().add(exit);
        }
        return defaultExceptionHandler;
    }

    private Phi getJointPhi(TryCatchJoint joint) {
        Phi phi = jointPhis[joint.getReceiver().getIndex()];
        if (phi == null) {
            phi = new Phi();
            phi.setReceiver(joint.getReceiver());
            BasicBlock handler = program.basicBlockAt(joint.getBlock().getHandler().getIndex());
            handler.getPhis().add(phi);
            jointPhis[joint.getReceiver().getIndex()] = phi;
        }
        return phi;
    }

    private Variable createReturnValueInstructions(List<Instruction> instructions) {
        ValueType returnType = method.getReturnType();
        if (returnType == ValueType.VOID) {
            return null;
        }

        Variable variable = program.createVariable();

        if (returnType instanceof ValueType.Primitive) {
            switch (((ValueType.Primitive) returnType).getKind()) {
                case BOOLEAN:
                case BYTE:
                case SHORT:
                case CHARACTER:
                case INTEGER:
                    IntegerConstantInstruction intConstant = new IntegerConstantInstruction();
                    intConstant.setReceiver(variable);
                    instructions.add(intConstant);
                    return variable;
                case LONG:
                    LongConstantInstruction longConstant = new LongConstantInstruction();
                    longConstant.setReceiver(variable);
                    instructions.add(longConstant);
                    return variable;
                case FLOAT:
                    FloatConstantInstruction floatConstant = new FloatConstantInstruction();
                    floatConstant.setReceiver(variable);
                    instructions.add(floatConstant);
                    return variable;
                case DOUBLE:
                    DoubleConstantInstruction doubleConstant = new DoubleConstantInstruction();
                    doubleConstant.setReceiver(variable);
                    instructions.add(doubleConstant);
                    return variable;
            }
        }

        NullConstantInstruction nullConstant = new NullConstantInstruction();
        nullConstant.setReceiver(variable);
        instructions.add(nullConstant);

        return variable;
    }
}
