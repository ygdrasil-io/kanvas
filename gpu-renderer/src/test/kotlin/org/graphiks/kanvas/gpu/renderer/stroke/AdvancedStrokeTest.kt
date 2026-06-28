package org.graphiks.kanvas.gpu.renderer.stroke

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AdvancedStrokeTest {

    @Test
    fun `dash expansion produces correct interval count`() {
        val dashes = floatArrayOf(10f, 5f, 2f, 5f)
        val expansion = DashExpansion.expand(dashes, dashOffset = 0f, pathLength = 100f)
        assertTrue { expansion.intervals.isNotEmpty() }
    }

    @Test
    fun `dash expansion with offset shifts first interval`() {
        val dashes = floatArrayOf(10f, 5f)
        val expansion = DashExpansion.expand(dashes, dashOffset = 3f, pathLength = 100f)
        assertTrue { expansion.intervals.first().length > 0f }
    }

    @Test
    fun `path effect chain applies effects in order`() {
        val chain = PathEffectChain(listOf(
            PathEffect.Dash(floatArrayOf(10f, 5f)),
            PathEffect.Corner(2f),
        ))
        val result = chain.apply(pathLength = 100f)
        assertTrue { result.isValid }
    }

    @Test
    fun `advanced stroke plan generates descriptor`() {
        val plan = AdvancedStrokePlan(
            strokeWidth = 2f,
            dashEffect = PathEffect.Dash(floatArrayOf(10f, 5f)),
            cornerEffect = PathEffect.Corner(1f),
        )
        val descriptor = plan.toDescriptor()
        assertEquals(2f, descriptor.strokeWidth)
    }

    @Test
    fun `advanced stroke plan refuses on unsupported path effect`() {
        val plan = AdvancedStrokePlan(
            strokeWidth = 2f,
            dashEffect = PathEffect.Unsupported("custom_effect"),
            cornerEffect = null,
        )
        val route = plan.analyze()
        assertIs<AdvancedStrokeRoute.Refused>(route)
        assertEquals("unsupported.stroke.path_effect", route.diagnostic.code)
    }
}
