package org.graphiks.math.geometry

import org.graphiks.math.vector.Vector2F32
import org.graphiks.math.vector.Vector2F64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PathNormalizationF64Test {
    @Test
    fun `normalization is translation and scale stable`() {
        val path = PathBuilder().addRect(RectF32.ofLTRB(3_000f, 4_000f, 5_000f, 6_000f)).build()
        val normalization = pathNormalizationF64(listOf(path))

        assertEquals(Point2F64(-0.5, -0.5), normalization.normalize(Point2F32(3_000f, 4_000f)))
        assertEquals(Point2F32(5_000f, 6_000f), normalization.denormalize(Point2F64(0.5, 0.5)))
        assertEquals(Vector2F64(0.5, -0.25), normalization.normalizeVector(Vector2F32(1_000f, -500f)))
    }

    @Test
    fun `empty normalization is origin with unit scale`() {
        assertEquals(PathNormalizationF64(Point2F64.Origin, 1.0), pathNormalizationF64(emptyList()))
    }

    @Test
    fun `finite double outside the F32 domain rejects instead of becoming infinity`() {
        val error = assertFailsWith<IllegalStateException> {
            PathNormalizationF64(Point2F64.Origin, 1.0).denormalize(
                Point2F64(Float.MAX_VALUE.toDouble() * 2.0, 0.0),
            )
        }

        assertEquals("path-f32-projection-collapse", error.message)
    }
}
