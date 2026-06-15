package org.graphiks.kanvas.gpu.renderer.scenes.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SceneCatalogContractsTest {
    @Test
    fun `scene ids are readable lowercase business identifiers`() {
        assertEquals("solid-card-stack", SceneId("solid-card-stack").value)
        assertFailsWith<IllegalArgumentException> { SceneId("KGPU-M1-001") }
        assertFailsWith<IllegalArgumentException> { SceneId("solid_card_stack") }
        assertFailsWith<IllegalArgumentException> { SceneId("a") }
    }

    @Test
    fun `product refusals require final product reason`() {
        assertEquals(
            "budget-exceeded",
            SceneExpectation.ProductRefusal(ProductRefusalReason.BudgetExceeded).reason.code,
        )
        assertEquals(SceneExpectation.ShouldRender, SceneExpectation.ShouldRender)
    }

    @Test
    fun `registry validation rejects duplicate ids and missing roadmap links`() {
        val scene = GPURendererScene(
            sceneId = SceneId("solid-card-stack"),
            title = "Solid Card Stack",
            description = "Rectangles with alpha and draw order.",
            dimensions = SceneDimensions(320, 200),
            tags = setOf(SceneTag.Rect, SceneTag.Blend),
            roadmapLinks = listOf(SceneRoadmapLink.milestone("M1", RStage.R1)),
            expectation = SceneExpectation.ShouldRender,
            commands = listOf("clear", "card-1"),
        )

        assertTrue(SceneRegistry(listOf(scene)).validate().isEmpty())
        assertTrue(SceneRegistry(listOf(scene, scene)).validate().any { it.contains("duplicate sceneId") })
        assertTrue(
            SceneRegistry(listOf(scene.copy(roadmapLinks = emptyList()))).validate()
                .any { it.contains("roadmapLinks") },
        )
    }
}
