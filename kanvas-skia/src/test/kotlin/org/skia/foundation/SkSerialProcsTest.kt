package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.skia.core.SkPicture
import org.skia.core.SkPictureRecorder
import org.graphiks.math.SkRect

/**
 * R2.17 verification suite for [SkSerialProcs] / [SkDeserialProcs].
 *
 * The structs are surface-only today (no `SkPicture.serialize(procs)`
 * consumer yet) — these tests pin the contract that
 *   1. defaults are all `null` (matches upstream `nullptr` defaults),
 *   2. data-class equality / hash work field-wise,
 *   3. lambdas can be set and invoked through the field,
 *   4. arbitrary `ctx` values can ride along.
 */
class SkSerialProcsTest {

    @Test
    fun `SkSerialProcs defaults are all null`() {
        val procs = SkSerialProcs()
        assertNull(procs.image)
        assertNull(procs.picture)
        assertNull(procs.typeface)
        assertNull(procs.imageCtx)
        assertNull(procs.pictureCtx)
        assertNull(procs.typefaceCtx)
    }

    @Test
    fun `SkDeserialProcs defaults are all null`() {
        val procs = SkDeserialProcs()
        assertNull(procs.image)
        assertNull(procs.picture)
        assertNull(procs.typeface)
        assertNull(procs.imageCtx)
        assertNull(procs.pictureCtx)
        assertNull(procs.typefaceCtx)
    }

    @Test
    fun `SkSerialProcs data-class equality is field-wise`() {
        val a = SkSerialProcs(imageCtx = "marker")
        val b = SkSerialProcs(imageCtx = "marker")
        val c = SkSerialProcs(imageCtx = "other")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }

    @Test
    fun `SkDeserialProcs data-class equality is field-wise`() {
        val a = SkDeserialProcs(typefaceCtx = 42)
        val b = SkDeserialProcs(typefaceCtx = 42)
        val c = SkDeserialProcs(typefaceCtx = 43)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }

    @Test
    fun `image proc lambda fires when explicitly invoked`() {
        val seen = mutableListOf<Pair<SkImage, Any?>>()
        val procs = SkSerialProcs(
            image = { img, ctx ->
                seen += img to ctx
                SkData.MakeWithCopy(byteArrayOf(1, 2, 3))
            },
            imageCtx = "tag",
        )
        val bitmap = SkBitmap(2, 2)
        for (i in 0 until 4) bitmap.pixels[i] = 0xFF000000.toInt() or i
        val img = SkImage.Make(bitmap)
        val out = procs.image!!(img, procs.imageCtx)
        assertNotNull(out)
        assertEquals(3, out!!.size)
        assertEquals(1, seen.size)
        assertSame(img, seen[0].first)
        assertEquals("tag", seen[0].second)
    }

    @Test
    fun `picture proc lambda fires when explicitly invoked`() {
        val rec = SkPictureRecorder()
        rec.beginRecording(SkRect.MakeWH(1f, 1f))
        val pic = rec.finishRecordingAsPicture()
        val procs = SkSerialProcs(
            picture = { _, _ -> SkData.MakeWithCopy(byteArrayOf(7, 7)) },
        )
        val out = procs.picture!!(pic, procs.pictureCtx)
        assertNotNull(out)
        assertEquals(2, out!!.size)
    }

    @Test
    fun `deserial proc lambda fires when explicitly invoked`() {
        val procs = SkDeserialProcs(
            picture = { data, _ ->
                // Drop the data, return a freshly recorded empty picture.
                val rec = SkPictureRecorder()
                rec.beginRecording(SkRect.MakeWH(data.size.toFloat(), 1f))
                rec.finishRecordingAsPicture()
            },
        )
        val recovered: SkPicture? = procs.picture!!(SkData.MakeWithCopy(ByteArray(8)), null)
        assertNotNull(recovered)
        assertEquals(8f, recovered!!.cullRect.width())
    }

    @Test
    fun `copy lets callers tweak a single field`() {
        val base = SkSerialProcs()
        val mutated = base.copy(imageCtx = "new")
        assertNull(base.imageCtx)
        assertEquals("new", mutated.imageCtx)
        // Other fields preserved.
        assertNull(mutated.image)
        assertNull(mutated.pictureCtx)
    }
}
