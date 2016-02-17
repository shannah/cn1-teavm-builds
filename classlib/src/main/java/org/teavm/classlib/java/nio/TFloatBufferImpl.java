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
package org.teavm.classlib.java.nio;

/**
 *
 * @author Alexey Andreev
 */
abstract class TFloatBufferImpl extends TFloatBuffer {
    public TFloatBufferImpl(int capacity, int position, int limit) {
        super(capacity, position, limit);
    }

    @Override
    public TFloatBuffer slice() {
        return duplicate(position, limit - position, 0, limit - position, isReadOnly());
    }

    @Override
    public TFloatBuffer duplicate() {
        return duplicate(0, capacity, position, limit, isReadOnly());
    }

    @Override
    public TFloatBuffer asReadOnlyBuffer() {
        return duplicate(0, capacity, position, limit, true);
    }

    abstract TFloatBuffer duplicate(int start, int capacity, int position, int limit, boolean readOnly);

    @Override
    public float get() {
        if (position >= limit) {
            throw new TBufferUnderflowException();
        }
        return getElement(position++);
    }

    @Override
    public TFloatBuffer put(float b) {
        if (isReadOnly()) {
            throw new TReadOnlyBufferException();
        }
        if (position >= limit) {
            throw new TBufferOverflowException();
        }
        putElement(position++, b);
        return this;
    }

    @Override
    public float get(int index) {
        if (index < 0 || index >= limit) {
            throw new IndexOutOfBoundsException("Index " + index + " is outside of range [0;" + limit + ")");
        }
        return getElement(index);
    }

    @Override
    public TFloatBuffer put(int index, float b) {
        if (isReadOnly()) {
            throw new TReadOnlyBufferException();
        }
        if (index < 0 || index >= limit) {
            throw new IndexOutOfBoundsException("Index " + index + " is outside of range [0;" + limit + ")");
        }
        putElement(index, b);
        return this;
    }

    @Override
    public TFloatBuffer compact() {
        if (isReadOnly()) {
            throw new TReadOnlyBufferException();
        }
        if (position > 0) {
            int sz = remaining();
            int src = position;
            for (int i = 0; i < sz; ++i) {
                putElement(i, getElement(src++));
            }
            position = sz;
        }
        limit = capacity;
        mark = -1;
        return this;
    }

    @Override
    public boolean isDirect() {
        return false;
    }

    @Override
    public boolean isReadOnly() {
        return readOnly();
    }

    abstract boolean readOnly();
}
