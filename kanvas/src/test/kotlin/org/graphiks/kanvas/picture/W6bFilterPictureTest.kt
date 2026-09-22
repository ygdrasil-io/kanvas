package org.graphiks.kanvas.picture

import java.nio.ByteBuffer
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.DropShadowMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class W6bFilterPictureTest {
    /**
     * Catches a writer which continues to emit the recursive v13 payload, or
     * a reader which aliases equal filters by value instead of captured identity.
     */
    @Test
    fun picture14PreservesSharedFilterIdentityWithoutValueAliasing() {
        val shared = ImageFilter.Blur(1f, 2f, TileMode.MIRROR)
        val equalButDistinct = ImageFilter.Blur(1f, 2f, TileMode.MIRROR)
        val picture = pictureWithThreeFilteredDraws(shared, shared, equalButDistinct)

        val bytes = picture.toByteArray()
        val memoryFilters = filtersFromPublicTraversal(picture)
        assertSame(memoryFilters[0], memoryFilters[1])
        assertNotSame(memoryFilters[0], memoryFilters[2])
        assertEquals(14, ByteBuffer.wrap(bytes).getInt(4))
        assertEquals(8, ByteBuffer.wrap(bytes).getInt(28))

        val decoded = assertNotNull(Picture.fromByteArray(bytes))
        val wireFilters = filtersFromPublicTraversal(decoded)
        assertSame(wireFilters[0], wireFilters[1])
        assertNotSame(wireFilters[0], wireFilters[2])
        assertContentEquals(bytes, decoded.toByteArray())
    }

    @Test
    fun dropShadowDefaultsToCompositeThroughPictureMemoryAndWireReplay() {
        val shadow = ImageFilter.DropShadow(1f, 2f, 3f, 4f, ColorARGB.Red)
        val picture = pictureWithThreeFilteredDraws(shadow)

        assertEquals(DropShadowMode.COMPOSITE, (filtersFromPublicTraversal(picture).single() as ImageFilter.DropShadow).mode)

        val decoded = assertNotNull(Picture.fromByteArray(picture.toByteArray()))
        assertEquals(DropShadowMode.COMPOSITE, (filtersFromPublicTraversal(decoded).single() as ImageFilter.DropShadow).mode)
    }

    private fun pictureWithThreeFilteredDraws(vararg filters: ImageFilter): Picture {
        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 3f, 1f))
        filters.forEachIndexed { index, filter ->
            canvas.drawRect(
                RectF32.ofLTRB(index.toFloat(), 0f, index + 1f, 1f),
                Paint(color = ColorARGB.Red, imageFilter = filter, antiAlias = false),
            )
        }
        return recorder.finishRecordingAsPicture()
    }

    private fun filtersFromPublicTraversal(picture: Picture): List<ImageFilter> = buildList {
        picture.forEachOp { operation ->
            val draw = operation as? DisplayOp.DrawRect ?: return@forEachOp
            draw.paint.imageFilter?.let(::add)
        }
    }
}
