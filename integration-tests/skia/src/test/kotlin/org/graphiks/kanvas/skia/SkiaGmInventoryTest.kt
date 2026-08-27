package org.graphiks.kanvas.skia

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class SkiaGmInventoryTest {
    @Test
    fun `inventory is deterministic and maps source metadata`() {
        val root = Files.createTempDirectory("gm-inventory").toFile()
        root.resolve("reference/a.png").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(1)) }
        root.resolve("scores.properties").writeText("a=97.5\n")
        val gm = InventoryProbeGm()

        val rows = buildSkiaGmInventory(
            listOf(gm), root.resolve("reference"), root.resolve("scores.properties"),
            mapOf("a" to InventoryRenderEvidence(true, true, false, 3, listOf("route.ok"))),
        )

        assertEquals(rows, buildSkiaGmInventory(
            listOf(gm), root.resolve("reference"), root.resolve("scores.properties"),
            mapOf("a" to InventoryRenderEvidence(true, true, false, 3, listOf("route.ok"))),
        ))
        assertEquals("a", rows.single().name)
        assertEquals("PATH", rows.single().family)
        assertEquals(97.5, rows.single().score)
        assertEquals(3, rows.single().operationCount)
        assertEquals("route.ok", rows.single().firstDiagnostic)
        root.deleteRecursively()
    }

    @Test
    fun `score parser rejects duplicate and orphan rows`() {
        val root = Files.createTempDirectory("gm-scores").toFile()
        val duplicate = root.resolve("duplicate.properties").apply { writeText("a=1\na=2\n") }
        assertThrows(IllegalArgumentException::class.java) { loadSkiaGmScores(duplicate, setOf("a")) }
        val orphan = root.resolve("orphan.properties").apply { writeText("other=1\n") }
        assertThrows(IllegalArgumentException::class.java) { loadSkiaGmScores(orphan, setOf("a")) }
        root.deleteRecursively()
    }

    @Test
    fun `json export is byte stable and escapes control characters`() {
        val row = SkiaGmInventoryRow("a\u0000", "PATH", "a", false, false, true, true, null, 0, "failure\n", "bad\t")
        val json = renderSkiaGmInventoryJson(listOf(row))
        assertEquals(json, renderSkiaGmInventoryJson(listOf(row)))
        assertEquals(true, "\\u0000" in json && "\\n" in json && "\\t" in json)
    }
}

private class InventoryProbeGm : SkiaGm {
    override val name = "a"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 90.0
    override fun draw(canvas: GmCanvas, width: Int, height: Int) = Unit
}
