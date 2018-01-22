/*
 *  Copyright 2013 Alexey Andreev.
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
package org.teavm.model.instructions;

import org.teavm.model.Instruction;
import org.teavm.model.Variable;

public class CastNumberInstruction extends Instruction {
    private Variable value;
    private Variable receiver;
    private NumericOperandType sourceType;
    private NumericOperandType targetType;

    public CastNumberInstruction(NumericOperandType sourceType, NumericOperandType targetType) {
        super();
        this.sourceType = sourceType;
        this.targetType = targetType;
    }

    public Variable getValue() {
        return value;
    }

    public void setValue(Variable value) {
        this.value = value;
    }

    public Variable getReceiver() {
        return receiver;
    }

    public void setReceiver(Variable receiver) {
        this.receiver = receiver;
    }

    public NumericOperandType getSourceType() {
        return sourceType;
    }

    public NumericOperandType getTargetType() {
        return targetType;
    }

    @Override
    public void acceptVisitor(InstructionVisitor visitor) {
        visitor.visit(this);
    }
}
