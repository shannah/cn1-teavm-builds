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
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 *
 * @author Alexey Andreev
 */
public class ClassNode {
    private String name;
    private String parentName;
    private Set<NodeModifier> modifiers = EnumSet.noneOf(NodeModifier.class);
    private List<FieldNode> fields = new ArrayList<>();
    private List<MethodNode> methods = new ArrayList<>();
    private List<String> interfaces = new ArrayList<>();

    public ClassNode(String name, String parentName) {
        this.name = name;
        this.parentName = parentName;
    }

    public String getName() {
        return name;
    }

    public String getParentName() {
        return parentName;
    }

    public List<FieldNode> getFields() {
        return fields;
    }

    public List<MethodNode> getMethods() {
        return methods;
    }

    public List<String> getInterfaces() {
        return interfaces;
    }

    public Set<NodeModifier> getModifiers() {
        return modifiers;
    }
}
