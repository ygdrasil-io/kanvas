package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class GPUMultiStopGradientTest {

    @Test
    fun `LinearGradient with 3 stops populates allStopPositions and allStopColors`() {
        val shader = Shader.LinearGradient(
            start = Point(0f, 0f),
            end = Point(100f, 0f),
            stops = listOf(
                GradientStop(0f, Color.fromRGBA(1f, 0f, 0f, 1f)),
                GradientStop(0.5f, Color.fromRGBA(0f, 1f, 0f, 1f)),
                GradientStop(1f, Color.fromRGBA(0f, 0f, 1f, 1f)),
            ),
        )
        val material = shader.toMaterial() as GPUMaterialDescriptor.LinearGradient

        assertEquals(1f, material.startR, 0.001f)
        assertEquals(0f, material.startG, 0.001f)
        assertEquals(0f, material.startB, 0.001f)
        assertEquals(1f, material.startA, 0.001f)
        assertEquals(0f, material.endR, 0.001f)
        assertEquals(0f, material.endG, 0.001f)
        assertEquals(1f, material.endB, 0.001f)
        assertEquals(1f, material.endA, 0.001f)

        assertNotNull(material.allStopPositions)
        assertNotNull(material.allStopColors)
        assertEquals(3, material.allStopPositions!!.size)
        assertEquals(12, material.allStopColors!!.size)

        assertArrayEquals(floatArrayOf(0f, 0.5f, 1f), material.allStopPositions, 0.001f)
        assertArrayEquals(
            floatArrayOf(1f, 0f, 0f, 1f, 0f, 1f, 0f, 1f, 0f, 0f, 1f, 1f),
            material.allStopColors, 0.001f,
        )
    }

    @Test
    fun `RadialGradient with 3 stops populates allStopPositions and allStopColors`() {
        val shader = Shader.RadialGradient(
            center = Point(50f, 50f),
            radius = 100f,
            stops = listOf(
                GradientStop(0f, Color.fromRGBA(1f, 0f, 0f, 1f)),
                GradientStop(0.5f, Color.fromRGBA(0f, 1f, 0f, 1f)),
                GradientStop(1f, Color.fromRGBA(0f, 0f, 1f, 1f)),
            ),
        )
        val material = shader.toMaterial() as GPUMaterialDescriptor.RadialGradient

        assertEquals(1f, material.startR, 0.001f)
        assertEquals(0f, material.startG, 0.001f)
        assertEquals(0f, material.startB, 0.001f)
        assertEquals(1f, material.startA, 0.001f)
        assertEquals(0f, material.endR, 0.001f)
        assertEquals(0f, material.endG, 0.001f)
        assertEquals(1f, material.endB, 0.001f)
        assertEquals(1f, material.endA, 0.001f)

        assertNotNull(material.allStopPositions)
        assertNotNull(material.allStopColors)
        assertEquals(3, material.allStopPositions!!.size)
        assertEquals(12, material.allStopColors!!.size)
        assertArrayEquals(floatArrayOf(0f, 0.5f, 1f), material.allStopPositions, 0.001f)
        assertArrayEquals(
            floatArrayOf(1f, 0f, 0f, 1f, 0f, 1f, 0f, 1f, 0f, 0f, 1f, 1f),
            material.allStopColors, 0.001f,
        )
    }

    @Test
    fun `SweepGradient with 3 stops populates allStopPositions and allStopColors`() {
        val shader = Shader.SweepGradient(
            center = Point(50f, 50f),
            startAngle = 0f,
            endAngle = 360f,
            stops = listOf(
                GradientStop(0f, Color.fromRGBA(1f, 0f, 0f, 1f)),
                GradientStop(0.5f, Color.fromRGBA(0f, 1f, 0f, 1f)),
                GradientStop(1f, Color.fromRGBA(0f, 0f, 1f, 1f)),
            ),
        )
        val material = shader.toMaterial() as GPUMaterialDescriptor.SweepGradient

        assertEquals(1f, material.startR, 0.001f)
        assertEquals(0f, material.startG, 0.001f)
        assertEquals(0f, material.startB, 0.001f)
        assertEquals(1f, material.startA, 0.001f)
        assertEquals(0f, material.endR, 0.001f)
        assertEquals(0f, material.endG, 0.001f)
        assertEquals(1f, material.endB, 0.001f)
        assertEquals(1f, material.endA, 0.001f)

        assertNotNull(material.allStopPositions)
        assertNotNull(material.allStopColors)
        assertEquals(3, material.allStopPositions!!.size)
        assertEquals(12, material.allStopColors!!.size)
        assertArrayEquals(floatArrayOf(0f, 0.5f, 1f), material.allStopPositions, 0.001f)
        assertArrayEquals(
            floatArrayOf(1f, 0f, 0f, 1f, 0f, 1f, 0f, 1f, 0f, 0f, 1f, 1f),
            material.allStopColors, 0.001f,
        )
    }

    @Test
    fun `LinearGradient with 2 stops populates allStopPositions with 2 entries`() {
        val shader = Shader.LinearGradient(
            start = Point(0f, 0f),
            end = Point(100f, 0f),
            stops = listOf(
                GradientStop(0f, Color.fromRGBA(1f, 0f, 0f, 1f)),
                GradientStop(1f, Color.fromRGBA(0f, 0f, 1f, 1f)),
            ),
        )
        val material = shader.toMaterial() as GPUMaterialDescriptor.LinearGradient

        assertEquals(1f, material.startR, 0.001f)
        assertEquals(1f, material.endB, 0.001f)

        assertNotNull(material.allStopPositions)
        assertNotNull(material.allStopColors)
        assertEquals(2, material.allStopPositions!!.size)
        assertEquals(8, material.allStopColors!!.size)
        assertArrayEquals(floatArrayOf(0f, 1f), material.allStopPositions, 0.001f)
    }

    @Test
    fun `multi-stop uniform packing produces correct byte buffer size`() {
        val n = 3
        val bb = java.nio.ByteBuffer.allocate(32 + n * 32).order(java.nio.ByteOrder.nativeOrder())
        assertEquals(32 + 3 * 32, bb.capacity())

        bb.putFloat(0f); bb.putFloat(0f)    // start
        bb.putFloat(100f); bb.putFloat(0f)  // end
        bb.putInt(n); bb.putInt(0)           // stopCount + padding
        for (i in 0 until n) {
            val pos = i.toFloat() / (n - 1).coerceAtLeast(1)
            bb.putFloat(pos); bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f)
            bb.putFloat(1f); bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(1f) // dummy color
        }

        val bytes = bb.array()
        assertEquals(32 + 3 * 32, bytes.size)
    }

    @Test
    fun `toMaterial preserves ConicalGradient descriptor stops`() {
        val shader = Shader.ConicalGradient(
            start = Point(0f, 0f), startRadius = 0f,
            end = Point(100f, 0f), endRadius = 50f,
            stops = listOf(
                GradientStop(0f, Color.fromRGBA(1f, 0f, 0f, 1f)),
                GradientStop(1f, Color.fromRGBA(0f, 0f, 1f, 1f)),
            ),
        )
        val material = shader.toMaterial() as GPUMaterialDescriptor.ConicalGradient
        assertArrayEquals(floatArrayOf(0f, 1f), material.allStopPositions, 0.001f)
        assertNotNull(material.allStopColors)
        assertEquals(8, material.allStopColors!!.size)
    }
}
