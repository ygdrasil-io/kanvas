package org.skia.foundation


import org.graphiks.math.SkColor4f
import org.graphiks.math.SkColorMatrix
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * R-suivi.16 — coverage for the [SkColorFilters.Matrix] overload that
 * accepts a [SkColorMatrix]. The overload must produce a filter whose
 * per-channel output is bit-identical to the 20-float form, since it
 * delegates via [SkColorMatrix.getRowMajor].
 */
class SkColorFiltersMatrixSkColorMatrixTest {

    @Test
    fun `Matrix(SkColorMatrix) matches Matrix(FloatArray) for identity`() {
        val cm = SkColorMatrix()
        val fromMatrix = SkColorFilters.Matrix(cm)
        val fromFloats = SkColorFilters.Matrix(cm.toFloatArray())
        val src = SkColor4f(0.25f, 0.5f, 0.75f, 1f)
        assertEquals(fromFloats.filterColor4f(src), fromMatrix.filterColor4f(src))
    }

    @Test
    fun `Matrix(SkColorMatrix) matches Matrix(FloatArray) for scale`() {
        val cm = SkColorMatrix().apply { setScale(0.5f, 0.25f, 2f, 1f) }
        val fromMatrix = SkColorFilters.Matrix(cm)
        val fromFloats = SkColorFilters.Matrix(cm.toFloatArray())
        // Probe several colours to make sure the matrix is wired identically
        // (not just on a happy-path identity input).
        for (sample in arrayOf(
            SkColor4f(0.1f, 0.2f, 0.3f, 0.4f),
            SkColor4f(1f, 0f, 0.5f, 1f),
            SkColor4f(0f, 0f, 0f, 0f),
            SkColor4f(0.6f, 0.7f, 0.8f, 0.9f),
        )) {
            val a = fromMatrix.filterColor4f(sample)
            val b = fromFloats.filterColor4f(sample)
            assertEquals(b.fR, a.fR, 1e-6f, "R for $sample")
            assertEquals(b.fG, a.fG, 1e-6f, "G for $sample")
            assertEquals(b.fB, a.fB, 1e-6f, "B for $sample")
            assertEquals(b.fA, a.fA, 1e-6f, "A for $sample")
        }
    }

    @Test
    fun `Matrix(SkColorMatrix) matches Matrix(FloatArray) for saturation`() {
        val cm = SkColorMatrix().apply { setSaturation(0f) } // grayscale
        val fromMatrix = SkColorFilters.Matrix(cm)
        val fromFloats = SkColorFilters.Matrix(cm.toFloatArray())
        val src = SkColor4f(1f, 0f, 0f, 1f) // pure red
        val a = fromMatrix.filterColor4f(src)
        val b = fromFloats.filterColor4f(src)
        assertEquals(b.fR, a.fR, 1e-6f)
        assertEquals(b.fG, a.fG, 1e-6f)
        assertEquals(b.fB, a.fB, 1e-6f)
        assertEquals(b.fA, a.fA, 1e-6f)
    }
}
