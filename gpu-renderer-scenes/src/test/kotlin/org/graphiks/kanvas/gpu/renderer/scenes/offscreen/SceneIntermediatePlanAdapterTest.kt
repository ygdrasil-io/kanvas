package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediatePlanStep
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererSceneRegistry

class SceneIntermediatePlanAdapterTest {
    @Test
    fun `saveLayer fill becomes layer target children and composite steps`() {
        val scene = GPURendererSceneRegistry.scenes.single { it.sceneId.value == "savelayer-isolated" }
        val drawPlan = prepareRectOnlyDrawPlan(
            sceneId = scene.sceneId.value,
            commands = scene.commands,
            width = scene.dimensions.width,
            height = scene.dimensions.height,
        )

        val plan = SceneIntermediatePlanAdapter().plan(
            sceneId = scene.sceneId.value,
            drawPlan = drawPlan,
            width = scene.dimensions.width,
            height = scene.dimensions.height,
        )

        assertTrue(plan.steps.any { it is GPUIntermediatePlanStep.CreateIntermediate })
        assertTrue(plan.steps.any { it is GPUIntermediatePlanStep.RenderLayerChildren })
        assertTrue(plan.steps.any { it is GPUIntermediatePlanStep.CompositeIntermediate })
        assertEquals(1L, plan.telemetry.layerTargets)
        assertEquals(1L, plan.telemetry.layerComposites)
    }
}
