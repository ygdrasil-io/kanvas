package org.graphiks.kanvas.skia

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.surface.RenderResult
import org.graphiks.kanvas.surface.Surface
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class SkiaGmInventoryTest {
    @AfterEach
    fun disposeSharedBackend() {
        GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun `terminal failure is rejected when Surface render was not attempted`() {
        assertThrows(IllegalArgumentException::class.java) {
            InventoryRenderEvidence(
                attempted = false,
                renderSucceeded = false,
                terminalFailure = true,
                operationCount = 0,
            )
        }
    }

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
        val row = SkiaGmInventoryRow("a\u0000", "PATH", "a", false, false, true, true, null, 0, "failure\n", "bad\t", "missing")
        val json = renderSkiaGmInventoryJson(listOf(row))
        assertEquals(json, renderSkiaGmInventoryJson(listOf(row)))
        assertTrue("\\u0000" in json && "\\n" in json && "\\t" in json)
        assertTrue("\"route\": \"failure\\n\"" in json)

        val root = Files.createTempDirectory("gm-json").toFile()
        val first = root.resolve("first.json")
        val second = root.resolve("second.json")
        writeSkiaGmInventoryJson(first, listOf(row))
        writeSkiaGmInventoryJson(second, listOf(row))
        assertEquals(Files.readAllBytes(first.toPath()).toList(), Files.readAllBytes(second.toPath()).toList())
        assertEquals(json + "\n", first.readText())
        root.deleteRecursively()
    }

    @Test
    fun `missing scores file is rejected and audit lists all sorted orphans`() {
        val root = Files.createTempDirectory("gm-audit").toFile()
        assertThrows(IllegalArgumentException::class.java) { loadSkiaGmScores(root.resolve("missing"), setOf("a")) }
        root.resolve("scores").writeText("z=1\na=2\ny=3\nz=4\n")
        val audit = auditSkiaGmScores(root.resolve("scores"), setOf("a"))
        assertEquals(listOf("y", "z"), audit.orphanRows)
        assertFalse(audit.strict)
        root.resolve("strict-scores").writeText("a=2\n")
        assertTrue(auditSkiaGmScores(root.resolve("strict-scores"), setOf("a")).strict)
        root.deleteRecursively()
    }

    @Test
    fun `reference status distinguishes missing and untrustable`() {
        val root = Files.createTempDirectory("gm-reference").toFile()
        root.resolve("reference/a.png").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(1)) }
        root.resolve("scores").writeText("a=1\n")
        val untrustable = InventoryProbeGm().withStatus(ReferenceStatusEntry("untrustable", "fixture"))
        val row = buildSkiaGmInventory(listOf(untrustable), root.resolve("reference"), root.resolve("scores")).single()
        assertEquals("untrustable", row.referenceStatus)
        assertEquals(false, row.referenceAvailable)
        root.deleteRecursively()
    }

    @Test
    fun `reference status distinguishes trusted and missing`() {
        val root = Files.createTempDirectory("gm-reference-status").toFile()
        root.resolve("reference/a.png").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(1)) }
        root.resolve("scores").writeText("a=1\nmissing=2\n")
        val missing = object : SkiaGm by InventoryProbeGm() {
            override val name = "missing"
            override val referenceName = "missing"
        }
        val rows = buildSkiaGmInventory(listOf(InventoryProbeGm(), missing), root.resolve("reference"), root.resolve("scores"))
            .associateBy { it.name }
        assertEquals("trusted", rows.getValue("a").referenceStatus)
        assertTrue(rows.getValue("a").referenceAvailable)
        assertEquals("missing", rows.getValue("missing").referenceStatus)
        assertFalse(rows.getValue("missing").referenceAvailable)
        root.deleteRecursively()
    }

    @Test
    fun `unloadable provider is setup failure and never terminal failure`() {
        val entry = SkiaGmRegistry.entries(sequenceOf("missing.inventory.Provider"), SkiaGm::class.java.classLoader).single()
        assertEquals(null, entry.gm)
        val row = providerUnloadableInventoryRow(entry)
        assertFalse(row.attempted)
        assertFalse(row.terminalFailure)
        assertEquals(InventorySetupState.FAILED, row.setupState)
        assertEquals("provider-unloadable", row.route)
        assertTrue(row.setupDiagnostic.orEmpty().contains("ClassNotFoundException"))
        assertEquals(row.setupDiagnostic, row.firstDiagnostic)
    }

    @Test
    fun `constructor setup failure keeps the GM row nonterminal`() {
        val evidence = captureInventoryEvidence(InventoryProbeGm()) {
            throw IllegalStateException("surface-construction-failed")
        }
        assertFalse(evidence.attempted)
        assertFalse(evidence.renderSucceeded)
        assertFalse(evidence.terminalFailure)
        assertEquals(InventorySetupState.FAILED, evidence.setupState)
        assertEquals("surface-construction-failed", evidence.setupDiagnostic)
        assertEquals("setup-failure", evidence.route)
    }

    @Test
    fun `one failing Surface render is terminal and is never retried`() {
        val surface = FailingRenderInventorySurface()
        val evidence = captureInventoryEvidence(InventoryProbeGm()) { surface }
        assertEquals(1, surface.renderCalls)
        assertTrue(evidence.attempted)
        assertFalse(evidence.renderSucceeded)
        assertTrue(evidence.terminalFailure)
        assertEquals(InventorySetupState.SUCCEEDED, evidence.setupState)
        assertEquals("render-failure", evidence.route)
        assertEquals("surface-render-failed", evidence.diagnostics.single())
    }

    @Test
    fun `actual Surface render refusal is terminal without requiring GPU hardware`() {
        val evidence = SkiaGmRenderer.inventoryEvidence(UnsupportedStrokeInventoryProbeGm())
        assertTrue(evidence.attempted)
        assertTrue(evidence.terminalFailure)
        assertFalse(evidence.renderSucceeded)
        assertEquals(InventorySetupState.SUCCEEDED, evidence.setupState)
        assertEquals("render-failure", evidence.route)
        assertTrue(evidence.diagnostics.single().startsWith("unsupported.stroke.rect_anti_alias:"))
    }
}

private class InventoryProbeGm : SkiaGm {
    override val name = "a"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 90.0
    override fun draw(canvas: GmCanvas, width: Int, height: Int) = Unit

    fun withStatus(status: ReferenceStatusEntry) = object : SkiaGm by this {
        override val referenceStatus = status
    }
}

private class UnsupportedStrokeInventoryProbeGm : SkiaGm by InventoryProbeGm() {
    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawRect(
            RectF32(1f, 1f, 4f, 4f),
            Paint.stroke(ColorARGB.Black, 1f),
        )
    }
}

private class FailingRenderInventorySurface : InventorySurfaceCapture {
    private val surface = Surface(8, 8)
    var renderCalls = 0

    override fun canvas() = surface.canvas()
    override fun snapshotOperationCount(): Int = surface.snapshotOps().size
    override fun render(): RenderResult {
        renderCalls += 1
        throw IllegalStateException("surface-render-failed")
    }
}
