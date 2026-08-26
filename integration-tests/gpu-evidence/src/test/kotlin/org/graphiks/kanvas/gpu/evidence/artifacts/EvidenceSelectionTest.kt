package org.graphiks.kanvas.gpu.evidence.artifacts

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.graphiks.kanvas.gpu.evidence.catalog.GpuEvidenceCatalog
import org.junit.jupiter.api.io.TempDir

class EvidenceSelectionTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `all selection is explicit and preserved`() {
        assertEquals(EvidenceSelection.All, EvidenceSelectionParser.from(emptyList(), all = true))
    }

    @Test
    fun `explicit selection resolves known ids in sorted order`() {
        val selection = EvidenceSelectionParser.from(
            listOf("solid-triangle-path", "solid-card-stack"),
            all = false,
        )

        assertEquals(
            listOf("solid-card-stack", "solid-triangle-path"),
            selection.resolve(GpuEvidenceCatalog.cases).map { it.descriptor.id.value },
        )
    }

    @Test
    fun `explicit selection rejects duplicate ids`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            EvidenceSelectionParser.from(
                listOf("solid-card-stack", "solid-card-stack"),
                all = false,
            )
        }

        assertEquals("duplicate evidence scene ids: solid-card-stack", failure.message)
    }

    @Test
    fun `explicit selection rejects unknown ids during resolution`() {
        val selection = EvidenceSelectionParser.from(listOf("solid-card-stack", "unknown-scene"), all = false)

        val failure = assertFailsWith<IllegalStateException> {
            selection.resolve(GpuEvidenceCatalog.cases)
        }

        assertEquals("unknown evidence scene: unknown-scene", failure.message)
    }

    @Test
    fun `scene file trims nonblank lines and rejects duplicates`() {
        val path = tempDir.resolve("scenes.txt")
        Files.writeString(path, "\n solid-triangle-path \nsolid-card-stack\nsolid-card-stack\n")

        val failure = assertFailsWith<IllegalArgumentException> {
            EvidenceSelectionParser.readSceneFile(path)
        }

        assertEquals("duplicate evidence scene ids in file: solid-card-stack", failure.message)
    }

    @Test
    fun `scene file rejects empty selections`() {
        val path = tempDir.resolve("empty-scenes.txt")
        Files.writeString(path, " \n\t\n")

        val failure = assertFailsWith<IllegalArgumentException> {
            EvidenceSelectionParser.readSceneFile(path)
        }

        assertEquals("scene file must contain at least one evidence scene id", failure.message)
    }
}
