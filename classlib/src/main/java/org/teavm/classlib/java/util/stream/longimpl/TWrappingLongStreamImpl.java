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
package org.teavm.classlib.java.util.stream.longimpl;

import java.util.function.LongPredicate;

public abstract class TWrappingLongStreamImpl extends TSimpleLongStreamImpl {
    TSimpleLongStreamImpl sourceStream;

    public TWrappingLongStreamImpl(TSimpleLongStreamImpl sourceStream) {
        this.sourceStream = sourceStream;
    }

    @Override
    public boolean next(LongPredicate consumer) {
        return sourceStream.next(wrap(consumer));
    }

    protected abstract LongPredicate wrap(LongPredicate consumer);

    @Override
    protected int estimateSize() {
        return sourceStream.estimateSize();
    }

    @Override
    public void close() throws Exception {
        sourceStream.close();
    }
}
