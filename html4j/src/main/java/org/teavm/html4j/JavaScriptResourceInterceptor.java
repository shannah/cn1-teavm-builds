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
package org.teavm.html4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import net.java.html.js.JavaScriptResource;
import org.apache.commons.io.IOUtils;
import org.teavm.javascript.RenderingContext;
import org.teavm.javascript.RenderingException;
import org.teavm.model.AnnotationReader;
import org.teavm.model.ClassReader;
import org.teavm.vm.BuildTarget;
import org.teavm.vm.spi.AbstractRendererListener;

/**
 *
 * @author Alexey Andreev
 */
public class JavaScriptResourceInterceptor extends AbstractRendererListener {
    @Override
    public void begin(RenderingContext context, BuildTarget buildTarget) throws IOException {
        boolean hasOneResource = false;
        for (String className : context.getClassSource().getClassNames()) {
            ClassReader cls = context.getClassSource().get(className);
            AnnotationReader annot = cls.getAnnotations().get(JavaScriptResource.class.getName());
            if (annot == null) {
                continue;
            }
            String path = annot.getValue("value").getString();
            String packageName = className.substring(0, className.lastIndexOf('.'));
            String resourceName = packageName.replace('.', '/') + "/" + path;
            try (InputStream input = context.getClassLoader().getResourceAsStream(resourceName)) {
                if (input == null) {
                    throw new RenderingException("Error processing JavaScriptResource annotation on class "
                            + className + ". Resource not found: " + resourceName);
                }
                StringWriter writer = new StringWriter();
                IOUtils.copy(input, writer);
                writer.close();
                context.getWriter().append("// Resource " + path + " included by " + className).newLine();
                context.getWriter().append(writer.toString()).newLine().newLine();
            }
            hasOneResource = true;
        }
        if (hasOneResource) {
            context.getWriter().append("// TeaVM generated classes").newLine();
        }
    }
}
