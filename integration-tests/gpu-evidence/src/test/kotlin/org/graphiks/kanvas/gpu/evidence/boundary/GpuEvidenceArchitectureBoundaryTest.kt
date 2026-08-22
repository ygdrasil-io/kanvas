package org.graphiks.kanvas.gpu.evidence.boundary

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse

class GpuEvidenceArchitectureBoundaryTest {
    @Test
    fun `gpu evidence has no second renderer boundary`() {
        val projectRoot = File(".").canonicalFile
        val forbidden = mapOf(
            "direct backend dependency" to Regex("wgpu4k|io\\.ygdrasil\\.webgpu"),
            "legacy module dependency" to Regex("project\\(\\\"?:gpu-renderer-scenes\\\"?\\)"),
            "legacy source import" to Regex("org\\.graphiks\\.kanvas\\.gpu\\.renderer\\.scenes"),
            "direct target allocation" to Regex("\\.createOffscreenTarget\\("),
            "direct encoding" to Regex("\\.encode(?:OffscreenTexture)?\\("),
            "scene-owned WGSL" to Regex("@(vertex|fragment|compute)|@group\\("),
        )
        val text = buildList {
            add(projectRoot.resolve("build.gradle.kts").readText())
            projectRoot.resolve("src").walkTopDown()
                .filter {
                    it.isFile &&
                        it.name != "GpuEvidenceArchitectureBoundaryTest.kt" &&
                        it.extension in setOf("kt", "kts", "wgsl")
                }
                .forEach { add(it.readText()) }
        }.joinToString("\n")
        forbidden.forEach { (label, pattern) ->
            assertFalse(pattern.containsMatchIn(text), "$label crossed the gpu-evidence boundary")
        }
    }
}
