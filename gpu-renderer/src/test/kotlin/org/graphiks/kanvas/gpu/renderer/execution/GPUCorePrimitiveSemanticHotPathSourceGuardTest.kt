package org.graphiks.kanvas.gpu.renderer.execution

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse

class GPUCorePrimitiveSemanticHotPathSourceGuardTest {
    @Test
    fun `coverage mask hot paths never evaluate semantic canonical hashes`() {
        val sourceRoot = File("src/main/kotlin/org/graphiks/kanvas/gpu/renderer")
        val hotPaths = listOf(
            sourceRoot.resolve(
                "recording/GPUCorePrimitivePreparedFrameTaskListBuilder.kt",
            ),
            sourceRoot.resolve("execution/GPUFramePreflighter.kt"),
            sourceRoot.resolve(
                "execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt",
            ),
        )

        hotPaths.forEach { sourceFile ->
            assertFalse(
                sourceFile.readText().contains("semantic.canonicalHash"),
                "${sourceFile.name} must validate the opaque semantic authority in O(1)",
            )
        }
    }
}
