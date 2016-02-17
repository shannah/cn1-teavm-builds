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
package org.teavm.javascript;

import java.util.Properties;
import org.teavm.codegen.NamingStrategy;
import org.teavm.codegen.SourceWriter;
import org.teavm.common.ServiceRepository;
import org.teavm.model.ListableClassReaderSource;

/**
 *
 * @author Alexey Andreev
 */
public interface RenderingContext extends ServiceRepository {
    NamingStrategy getNaming();

    SourceWriter getWriter();

    boolean isMinifying();

    ListableClassReaderSource getClassSource();

    ClassLoader getClassLoader();

    Properties getProperties();
}
