package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererSceneRegistry

class RectOnlyPipelineCacheTelemetryTest {

    private fun planFor(sceneId: String): RectOnlyDrawPlan {
        val scene = GPURendererSceneRegistry.registry.requireScene(sceneId)
        return prepareRectOnlyDrawPlan(
            sceneId = sceneId,
            commands = scene.commands,
            width = scene.dimensions.width,
            height = scene.dimensions.height,
        )
    }

    @Test
    fun `solid scene assembles one pipeline and warms the cache across frames`() {
        val plan = planFor("solid-card-stack")
        val telemetry = rectOnlyPipelineCacheTelemetry(plan, "solid-card-stack", frameCount = 100)

        assertEquals("solid-card-stack", telemetry.sceneId)
        assertEquals(1L, telemetry.moduleCount)
        assertEquals(1L, telemetry.missCount)
        assertEquals(99L, telemetry.hitCount)
        assertEquals(0L, telemetry.evictionCount)
        assertEquals(1L, telemetry.totalPipelineCreations)
        assertEquals(mapOf("SolidRect" to 1L), telemetry.pipelineCreationCountsByFamily)
    }

    @Test
    fun `blur scene assembles a blur pipeline`() {
        val plan = planFor("blur-radius-ladder")
        val telemetry = rectOnlyPipelineCacheTelemetry(plan, "blur-radius-ladder", frameCount = 10)

        assertEquals(1L, telemetry.moduleCount)
        assertEquals(mapOf("Blur" to 1L), telemetry.pipelineCreationCountsByFamily)
        assertEquals(1L, telemetry.missCount)
        assertEquals(9L, telemetry.hitCount)
    }

    @Test
    fun `linear gradient scene assembles a linear gradient pipeline`() {
        val plan = planFor("linear-gradient-lanes")
        val telemetry = rectOnlyPipelineCacheTelemetry(plan, "linear-gradient-lanes", frameCount = 1)

        assertTrue(telemetry.pipelineCreationCountsByFamily.containsKey("LinearGradient"))
        assertEquals(0L, telemetry.hitCount)
        assertEquals(telemetry.moduleCount, telemetry.missCount)
    }

    @Test
    fun `single frame produces only misses`() {
        val plan = planFor("solid-card-stack")
        val telemetry = rectOnlyPipelineCacheTelemetry(plan, "solid-card-stack", frameCount = 1)
        assertEquals(0L, telemetry.hitCount)
        assertEquals(1L, telemetry.missCount)
    }

    @Test
    fun `frame count must be positive`() {
        val plan = planFor("solid-card-stack")
        assertFailsWith<IllegalArgumentException> {
            rectOnlyPipelineCacheTelemetry(plan, "solid-card-stack", frameCount = 0)
        }
    }

    @Test
    fun `renderer exposes pipeline cache telemetry for a draw plan`() {
        val plan = planFor("solid-card-stack")
        val telemetry = RectOnlyOffscreenRenderer()
            .pipelineCacheTelemetry(plan, "solid-card-stack", frameCount = 100)
        assertEquals(1L, telemetry.moduleCount)
        assertEquals(0.99, telemetry.hitRate, 0.0001)
    }
}
