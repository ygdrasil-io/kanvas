package org.graphiks.kanvas.skia

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Named
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
    fun `runner selection applies a half open registry index range`() {
        val selected = selectSkiaGmsForRunner(
            listOf(
                StubRunnerGm("first"),
                StubRunnerGm("second"),
                StubRunnerGm("third"),
            ),
            null,
            from = 1,
            to = 3,
        )

        assertEquals(listOf("second", "third"), selected.map { it.name })
    }

    @Test
    fun `runner selection rejects invalid registry index ranges`() {
        val gms = listOf(StubRunnerGm("first"), StubRunnerGm("second"))

        assertThrows(IllegalArgumentException::class.java) {
            selectSkiaGmsForRunner(gms, null, from = -1, to = 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            selectSkiaGmsForRunner(gms, null, from = 1, to = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            selectSkiaGmsForRunner(gms, null, from = 0, to = 3)
        }
    }

    @Test
    fun `runner parameters expose logical GM names to JUnit`() {
        val named = namedSkiaGmsForRunner(
            listOf(StubRunnerGm("first"), StubRunnerGm("second")),
        )

        assertEquals(listOf("first", "second"), named.map { it.name })
        assertEquals("second", named[1].payload.name)
    }

    @Test
    fun `reference resource path uses reference name while preserving logical name`() {
        val gm = AliasStubRunnerGm()

        assertEquals("/reference/cpp-gm.png", referenceResourcePath(gm))
        assertEquals("logical-gm", gm.name)
    }

    @Test
    fun `missing reference guidance uses the reference path`() {
        assertEquals(
            "Reference PNG not found at /reference/cpp-gm.png. Run: cp <skia-native-reference> src/test/resources/reference/cpp-gm.png",
            missingReferenceMessage("/reference/cpp-gm.png"),
        )
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
