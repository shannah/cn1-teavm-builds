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
package org.teavm.javascript.ast;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.teavm.model.MethodReference;

/**
 *
 * @author Alexey Andreev
 */
public class RegularMethodNode extends MethodNode {
    private Statement body;
    private List<Integer> variables = new ArrayList<>();
    private List<Set<String>> parameterDebugNames = new ArrayList<>();

    public RegularMethodNode(MethodReference reference) {
        super(reference);
    }

    public Statement getBody() {
        return body;
    }

    public void setBody(Statement body) {
        this.body = body;
    }

    public List<Integer> getVariables() {
        return variables;
    }

    @Override
    public List<Set<String>> getParameterDebugNames() {
        return parameterDebugNames;
    }

    @Override
    public void acceptVisitor(MethodNodeVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public boolean isAsync() {
        return false;
    }
}
