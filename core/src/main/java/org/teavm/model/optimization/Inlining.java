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
package org.teavm.model.optimization;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.teavm.model.BasicBlock;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.Incoming;
import org.teavm.model.Instruction;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.Phi;
import org.teavm.model.Program;
import org.teavm.model.instructions.AssignInstruction;
import org.teavm.model.instructions.BinaryBranchingInstruction;
import org.teavm.model.instructions.BranchingInstruction;
import org.teavm.model.instructions.EmptyInstruction;
import org.teavm.model.instructions.ExitInstruction;
import org.teavm.model.instructions.InitClassInstruction;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.JumpInstruction;
import org.teavm.model.instructions.SwitchInstruction;
import org.teavm.model.util.BasicBlockMapper;
import org.teavm.model.util.InstructionTransitionExtractor;
import org.teavm.model.util.InstructionVariableMapper;
import org.teavm.model.util.ProgramUtils;

public class Inlining {
    private static final int DEFAULT_THRESHOLD = 15;
    private static final int MAX_DEPTH = 5;

    public void apply(Program program, ClassReaderSource classSource) {
        List<PlanEntry> plan = buildPlan(program, classSource, 0);
        execPlan(program, plan, 0);
    }

    private void execPlan(Program program, List<PlanEntry> plan, int offset) {
        for (PlanEntry entry : plan) {
            execPlanEntry(program, entry, offset);
        }
    }

    private void execPlanEntry(Program program, PlanEntry planEntry, int offset) {
        BasicBlock block = program.basicBlockAt(planEntry.targetBlock + offset);
        InvokeInstruction invoke = (InvokeInstruction) block.getInstructions().get(planEntry.targetInstruction);
        BasicBlock splitBlock = program.createBasicBlock();
        BasicBlock firstInlineBlock = program.createBasicBlock();
        Program inlineProgram = planEntry.program;
        for (int i = 1; i < inlineProgram.basicBlockCount(); ++i) {
            program.createBasicBlock();
        }

        int variableOffset = program.variableCount();
        for (int i = 0; i < inlineProgram.variableCount(); ++i) {
            program.createVariable();
        }

        List<Instruction> movedInstructions = block.getInstructions().subList(planEntry.targetInstruction + 1,
                block.getInstructions().size());
        List<Instruction> instructionsToMove = new ArrayList<>(movedInstructions);
        movedInstructions.clear();
        splitBlock.getInstructions().addAll(instructionsToMove);
        splitBlock.getTryCatchBlocks().addAll(ProgramUtils.copyTryCatches(block, program));

        block.getInstructions().remove(block.getInstructions().size() - 1);
        if (invoke.getInstance() == null || invoke.getMethod().getName().equals("<init>")) {
            InitClassInstruction clinit = new InitClassInstruction();
            clinit.setClassName(invoke.getMethod().getClassName());
            block.getInstructions().add(clinit);
        }
        JumpInstruction jumpToInlinedProgram = new JumpInstruction();
        jumpToInlinedProgram.setTarget(firstInlineBlock);
        block.getInstructions().add(jumpToInlinedProgram);

        for (int i = 0; i < inlineProgram.basicBlockCount(); ++i) {
            BasicBlock blockToInline = inlineProgram.basicBlockAt(i);
            BasicBlock inlineBlock = program.basicBlockAt(firstInlineBlock.getIndex() + i);
            ProgramUtils.copyBasicBlock(blockToInline, inlineBlock);
        }

        BasicBlockMapper blockMapper = new BasicBlockMapper(index -> index + firstInlineBlock.getIndex());
        InstructionVariableMapper variableMapper = new InstructionVariableMapper(var -> {
            if (var.getIndex() == 0) {
                return invoke.getInstance();
            } else if (var.getIndex() <= invoke.getArguments().size()) {
                return invoke.getArguments().get(var.getIndex() - 1);
            } else {
                return program.variableAt(var.getIndex() + variableOffset);
            }
        });

        List<Incoming> resultVariables = new ArrayList<>();
        for (int i = 0; i < inlineProgram.basicBlockCount(); ++i) {
            BasicBlock mappedBlock = program.basicBlockAt(firstInlineBlock.getIndex() + i);
            blockMapper.transform(mappedBlock);
            variableMapper.apply(mappedBlock);
            mappedBlock.getTryCatchBlocks().addAll(ProgramUtils.copyTryCatches(block, program));
            Instruction lastInsn = mappedBlock.getLastInstruction();
            if (lastInsn instanceof ExitInstruction) {
                ExitInstruction exit = (ExitInstruction) lastInsn;
                JumpInstruction exitReplacement = new JumpInstruction();
                exitReplacement.setTarget(splitBlock);
                mappedBlock.getInstructions().set(mappedBlock.getInstructions().size() - 1, exitReplacement);
                if (exit.getValueToReturn() != null) {
                    Incoming resultIncoming = new Incoming();
                    resultIncoming.setSource(mappedBlock);
                    resultIncoming.setValue(exit.getValueToReturn());
                    resultVariables.add(resultIncoming);
                }
            }
        }

        if (!resultVariables.isEmpty() && invoke.getReceiver() != null) {
            if (resultVariables.size() == 1) {
                AssignInstruction resultAssignment = new AssignInstruction();
                resultAssignment.setReceiver(invoke.getReceiver());
                resultAssignment.setAssignee(resultVariables.get(0).getValue());
                splitBlock.getInstructions().add(0, resultAssignment);
            } else {
                Phi resultPhi = new Phi();
                resultPhi.setReceiver(invoke.getReceiver());
                resultPhi.getIncomings().addAll(resultVariables);
                splitBlock.getPhis().add(resultPhi);
            }
        }

        InstructionTransitionExtractor transitionExtractor = new InstructionTransitionExtractor();
        Instruction splitLastInsn = splitBlock.getLastInstruction();
        if (splitLastInsn != null) {
            splitLastInsn.acceptVisitor(transitionExtractor);
            if (transitionExtractor.getTargets() != null) {
                List<Incoming> incomings = Arrays.stream(transitionExtractor.getTargets())
                        .flatMap(bb -> bb.getPhis().stream())
                        .flatMap(phi -> phi.getIncomings().stream())
                        .filter(incoming -> incoming.getSource() == block)
                        .collect(Collectors.toList());
                for (Incoming incoming : incomings) {
                    incoming.setSource(splitBlock);
                }
            }
        }

        execPlan(program, planEntry.innerPlan, firstInlineBlock.getIndex());
    }

