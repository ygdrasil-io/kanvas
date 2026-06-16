package org.graphiks.kanvas.gpu.renderer.scenes.catalog

import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneRect

val runtimeEffectUniformLadderScene: GPURendererScene<SceneCommand> =
    GPURendererScene(
        sceneId = SceneId("runtime-effect-uniform-ladder"),
        title = "Runtime Effect Uniform Ladder",
        description = "Clipped SimpleRT tiles with varying gColor uniforms arranged as a promotion candidate ladder.",
        dimensions = SceneDimensions(320, 200),
        tags = setOf(SceneTag.RuntimeEffect, SceneTag.RRect, SceneTag.Clip),
        roadmapLinks = listOf(SceneRoadmapLink.milestone("M7")),
        expectation = SceneExpectation.ShouldRender,
        commands = listOf(
            SceneCommand.Clear(SceneColor(0.040f, 0.044f, 0.052f, 1f)),
            SceneCommand.FillRRect(
                label = "runtime-effect-uniform-ladder-panel",
                rect = SceneRect(24f, 20f, 296f, 180f),
                radius = 18f,
                color = SceneColor(0.10f, 0.12f, 0.16f, 1f),
            ),
            SceneCommand.Clip(
                label = "runtime-effect-uniform-ladder-clip",
                rect = SceneRect(44f, 34f, 284f, 168f),
            ),
            SceneCommand.RuntimeEffectTile(
                label = "simple-rt-ladder-step-1",
                rect = SceneRect(52f, 44f, 96f, 156f),
                stableId = "runtime.simple_rt",
                wgslImplementationId = "wgsl/runtime_simple_rt",
                uniformColor = SceneColor(0.10f, 0.18f, 0.28f, 1f),
                paintOrder = 1,
            ),
            SceneCommand.RuntimeEffectTile(
                label = "simple-rt-ladder-step-2",
                rect = SceneRect(104f, 60f, 148f, 156f),
                stableId = "runtime.simple_rt",
                wgslImplementationId = "wgsl/runtime_simple_rt",
                uniformColor = SceneColor(0.12f, 0.32f, 0.46f, 1f),
                paintOrder = 2,
            ),
            SceneCommand.RuntimeEffectTile(
                label = "simple-rt-ladder-step-3",
                rect = SceneRect(156f, 76f, 200f, 156f),
                stableId = "runtime.simple_rt",
                wgslImplementationId = "wgsl/runtime_simple_rt",
                uniformColor = SceneColor(0.14f, 0.50f, 0.66f, 1f),
                paintOrder = 3,
            ),
            SceneCommand.RuntimeEffectTile(
                label = "simple-rt-ladder-step-4",
                rect = SceneRect(208f, 92f, 252f, 156f),
                stableId = "runtime.simple_rt",
                wgslImplementationId = "wgsl/runtime_simple_rt",
                uniformColor = SceneColor(0.16f, 0.72f, 0.84f, 1f),
                paintOrder = 4,
            ),
        ),
    )

val m7CandidatePromotionScenes: List<GPURendererScene<SceneCommand>> =
    listOf(runtimeEffectUniformLadderScene)
