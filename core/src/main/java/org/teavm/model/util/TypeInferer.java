/*
 *  Copyright 2015 Alexey Andreev.
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
package org.teavm.model.util;

import java.util.List;
import org.teavm.common.Graph;
import org.teavm.common.GraphBuilder;
import org.teavm.common.IntegerStack;
import org.teavm.model.*;
import org.teavm.model.instructions.*;

/**
 *
 * @author Alexey Andreev
 */
public class TypeInferer {
    VariableType[] types;
    GraphBuilder builder;
    GraphBuilder arrayElemBuilder;

    public void inferTypes(ProgramReader program, MethodReference method) {
        int sz = Math.max(method.parameterCount(), program.variableCount());
        types = new VariableType[sz];

        types[0] = VariableType.OBJECT;
        for (int i = 0; i < method.parameterCount(); ++i) {
            ValueType param = method.parameterType(i);
            types[i + 1] = convert(param);
        }

        builder = new GraphBuilder(sz);
        arrayElemBuilder = new GraphBuilder(sz);
        for (int i = 0; i < program.basicBlockCount(); ++i) {
            BasicBlockReader block = program.basicBlockAt(i);
            block.readAllInstructions(reader);
            for (PhiReader phi : block.readPhis()) {
                for (IncomingReader incoming : phi.readIncomings()) {
                    builder.addEdge(incoming.getValue().getIndex(), phi.getReceiver().getIndex());
                }
            }
            for (TryCatchBlockReader tryCatch : block.readTryCatchBlocks()) {
                if (tryCatch.getExceptionVariable() != null) {
                    types[tryCatch.getExceptionVariable().getIndex()] = VariableType.OBJECT;
                }
            }
        }

        IntegerStack stack = new IntegerStack(sz);
        Graph graph = builder.build();
        Graph arrayElemGraph = builder.build();
        for (int i = 0; i < sz; ++i) {
            if ((i >= graph.size() || graph.incomingEdgesCount(i) == 0)
                    && (i >= arrayElemGraph.size() || arrayElemGraph.incomingEdgesCount(i) == 0)) {
                stack.push(i);
            }
        }

        boolean[] visited = new boolean[sz];
        while (!stack.isEmpty()) {
            int node = stack.pop();
            if (visited[node]) {
                continue;
            }
            visited[node] = true;
            if (types[node] == null) {
                for (int pred : graph.incomingEdges(node)) {
                    if (types[pred] != null) {
                        types[node] = types[pred];
                        break;
                    }
                }
            }
            if (types[node] == null) {
                for (int pred : arrayElemGraph.incomingEdges(node)) {
                    if (types[pred] != null) {
                        types[node] = convertFromArray(types[pred]);
                        break;
                    }
                }
            }
            for (int succ : graph.outgoingEdges(node)) {
                if (!visited[succ]) {
                    stack.push(succ);
                }
            }
            for (int succ : arrayElemGraph.outgoingEdges(node)) {
                if (!visited[succ]) {
                    stack.push(succ);
                }
            }
        }
    }

    public VariableType typeOf(int variableIndex) {
        return types[variableIndex];
    }

    VariableType convert(ValueType type) {
        if (type instanceof ValueType.Primitive) {
            switch (((ValueType.Primitive) type).getKind()) {
                case BOOLEAN:
                case BYTE:
                case SHORT:
                case CHARACTER:
                case INTEGER:
                    return VariableType.INT;
                case FLOAT:
                    return VariableType.FLOAT;
                case DOUBLE:
                    return VariableType.DOUBLE;
                case LONG:
                    return VariableType.LONG;
            }
        } else if (type instanceof ValueType.Array) {
            ValueType item = ((ValueType.Array) type).getItemType();
            return convertArray(item);
        }
        return VariableType.OBJECT;
    }

