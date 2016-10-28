/*
 *  Copyright 2016 Alexey Andreev.
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
package org.teavm.model.classes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ElementModifier;
import org.teavm.model.ListableClassReaderSource;

public class TagRegistry {
    private Map<String, List<Range>> ranges = new HashMap<>();

    public TagRegistry(ListableClassReaderSource classSource) {
        List<String> roots = new ArrayList<>();
        Map<String, Set<String>> implementedBy = new HashMap<>();

        Map<String, List<String>> hierarchy = new HashMap<>();
        for (String className : classSource.getClassNames()) {
            ClassReader cls = classSource.get(className);
            if (cls == null || cls.hasModifier(ElementModifier.INTERFACE)) {
                continue;
            }
            for (String iface : cls.getInterfaces()) {
                String topmostImplementor = findTopmostImplementor(classSource, className, iface);
                markImplementor(classSource, topmostImplementor, iface, implementedBy);
            }
            if (cls.getParent() == null || cls.getParent().equals(cls.getName())) {
                roots.add(className);
            } else {
                hierarchy.computeIfAbsent(cls.getParent(), key -> new ArrayList<>()).add(className);
            }
        }

        Map<String, Range> simpleRanges = new HashMap<>();
        int current = 0;
        for (String root : roots) {
            assignRange(current, hierarchy, root, simpleRanges);
            ++current;
        }

        for (String className : classSource.getClassNames()) {
            ClassReader cls = classSource.get(className);
            if (cls == null) {
                continue;
            }
            if (cls.hasModifier(ElementModifier.INTERFACE)) {
                Set<String> implementorRoots = implementedBy.get(cls.getName());
                if (implementorRoots != null) {
                    List<Range> ifaceRanges = implementorRoots.stream()
                            .map(implementor -> simpleRanges.get(implementor))
                            .filter(Objects::nonNull)
                            .sorted(Comparator.comparing(range -> range.lower))
                            .collect(Collectors.toList());
                    if (!ifaceRanges.isEmpty()) {
                        ranges.put(className, ifaceRanges);
                    }
                }
            } else {
                Range simpleRange = simpleRanges.get(className);
                if (simpleRange != null) {
                    ranges.put(className, new ArrayList<>(Arrays.asList(simpleRange)));
                }
            }
        }
    }

    private String findTopmostImplementor(ClassReaderSource classSource, String className, String ifaceName) {
        ClassReader cls = classSource.get(className);
        if (cls == null) {
            return null;
        }

        if (cls.getParent() != null && !cls.getParent().equals(cls.getName())) {
            String candidate = findTopmostImplementor(classSource, cls.getParent(), ifaceName);
            if (candidate != null) {
                return candidate;
            }
        }

        return cls.getInterfaces().contains(ifaceName) ? className : null;
    }

    private void markImplementor(ClassReaderSource classSource, String className, String ifaceName,
            Map<String, Set<String>> implementedBy) {
        if (!implementedBy.computeIfAbsent(ifaceName, key -> new HashSet<>()).add(className)) {
            return;
        }

        ClassReader iface = classSource.get(ifaceName);
        if (iface == null) {
            return;
        }

        for (String superIface : iface.getInterfaces()) {
            markImplementor(classSource, className, superIface, implementedBy);
        }
    }

    private int assignRange(int start, Map<String, List<String>> hierarhy, String className,
            Map<String, Range> ranges) {
        int end = start + 1;
        for (String childClass : hierarhy.getOrDefault(className, Collections.emptyList())) {
            end = assignRange(end, hierarhy, childClass, ranges);
        }
        ++end;
        ranges.put(className, new Range(start, end));
        return end;
    }

    public List<Range> getRanges(String className) {
        return new ArrayList<>(ranges.getOrDefault(className, Collections.emptyList()));
    }

    public static class Range {
        public int lower;
        public int upper;

        private Range(int lower, int upper) {
            this.lower = lower;
            this.upper = upper;
        }
    }
}
