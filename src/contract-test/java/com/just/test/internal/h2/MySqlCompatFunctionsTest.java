package com.just.test.internal.h2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MySqlCompatFunctionsTest {

    @Test
    void findInSetMatchesCaseInsensitively() {
        assertEquals(2, MySqlCompatFunctions.findInSet("b", "a,B,c"));
        assertEquals(2, MySqlCompatFunctions.findInSet("B", "a,b,c"));
        assertEquals(1, MySqlCompatFunctions.findInSet("à", "À,b"));
        assertEquals(2, MySqlCompatFunctions.findInSet("b", "a,B,b"));
    }

    @Test
    void findInSetKeepsAccentSpaceAndCommaDifferences() {
        assertEquals(0, MySqlCompatFunctions.findInSet("a", "á"));
        assertEquals(0, MySqlCompatFunctions.findInSet("á", "a"));
        assertEquals(0, MySqlCompatFunctions.findInSet("a", "a "));
        assertEquals(0, MySqlCompatFunctions.findInSet("a ", "a"));
        assertEquals(0, MySqlCompatFunctions.findInSet("a,b", "a,b,c"));
        assertEquals(0, MySqlCompatFunctions.findInSet("z", "a,b"));
        assertEquals(1, MySqlCompatFunctions.findInSet("", ""));
        assertEquals(2, MySqlCompatFunctions.findInSet("", "a,,b"));
        assertNull(MySqlCompatFunctions.findInSet(null, "a"));
        assertNull(MySqlCompatFunctions.findInSet("a", null));
    }
}
