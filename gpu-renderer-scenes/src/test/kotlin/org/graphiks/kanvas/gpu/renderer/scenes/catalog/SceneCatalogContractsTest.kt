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
        assertFailsWith<IllegalArgumentException> { SceneId("m1-001") }
        assertFailsWith<IllegalArgumentException> { SceneId("m70-a") }
        assertFailsWith<IllegalArgumentException> { SceneId("a") }
    }

    @Test
    fun `product refusals require final product reason`() {
        assertEquals(
            listOf(
                "budget-exceeded",
                "unsupported-target-format",
                "arbitrary-sksl-source",
            ),
            ProductRefusalReason.entries.map { it.code },
        )
        assertEquals(
            "budget-exceeded",
            SceneExpectation.ProductRefusal(ProductRefusalReason.BudgetExceeded).reason.code,
        )
        assertEquals(SceneExpectation.ShouldRender, SceneExpectation.ShouldRender)
    }

    @Test
    fun `roadmap links accept active suffixed milestones`() {
        assertEquals("M70-A", SceneRoadmapLink.milestone("M70-A", RStage.R1).milestone)
        assertEquals("M70-C", SceneRoadmapLink("M70-C", null, null).milestone)
        assertEquals("M2", SceneRoadmapLink.ticket("KGPU-M2-001", RStage.R2).milestone)
    }

    @Test
    fun `roadmap links reject milestone ticket mismatches`() {
        assertFailsWith<IllegalArgumentException> {
            SceneRoadmapLink("M1", "KGPU-M2-001", null)
        }
    }

    @Test
    fun `registry validation rejects duplicate ids and missing roadmap links`() {
        val scene = sampleScene()

        assertTrue(SceneRegistry(listOf(scene)).validate().isEmpty())
        assertTrue(SceneRegistry(listOf(scene, scene)).validate().any { it.contains("duplicate sceneId") })
        assertTrue(
            SceneRegistry(listOf(scene.copy(roadmapLinks = emptyList()))).validate()
                .any { it.contains("roadmapLinks") },
        )
    }

    @Test
    fun `require scene distinguishes duplicate ids from absent ids`() {
        val scene = sampleScene()

        assertEquals(scene, SceneRegistry(listOf(scene)).requireScene("solid-card-stack"))
        assertTrue(
            assertFailsWith<IllegalStateException> {
                SceneRegistry(listOf(scene, scene)).requireScene("solid-card-stack")
            }.message.orEmpty().contains("Duplicate GPU renderer scene"),
        )
        assertTrue(
            assertFailsWith<IllegalStateException> {
                SceneRegistry(listOf(scene)).requireScene("missing-scene")
            }.message.orEmpty().contains("Unknown GPU renderer scene"),
        )
    }

    private fun sampleScene(): GPURendererScene<String> =
        GPURendererScene(
            sceneId = SceneId("solid-card-stack"),
            title = "Solid Card Stack",
            description = "Rectangles with alpha and draw order.",
            dimensions = SceneDimensions(320, 200),
            tags = setOf(SceneTag.Rect, SceneTag.Blend),
            roadmapLinks = listOf(SceneRoadmapLink.milestone("M1", RStage.R1)),
            expectation = SceneExpectation.ShouldRender,
            commands = listOf("clear", "card-1"),
        )
}
