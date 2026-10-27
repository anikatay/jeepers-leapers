package com.neueda.leap.hunter.catalog;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UniverseTest {

    private final Universe universe = new Universe();

    @Test
    void loadsTheWholeIndexFromTheCsv() {
        assertEquals(503, universe.size());
        assertNotNull(universe.asOf());
        assertEquals(GicsSector.INFORMATION_TECHNOLOGY, universe.find("NVDA").orElseThrow().sector());
    }

    @Test
    void findsATickerInEitherForm() {
        assertEquals("BRK-B", universe.find("brk.b").orElseThrow().ticker());
        assertEquals("BRK-B", universe.find(" BRK-B ").orElseThrow().ticker());
        assertTrue(universe.find("ZZZZ").isEmpty());
        assertTrue(universe.find(null).isEmpty());
    }

    @Test
    void readsQuotedNamesWithCommas() {
        assertEquals("F5, Inc.", universe.find("FFIV").orElseThrow().name());
        assertEquals(List.of("a", "b, c", "say \"hi\""), Universe.parseLine("a,\"b, c\",\"say \"\"hi\"\"\""));
    }
}
