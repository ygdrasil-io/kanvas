package org.skia.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.math.SK_ColorBLUE
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkData
import org.skia.foundation.SkDeserialProcs
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkSerialProcs

/**
 * R-suivi.22 — verifies that custom `image` procs registered on
 * [SkSerialProcs] / [SkDeserialProcs] are actually invoked by
 * [SkPicture.serialize] / [SkPicture.MakeFromData].
 *
 * The full binary picture format is out of scope for R-suivi ; what we
 * verify here is the *callback contract* (the proc fires once per
 * embedded image, with the user-supplied `ctx` threaded through).
 */
class SkPictureSerializeProcsTest {

    private fun makePictureWithImage(): SkPicture {
        // Build a tiny picture with one drawImage op so the
        // serialiser has at least one image to feed through the proc.
        val bitmap = SkBitmap(4, 4)
        SkCanvas(bitmap).clear(SK_ColorBLUE)
        val image = bitmap.asImage()

        val recorder = SkPictureRecorder()
        val canvas = recorder.beginRecording(8f, 8f)
        canvas.drawImage(image, 0f, 0f, SkSamplingOptions(), null)
        return recorder.finishRecordingAsPicture()
    }

    @Test
    fun `serialize invokes the image proc once per embedded image`() {
        val picture = makePictureWithImage()
        var invocations = 0
        val ctxToken = Any()
        var seenCtx: Any? = null
        val procs = SkSerialProcs(
            image = { _, ctx ->
                invocations++
                seenCtx = ctx
                // Return a synthetic blob so the proc path is
                // exercised end-to-end (default path would re-encode
                // via PNG, the proc lets us short-circuit).
                SkData.MakeWithCopy(byteArrayOf(1, 2, 3, 4))
            },
            imageCtx = ctxToken,
        )
        val data = picture.serialize(procs)
        assertEquals(1, invocations, "image proc must fire exactly once for one image")
        assertEquals(ctxToken, seenCtx, "imageCtx must be threaded through to the proc")
        assertNotNull(data)
        assertTrue(data.size > 0, "serialised SkData must carry at least the framing header")
    }

    @Test
    fun `MakeFromData invokes the deserialise image proc on every embedded blob`() {
        val picture = makePictureWithImage()
        // Use a custom encoder so we know exactly what the deser proc
        // will see (avoids dragging in the PNG round-trip).
        val expectedBlob = byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte())
        val serialised = picture.serialize(
            SkSerialProcs(image = { _, _ -> SkData.MakeWithCopy(expectedBlob) })
        )

        var deserCalls = 0
        var seenBytes: ByteArray? = null
        val ctxToken = Any()
        var seenCtx: Any? = null
        val deserProcs = SkDeserialProcs(
            image = { blob, ctx ->
                deserCalls++
                seenBytes = blob.toByteArray()
                seenCtx = ctx
                // Returning null is fine — the placeholder picture
                // doesn't replay the image anyway.
                null as SkImage?
            },
            imageCtx = ctxToken,
        )
        val rebuilt = SkPicture.MakeFromData(serialised, deserProcs)
        assertNotNull(rebuilt)
        assertEquals(1, deserCalls, "deser image proc must fire once per embedded blob")
        assertEquals(ctxToken, seenCtx)
        assertEquals(expectedBlob.toList(), seenBytes?.toList())
    }

    @Test
    fun `MakeFromData rejects non-kanvas data with a null result`() {
        val bogus = SkData.MakeWithCopy(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
        assertEquals(null, SkPicture.MakeFromData(bogus))
    }

    @Test
    fun `serialize with default procs still produces a non-empty SkData`() {
        // Default procs : proc=null, fall through to image.encodeToData().
        val picture = makePictureWithImage()
        val data = picture.serialize()
        assertTrue(data.size > 0, "default-procs serialisation must still emit a header")
    }

    @Test
    fun `serialize works for a picture with no images`() {
        // No drawImage ops — the image proc should never fire.
        val recorder = SkPictureRecorder()
        val canvas = recorder.beginRecording(4f, 4f)
        canvas.drawPaint(SkPaint().apply { color = SK_ColorBLUE })
        val picture = recorder.finishRecordingAsPicture()
        var invocations = 0
        val data = picture.serialize(
            SkSerialProcs(image = { _, _ -> invocations++; null })
        )
        assertEquals(0, invocations)
        assertTrue(data.size > 0)
    }
}
