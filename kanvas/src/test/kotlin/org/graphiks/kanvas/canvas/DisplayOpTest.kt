package org.graphiks.kanvas.canvas

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.types.*
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class DisplayOpTest {
    @Test fun `DrawRect op`() { assertTrue(DisplayOp.DrawRect(RectF32.ofLTRB(0f,0f,100f,80f), Paint.fill(ColorARGB.Red), Matrix3x3F32.Identity, ClipStack.WideOpen) is DisplayOp.DrawRect) }
    @Test
    fun `DrawPath keeps its historical four component JVM data class ABI`() {
        val operation = DisplayOp.DrawPath(
            Path().addRect(RectF32.ofLTRB(0f, 0f, 100f, 100f)),
            Paint.fill(ColorARGB.Blue),
            Matrix3x3F32.Identity,
            ClipStack.WideOpen,
        )

        assertEquals("drawPath", operation.sourceOperation)
        assertEquals(
            listOf(4),
            DisplayOp.DrawPath::class.java.constructors
                .filterNot { it.isSynthetic }
                .map { it.parameterCount }
                .sorted(),
        )
        assertEquals(
            listOf("component1", "component2", "component3", "component4"),
            DisplayOp.DrawPath::class.java.declaredMethods
                .map { it.name }
                .filter { it.startsWith("component") }
                .sorted(),
        )
        assertEquals(
            listOf(4),
            DisplayOp.DrawPath::class.java.declaredMethods
                .filter { it.name == "copy" }
                .map { it.parameterCount },
        )
        assertEquals(
            listOf(7),
            DisplayOp.DrawPath::class.java.declaredMethods
                .filter { it.name == "copy\$default" }
                .map { it.parameterCount },
        )
    }

    @Test
    fun `public data class copy sanitizes internal text provenance`() {
        val expanded = DisplayOp.DrawPath.withSourceOperation(
            path = Path().addRect(RectF32.ofLTRB(0f, 0f, 1f, 1f)),
            paint = Paint.fill(ColorARGB.Blue),
            transform = Matrix3x3F32.Identity,
            clip = ClipStack.WideOpen,
            sourceOperation = DrawPathSourceOperation.TEXT_EXPANDED,
        )

        assertEquals("text-expanded", expanded.sourceOperation)
        assertEquals("drawPath", expanded.copy().sourceOperation)
    }

    @Test fun `SetTransform op`() { assertTrue(DisplayOp.SetTransform(Matrix3x3F32.translation(10f, 20f)) is DisplayOp.SetTransform) }
    @Test fun `BeginLayer and EndLayer`() { assertTrue(DisplayOp.BeginLayer(null, null) is DisplayOp.BeginLayer); assertTrue(DisplayOp.EndLayer is DisplayOp.EndLayer) }
}
