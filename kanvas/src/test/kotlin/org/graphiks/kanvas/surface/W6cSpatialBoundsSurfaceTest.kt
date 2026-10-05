@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.geometry.Path
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
    fun `offset maps its local displacement through scale`() {
        val expected = bytes(0, 0, 255, 255, 0)
        val surface = Surface(5, 1)
        surface.canvas {
            scale(2f, 1f)
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
    fun `tile with a source outside physical input stays transparent`() {
        val expected = bytes(0, 0, 0, 0)
        val surface = Surface(4, 1)
        surface.canvas {
            saveLayer(SaveLayerRec(paint = Paint(imageFilter = ImageFilter.Tile(
                RectF32.ofLTRB(2f, 0f, 3f, 1f),
                RectF32.ofLTRB(0f, 0f, 4f, 1f),
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

    @Test
    fun `filtered result entirely outside clip is a successful no-op and surface recovers`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 1f, 1f)
        val sentinel = UByteArray(3 * 4) { 0x5au }
        val surface = Surface(3, 1)
        surface.canvas {
            clipRect(bounds, antiAlias = false)
            saveLayer(SaveLayerRec(paint = Paint(imageFilter = ImageFilter.Offset(2f, 0f), antiAlias = false)))
            drawRect(bounds, Paint(ColorARGB.Blue, antiAlias = false))
            restore()
        }

        assertTrue(surface.readPixels(RectF32.ofLTRB(0f, 0f, 3f, 1f), sentinel))
        assertContentEquals(bytes(0, 0, 0), sentinel)

        surface.discardRecordedOperations()
        surface.canvas { drawRect(bounds, Paint(ColorARGB.Red, antiAlias = false)) }
        assertContentEquals(ubyteArrayOf(255u, 0u, 0u, 255u, 0u, 0u, 0u, 0u, 0u, 0u, 0u, 0u), surface.render().pixels)
    }

    @Test
    fun `direct filtered draw outside its clipped terminal is a no-op and surface recovers`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 1f, 1f)
        val sentinel = UByteArray(3 * 4) { 0x5au }
        val surface = Surface(3, 1)
        surface.canvas {
            clipRect(bounds, antiAlias = false)
            drawRect(bounds, Paint(ColorARGB.Blue, imageFilter = ImageFilter.Offset(2f, 0f), antiAlias = false))
        }

        assertTrue(surface.readPixels(RectF32.ofLTRB(0f, 0f, 3f, 1f), sentinel))
        assertContentEquals(bytes(0, 0, 0), sentinel)

        surface.discardRecordedOperations()
        surface.canvas { drawRect(bounds, Paint(ColorARGB.Red, antiAlias = false)) }
        assertContentEquals(ubyteArrayOf(255u, 0u, 0u, 255u, 0u, 0u, 0u, 0u, 0u, 0u, 0u, 0u), surface.render().pixels)
    }

    @Test
    fun `huge Crop and Tile domains are bounded by the downstream clip before allocation`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 1f, 1f)
        val huge = RectF32.ofLTRB(0f, 0f, 1_000_000_000f, 1f)
        val expected = bytes(255)

        listOf(
            ImageFilter.Crop(huge, TileMode.DECAL),
            ImageFilter.Tile(bounds, huge),
        ).forEach { filter ->
            val surface = Surface(1, 1)
            surface.canvas {
                clipRect(bounds, antiAlias = false)
                drawRect(bounds, Paint(ColorARGB.Blue, imageFilter = filter, antiAlias = false))
            }
            assertContentEquals(expected, surface.render().pixels)
        }
    }

    @Test
    fun `huge disjoint Crop and Tile domains are terminal no-ops and recover`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 1f, 1f)
        val disjointHuge = RectF32.ofLTRB(1f, 0f, 1_000_000_000f, 1f)

        listOf(
            ImageFilter.Crop(disjointHuge, TileMode.DECAL),
            ImageFilter.Tile(bounds, disjointHuge),
        ).forEach { filter ->
            val surface = Surface(1, 1)
            val sentinel = UByteArray(4) { 0x5au }
            surface.canvas {
                clipRect(bounds, antiAlias = false)
                drawRect(bounds, Paint(ColorARGB.Blue, imageFilter = filter, antiAlias = false))
            }
            assertTrue(surface.readPixels(bounds, sentinel))
            assertContentEquals(bytes(0), sentinel)

            surface.discardRecordedOperations()
            surface.canvas { drawRect(bounds, Paint(ColorARGB.Red, antiAlias = false)) }
            assertContentEquals(ubyteArrayOf(255u, 0u, 0u, 255u), surface.render().pixels)
        }
    }

    @Test
    fun `nested Compose retains demanded Tile output beyond terminal clip`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 1f, 1f)
        val filter = ImageFilter.Compose(
            ImageFilter.Offset(-500f, 0f),
            ImageFilter.Tile(bounds, RectF32.ofLTRB(1f, 0f, 501f, 1f)),
        )
        val surface = Surface(1, 1)
        surface.canvas { drawRect(bounds, Paint(ColorARGB.Blue, imageFilter = filter, antiAlias = false)) }

        assertContentEquals(bytes(255), surface.render().pixels)
    }

    @Test
    fun `nested Compose retains overlapping Tile output beyond terminal clip`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 1f, 1f)
        val filter = ImageFilter.Compose(
            ImageFilter.Offset(-500f, 0f),
            ImageFilter.Tile(bounds, RectF32.ofLTRB(0f, 0f, 501f, 1f)),
        )
        val surface = Surface(1, 1)
        surface.canvas { drawRect(bounds, Paint(ColorARGB.Blue, imageFilter = filter, antiAlias = false)) }

        assertContentEquals(bytes(255), surface.render().pixels)
    }

    @Test
    fun `huge nested Compose Tile refuses before readback mutation and recovers`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 1f, 1f)
        val filter = ImageFilter.Compose(
            ImageFilter.Offset(-500f, 0f),
            ImageFilter.Tile(bounds, RectF32.ofLTRB(1f, 0f, 1_000_000_000f, 1f)),
        )
        val surface = Surface(1, 1)
        val sentinel = UByteArray(4) { 0x5au }
        surface.canvas { drawRect(bounds, Paint(ColorARGB.Blue, imageFilter = filter, antiAlias = false)) }

        val failure = assertFailsWith<IllegalStateException> { surface.readPixels(bounds, sentinel) }
        assertTrue(failure.message?.startsWith("w6b.filter.frame_budget_exceeded:") == true,
            failure.message ?: "missing nested Tile budget refusal")
        assertContentEquals(UByteArray(4) { 0x5au }, sentinel)

        surface.discardRecordedOperations()
        surface.canvas { drawRect(bounds, Paint(ColorARGB.Red, antiAlias = false)) }
        assertContentEquals(ubyteArrayOf(255u, 0u, 0u, 255u), surface.render().pixels)
    }

    @Test
    fun `huge overlapping nested Compose Tile refuses before readback mutation and recovers`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 1f, 1f)
        val filter = ImageFilter.Compose(
            ImageFilter.Offset(-500f, 0f),
            ImageFilter.Tile(bounds, RectF32.ofLTRB(0f, 0f, 1_000_000_000f, 1f)),
        )
        val surface = Surface(1, 1)
        val sentinel = UByteArray(4) { 0x5au }
        surface.canvas { drawRect(bounds, Paint(ColorARGB.Blue, imageFilter = filter, antiAlias = false)) }

        val failure = assertFailsWith<IllegalStateException> { surface.readPixels(bounds, sentinel) }
        assertTrue(failure.message?.startsWith("w6b.filter.frame_budget_exceeded:") == true,
            failure.message ?: "missing overlapping nested Tile budget refusal")
        assertContentEquals(UByteArray(4) { 0x5au }, sentinel)

        surface.discardRecordedOperations()
        surface.canvas { drawRect(bounds, Paint(ColorARGB.Red, antiAlias = false)) }
        assertContentEquals(ubyteArrayOf(255u, 0u, 0u, 255u), surface.render().pixels)
    }

    @Test
    fun `direct filtered complex terminal clip refuses and same surface recovers`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 1f, 1f)
        val surface = Surface(3, 1)
        val sentinel = UByteArray(3 * 4) { 0x5au }
        surface.canvas {
            clipPath(Path().apply { addRect(bounds) }, antiAlias = false)
            drawRect(bounds, Paint(ColorARGB.Blue, imageFilter = ImageFilter.Offset(2f, 0f), antiAlias = false))
        }

        val failure = assertFailsWith<IllegalStateException> {
            surface.readPixels(RectF32.ofLTRB(0f, 0f, 3f, 1f), sentinel)
        }
        assertTrue(failure.message?.startsWith("w6b.filter.direct_terminal_clip:") == true,
            failure.message ?: "missing direct complex-clip refusal")
        assertContentEquals(UByteArray(3 * 4) { 0x5au }, sentinel)

        surface.discardRecordedOperations()
        surface.canvas { drawRect(bounds, Paint(ColorARGB.Red, antiAlias = false)) }
        assertContentEquals(ubyteArrayOf(255u, 0u, 0u, 255u, 0u, 0u, 0u, 0u, 0u, 0u, 0u, 0u), surface.render().pixels)
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
