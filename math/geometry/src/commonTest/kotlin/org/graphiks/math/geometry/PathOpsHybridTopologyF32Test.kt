package org.graphiks.math.geometry

import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PathOpsHybridTopologyF32Test {
    @Test
    fun `single source witness cannot erase either significant region`() {
        val e = 2.0.pow(-25)
        val lower = normalizedContourF64(
            0.0 to 1.0, 1.0 to 1.0 - e, 2.0 to 1.0 - e / 2.0,
            2.0 to -1.0, 0.0 to -1.0,
        )
        val upper = normalizedContourF64(
            0.0 to 1.0, 1.0 to 1.0 + e, 2.0 to 1.0 + e / 2.0,
            2.0 to 3.0, 0.0 to 3.0,
        )

        assertTrue(PathAnalysisF32.contains(projectOneF64(lower), Point2F32(1f, .5f)))
        assertTrue(PathAnalysisF32.contains(projectOneF64(upper), Point2F32(1f, 1.5f)))

        val error = assertFailsWith<IllegalStateException> { projectTogetherF64(lower, upper) }

        assertEquals("path-f32-projection-collapse", error.message)
    }

    @Test
    fun `distinct witnesses cannot consume one another`() {
        val e = 2.0.pow(-25)
        val main = normalizedContourF64(
            0.0 to 1.0, 1.0 to 1.0 + e, 2.0 to 1.0, 3.0 to 1.0 - e,
            3.0 to -1.0, 1.5 to -2.0, 0.0 to -1.0,
        )
        val firstTouch = normalizedContourF64(0.0 to 1.0, -0.4 to 2.0, 0.4 to 2.0)
        val secondTouch = normalizedContourF64(2.0 to 1.0, 1.6 to 2.0, 2.4 to 2.0)

        assertTrue(PathAnalysisF32.contains(projectOneF64(main), Point2F32(1.5f, 0f)))

        val error = assertFailsWith<IllegalStateException> {
            projectTogetherF64(main, firstTouch, secondTouch)
        }

        assertEquals("path-f32-projection-collapse", error.message)
    }

    @Test
    fun `under threshold collapse never leaks a generic Kotlin error`() {
        val result = projectUnderThresholdWitnessFixtureF32()

        assertTrue(PathAnalysisF32.contains(result, Point2F32(-0.5e-8f, 0f)))
    }
}

private val identityNormalizationF64 =
    PathNormalizationF64(origin = Point2F64(0.0, 0.0), scale = 1.0)

private fun normalizedContourF64(vararg coordinatesF64: Pair<Double, Double>): PathContourF64 =
    PathContourF64(
        coordinatesF64.map { (xF64, yF64) ->
            PathContourVertexF64(Point2F64(xF64, yF64), originalPointF32 = null)
        },
    )

private fun projectOneF64(contourF64: PathContourF64): PathF32 =
    projectContoursF64ToPathF32(listOf(contourF64), identityNormalizationF64, FillRule.WINDING)

private fun projectTogetherF64(vararg contoursF64: PathContourF64): PathF32 =
    projectContoursF64ToPathF32(contoursF64.toList(), identityNormalizationF64, FillRule.WINDING)

private fun projectUnderThresholdWitnessFixtureF32(): PathF32 {
    val scaleF64 = 1.0e-8
    val tinyF64 = 1.0e-46
    val runF64 = normalizedContourF64(
        0.0 to 0.0,
        scaleF64 to tinyF64,
        2.0 * scaleF64 to -tinyF64,
        2.0 * scaleF64 to scaleF64,
    )
    val touchF64 = normalizedContourF64(
        0.0 to 0.0,
        -scaleF64 to scaleF64,
        -scaleF64 to -scaleF64,
    )
    return projectTogetherF64(runF64, touchF64)
}
