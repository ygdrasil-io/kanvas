package org.graphiks.kanvas.gpu.renderer.scenes.catalog

import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneRect

val runtimeEffectUniformScene: GPURendererScene<SceneCommand> =
    GPURendererScene(
        sceneId = SceneId("runtime-effect-uniform"),
        title = "Runtime Effect Uniform",
        description = "Four SimpleRT tiles with different gColor uniforms demonstrating per-tile uniform variation.",
        dimensions = SceneDimensions(320, 200),
        tags = setOf(SceneTag.RuntimeEffect),
        roadmapLinks = listOf(SceneRoadmapLink.milestone("M21")),
        expectation = SceneExpectation.ShouldRender,
        commands = listOf(
            SceneCommand.Clear(SceneColor(0.040f, 0.044f, 0.052f, 1f)),
            SceneCommand.RuntimeEffectTile(
                label = "uniform-tile-1",
                rect = SceneRect(16f, 16f, 76f, 184f),
                stableId = "runtime.simple_rt",
                wgslImplementationId = "wgsl/runtime_simple_rt",
                uniformColor = SceneColor(0.10f, 0.18f, 0.28f, 1f),
                paintOrder = 1,
            ),
            SceneCommand.RuntimeEffectTile(
                label = "uniform-tile-2",
                rect = SceneRect(84f, 16f, 144f, 184f),
                stableId = "runtime.simple_rt",
                wgslImplementationId = "wgsl/runtime_simple_rt",
                uniformColor = SceneColor(0.12f, 0.32f, 0.46f, 1f),
                paintOrder = 2,
            ),
            SceneCommand.RuntimeEffectTile(
                label = "uniform-tile-3",
                rect = SceneRect(152f, 16f, 212f, 184f),
                stableId = "runtime.simple_rt",
                wgslImplementationId = "wgsl/runtime_simple_rt",
                uniformColor = SceneColor(0.14f, 0.50f, 0.66f, 1f),
                paintOrder = 3,
            ),
            SceneCommand.RuntimeEffectTile(
                label = "uniform-tile-4",
                rect = SceneRect(220f, 16f, 296f, 184f),
                stableId = "runtime.simple_rt",
                wgslImplementationId = "wgsl/runtime_simple_rt",
                uniformColor = SceneColor(0.16f, 0.72f, 0.84f, 1f),
                paintOrder = 4,
            ),
        ),
    )

val runtimeEffectChildScene: GPURendererScene<SceneCommand> =
    GPURendererScene(
        sceneId = SceneId("runtime-effect-child"),
        title = "Runtime Effect Child",
        description = "SimpleRT tile with a child shader reference to exercise the child-slot descriptor path.",
        dimensions = SceneDimensions(320, 200),
        tags = setOf(SceneTag.RuntimeEffect),
        roadmapLinks = listOf(SceneRoadmapLink.milestone("M21")),
        expectation = SceneExpectation.ShouldRender,
        commands = listOf(
            SceneCommand.Clear(SceneColor(0.035f, 0.040f, 0.048f, 1f)),
            SceneCommand.RuntimeEffectTile(
                label = "child-effect-tile",
                rect = SceneRect(40f, 30f, 280f, 170f),
                stableId = "runtime.simple_rt",
                wgslImplementationId = "wgsl/runtime_simple_rt",
                uniformColor = SceneColor(0.18f, 0.42f, 0.72f, 1f),
                paintOrder = 1,
            ),
        ),
    )

val m21CandidatePromotionScenes: List<GPURendererScene<SceneCommand>> =
    listOf(runtimeEffectUniformScene, runtimeEffectChildScene)
