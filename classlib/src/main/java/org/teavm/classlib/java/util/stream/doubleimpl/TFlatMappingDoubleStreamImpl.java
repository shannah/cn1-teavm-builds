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
package org.teavm.classlib.java.util.stream.doubleimpl;

import java.util.PrimitiveIterator;
import java.util.function.DoubleFunction;
import java.util.function.DoublePredicate;
import org.teavm.classlib.java.util.stream.TDoubleStream;

public class TFlatMappingDoubleStreamImpl extends TSimpleDoubleStreamImpl {
    private TSimpleDoubleStreamImpl sourceStream;
    private TDoubleStream current;
    private PrimitiveIterator.OfDouble iterator;
    private DoubleFunction<? extends TDoubleStream> mapper;
    private boolean done;

    public TFlatMappingDoubleStreamImpl(TSimpleDoubleStreamImpl sourceStream,
            DoubleFunction<? extends TDoubleStream> mapper) {
        this.sourceStream = sourceStream;
        this.mapper = mapper;
    }

    @Override
    public boolean next(DoublePredicate consumer) {
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
            if (current instanceof TSimpleDoubleStreamImpl) {
                @SuppressWarnings("unchecked")
                TSimpleDoubleStreamImpl castCurrent = (TSimpleDoubleStreamImpl) current;
                if (castCurrent.next(consumer)) {
                    return true;
                }
                current = null;
            } else {
                iterator = current.iterator();
                while (iterator.hasNext()) {
                    double e = iterator.nextDouble();
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
