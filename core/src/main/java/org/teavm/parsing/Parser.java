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
package org.teavm.parsing;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.JSRInlinerAdapter;
import org.objectweb.asm.tree.*;
import org.teavm.model.*;
import org.teavm.optimization.UnreachableBasicBlockEliminator;

/**
 *
 * @author Alexey Andreev
 */
public final class Parser {
    private Parser() {
    }

    public static MethodHolder parseMethod(MethodNode node, String className, String fileName) {
        MethodNode nodeWithoutJsr = new MethodNode(Opcodes.ASM5, node.access, node.name, node.desc, node.signature,
                node.exceptions.toArray(new String[0]));
        JSRInlinerAdapter adapter = new JSRInlinerAdapter(nodeWithoutJsr, node.access, node.name, node.desc,
                node.signature, node.exceptions.toArray(new String[0]));
        node.accept(adapter);
        node = nodeWithoutJsr;
        ValueType[] signature = MethodDescriptor.parseSignature(node.desc);
        MethodHolder method = new MethodHolder(node.name, signature);
        parseModifiers(node.access, method);
        ProgramParser programParser = new ProgramParser();
        programParser.setFileName(fileName);
        Program program = programParser.parse(node, className);
        new UnreachableBasicBlockEliminator().optimize(program);
        SSATransformer ssaProducer = new SSATransformer();
        ssaProducer.transformToSSA(program, programParser, method.getParameterTypes());
        method.setProgram(program);
        parseAnnotations(method.getAnnotations(), node.visibleAnnotations, node.invisibleAnnotations);
        while (program.variableCount() <= method.parameterCount()) {
            program.createVariable();
        }
        if (node.annotationDefault != null) {
            method.setAnnotationDefault(parseAnnotationValue(node.annotationDefault));
        }
        for (int i = 0; i < method.parameterCount(); ++i) {
            parseAnnotations(method.parameterAnnotation(i),
                    node.visibleParameterAnnotations != null ? node.visibleParameterAnnotations[i] : null,
                    node.invisibleParameterAnnotations != null ? node.invisibleParameterAnnotations[i] : null);
        }
        return method;
    }

    public static ClassHolder parseClass(ClassNode node) {
        ClassHolder cls = new ClassHolder(node.name.replace('/', '.'));
        parseModifiers(node.access, cls);
        if (node.superName != null) {
            cls.setParent(node.superName.replace('/', '.'));
        }
        if (cls.getName().equals("java.lang.Object")) {
            cls.setParent(null);
        }
        if (node.interfaces != null) {
            for (String iface : node.interfaces) {
                cls.getInterfaces().add(iface.replace('/', '.'));
            }
        }
        for (Object obj : node.fields) {
            FieldNode fieldNode = (FieldNode) obj;
            cls.addField(parseField(fieldNode));
        }
        String fullFileName = node.name.substring(0, node.name.lastIndexOf('/') + 1) + node.sourceFile;
        for (MethodNode methodNode : node.methods) {
            cls.addMethod(parseMethod(methodNode, node.name, fullFileName));
        }
        if (node.outerClass != null) {
            cls.setOwnerName(node.outerClass.replace('/', '.'));
        } else {
            int lastIndex = node.name.lastIndexOf('$');
            if (lastIndex != -1) {
                cls.setOwnerName(node.name.substring(0, lastIndex).replace('/', '.'));
            }
        }
        parseAnnotations(cls.getAnnotations(), node.visibleAnnotations, node.invisibleAnnotations);
        return cls;
    }

    public static FieldHolder parseField(FieldNode node) {
        FieldHolder field = new FieldHolder(node.name);
        field.setType(ValueType.parse(node.desc));
        field.setInitialValue(node.value);
        parseModifiers(node.access, field);
        parseAnnotations(field.getAnnotations(), node.visibleAnnotations, node.invisibleAnnotations);
        return field;
    }

