@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Test

/** Public byte-exact coverage for W6c Crop, Offset and Tile sampling. */
class W6cSpatialBoundsSurfaceTest {
    @Test
    fun `crop clips and uses transparent black outside source`() {
        val expected = bytes(0, 255, 0)
        val surface = Surface(3, 1)
        surface.canvas {
            saveLayer(SaveLayerRec(paint = Paint(imageFilter = ImageFilter.Crop(
                RectF32.ofLTRB(0f, 0f, 3f, 1f), TileMode.DECAL,
            ))))
            drawRect(RectF32.ofLTRB(1f, 0f, 2f, 1f), Paint(ColorARGB.Blue, antiAlias = false))
            restore()
        }

        assertContentEquals(expected, surface.render().pixels)
    }

    @Test
    fun `offset moves the captured layer source exactly once`() {
        val expected = bytes(0, 255, 0)
        val surface = Surface(3, 1)
        surface.canvas {
            saveLayer(SaveLayerRec(paint = Paint(imageFilter = ImageFilter.Offset(1f, 0f))))
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.Blue, antiAlias = false))
            restore()
        }

        assertContentEquals(expected, surface.render().pixels)
    }

    @Test
    fun `tile repeats only inside destination`() {
        val expected = bytes(0, 255, 255, 0)
        val surface = Surface(4, 1)
        surface.canvas {
            saveLayer(SaveLayerRec(paint = Paint(imageFilter = ImageFilter.Tile(
                RectF32.ofLTRB(0f, 0f, 1f, 1f),
                RectF32.ofLTRB(1f, 0f, 3f, 1f),
            ))))
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.Blue, antiAlias = false))
            restore()
        }

        assertContentEquals(expected, surface.render().pixels)
    }

    @Test
    fun `fractional crop offset tile keeps origin and clip`() {
        val expected = bytes(0, 255, 255, 0)
        val surface = Surface(4, 1)
        val filter = ImageFilter.Crop(
            RectF32.ofLTRB(1.25f, 0f, 2.75f, 1f),
            TileMode.DECAL,
            ImageFilter.Offset(.5f, 0f, ImageFilter.Tile(
                RectF32.ofLTRB(0f, 0f, 1f, 1f),
                RectF32.ofLTRB(.5f, 0f, 3.5f, 1f),
            )),
        )
        surface.canvas {
            saveLayer(SaveLayerRec(paint = Paint(imageFilter = filter)))
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.Blue, antiAlias = false))
            restore()
        }

        assertContentEquals(expected, surface.render().pixels)
    }

    /** Literal RGBA8 output; each entry is one opaque-blue texel or transparent black. */
    private fun bytes(vararg blue: Int): UByteArray = UByteArray(blue.size * 4).also { result ->
        blue.forEachIndexed { indexI32, valueI32 ->
            val offsetI32 = indexI32 * 4
            result[offsetI32 + 2] = valueI32.toUByte()
            result[offsetI32 + 3] = valueI32.toUByte()
        }
    }
}
