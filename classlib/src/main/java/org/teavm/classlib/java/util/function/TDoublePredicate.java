/*
 *  Copyright 2015 Alexey Andreev.
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
package org.teavm.classlib.java.util.function;

@FunctionalInterface
public interface TDoublePredicate {
    boolean test(double value);

    default TDoublePredicate and(TDoublePredicate other) {
        return v -> test(v) && other.test(v);
    }

    default TDoublePredicate negate() {
        return v -> !test(v);
    }

    default TDoublePredicate or(TDoublePredicate other) {
        return v -> test(v) || other.test(v);
    }
}
