package org.graphiks.kanvas.gpu.renderer.stroke

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

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
    fun `dash expansion normalizes large positive phase`() {
        val expansion = DashExpansion.expand(floatArrayOf(10f, 5f), dashOffset = 37f, pathLength = 30f)

        assertEquals(
            listOf(
                DashInterval(length = 3f, isOn = true),
                DashInterval(length = 5f, isOn = false),
                DashInterval(length = 10f, isOn = true),
                DashInterval(length = 5f, isOn = false),
                DashInterval(length = 7f, isOn = true),
            ),
            expansion.intervals,
        )
    }

    @Test
    fun `dash expansion normalizes negative phase`() {
        val expansion = DashExpansion.expand(floatArrayOf(10f, 5f), dashOffset = -3f, pathLength = 20f)

        assertEquals(DashInterval(length = 3f, isOn = false), expansion.intervals.first())
        assertEquals(DashInterval(length = 10f, isOn = true), expansion.intervals.first { it.isOn })
        assertEquals(
            listOf(
                DashInterval(length = 3f, isOn = false),
                DashInterval(length = 10f, isOn = true),
                DashInterval(length = 5f, isOn = false),
                DashInterval(length = 2f, isOn = true),
            ),
            expansion.intervals,
        )
    }

    @Test
    fun `dash plan refuses all zero pattern`() {
        val result = runCatching { GPUComplexDashPlan.plan(floatArrayOf(0f, 0f), 0f) }

        assertTrue(result.isFailure)
        assertEquals("Dash array must contain a positive interval", result.exceptionOrNull()?.message)
    }

    @Test
    fun `dash expansion handles zero off intervals without hanging`() {
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "dash-zero-off-test").apply { isDaemon = true }
        }

        try {
            val future = executor.submit<DashExpansion> {
                DashExpansion.expand(floatArrayOf(5f, 0f, 2f, 0f), dashOffset = 0f, pathLength = 10f)
            }
            val expansion = try {
                future.get(1, TimeUnit.SECONDS)
            } catch (e: Exception) {
                fail("DashExpansion.expand should complete for zero off intervals: ${e::class.simpleName}")
            }

            assertEquals(listOf(DashInterval(length = 10f, isOn = true)), expansion.intervals)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `dash vertex expansion handles zero off intervals without hanging`() {
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "dash-vertex-zero-off-test").apply { isDaemon = true }
        }

        try {
            val future = executor.submit<DashVertexExpansion> {
                DashVertexExpansion.expandVertices(
                    tessellatedVertices = listOf(0f, 0f, 10f, 0f),
                    dashIntervals = floatArrayOf(5f, 0f, 2f, 0f),
                    dashPhase = 0f,
                    strokeWidth = 1f,
                )
            }
            val expansion = try {
                future.get(1, TimeUnit.SECONDS)
            } catch (e: Exception) {
                fail("DashVertexExpansion.expandVertices should complete for zero off intervals: ${e::class.simpleName}")
            }

            assertEquals(4, expansion.vertices.size)
            assertEquals(listOf(0), expansion.contourStarts)
        } finally {
            executor.shutdownNow()
        }
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
        assertEquals("unsupported.stroke.unsupported_path_effect", route.diagnostic.code)
    }

    @Test
    fun `dash classification SimpleRepeat for 2 elements`() {
        val classification = GPUComplexDashPlan.classify(floatArrayOf(10f, 5f))
        assertEquals(GPUDashClassification.SimpleRepeat, classification)
    }

    @Test
    fun `dash classification SimpleRepeat for 4 elements`() {
        val classification = GPUComplexDashPlan.classify(floatArrayOf(10f, 5f, 2f, 5f))
        assertEquals(GPUDashClassification.SimpleRepeat, classification)
    }

    @Test
    fun `dash classification ComplexPattern for more than 4 elements`() {
        val classification = GPUComplexDashPlan.classify(floatArrayOf(10f, 5f, 2f, 3f, 1f))
        assertEquals(GPUDashClassification.ComplexPattern, classification)
    }

    @Test
    fun `dash classification UnsupportedLength when exceeding max`() {
        val largeDashes = FloatArray(33) { 1f }
        val classification = GPUComplexDashPlan.classify(largeDashes)
        assertEquals(GPUDashClassification.UnsupportedLength, classification)
    }

    @Test
    fun `GPUComplexDashPlan plan creates valid plan`() {
        val plan = GPUComplexDashPlan.plan(floatArrayOf(10f, 5f), 0f)
        assertEquals(GPUDashClassification.SimpleRepeat, plan.classification)
        assertTrue(plan.dashArray.contentEquals(floatArrayOf(10f, 5f)))
    }

    @Test
    fun `dash plan with phase offset preserves phase`() {
        val plan = GPUComplexDashPlan.plan(floatArrayOf(10f, 5f), 3f)
        assertEquals(3f, plan.dashPhase)
    }

    @Test
    fun `path effect chain depth limit refuses when exceeded`() {
        val chain = PathEffectChain(listOf(
            PathEffect.Dash(floatArrayOf(10f, 5f)),
            PathEffect.Corner(1f),
            PathEffect.Dash(floatArrayOf(2f, 3f)),
            PathEffect.Corner(2f),
        ))
        val result = chain.apply(pathLength = 100f, maxDepth = 3)
        assertFalse(result.isValid)
        assertTrue(result.report.contains("depth_exceeded"))
    }

    @Test
    fun `path effect chain depth within limit is valid`() {
        val chain = PathEffectChain(listOf(
            PathEffect.Dash(floatArrayOf(10f, 5f)),
            PathEffect.Corner(1f),
            PathEffect.Dash(floatArrayOf(2f, 3f)),
        ))
        val result = chain.apply(pathLength = 100f, maxDepth = 3)
        assertTrue(result.isValid)
    }

    @Test
    fun `advanced stroke plan refuses on chain depth exceeded via proper diagnostic code`() {
        val plan = AdvancedStrokePlan(
            strokeWidth = 2f,
            dashEffect = PathEffect.Dash(floatArrayOf(10f, 5f)),
            cornerEffect = PathEffect.Corner(1f),
        )
        val effects = listOf(
            PathEffect.Dash(floatArrayOf(10f, 5f)),
            PathEffect.Corner(1f),
            PathEffect.Dash(floatArrayOf(2f, 3f)),
            PathEffect.Corner(2f),
        )
        val chain = PathEffectChain(effects)
        val result = chain.apply(pathLength = 100f, maxDepth = 3)
        assertFalse(result.isValid)
    }

    @Test
    fun `GPUStrokeStyleCompositionPlan refuses with dash_pattern_length code`() {
        val largeDashes = FloatArray(33) { 1f }
        val result = GPUStrokeStyleCompositionPlan.plan(
            width = 2f,
            dashArray = largeDashes,
        )
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertNotNull(ex)
        assertEquals("unsupported.stroke.dash_pattern_length", ex.message)
    }

    @Test
    fun `GPUStrokeStyleCompositionPlan refuses with path_effect_chain_depth code`() {
        val pathEffects = listOf(
            GPUPathEffectDescriptor("dash", mapOf("on" to 10f, "off" to 5f)),
            GPUPathEffectDescriptor("corner", mapOf("radius" to 2f)),
            GPUPathEffectDescriptor("dash", mapOf("on" to 2f, "off" to 3f)),
            GPUPathEffectDescriptor("corner", mapOf("radius" to 1f)),
        )
        val result = GPUStrokeStyleCompositionPlan.plan(
            width = 2f,
            pathEffects = pathEffects,
        )
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertNotNull(ex)
        assertEquals("unsupported.stroke.path_effect_chain_depth", ex.message)
    }

    @Test
    fun `GPUStrokeStyleCompositionPlan succeeds with valid inputs`() {
        val result = GPUStrokeStyleCompositionPlan.plan(
            width = 2f,
            dashArray = floatArrayOf(10f, 5f),
            dashPhase = 0f,
            pathEffects = listOf(
                GPUPathEffectDescriptor("dash", mapOf("on" to 10f, "off" to 5f)),
            ),
        )
        assertTrue(result.isSuccess)
        val plan = result.getOrNull()
        assertNotNull(plan)
        assertEquals(2f, plan.width)
        assertEquals(GPUStrokeCap.Butt, plan.cap)
        assertEquals(GPUStrokeJoin.Miter, plan.join)
        assertNotNull(plan.dashPlan)
        assertEquals(GPUDashClassification.SimpleRepeat, plan.dashPlan.classification)
    }

    @Test
    fun `GPUStrokeStyleCompositionPlan with no dash and no effects succeeds`() {
        val result = GPUStrokeStyleCompositionPlan.plan(width = 2f)
        assertTrue(result.isSuccess)
        val plan = result.getOrNull()
        assertNotNull(plan)
        assertEquals(null, plan.dashPlan)
        assertEquals(null, plan.pathEffectChain)
    }

    @Test
    fun `GPUPathEffectChainPlan detects maxDepthReached`() {
        val effects = listOf(
            GPUPathEffectDescriptor("dash"),
            GPUPathEffectDescriptor("corner"),
            GPUPathEffectDescriptor("dash"),
            GPUPathEffectDescriptor("corner"),
        )
        val plan = GPUPathEffectChainPlan.plan(effects, maxDepth = 3)
        assertTrue(plan.maxDepthReached)
    }

    @Test
    fun `GPUPathEffectChainPlan valid when within max depth`() {
        val effects = listOf(
            GPUPathEffectDescriptor("dash"),
            GPUPathEffectDescriptor("corner"),
        )
        val plan = GPUPathEffectChainPlan.plan(effects, maxDepth = 3)
        assertFalse(plan.maxDepthReached)
    }

    @Test
    fun `simple stroke oracle produces deterministic output`() {
        assertTrue(StrokeOracle.deterministicOracle(
            width = 2f,
            dashArray = floatArrayOf(10f, 5f),
            dashPhase = 1f,
            pathEffects = listOf(GPUPathEffectDescriptor("dash")),
        ))
    }

    @Test
    fun `complex stroke oracle produces deterministic output`() {
        assertTrue(StrokeOracle.deterministicOracle(
            width = 4f,
            dashArray = floatArrayOf(20f, 10f, 5f, 10f),
            dashPhase = 0f,
            pathEffects = listOf(
                GPUPathEffectDescriptor("dash"),
                GPUPathEffectDescriptor("corner"),
            ),
        ))
    }

    @Test
    fun `stroke oracle with large dash pattern is deterministic`() {
        assertTrue(StrokeOracle.deterministicOracle(
            width = 2f,
            dashArray = null,
            dashPhase = 0f,
            pathEffects = null,
        ))
    }

    @Test
    fun `computeExpectedStroke with dash scales width`() {
        val descriptor = StrokeOracle.computeExpectedStroke(
            width = 10f,
            dashArray = floatArrayOf(10f, 10f),
            pathLength = 100f,
        )
        assertEquals(5f, descriptor.strokeWidth)
    }

    @Test
    fun `computeExpectedStroke with no dash preserves width`() {
        val descriptor = StrokeOracle.computeExpectedStroke(
            width = 10f,
            dashArray = null,
            pathLength = 100f,
        )
        assertEquals(10f, descriptor.strokeWidth)
    }
}
