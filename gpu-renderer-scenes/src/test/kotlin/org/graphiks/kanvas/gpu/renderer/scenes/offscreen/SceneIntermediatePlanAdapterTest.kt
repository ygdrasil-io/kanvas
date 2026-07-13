package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediatePlanStep
import org.graphiks.kanvas.gpu.renderer.intermediates.dumpLines
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererSceneRegistry
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneBlendMode
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneRect

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

    @Test
    fun `dst-read-strategy scene emits destination copy bind and shader blend render steps`() {
        val scene = GPURendererSceneRegistry.scenes.single { it.sceneId.value == "dst-read-strategy" }
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

        assertTrue(plan.steps.any { it is GPUIntermediatePlanStep.CopyDestination }, plan.dumpLines().joinToString("\n"))
        assertTrue(plan.steps.any { it is GPUIntermediatePlanStep.BindIntermediate }, plan.dumpLines().joinToString("\n"))
        assertTrue(
            plan.steps.any {
                it is GPUIntermediatePlanStep.RenderToTarget && it.routeLabel == "shader-blend:Multiply"
            },
            plan.dumpLines().joinToString("\n"),
        )
        assertEquals(1L, plan.telemetry.destinationReadCopies)
        assertEquals(1L, plan.telemetry.destinationReadIntermediateBinds)
        assertEquals(1L, plan.telemetry.passSplits)
    }

    @Test
    fun `destination-read blend mode comes from command metadata not scene label`() {
        val drawPlan = prepareRectOnlyDrawPlan(
            sceneId = "metadata-driven-dst-read",
            commands = listOf(
                SceneCommand.Clear(SceneColor(0f, 0f, 0f, 1f)),
                SceneCommand.FillRect(
                    label = "background",
                    rect = SceneRect(0f, 0f, 64f, 64f),
                    color = SceneColor.green(1f),
                    paintOrder = 1,
                ),
                SceneCommand.FillRect(
                    label = "foreground",
                    rect = SceneRect(8f, 8f, 56f, 56f),
                    color = SceneColor.amber(0.6f),
                    paintOrder = 2,
                    blendMode = SceneBlendMode.Multiply,
                ),
            ),
            width = 64,
            height = 64,
        )

        val plan = SceneIntermediatePlanAdapter().plan(
            sceneId = "metadata-driven-dst-read",
            drawPlan = drawPlan,
            width = 64,
            height = 64,
        )

        assertTrue(plan.steps.any { it is GPUIntermediatePlanStep.CopyDestination }, plan.dumpLines().joinToString("\n"))
        assertTrue(
            plan.steps.any {
                it is GPUIntermediatePlanStep.RenderToTarget && it.routeLabel == "shader-blend:Multiply"
            },
            plan.dumpLines().joinToString("\n"),
        )
    }

    @Test
    fun `saveLayer with unsupported child family refuses before preparation`() {
        val drawPlan = prepareRectOnlyDrawPlan(
            sceneId = "unsupported-layer-child",
            commands = listOf(
                SceneCommand.Clear(SceneColor(0f, 0f, 0f, 1f)),
                SceneCommand.SaveLayer(
                    label = "layer-a",
                    bounds = SceneRect(0f, 0f, 64f, 64f),
                    contentRect = SceneRect(8f, 8f, 48f, 48f),
                    radius = 0f,
                    contentColor = SceneColor(1f, 1f, 1f, 1f),
                    shadowColor = SceneColor(0f, 0f, 0f, 0f),
                    paintOrder = 1,
                ),
                SceneCommand.LinearGradientRect(
                    label = "gradient-child",
                    rect = SceneRect(4f, 4f, 60f, 60f),
                    startColor = SceneColor(1f, 0f, 0f, 1f),
                    endColor = SceneColor(0f, 0f, 1f, 1f),
                    paintOrder = 2,
                ),
            ),
            width = 64,
            height = 64,
        )

        val plan = SceneIntermediatePlanAdapter().plan(
            sceneId = "unsupported-layer-child",
            drawPlan = drawPlan,
            width = 64,
            height = 64,
        )

        val refusal = assertIs<GPUIntermediatePlanStep.Refuse>(plan.steps.single())
        assertEquals("layer:layer-a", refusal.scopeLabel)
        assertEquals("unsupported.layer.child_family.linear-gradient-rect", refusal.reasonCode)
    }
}
