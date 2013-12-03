package org.teavm.classlib.java.lang;

import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author Alexey Andreev
 */
class TClassTests {
    @Test
    public void classNameEvaluated() {
        assertEquals("java.lang.Object", Object.class.getName());
    }

    @Test
    public void objectClassNameEvaluated() {
        assertEquals("java.lang.Object", new Object().getClass().getName());
    }

    @Test
    public void objectClassConsideredNotArray() {
        assertFalse(Object.class.isArray());
    }

    @Test
    public void arrayClassConsideredArray() {
        assertFalse(Object[].class.isArray());
    }
}
