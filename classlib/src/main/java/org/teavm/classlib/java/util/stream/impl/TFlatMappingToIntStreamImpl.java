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
package org.teavm.classlib.java.util.stream.impl;

import java.util.PrimitiveIterator;
import java.util.function.Function;
import java.util.function.IntPredicate;
import org.teavm.classlib.java.util.stream.TIntStream;
import org.teavm.classlib.java.util.stream.intimpl.TSimpleIntStreamImpl;

public class TFlatMappingToIntStreamImpl<T> extends TSimpleIntStreamImpl {
    private TSimpleStreamImpl<T> sourceStream;
    private TIntStream current;
    private PrimitiveIterator.OfInt iterator;
    private Function<? super T, ? extends TIntStream> mapper;
    private boolean done;

    public TFlatMappingToIntStreamImpl(TSimpleStreamImpl<T> sourceStream,
            Function<? super T, ? extends TIntStream> mapper) {
        this.sourceStream = sourceStream;
        this.mapper = mapper;
    }

    @Override
    public boolean next(IntPredicate consumer) {
        while (true) {
            if (current == null) {
                if (done) {
                    return false;
                }
                boolean hasMore = sourceStream.next(e -> {
                    current = mapper.apply(e);
                    return false;
                });
                if (!hasMore) {
                    done = true;
                }
                if (current == null) {
                    done = true;
                    return false;
                }
            }
            if (current instanceof TSimpleIntStreamImpl) {
                @SuppressWarnings("unchecked")
                TSimpleIntStreamImpl castCurrent = (TSimpleIntStreamImpl) current;
                if (castCurrent.next(consumer)) {
                    return true;
                }
                current = null;
            } else {
                iterator = current.iterator();
                while (iterator.hasNext()) {
                    int e = iterator.nextInt();
                    if (!consumer.test(e)) {
                        return true;
                    }
                }
                iterator = null;
                current = null;
            }
        }
    }

    @Override
    public void close() throws Exception {
        current = null;
        iterator = null;
        sourceStream.close();
    }
}
