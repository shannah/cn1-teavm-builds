/*
 *  Copyright 2017 Alexey Andreev.
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
package org.teavm.callgraph;

import java.io.Serializable;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodReference;
import org.teavm.model.TextLocation;

class SerializableCallGraph implements Serializable {
    int[] nodeIndexes;
    int[] fieldAccessIndexes;
    int[] classAccessIndexes;
    Node[] nodes;
    CallSite[] callSites;
    FieldAccess[] fieldAccessList;
    ClassAccess[] classAccessList;

    static class Node implements Serializable {
        MethodReference method;
        int[] callSites;
        int[] callerCallSites;
        int[] fieldAccessSites;
        int[] classAccessSites;
    }

    static class CallSite implements Serializable  {
        TextLocation location;
        int callee;
        int caller;
    }

    static class FieldAccess implements Serializable  {
        TextLocation location;
        int callee;
        FieldReference field;
    }

    static class ClassAccess implements Serializable  {
        TextLocation location;
        int callee;
        String className;
    }
}
