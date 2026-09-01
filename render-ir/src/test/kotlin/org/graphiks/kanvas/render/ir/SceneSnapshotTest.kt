package org.graphiks.kanvas.render.ir

import kotlin.test.Test
import kotlin.test.assertEquals

class SceneSnapshotTest {
    @Test
    fun `equivalent scene values have a stable canonical identity`() {
        val first = SceneSnapshot.of(
            extent = SceneExtent(64, 32),
            colorSpace = org.graphiks.kanvas.color.ColorSpace.SRGB,
            commands = listOf(SceneCommand.Annotation("phase", "recorded")),
        )
        val second = SceneSnapshot.of(
            extent = SceneExtent(64, 32),
            colorSpace = org.graphiks.kanvas.color.ColorSpace.SRGB,
            commands = listOf(SceneCommand.Annotation("phase", "recorded")),
        )

        assertEquals(first.canonicalId, second.canonicalId)
    }

    @Test
    fun `snapshot keeps commands recorded before the caller mutates its collection`() {
        val commands = mutableListOf<SceneCommand>(SceneCommand.Annotation("phase", "recorded"))
        val snapshot = SceneSnapshot.of(
            extent = SceneExtent(64, 32),
            colorSpace = org.graphiks.kanvas.color.ColorSpace.SRGB,
            commands = commands,
        )

        commands.clear()

        assertEquals(1, snapshot.commandCount)
        assertEquals(SceneCommand.Annotation("phase", "recorded"), snapshot.commandAt(0))
        assertEquals(listOf(SceneCommand.Annotation("phase", "recorded")), snapshot.toList())
    }
}