    VariableType convertFromArray(VariableType type) {
        switch (type) {
            case BYTE_ARRAY:
            case SHORT_ARRAY:
            case CHAR_ARRAY:
            case INT_ARRAY:
                return VariableType.INT;
            case LONG_ARRAY:
                return VariableType.LONG;
            case FLOAT_ARRAY:
                return VariableType.FLOAT;
            case DOUBLE_ARRAY:
                return VariableType.DOUBLE;
            default:
                return VariableType.OBJECT;
        }
    }

    VariableType convert(ArrayElementType type) {
        switch (type) {
            case BYTE:
                return VariableType.BYTE_ARRAY;
            case CHAR:
                return VariableType.CHAR_ARRAY;
            case SHORT:
                return VariableType.SHORT_ARRAY;
            case INT:
                return VariableType.INT_ARRAY;
            case LONG:
                return VariableType.LONG_ARRAY;
            case FLOAT:
                return VariableType.FLOAT_ARRAY;
            case DOUBLE:
                return VariableType.DOUBLE_ARRAY;
            case OBJECT:
                return VariableType.OBJECT_ARRAY;
            default:
                throw new AssertionError();
        }
    }

    VariableType convert(NumericOperandType type) {
        switch (type) {
            case INT:
                return VariableType.INT;
            case LONG:
                return VariableType.LONG;
            case FLOAT:
                return VariableType.FLOAT;
            case DOUBLE:
                return VariableType.DOUBLE;
            default:
                throw new AssertionError();
        }
    }

    VariableType convertArray(ValueType type) {
        if (type instanceof ValueType.Primitive) {
            switch (((ValueType.Primitive) type).getKind()) {
                case BOOLEAN:
                case BYTE:
                    return VariableType.BYTE_ARRAY;
                case SHORT:
                    return VariableType.SHORT_ARRAY;
                case CHARACTER:
                    return VariableType.CHAR_ARRAY;
                case INTEGER:
                    return VariableType.INT_ARRAY;
                case FLOAT:
                    return VariableType.FLOAT_ARRAY;
                case DOUBLE:
                    return VariableType.DOUBLE_ARRAY;
                case LONG:
                    return VariableType.LONG_ARRAY;
            }
        }
        return VariableType.OBJECT_ARRAY;
    }

