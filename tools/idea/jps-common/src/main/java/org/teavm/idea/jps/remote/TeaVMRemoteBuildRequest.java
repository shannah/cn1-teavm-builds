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
package org.teavm.idea.jps.remote;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.teavm.tooling.TeaVMTargetType;

public class TeaVMRemoteBuildRequest implements Serializable {
    public final List<String> sourceDirectories = new ArrayList<>();
    public final List<String> sourceJarFiles = new ArrayList<>();
    public final List<String> classPath = new ArrayList<>();
    public TeaVMTargetType targetType;
    public String mainClass;
    public String targetDirectory;
    public boolean sourceMapsFileGenerated;
    public boolean debugInformationGenerated;
    public boolean sourceFilesCopied;
    public boolean incremental;
    public Properties properties;
}
