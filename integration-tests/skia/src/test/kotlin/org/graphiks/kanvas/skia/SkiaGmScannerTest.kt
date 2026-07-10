package org.graphiks.kanvas.skia

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.File

class SkiaGmScannerTest {
    @Test
    fun `parser accepts comma separated nonblank names`() {
        val options = parseSkiaGmScanOptions(arrayOf("--names= c, ,a, "))

        assertEquals(setOf("c", "a"), options.names)
    }

    @Test
    fun `parser accepts registry indices and makes output path absolute`() {
        val options = parseSkiaGmScanOptions(arrayOf("--indices= 7, ,2, ", "--output", "build/scan.ndjson"))

        assertEquals(setOf(7, 2), options.indices)
        assertEquals(File("build/scan.ndjson").absolutePath, options.outputPath)
    }

    @Test
    fun `selection keeps registry order and original indices for requested names`() {
        val selected = selectSkiaGmsForScan(
            listOf(ScannerStubGm("a"), ScannerStubGm("b"), ScannerStubGm("c")),
            SkiaGmScanOptions(names = setOf("c", "a")),
        )

        assertEquals(listOf(0, 2), selected.map { it.index })
        assertEquals(listOf("a", "c"), selected.map { it.value.name })
    }

    @Test
    fun `selection rejects unknown requested names`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            selectSkiaGmsForScan(
                listOf(ScannerStubGm("a"), ScannerStubGm("b"), ScannerStubGm("c")),
                SkiaGmScanOptions(names = setOf("a", "missing")),
            )
        }

        assertEquals("Unknown Skia GM names: missing", error.message)
    }

    @Test
    fun `index selection keeps duplicate display names distinct`() {
        val selected = selectSkiaGmsForScan(
            listOf(ScannerStubGm("duplicate"), ScannerStubGm("other"), ScannerStubGm("duplicate")),
            SkiaGmScanOptions(indices = setOf(0, 2)),
        )

        assertEquals(listOf(0, 2), selected.map { it.index })
        assertEquals(listOf("duplicate", "duplicate"), selected.map { it.value.name })
    }

    @Test
    fun `index selection rejects a missing registry index`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            selectSkiaGmsForScan(
                listOf(ScannerStubGm("a"), ScannerStubGm("b")),
                SkiaGmScanOptions(indices = setOf(1, 9)),
            )
        }

        assertEquals("Unknown Skia GM indices: 9", error.message)
    }

    @Test
    fun `blocking list retains original indexes for duplicate display names`() {
        val entries = listBlockingSkiaGmEntries(
            listOf(
                ScannerStubGm("duplicate", RenderCost.BLOCKING),
                ScannerStubGm("fast"),
                ScannerStubGm("duplicate", RenderCost.BLOCKING),
            ),
        )

        assertEquals(listOf(0, 2), entries.map { it.index })
        assertEquals(listOf("duplicate", "duplicate"), entries.map { it.value.name })
    }

    @Test
    fun `named selection reports normalized effective range and empty diagnostic total`() {
        val options = SkiaGmScanOptions(from = 2, names = setOf("a", "c"))

        val selection = resolveSkiaGmScanSelection(
            listOf(ScannerStubGm("a"), ScannerStubGm("b"), ScannerStubGm("c")),
            options,
        )

        assertEquals(2, selection.total)
        assertEquals(2, selection.effectiveFrom)
        assertEquals(2, selection.effectiveTo)
        assertEquals(emptyList<IndexedValue<SkiaGm>>(), selection.gms)
        assertEquals("[SKIP] --from=2 >= total=2", selection.emptyDiagnostic)
    }
}

private class ScannerStubGm(
    override val name: String,
    override val renderCost: RenderCost = RenderCost.FAST,
) : SkiaGm {
    override val renderFamily: RenderFamily = RenderFamily.IMAGE
    override val minSimilarity: Double = 99.0
    override fun draw(canvas: GmCanvas, width: Int, height: Int) = Unit
}
