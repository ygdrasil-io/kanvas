package org.skia.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkData
import org.skia.foundation.SkDeserialProcs
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSerialProcs
import org.skia.foundation.SkTypeface

/**
 * R-suivi.22 / S6-C — verifies that the `typeface` proc on
 * [SkSerialProcs] / [SkDeserialProcs] fires once per distinct
 * [SkTypeface] reached through any text-bearing record (drawString /
 * drawSimpleText / drawTextBlob).
 */
class SkPictureSerializeProcsTypefaceTest {

    private fun makePictureWithText(typeface: SkTypeface): SkPicture {
        val recorder = SkPictureRecorder()
        val canvas = recorder.beginRecording(64f, 64f)
        canvas.drawString(
            "hello",
            x = 0f,
            y = 12f,
            font = SkFont(typeface),
            paint = SkPaint(),
        )
        return recorder.finishRecordingAsPicture()
    }

    @Test
    fun `typeface proc fires once per distinct typeface`() {
        val tf = SkTypeface.MakeEmpty()
        val picture = makePictureWithText(tf)
        var invocations = 0
        val ctxToken = Any()
        var seenCtx: Any? = null
        var seenTypeface: SkTypeface? = null
        val procs = SkSerialProcs(
            typeface = { t, ctx ->
                invocations++
                seenTypeface = t
                seenCtx = ctx
                SkData.MakeWithCopy(byteArrayOf(9, 8, 7))
            },
            typefaceCtx = ctxToken,
        )
        val data = picture.serialize(procs)
        assertEquals(1, invocations)
        assertSame(tf, seenTypeface)
        assertEquals(ctxToken, seenCtx)
        assertTrue(data.size > 0)
    }

    @Test
    fun `typeface proc deduplicates the same typeface used multiple times`() {
        val tf = SkTypeface.MakeEmpty()
        val recorder = SkPictureRecorder()
        val canvas = recorder.beginRecording(64f, 64f)
        // Three drawString ops, all sharing the same typeface.
        for (i in 0 until 3) {
            canvas.drawString("foo", x = 0f, y = 8f + i * 10f, font = SkFont(tf), paint = SkPaint())
        }
        val picture = recorder.finishRecordingAsPicture()
        var invocations = 0
        picture.serialize(SkSerialProcs(typeface = { _, _ -> invocations++; null }))
        assertEquals(1, invocations, "identity dedup must collapse repeated typefaces")
    }

    @Test
    fun `MakeFromData invokes the deserialise typeface proc on every embedded blob`() {
        val tf = SkTypeface.MakeEmpty()
        val picture = makePictureWithText(tf)
        val expectedBlob = byteArrayOf(0xCA.toByte(), 0xFE.toByte())
        val serialised = picture.serialize(
            SkSerialProcs(typeface = { _, _ -> SkData.MakeWithCopy(expectedBlob) })
        )
        var deserCalls = 0
        var seenBytes: ByteArray? = null
        val deserProcs = SkDeserialProcs(
            typeface = { blob, _ ->
                deserCalls++
                seenBytes = blob.toByteArray()
                null
            },
        )
        val rebuilt = SkPicture.MakeFromData(serialised, deserProcs)
        assertNotNull(rebuilt)
        assertEquals(1, deserCalls)
        assertEquals(expectedBlob.toList(), seenBytes?.toList())
    }

    @Test
    fun `typeface proc returning null emits a zero-length blob`() {
        // The default surface has no built-in typeface serialiser ; a
        // `null` proc (or one returning null) emits a zero-length blob
        // which the reader treats as "no embedded data".
        val tf = SkTypeface.MakeEmpty()
        val picture = makePictureWithText(tf)
        // Use a proc that always returns null.
        val serialised = picture.serialize(
            SkSerialProcs(typeface = { _, _ -> null })
        )
        // The reader skips zero-length blobs ; the deser proc should
        // NOT be invoked for them.
        var deserCalls = 0
        SkPicture.MakeFromData(
            serialised,
            SkDeserialProcs(typeface = { _, _ -> deserCalls++; null })
        )
        assertEquals(0, deserCalls, "zero-length blobs must be skipped on the reader")
    }
}
