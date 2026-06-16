package org.graphiks.kanvas.gpu.renderer.scenes.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand

class M0M1CandidatePromotionScenesTest {
    @Test
    fun `product route smoke lanes exports the documented M0 M1 candidate as renderable`() {
        val scene = productRouteSmokeLanesScene

        assertEquals("product-route-smoke-lanes", scene.sceneId.value)
        assertEquals("Product Route Smoke Lanes", scene.title)
        assertEquals(setOf(SceneTag.Rect, SceneTag.LegacyComparison), scene.tags)
        assertEquals(listOf("M0", "M1"), scene.roadmapLinks.map { it.milestone })
        assertEquals(SceneExpectation.ShouldRender, scene.expectation)
        assertEquals(SceneDimensions(320, 200), scene.dimensions)
    }

    @Test
    fun `product route smoke lanes stays in clear plus fill rect smoke lane family with horizontal bands`() {
        val scene = productRouteSmokeLanesScene
        val fills = scene.commands.filterIsInstance<SceneCommand.FillRect>()

        assertIs<SceneCommand.Clear>(scene.commands.first())
        assertEquals(listOf("clear") + List(fills.size) { "fill-rect" }, scene.commands.map { it.family })
        assertEquals(
            listOf(
                "legacy-smoke-lane",
                "candidate-smoke-lane",
                "rollback-smoke-lane",
                "legacy-lane-signal",
                "candidate-route-signal",
                "rollback-refusal-signal",
                "smoke-lane-divider-left",
                "smoke-lane-divider-right",
            ),
            fills.map { it.label },
        )
        assertEquals((1..8).toList(), fills.map { it.paintOrder })
        assertEquals(3, fills.count { (it.rect.right - it.rect.left) >= 240f && (it.rect.bottom - it.rect.top) <= 40f })
        assertEquals(2, fills.count { (it.rect.right - it.rect.left) <= 8f && (it.rect.bottom - it.rect.top) >= 120f })
        assertTrue(
            fills.take(3).zip(fills.take(3).drop(1)).all { (upper, lower) -> upper.rect.top < lower.rect.top },
        )
    }
}
