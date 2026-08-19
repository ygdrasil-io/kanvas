package org.graphiks.kanvas.gpu.renderer.vertices

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import java.nio.file.Files
import java.nio.file.Path

class GPUPreparedVerticesRefusalCodesTest {
    @Test
    fun `canonical authority exposes each required distinct vertices and mesh refusal`() {
        val requiredCodes = setOf(
            "unsupported.vertices.topology",
            "unsupported.vertices.position_count",
            "unsupported.vertices.attribute_count",
            "unsupported.vertices.non_finite",
            "unsupported.vertices.index_out_of_range",
            "unsupported.vertices.index_format",
            "unsupported.vertices.attribute_layout",
            "unsupported.vertices.transform",
            "unsupported.vertices.color_conversion_unvalidated",
            "unsupported.vertices.primitive_blender_unregistered",
            "unsupported.vertices.material",
            "unsupported.vertices.budget",
            "unsupported.vertices.clip_coverage",
            "unsupported.mesh.bounds",
            "unsupported.mesh.program_unregistered",
            "unsupported.mesh.program_cpu_not_available",
            "unsupported.mesh.program_wgsl_not_available",
            "unsupported.mesh.program_wgsl_validation",
            "unsupported.mesh.program_abi",
            "unsupported.mesh.program_child",
            "unsupported.mesh.program_resource",
            "unsupported.mesh.budget",
        )

        assertEquals(requiredCodes, GPUPreparedVerticesRefusalCodes.ALL)
        assertEquals(requiredCodes.size, GPUPreparedVerticesRefusalCodes.ALL.size)
    }

    @Test
    fun `canonical refusal set cannot be modified`() {
        val mutableView = GPUPreparedVerticesRefusalCodes.ALL as MutableSet<String>
        val initialCodes = GPUPreparedVerticesRefusalCodes.ALL.toSet()

        try {
            assertFailsWith<UnsupportedOperationException> { mutableView.clear() }
        } finally {
            if (mutableView.isEmpty()) {
                mutableView.addAll(initialCodes)
            }
        }
        assertEquals(initialCodes, GPUPreparedVerticesRefusalCodes.ALL)
    }

    @Test
    fun `migrated refusal literals exist only in their canonical authority`() {
        val moduleRoot = Path.of(System.getProperty("user.dir")).let { workingDirectory ->
            if (Files.isDirectory(workingDirectory.resolve("src/main/kotlin"))) {
                workingDirectory
            } else {
                workingDirectory.resolve("gpu-renderer")
            }
        }
        val authority = moduleRoot
            .resolve("src/main/kotlin/org/graphiks/kanvas/gpu/renderer/vertices/GPUPreparedVerticesRefusalCodes.kt")
            .normalize()
        val sourceRoot = moduleRoot.resolve("src/main/kotlin")
        val migratedLiterals = setOf(
            "unsupported.vertices.topology",
            "unsupported.vertices.index_format",
            "unsupported.vertices.index_out_of_range",
            "unsupported.vertices.primitive_blender_unregistered",
        )
        val leaks = mutableListOf<String>()

        Files.walk(sourceRoot).use { paths ->
            paths.filter { path -> Files.isRegularFile(path) && path.normalize() != authority }
                .forEach { path ->
                    val source = Files.readString(path)
                    migratedLiterals.filter { literal -> source.contains("\"$literal\"") }
                        .forEach { literal -> leaks += "${path.fileName}:$literal" }
                }
        }

        assertEquals(emptyList(), leaks)
    }
}
