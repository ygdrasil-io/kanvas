package org.graphiks.kanvas.surface

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
class DiagnosticsTest {
    @Test fun `empty diagnostics`() { val d = Diagnostics(); assertTrue(d.isEmpty); assertFalse(d.hasFatal); assertEquals(0, d.fatalCount) }
    @Test fun `fatal entry`() { val d = Diagnostics(); d.fatal("code", "op", "reason"); assertEquals(1, d.fatalCount); assertTrue(d.hasFatal) }
    @Test fun `degrade and warn`() { val d = Diagnostics(); d.degrade("d", "op", "r"); d.warn("w", "op", "r"); assertEquals(1, d.degradeCount); assertEquals(1, d.warnCount) }
    @Test fun `summary`() { val d = Diagnostics(); d.fatal("e", "op", "r"); assertTrue(d.summary().contains("FATAL=1")) }
}
