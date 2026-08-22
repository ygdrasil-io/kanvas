package org.graphiks.kanvas.gpu.renderer.scenes

import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererSceneRegistry
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.SceneExpectation

class LegacySceneFreezeTest {
    @Test
    fun `legacy scene catalog remains the frozen 88 entry surface`() {
        val scenes = GPURendererSceneRegistry.scenes
        val ids = scenes.map { it.sceneId.value }
        val sortedIds = ids.sorted()

        assertEquals(88, ids.size)
        assertEquals(ids.size, ids.toSet().size, "legacy catalog IDs must be unique")
        assertEquals(
            "f5f0e7fe9f25f140a9d36fdb453a7b12b7b07cc1b05676c5320bfce7c4e66d29",
            sha256(sortedIds.joinToString("\n")),
            "legacy catalog ID snapshot changed",
        )
        assertEquals(87, scenes.count { it.expectation is SceneExpectation.ShouldRender })
        assertEquals(1, scenes.count { it.expectation is SceneExpectation.ShouldRefuse })
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
}
