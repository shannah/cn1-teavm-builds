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
package org.teavm.codegen;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReference;

/**
 *
 * @author Alexey Andreev
 */
public class MinifyingAliasProvider implements AliasProvider {
    private static Set<String> keywords = new HashSet<>(Arrays.asList("do", "if", "else", "for", "case",
            "goto", "in", "let", "new", "this", "try", "var", "void", "with"));
    private static String startLetters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static String letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static String startVirtualLetters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private int lastSuffix;
    private int lastVirtual;
    private Set<String> usedAliases = new HashSet<>();

    @Override
    public String getAlias(FieldReference field) {
        String result;
        do {
            result = getNewAlias(lastVirtual++, startVirtualLetters);
        } while (!usedAliases.add(result) || keywords.contains(result));
        return result;
    }

    @Override
    public String getAlias(MethodReference method) {
        return getNewAlias(lastSuffix++, startLetters);
    }

    @Override
    public String getAlias(MethodDescriptor method) {
        String result;
        do {
            result = getNewAlias(lastVirtual++, startVirtualLetters);
        } while (!usedAliases.add(result) || keywords.contains(result));
        return result;
    }

    @Override
    public String getAlias(String className) {
        return getNewAlias(lastSuffix++, startLetters);
    }

    @Override
    public String getFunctionAlias(String className) {
        return getNewAlias(lastSuffix++, startLetters);
    }

    private String getNewAlias(int index, String startLetters) {
        StringBuilder sb = new StringBuilder();
        sb.append(startLetters.charAt(index % startLetters.length()));
        index /= startLetters.length();
        while (index > 0) {
            sb.append(letters.charAt(index % letters.length()));
            index /= letters.length();
        }
        return sb.toString();
    }
}
