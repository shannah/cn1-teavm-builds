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

/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/**
 * @author Nikolay A. Kuznetsov
 */
package org.teavm.classlib.java.util.regex;

import java.util.ArrayList;

/**
 * Positive lookbehind node.
 *
 * @author Nikolay A. Kuznetsov
 */
class TPositiveLookBehind extends TAtomicJointSet {

    public TPositiveLookBehind(ArrayList<TAbstractSet> children, TFSet fSet) {
        super(children, fSet);
    }

    /**
     * Returns stringIndex+shift, the next position to match
     */
    @Override
    public int matches(int stringIndex, CharSequence testString, TMatchResultImpl matchResult) {

        int size = children.size();
        int leftBound = matchResult.hasTransparentBounds() ? 0 : matchResult.getLeftBound();

        int shift = next.matches(stringIndex, testString, matchResult);
        if (shift >= 0) {
            // fSet will take this index to check if we at the right bound
            // and return true if the current index equal to this one
            matchResult.setConsumed(groupIndex, stringIndex);
            for (int i = 0; i < size; i++) {
                TAbstractSet e = children.get(i);
                // find limits could be calculated though e.getCharCount()
                // fSet will return true only if string index at fSet equal
                // to stringIndex
                if (e.findBack(leftBound, stringIndex, testString, matchResult) >= 0) {
                    matchResult.setConsumed(groupIndex, -1);
                    return shift;
                }
            }
        }

        return -1;
    }

    @Override
    public boolean hasConsumed(TMatchResultImpl matchResult) {
        return false;
    }

    @Override
    protected String getName() {
        return "PosBehindJointSet";
    }
}
