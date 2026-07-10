package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.skia.SkiaGmRenderer
import org.graphiks.kanvas.test.GpuAvailability
import org.graphiks.kanvas.test.ReferenceManager
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AAXfermodesRegressionTest {
    @Test
    fun `clipped AA blend mode grid retains visible pixels`() {
        GpuAvailability.requireWebGpu()

        val gm = AAXfermodesGm()
        val actual = SkiaGmRenderer.render(gm).rgba
        val reference = ReferenceManager.loadReference("/reference/${gm.name}.png")
        val matchingPixels = actual.asList()
            .zip(reference.asList())
            .chunked(4)
            .count { channels -> channels.all { (actualByte, referenceByte) -> actualByte == referenceByte } }

        assertTrue(
            matchingPixels > 10_000,
            "expected clipped blend grid to retain visible reference pixels, matched=$matchingPixels",
        )
    }

    @Test
    fun `first translucent background cell retains checkerboard through saveLayer`() {
        GpuAvailability.requireWebGpu()

        val gm = AAXfermodesGm()
        val actual = SkiaGmRenderer.render(gm).rgba
        val reference = ReferenceManager.loadReference("/reference/${gm.name}.png")

        // (89,72) is in the first Clear-mode/first-colour background cell: it is inside the
        // clipped 30px cell but outside the 22px square, text, AA edge, and 10px checker edge.
        // Four byte values accommodates deterministic WGSL quantisation while still detecting
        // loss of the checkerboard from an incorrect saveLayer composite.
        assertPixelNearReference(actual, reference, x = 89, y = 72, width = gm.width, tolerance = 4)
    }

    private fun assertPixelNearReference(
        actual: ByteArray,
        reference: ByteArray,
        x: Int,
        y: Int,
        width: Int,
        tolerance: Int,
    ) {
        val offset = (y * width + x) * 4
        (0 until 4).forEach { channel ->
            val actualByte = actual[offset + channel].toInt() and 0xff
            val referenceByte = reference[offset + channel].toInt() and 0xff
            assertTrue(
                kotlin.math.abs(actualByte - referenceByte) <= tolerance,
                "channel=$channel at ($x,$y): reference=$referenceByte +/- $tolerance, actual=$actualByte",
            )
        }
    }

    companion object {
        @AfterAll
        @JvmStatic
        fun cleanup() {
            GPUBackendRuntimeFactory.dispose()
        }
    }
}
