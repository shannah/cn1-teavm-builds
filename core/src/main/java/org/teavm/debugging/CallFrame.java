/*
 *  Copyright 2014 Alexey Andreev.
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
package org.teavm.debugging;

import java.util.Collections;
import java.util.Map;
import org.teavm.debugging.information.SourceLocation;
import org.teavm.debugging.javascript.JavaScriptCallFrame;
import org.teavm.debugging.javascript.JavaScriptLocation;
import org.teavm.model.MethodReference;

public class CallFrame {
    private Debugger debugger;
    private JavaScriptCallFrame originalCallFrame;
    private SourceLocation location;
    private MethodReference method;
    private Map<String, Variable> variables;

    CallFrame(Debugger debugger, JavaScriptCallFrame originalFrame, SourceLocation location, MethodReference method,
            Map<String, Variable> variables) {
        this.debugger = debugger;
        this.originalCallFrame = originalFrame;
        this.location = location;
        this.method = method;
        this.variables = Collections.unmodifiableMap(variables);
    }

    public Debugger getDebugger() {
        return debugger;
    }

    public JavaScriptLocation getOriginalLocation() {
        return originalCallFrame.getLocation();
    }

    public JavaScriptCallFrame getOriginalCallFrame() {
        return originalCallFrame;
    }

    public SourceLocation getLocation() {
        return location;
    }

    public MethodReference getMethod() {
        return method;
    }

    public Map<String, Variable> getVariables() {
        return variables;
    }
}
