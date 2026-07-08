package org.graphiks.kanvas.skia

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.File

class SkiaRenderGeneratorFilterTest {
    @Test
    fun `parser accepts family name and gm name filters`() {
        val options = parseSkiaRenderGeneratorOptions(
            arrayOf("/tmp/renders", "--family", "IMAGE", "--name", "DrawBitmapRect3"),
        )

        assertEquals(File("/tmp/renders"), options.outputDir)
        assertEquals(RenderFamily.IMAGE, options.family)
        assertEquals("DrawBitmapRect3", options.name)
        assertEquals(false, options.includeBlocking)
    }

    @Test
    fun `parser accepts include blocking independently of filters`() {
        val options = parseSkiaRenderGeneratorOptions(
            arrayOf("/tmp/renders", "--include-blocking", "--family", "PATH"),
        )

        assertEquals(RenderFamily.PATH, options.family)
        assertEquals(null, options.name)
        assertEquals(true, options.includeBlocking)
    }

    @Test
    fun `parser rejects unknown family`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            parseSkiaRenderGeneratorOptions(arrayOf("/tmp/renders", "--family", "NOT_A_FAMILY"))
        }

        assertEquals("Unknown GM family: NOT_A_FAMILY", error.message)
    }

    @Test
    fun `selection filters by family and name before rendering`() {
        val selected = selectSkiaGmsForRender(
            listOf(
                StubGm("DrawBitmapRect3", RenderFamily.IMAGE),
                StubGm("aaclip", RenderFamily.CLIP),
                StubGm("ImageShader", RenderFamily.IMAGE),
            ),
            SkiaRenderGeneratorOptions(
                outputDir = File("/tmp/renders"),
                family = RenderFamily.IMAGE,
                name = "ImageShader",
                includeBlocking = false,
            ),
        )

        assertEquals(listOf("ImageShader"), selected.map { it.name })
    }

    @Test
    fun `selection excludes blocking rows unless explicitly included`() {
        val selected = selectSkiaGmsForRender(
            listOf(
                StubGm("fast-image", RenderFamily.IMAGE, RenderCost.FAST),
                StubGm("blocking-image", RenderFamily.IMAGE, RenderCost.BLOCKING),
            ),
            SkiaRenderGeneratorOptions(
                outputDir = File("/tmp/renders"),
                family = RenderFamily.IMAGE,
                name = null,
                includeBlocking = false,
            ),
        )

        assertEquals(listOf("fast-image"), selected.map { it.name })
    }

    @Test
    fun `selection returns empty when no gm matches filters`() {
        val selected = selectSkiaGmsForRender(
            listOf(
                StubGm("draw-rect", RenderFamily.IMAGE),
                StubGm("clip-rect", RenderFamily.CLIP),
            ),
            SkiaRenderGeneratorOptions(
                outputDir = File("/tmp/renders"),
                family = RenderFamily.PATH,
                name = "missing-name",
                includeBlocking = false,
            ),
        )

        assertTrue(selected.isEmpty())
    }
}

private class StubGm(
    override val name: String,
    override val renderFamily: RenderFamily,
    override val renderCost: RenderCost = RenderCost.FAST,
) : SkiaGm {
    override val minSimilarity: Double = 99.0
    override fun draw(canvas: GmCanvas, width: Int, height: Int) = Unit
}
