@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Test

/** Public W6b ownership, terminal-admission, and same-surface recovery contract. */
class W6bFilterAdmissionRecoverySurfaceTest {
    @Test
    fun `w6c filter refuses terminally and same surface recovers`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 2f, 2f)
        val surface = Surface(2, 2)
        surface.canvas {
            drawRect(bounds, Paint(imageFilter = ImageFilter.Offset(1f, 0f)))
        }

        assertTerminalWithoutReadbackMutation(surface, "w6b.filter.unsupported_family:")

        surface.discardRecordedOperations()
        surface.canvas {
            drawRect(bounds, Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false))
        }
        assertContentEquals(recoveryBlue2x2(), surface.render().pixels)
    }

    @Test
    fun `direct no-filter w6a layer control remains admitted`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 2f, 2f)
        val surface = Surface(2, 2)
        surface.canvas {
            saveLayer()
            drawRect(bounds, Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false))
            restore()
        }

        assertContentEquals(recoveryBlue2x2(), surface.render().pixels)
    }

    private fun assertTerminalWithoutReadbackMutation(surface: Surface, diagnosticPrefix: String) {
        val sentinel = UByteArray(16) { 0x5au }
        val before = sentinel.copyOf()
        val failure = assertFailsWith<IllegalStateException> {
            surface.readPixels(RectF32.ofLTRB(0f, 0f, 2f, 2f), sentinel)
        }
        assertTrue(failure.message?.startsWith(diagnosticPrefix) == true, failure.message ?: "missing diagnostic")
        assertContentEquals(before, sentinel)
    }

    private fun recoveryBlue2x2(): UByteArray = ubyteArrayOf(
        17u, 61u, 211u, 255u,
        17u, 61u, 211u, 255u,
        17u, 61u, 211u, 255u,
        17u, 61u, 211u, 255u,
    )
}
