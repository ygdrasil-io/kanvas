package org.graphiks.kanvas.skia

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SkiaGmScannerTest {
    @Test
    fun `parser accepts comma separated nonblank names`() {
        val options = parseSkiaGmScanOptions(arrayOf("--names= c, ,a, "))

        assertEquals(setOf("c", "a"), options.names)
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
) : SkiaGm {
    override val renderFamily: RenderFamily = RenderFamily.IMAGE
    override val renderCost: RenderCost = RenderCost.FAST
    override val minSimilarity: Double = 99.0
    override fun draw(canvas: GmCanvas, width: Int, height: Int) = Unit
}