    InstructionReader reader = new InstructionReader() {
        @Override
        public void unwrapArray(VariableReader receiver, VariableReader array, ArrayElementType elementType) {
            types[receiver.getIndex()] = convert(elementType);
        }

        @Override
        public void stringConstant(VariableReader receiver, String cst) {
            types[receiver.getIndex()] = VariableType.OBJECT;
        }

        @Override
        public void raise(VariableReader exception) {
        }

        @Override
        public void putField(VariableReader instance, FieldReference field, VariableReader value, ValueType fieldType) {
        }

        @Override
        public void putElement(VariableReader array, VariableReader index, VariableReader value) {
        }

        @Override
        public void nullConstant(VariableReader receiver) {
            types[receiver.getIndex()] = VariableType.OBJECT;
        }

        @Override
        public void nullCheck(VariableReader receiver, VariableReader value) {
            builder.addEdge(value.getIndex(), receiver.getIndex());
        }

        @Override
        public void nop() {
        }

        @Override
        public void negate(VariableReader receiver, VariableReader operand, NumericOperandType type) {
            types[receiver.getIndex()] = convert(type);
        }

        @Override
        public void monitorExit(VariableReader objectRef) {
        }

        @Override
        public void monitorEnter(VariableReader objectRef) {
        }

        @Override
        public void longConstant(VariableReader receiver, long cst) {
            types[receiver.getIndex()] = VariableType.LONG;
        }

        @Override
        public void location(InstructionLocation location) {
        }

        @Override
        public void jumpIf(BinaryBranchingCondition cond, VariableReader first, VariableReader second,
                BasicBlockReader consequent, BasicBlockReader alternative) {
        }

        @Override
        public void jumpIf(BranchingCondition cond, VariableReader operand, BasicBlockReader consequent,
                BasicBlockReader alternative) {
        }

        @Override
        public void jump(BasicBlockReader target) {
        }

        @Override
        public void isInstance(VariableReader receiver, VariableReader value, ValueType type) {
            types[receiver.getIndex()] = VariableType.INT;
        }

        @Override
        public void invoke(VariableReader receiver, VariableReader instance, MethodReference method,
                List<? extends VariableReader> arguments, InvocationType type) {
            if (receiver != null) {
                types[receiver.getIndex()] = convert(method.getReturnType());
            }
        }

        @Override
        public void invokeDynamic(VariableReader receiver, VariableReader instance, MethodDescriptor method,
                List<? extends VariableReader> arguments, MethodHandle bootstrapMethod,
                List<RuntimeConstant> bootstrapArguments) {
            if (receiver != null) {
                types[receiver.getIndex()] = convert(method.getResultType());
            }
        }

        @Override
        public void integerConstant(VariableReader receiver, int cst) {
            types[receiver.getIndex()] = VariableType.INT;
        }

        @Override
        public void initClass(String className) {
        }

        @Override
        public void getField(VariableReader receiver, VariableReader instance, FieldReference field,
                ValueType fieldType) {
            types[receiver.getIndex()] = convert(fieldType);
        }

        @Override
        public void getElement(VariableReader receiver, VariableReader array, VariableReader index) {
            arrayElemBuilder.addEdge(array.getIndex(), receiver.getIndex());
        }

        @Override
        public void floatConstant(VariableReader receiver, float cst) {
            types[receiver.getIndex()] = VariableType.FLOAT;
        }

        @Override
        public void exit(VariableReader valueToReturn) {
        }

        @Override
        public void doubleConstant(VariableReader receiver, double cst) {
            types[receiver.getIndex()] = VariableType.DOUBLE;
        }

        @Override
        public void createArray(VariableReader receiver, ValueType itemType,
                List<? extends VariableReader> dimensions) {
            types[receiver.getIndex()] = convert(ValueType.arrayOf(itemType));
        }

        @Override
        public void createArray(VariableReader receiver, ValueType itemType, VariableReader size) {
            types[receiver.getIndex()] = convert(ValueType.arrayOf(itemType));
        }

        @Override
        public void create(VariableReader receiver, String type) {
            types[receiver.getIndex()] = VariableType.OBJECT;
        }

        @Override
        public void cloneArray(VariableReader receiver, VariableReader array) {
            builder.addEdge(array.getIndex(), receiver.getIndex());
        }

        @Override
        public void classConstant(VariableReader receiver, ValueType cst) {
            types[receiver.getIndex()] = VariableType.OBJECT;
        }

        @Override
        public void choose(VariableReader condition, List<? extends SwitchTableEntryReader> table,
                BasicBlockReader defaultTarget) {
        }

        @Override
        public void cast(VariableReader receiver, VariableReader value, IntegerSubtype type,
                CastIntegerDirection targetType) {
            types[receiver.getIndex()] = VariableType.INT;
        }

        @Override
        public void cast(VariableReader receiver, VariableReader value, NumericOperandType sourceType,
                NumericOperandType targetType) {
            types[receiver.getIndex()] = convert(targetType);
        }

        @Override
        public void cast(VariableReader receiver, VariableReader value, ValueType targetType) {
            types[receiver.getIndex()] = convert(targetType);
        }

        @Override
        public void binary(BinaryOperation op, VariableReader receiver, VariableReader first, VariableReader second,
                NumericOperandType type) {
            types[receiver.getIndex()] = convert(type);
        }

        @Override
        public void assign(VariableReader receiver, VariableReader assignee) {
            builder.addEdge(assignee.getIndex(), receiver.getIndex());
        }

        @Override
        public void arrayLength(VariableReader receiver, VariableReader array) {
            types[receiver.getIndex()] = VariableType.INT;
        }
    };
}
