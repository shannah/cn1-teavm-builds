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

import java.util.Spliterator;
import java.util.function.DoubleConsumer;

public class TSimpleDoubleStreamSpliterator implements Spliterator.OfDouble {
    private TSimpleDoubleStreamImpl stream;
    private boolean done;

    public TSimpleDoubleStreamSpliterator(TSimpleDoubleStreamImpl stream) {
        this.stream = stream;
    }

    @Override
    public void forEachRemaining(DoubleConsumer action) {
        stream.next(x -> {
            action.accept(x);
            return true;
        });
    }

    @Override
    public boolean tryAdvance(DoubleConsumer action) {
        if (done) {
            return false;
        }
        done = !stream.next(x -> {
            action.accept(x);
            return false;
        });
        return true;
    }

    @Override
    public OfDouble trySplit() {
        return null;
    }

    @Override
    public long estimateSize() {
        return stream.estimateSize();
    }

    @Override
    public int characteristics() {
        return 0;
    }
}
