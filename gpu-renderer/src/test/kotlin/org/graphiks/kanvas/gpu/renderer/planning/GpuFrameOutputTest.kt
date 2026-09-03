package org.graphiks.kanvas.gpu.renderer.planning

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.graphiks.kanvas.render.ir.RenderDiagnostic
import org.graphiks.kanvas.render.ir.RenderDiagnosticCode
import org.graphiks.kanvas.render.ir.RenderDiagnosticDomain
import org.graphiks.kanvas.render.ir.RenderDiagnosticSeverity

class GpuFrameOutputTest {
    @Test fun `output defensively owns all mutable inputs and snapshots`() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val diagnostics = mutableListOf(RenderDiagnostic(RenderDiagnosticCode("w3.test"), RenderDiagnosticDomain.EXECUTION, RenderDiagnosticSeverity.INFO, "test"))
        val counters = mutableMapOf("draws" to 1L)
        val output = GpuFrameOutput.of(1, 1, 4, GpuFrameChannelOrder.RGBA, bytes, GpuFrameMetrics(1, 1, 1, 1f, true), diagnostics, mutableListOf("submitted"), counters, mutableListOf("RenderPass"))
        bytes[0] = 9; diagnostics.clear(); counters["draws"] = 9
        assertContentEquals(byteArrayOf(1, 2, 3, 4), output.copyBytes())
        assertEquals(1, output.diagnostics().size)
        assertEquals(1L, output.nativeEvidenceCounters().getValue("draws"))
        assertFailsWith<UnsupportedOperationException> { (output.structuralSteps() as MutableList<String>).add("other") }
    }
    @Test fun `output rejects non tight storage and invalid evidence`() {
        assertFailsWith<IllegalArgumentException> { GpuFrameOutput.of(1, 1, 8, GpuFrameChannelOrder.RGBA, ByteArray(8), GpuFrameMetrics(0, 0, 0, 0f, false), emptyList(), emptyList(), emptyMap(), emptyList()) }
    }
}
