package org.graphiks.kanvas.skia

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SkiaGmRunnerFilterTest {
    @Test
    fun `runner selection keeps every GM when no name is selected`() {
        val selected = selectSkiaGmsForRunner(
            listOf(
                StubRunnerGm("first"),
                StubRunnerGm("second"),
            ),
            null,
        )

        assertEquals(listOf("first", "second"), selected.map { it.name })
    }

    @Test
    fun `runner selection keeps only the selected GM`() {
        val selected = selectSkiaGmsForRunner(
            listOf(
                StubRunnerGm("first"),
                StubRunnerGm("text_scale_skew_rotate"),
            ),
            "text_scale_skew_rotate",
        )

        assertEquals(listOf("text_scale_skew_rotate"), selected.map { it.name })
    }

    @Test
    fun `reference resource path uses reference name while preserving logical name`() {
        val gm = AliasStubRunnerGm()

        assertEquals("/reference/cpp-gm.png", referenceResourcePath(gm))
        assertEquals("logical-gm", gm.name)
    }
}

private class StubRunnerGm(
    override val name: String,
) : SkiaGm {
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 80.0
    override fun draw(canvas: GmCanvas, width: Int, height: Int) = Unit
}

private class AliasStubRunnerGm(
    override val name: String = "logical-gm",
) : SkiaGm {
    override val referenceName: String = "cpp-gm"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 80.0
    override fun draw(canvas: GmCanvas, width: Int, height: Int) = Unit
}