    public static void parseModifiers(int access, ElementHolder member) {
        if ((access & Opcodes.ACC_PRIVATE) != 0) {
            member.setLevel(AccessLevel.PRIVATE);
        } else if ((access & Opcodes.ACC_PROTECTED) != 0) {
            member.setLevel(AccessLevel.PROTECTED);
        } else if ((access & Opcodes.ACC_PUBLIC) != 0) {
            member.setLevel(AccessLevel.PUBLIC);
        }

        if ((access & Opcodes.ACC_ABSTRACT) != 0) {
            member.getModifiers().add(ElementModifier.ABSTRACT);
        }
        if ((access & Opcodes.ACC_ANNOTATION) != 0) {
            member.getModifiers().add(ElementModifier.ANNOTATION);
        }
        if ((access & Opcodes.ACC_BRIDGE) != 0) {
            member.getModifiers().add(ElementModifier.BRIDGE);
        }
        if ((access & Opcodes.ACC_DEPRECATED) != 0) {
            member.getModifiers().add(ElementModifier.DEPRECATED);
        }
        if ((access & Opcodes.ACC_ENUM) != 0) {
            member.getModifiers().add(ElementModifier.ENUM);
        }
        if ((access & Opcodes.ACC_FINAL) != 0) {
            member.getModifiers().add(ElementModifier.FINAL);
        }
        if ((access & Opcodes.ACC_INTERFACE) != 0) {
            member.getModifiers().add(ElementModifier.INTERFACE);
        }
        if ((access & Opcodes.ACC_NATIVE) != 0) {
            member.getModifiers().add(ElementModifier.NATIVE);
        }
        if ((access & Opcodes.ACC_STATIC) != 0) {
            member.getModifiers().add(ElementModifier.STATIC);
        }
        if ((access & Opcodes.ACC_STRICT) != 0) {
            member.getModifiers().add(ElementModifier.STRICT);
        }
        if ((access & Opcodes.ACC_SUPER) != 0) {
            member.getModifiers().add(ElementModifier.SUPER);
        }
        if ((access & Opcodes.ACC_SYNCHRONIZED) != 0) {
            member.getModifiers().add(ElementModifier.SYNCHRONIZED);
        }
        if ((access & Opcodes.ACC_SYNTHETIC) != 0) {
            member.getModifiers().add(ElementModifier.SYNTHETIC);
        }
        if ((access & Opcodes.ACC_TRANSIENT) != 0) {
            member.getModifiers().add(ElementModifier.TRANSIENT);
        }
        if ((access & Opcodes.ACC_VARARGS) != 0) {
            member.getModifiers().add(ElementModifier.VARARGS);
        }
        if ((access & Opcodes.ACC_VOLATILE) != 0) {
            member.getModifiers().add(ElementModifier.VOLATILE);
        }
    }

    private static void parseAnnotations(AnnotationContainer annotations, List<AnnotationNode> visibleAnnotations,
            List<AnnotationNode> invisibleAnnotations) {
        List<Object> annotNodes = new ArrayList<>();
        if (visibleAnnotations != null) {
            annotNodes.addAll(visibleAnnotations);
        }
        if (invisibleAnnotations != null) {
            annotNodes.addAll(invisibleAnnotations);
        }
        for (Object obj : annotNodes) {
            AnnotationNode annotNode = (AnnotationNode) obj;
            String desc = annotNode.desc;
            if (desc.startsWith("L") && desc.endsWith(";")) {
                desc = desc.substring(1, desc.length() - 1);
            }
            desc = desc.replace('/', '.');
            AnnotationHolder annot = new AnnotationHolder(desc);
            parseAnnotationValues(annot, annotNode.values);
            annotations.add(annot);
        }
    }

    private static void parseAnnotationValues(AnnotationHolder annot, List<Object> values) {
        if (values == null) {
            return;
        }
        for (int i = 0; i < values.size(); i += 2) {
            String key = (String) values.get(i);
            Object value = values.get(i + 1);
            annot.getValues().put(key, parseAnnotationValue(value));
        }
    }

    private static AnnotationValue parseAnnotationValue(Object value) {
        if (value instanceof String[]) {
            String[] enumInfo = (String[]) value;
            ValueType.Object object = (ValueType.Object) ValueType.parse(enumInfo[0]);
            return new AnnotationValue(new FieldReference(object.getClassName(), enumInfo[1]));
        } else if (value instanceof Type) {
            Type cls = (Type) value;
            return new AnnotationValue(ValueType.parse(cls.getDescriptor()));
        } else if (value instanceof List<?>) {
            List<?> originalList = (List<?>) value;
            List<AnnotationValue> resultList = new ArrayList<>();
            for (Object item : originalList) {
                resultList.add(parseAnnotationValue(item));
            }
            return new AnnotationValue(resultList);
        } else if (value instanceof AnnotationNode) {
            AnnotationNode annotNode = (AnnotationNode) value;
            ValueType.Object object = (ValueType.Object) ValueType.parse(annotNode.desc);
            AnnotationHolder annotation = new AnnotationHolder(object.getClassName());
            parseAnnotationValues(annotation, annotNode.values);
            return new AnnotationValue(annotation);
        } else if (value instanceof String) {
            return new AnnotationValue((String) value);
        } else if (value instanceof Boolean) {
            return new AnnotationValue((Boolean) value);
        } else if (value instanceof Byte) {
            return new AnnotationValue((Byte) value);
        } else if (value instanceof Short) {
            return new AnnotationValue((Short) value);
        } else if (value instanceof Integer) {
            return new AnnotationValue((Integer) value);
        } else if (value instanceof Long) {
            return new AnnotationValue((Long) value);
        } else if (value instanceof Float) {
            return new AnnotationValue((Float) value);
        } else if (value instanceof Double) {
            return new AnnotationValue((Double) value);
        } else if (value.getClass().isArray()) {
            List<AnnotationValue> resultList = new ArrayList<>();
            int size = Array.getLength(value);
            for (int i = 0; i < size; ++i) {
                Object item = Array.get(value, i);
                resultList.add(parseAnnotationValue(item));
            }
            return new AnnotationValue(resultList);
        } else {
            throw new AssertionError();
        }
    }
}
