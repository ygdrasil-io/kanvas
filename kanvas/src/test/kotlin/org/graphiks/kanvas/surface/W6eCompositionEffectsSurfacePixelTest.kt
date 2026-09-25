@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Test

/** Public byte-exact W6e convergence evidence for the six composition filter families. */
class W6eCompositionEffectsSurfacePixelTest {
    @Test
    fun compositionShardPreservesContextAndInputOrder() {
        // Every literal expected is fixed before the first Surface exists.  Luma(red) rounds to
        // transparent black with alpha 54; Compose first moves red, then evaluates that luma.
        val colorFilterExpected = ubyteArrayOf(0u, 0u, 0u, 54u)
        val composeExpected = ubyteArrayOf(0u, 0u, 0u, 0u, 0u, 0u, 0u, 54u)
        // Merge is source-over in declared order.  W5f first converts the two alpha-128 black
        // filters to linear-premul, then the final sRGB OETF rounds the remaining red to 136.
        // One missing shared input and reversed order are distinct, so this byte witnesses both
        // duplication and order without replacing W5f's numeric authority.
        val mergeExpected = ubyteArrayOf(136u, 0u, 0u, 255u)
        // SRC_IN retains the foreground when both inputs are opaque, so the declared blue
        // foreground must remain blue rather than the red background.
        val blendExpected = ubyteArrayOf(0u, 0u, 255u, 255u)
        val dilateExpected = opaqueRed(0, 1, 2, width = 3)
        val erodeExpected = opaqueRed(2, width = 5)

        assertRenderedExactly(colorFilterExpected) {
            renderFiltered(1, 1, ImageFilter.ColorFilter(ColorFilter.Luma)) {
                drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.Red, antiAlias = false))
            }
        }
        assertRenderedExactly(composeExpected) {
            renderFiltered(2, 1, ImageFilter.Compose(
                ImageFilter.ColorFilter(ColorFilter.Luma), ImageFilter.Offset(1f, 0f),
            )) {
                drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.Red, antiAlias = false))
            }
        }
        assertRenderedExactly(mergeExpected) {
            val red = ImageFilter.ColorFilter(ColorFilter.Blend(ColorARGB.Red, BlendMode.SRC))
            val sharedBlack = ImageFilter.ColorFilter(ColorFilter.Blend(
                ColorARGB.of(128, 0, 0, 0), BlendMode.SRC,
            ))
            renderFiltered(1, 1, ImageFilter.Merge(listOf(red, sharedBlack, sharedBlack))) {
                drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.White, antiAlias = false))
            }
        }
        assertRenderedExactly(blendExpected) {
            val background = ImageFilter.ColorFilter(ColorFilter.Blend(ColorARGB.Red, BlendMode.SRC))
            val foreground = ImageFilter.ColorFilter(ColorFilter.Blend(ColorARGB.Blue, BlendMode.SRC))
            renderFiltered(1, 1, ImageFilter.Blend(BlendMode.SRC_IN, background, foreground)) {
                drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.White, antiAlias = false))
            }
        }
        assertRenderedExactly(dilateExpected) {
            renderFiltered(3, 1, ImageFilter.Dilate(1f, 0f)) {
                drawRect(RectF32.ofLTRB(1f, 0f, 2f, 1f), Paint(ColorARGB.Red, antiAlias = false))
            }
        }
        assertRenderedExactly(erodeExpected) {
            renderFiltered(5, 1, ImageFilter.Erode(1f, 0f)) {
                drawRect(RectF32.ofLTRB(1f, 0f, 4f, 1f), Paint(ColorARGB.Red, antiAlias = false))
            }
        }
    }

    private fun assertRenderedExactly(expected: UByteArray, render: () -> RenderResult) {
        val actual = render()
        assertContentEquals(expected, actual.pixels)
        assertTrue(actual.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            actual.nativeEvidenceScopeKinds.toString())
    }

    private fun renderFiltered(
        width: Int,
        height: Int,
        filter: ImageFilter,
        draw: org.graphiks.kanvas.canvas.Canvas.() -> Unit,
    ): RenderResult = Surface(width, height).also { surface ->
        surface.canvas {
            saveLayer(SaveLayerRec(paint = Paint(imageFilter = filter, antiAlias = false)))
            draw()
            restore()
        }
    }.render()

    private fun opaqueRed(vararg indices: Int, width: Int): UByteArray = UByteArray(width * 4).also { result ->
        indices.forEach { index ->
            result[index * 4] = 255u
            result[index * 4 + 3] = 255u
        }
    }
}
