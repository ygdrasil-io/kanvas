package org.graphiks.kanvas.gpu.renderer.scenes.catalog

import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class M10CandidatePromotionScenesTest {
    @Test
    fun `legacy parity snapshot board carries M10 metadata and render expectation`() {
        val scene = legacyParitySnapshotBoardScene

        assertEquals("legacy-parity-snapshot-board", scene.sceneId.value)
        assertEquals("Legacy Parity Snapshot Board", scene.title)
        assertEquals(
            setOf(SceneTag.LegacyComparison, SceneTag.Rect, SceneTag.RRect),
            scene.tags,
        )
        assertEquals(listOf("M10"), scene.roadmapLinks.map { it.milestone })
        assertEquals(SceneExpectation.ShouldRender, scene.expectation)
        assertTrue(scene.description.contains("legacy", ignoreCase = true))
        assertTrue(scene.description.contains("parity", ignoreCase = true))
        assertEquals(listOf(scene), m10CandidatePromotionScenes)
    }

    @Test
    fun `legacy parity snapshot board renders three review lanes with evidence parity and retirement blockers`() {
        val scene = legacyParitySnapshotBoardScene
        val fills = scene.commands.filterIsInstance<SceneCommand.FillRect>()
        val fillLabels = fills.map { it.label }

        assertIs<SceneCommand.Clear>(scene.commands[0])
        assertIs<SceneCommand.FillRRect>(scene.commands[1])
        assertIs<SceneCommand.Clip>(scene.commands[2])
        assertEquals(12, scene.commands.size)
        assertEquals(9, fills.size)
        assertEquals(
            listOf(
                "evidence-lane-header",
                "parity-lane-header",
                "retirement-blocker-lane-header",
                "snapshot-evidence-captured",
                "snapshot-evidence-gap",
                "parity-delta-stable",
                "parity-manual-review-required",
                "retirement-blocker-open",
                "retirement-blocker-contained",
            ),
            fillLabels,
        )
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9), fills.map { it.paintOrder })
        assertTrue(fillLabels.count { it.contains("evidence") } >= 3)
        assertTrue(fillLabels.count { it.contains("parity") } >= 2)
        assertTrue(fillLabels.count { it.contains("retirement-blocker") } >= 3)
        assertTrue(
            scene.commands.all { command ->
                command is SceneCommand.Clear ||
                    command is SceneCommand.FillRRect ||
                    command is SceneCommand.Clip ||
                    command is SceneCommand.FillRect
            },
        )
    }
}
