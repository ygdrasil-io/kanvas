package org.graphiks.kanvas.dsl

import org.graphiks.kanvas.operators.*
import org.graphiks.kanvas.types.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class DslTest {
    @Test fun `Point operators`() { assertEquals(Point(4f,6f), Point(1f,2f)+Point(3f,4f)); assertEquals(Point(-2f,-2f), Point(1f,2f)-Point(3f,4f)); assertEquals(Point(2f,4f), Point(1f,2f)*2f); assertEquals(Point(0.5f,1f), Point(1f,2f)/2f) }
    @Test fun `Matrix33 times Point`() { assertEquals(Point(15f,25f), Matrix33.translate(10f,20f) * Point(5f,5f)) }
}
