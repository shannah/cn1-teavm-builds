/*
 *  Copyright 2012 Alexey Andreev.
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
package org.teavm.javascript;

import java.util.BitSet;
import java.util.List;
import org.teavm.common.Graph;
import org.teavm.javascript.ast.AsyncMethodNode;
import org.teavm.javascript.ast.AsyncMethodPart;
import org.teavm.javascript.ast.RegularMethodNode;
import org.teavm.model.Instruction;
import org.teavm.model.Program;
import org.teavm.model.Variable;
import org.teavm.model.util.AsyncProgramSplitter;
import org.teavm.model.util.DefinitionExtractor;
import org.teavm.model.util.LivenessAnalyzer;
import org.teavm.model.util.ProgramUtils;
import org.teavm.model.util.UsageExtractor;

/**
 *
 * @author Alexey Andreev
 */
public class Optimizer {
    public void optimize(RegularMethodNode method, Program program) {
        ReadWriteStatsBuilder stats = new ReadWriteStatsBuilder(method.getVariables().size());
        stats.analyze(program);
        boolean[] preservedVars = new boolean[stats.writes.length];
        for (int i = 0; i < preservedVars.length; ++i) {
            if (stats.writes[i] != 1) {
                preservedVars[i] = true;
            }
        }
        BreakEliminator breakEliminator = new BreakEliminator();
        breakEliminator.eliminate(method.getBody());
        OptimizingVisitor optimizer = new OptimizingVisitor(preservedVars, stats.reads);
        method.getBody().acceptVisitor(optimizer);
        method.setBody(optimizer.resultStmt);
        int paramCount = method.getReference().parameterCount();
        UnusedVariableEliminator unusedEliminator = new UnusedVariableEliminator(paramCount, method.getVariables());
        method.getBody().acceptVisitor(unusedEliminator);
        method.getVariables().subList(unusedEliminator.lastIndex, method.getVariables().size()).clear();
        RedundantLabelEliminator labelEliminator = new RedundantLabelEliminator();
        method.getBody().acceptVisitor(labelEliminator);
        for (int i = 0; i < method.getVariables().size(); ++i) {
            method.getVariables().set(i, i);
        }
    }

    public void optimize(AsyncMethodNode method, AsyncProgramSplitter splitter) {
        LivenessAnalyzer liveness = new LivenessAnalyzer();
        liveness.analyze(splitter.getOriginalProgram());

        Graph cfg = ProgramUtils.buildControlFlowGraph(splitter.getOriginalProgram());

        for (int i = 0; i < splitter.size(); ++i) {
            boolean[] preservedVars = new boolean[method.getVariables().size()];
            ReadWriteStatsBuilder stats = new ReadWriteStatsBuilder(method.getVariables().size());
            stats.analyze(splitter.getProgram(i));
            for (int j = 0; j < stats.writes.length; ++j) {
                if (stats.writes[j] != 1 && stats.reads[j] > 0) {
                    preservedVars[j] = true;
                }
            }

            AsyncMethodPart part = method.getBody().get(i);
            BreakEliminator breakEliminator = new BreakEliminator();
            breakEliminator.eliminate(part.getStatement());
            findEscapingLiveVars(liveness, cfg, splitter, i, preservedVars);
            OptimizingVisitor optimizer = new OptimizingVisitor(preservedVars, stats.reads);
            part.getStatement().acceptVisitor(optimizer);
            part.setStatement(optimizer.resultStmt);
        }
        int paramCount = method.getReference().parameterCount();
        UnusedVariableEliminator unusedEliminator = new UnusedVariableEliminator(paramCount, method.getVariables());
        for (AsyncMethodPart part : method.getBody()) {
            part.getStatement().acceptVisitor(unusedEliminator);
        }
        method.getVariables().subList(unusedEliminator.lastIndex, method.getVariables().size()).clear();
        RedundantLabelEliminator labelEliminator = new RedundantLabelEliminator();
        for (AsyncMethodPart part : method.getBody()) {
            part.getStatement().acceptVisitor(labelEliminator);
        }
        for (int i = 0; i < method.getVariables().size(); ++i) {
            method.getVariables().set(i, i);
        }
    }

    private void findEscapingLiveVars(LivenessAnalyzer liveness, Graph cfg, AsyncProgramSplitter splitter,
            int partIndex, boolean[] output) {
        Program originalProgram = splitter.getOriginalProgram();
        Program program = splitter.getProgram(partIndex);
        int[] successors = splitter.getBlockSuccessors(partIndex);
        int[] splitPoints = splitter.getSplitPoints(partIndex);
        int[] originalBlocks = splitter.getOriginalBlocks(partIndex);

        for (int i = 0; i < program.basicBlockCount(); ++i) {
            if (successors[i] < 0 || originalBlocks[i] < 0) {
                continue;
            }

            // Determine live-out vars
            BitSet liveVars = new BitSet();
            for (int succ : cfg.outgoingEdges(originalBlocks[i])) {
                liveVars.or(liveness.liveIn(succ));
            }

            // Remove from live set all variables that are defined in these blocks
            DefinitionExtractor defExtractor = new DefinitionExtractor();
            UsageExtractor useExtractor = new UsageExtractor();
            List<Instruction> instructions = originalProgram.basicBlockAt(originalBlocks[i]).getInstructions();
            int splitPoint = splitPoints[i];
            for (int j = instructions.size() - 1; j >= splitPoint; --j) {
                instructions.get(j).acceptVisitor(defExtractor);
                instructions.get(j).acceptVisitor(useExtractor);
                for (Variable var : defExtractor.getDefinedVariables()) {
                    liveVars.clear(var.getIndex());
                }
                for (Variable var : useExtractor.getUsedVariables()) {
                    liveVars.set(var.getIndex());
                }
            }

            // Add live variables to output
            for (int j = liveVars.nextSetBit(0); j >= 0; j = liveVars.nextSetBit(j + 1)) {
                output[j] = true;
            }
        }
    }
}
