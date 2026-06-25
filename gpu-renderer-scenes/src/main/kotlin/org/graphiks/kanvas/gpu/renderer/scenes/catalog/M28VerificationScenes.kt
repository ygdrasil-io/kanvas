package org.graphiks.kanvas.gpu.renderer.scenes.catalog

import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneRect

/**
 * KGPU-M28-006 (proof hardening): a saveLayer whose group alpha is < 1 and whose
 * layer contains two OVERLAPPING OPAQUE children. The overlap region is the
 * discriminator that the older `savelayer-isolated` scene cannot exercise:
 *
 * - Genuine layered compositing renders both opaque children into an isolated
 *   layer first (so the layer's coverage alpha is 1 everywhere in the union),
 *   then fades the WHOLE layer by group alpha 0.5 at composite time. The overlap
 *   region therefore blends at exactly 50% over the background, identical to the
 *   non-overlap regions.
 * - A naive "draw children directly at group alpha" path would composite child A
 *   at 50%, then child B at 50% on top, making the overlap ~75% opaque — visibly
 *   different from the non-overlap 50%.
 *
 * The layer's content card and shadow are transparent (alpha 0), so the isolated
 * layer contains ONLY the two opaque children; the discriminator is not masked by
 * an opaque card.
 */
val savelayerGroupAlphaScene: GPURendererScene<SceneCommand> =
    GPURendererScene(
        sceneId = SceneId("savelayer-group-alpha"),
        title = "SaveLayer Group Alpha",
        description = "Group-alpha layer with two overlapping opaque children; the overlap region " +
            "proves true layer isolation (uniform 50% blend) versus naive direct compositing.",
        dimensions = SceneDimensions(320, 200),
        tags = setOf(SceneTag.Layer, SceneTag.Blend),
        roadmapLinks = listOf(SceneRoadmapLink.ticket("KGPU-M28-006")),
        expectation = SceneExpectation.ShouldRender,
        commands = listOf(
            SceneCommand.Clear(SceneColor(0.05f, 0.05f, 0.06f, 1f)),
            SceneCommand.FillRect(
                label = "group-alpha-background",
                rect = SceneRect(24f, 30f, 296f, 180f),
                color = SceneColor(0.20f, 0.25f, 0.35f, 1f),
                paintOrder = 1,
            ),
            SceneCommand.SaveLayer(
                label = "group-alpha-layer",
                bounds = SceneRect(60f, 50f, 280f, 180f),
                contentRect = SceneRect(70f, 60f, 100f, 90f),
                radius = 0f,
                // Transparent content + shadow: the isolated layer carries only the
                // two opaque children below, so the overlap discriminator is clean.
                contentColor = SceneColor(0f, 0f, 0f, 0f),
                shadowColor = SceneColor(0f, 0f, 0f, 0f),
                shadowOffsetX = 0f,
                shadowOffsetY = 0f,
                paintOrder = 2,
                groupAlpha = 0.5f,
            ),
            SceneCommand.FillRect(
                label = "group-alpha-child-a",
                rect = SceneRect(80f, 70f, 200f, 140f),
                color = SceneColor(0.90f, 0.20f, 0.20f, 1f),
                paintOrder = 3,
            ),
            SceneCommand.FillRect(
                label = "group-alpha-child-b",
                rect = SceneRect(140f, 90f, 260f, 160f),
                color = SceneColor(0.20f, 0.80f, 0.30f, 1f),
                paintOrder = 4,
            ),
        ),
    )