    private List<PlanEntry> buildPlan(Program program, ClassReaderSource classSource, int depth) {
        if (depth >= MAX_DEPTH) {
            return Collections.emptyList();
        }
        List<PlanEntry> plan = new ArrayList<>();
        int ownComplexity = getComplexity(program);

        for (int i = program.basicBlockCount() - 1; i >= 0; --i) {
            BasicBlock block = program.basicBlockAt(i);
            if (!block.getTryCatchBlocks().isEmpty()) {
                continue;
            }
            List<Instruction> instructions = block.getInstructions();
            for (int j = instructions.size() - 1; j >= 0; --j) {
                Instruction insn = instructions.get(j);
                if (!(insn instanceof InvokeInstruction)) {
                    continue;
                }
                InvokeInstruction invoke = (InvokeInstruction) insn;
                if (invoke.getType() == InvocationType.VIRTUAL) {
                    continue;
                }

                MethodReader invokedMethod = getMethod(classSource, invoke.getMethod());
                if (invokedMethod == null || invokedMethod.getProgram() == null
                        || invokedMethod.getProgram().basicBlockCount() == 0) {
                    continue;
                }

                Program invokedProgram = ProgramUtils.copy(invokedMethod.getProgram());
                int complexityThreshold = DEFAULT_THRESHOLD - depth * 2;
                if (ownComplexity < DEFAULT_THRESHOLD) {
                    complexityThreshold += DEFAULT_THRESHOLD;
                }
                if (getComplexity(invokedProgram) > complexityThreshold) {
                    continue;
                }

                PlanEntry entry = new PlanEntry();
                entry.targetBlock = i;
                entry.targetInstruction = j;
                entry.program = invokedProgram;
                entry.innerPlan.addAll(buildPlan(invokedProgram, classSource, depth + 1));
                plan.add(entry);
            }
        }

        return plan;
    }

    private MethodReader getMethod(ClassReaderSource classSource, MethodReference methodRef) {
        ClassReader cls = classSource.get(methodRef.getClassName());
        return cls != null ? cls.getMethod(methodRef.getDescriptor()) : null;
    }

    private int getComplexity(Program program) {
        int complexity = 0;
        for (int i = 0; i < program.basicBlockCount(); ++i) {
            BasicBlock block = program.basicBlockAt(i);
            List<Instruction> instructions = block.getInstructions();
            int nopCount = (int) instructions.stream().filter(insn -> insn instanceof EmptyInstruction).count();
            int invokeCount = instructions.stream().mapToInt(insn -> {
                if (!(insn instanceof InvokeInstruction)) {
                    return 0;
                }
                InvokeInstruction invoke = (InvokeInstruction) insn;
                int count = invoke.getArguments().size();
                if (invoke.getInstance() != null) {
                    count++;
                }
                return count + 1;
            }).sum();
            complexity += instructions.size() - 1 - nopCount + invokeCount;
            Instruction lastInsn = block.getLastInstruction();
            if (lastInsn instanceof SwitchInstruction) {
                complexity += 3;
            } else if (lastInsn instanceof BinaryBranchingInstruction || lastInsn instanceof BranchingInstruction) {
                complexity += 2;
            }
        }
        return complexity;
    }

    private class PlanEntry {
        int targetBlock;
        int targetInstruction;
        Program program;
        final List<PlanEntry> innerPlan = new ArrayList<>();
    }
}
