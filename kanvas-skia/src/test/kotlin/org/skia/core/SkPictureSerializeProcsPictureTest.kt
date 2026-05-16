package org.skia.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.math.SK_ColorBLUE
import org.skia.foundation.SkData
import org.skia.foundation.SkDeserialProcs
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSerialProcs

/**
 * R-suivi.22 / S6-C — verifies that the `picture` proc on
 * [SkSerialProcs] / [SkDeserialProcs] fires once per embedded
 * sub-picture during [SkPicture.serialize] / [SkPicture.MakeFromData].
 */
class SkPictureSerializeProcsPictureTest {

    private fun makeSubPicture(): SkPicture {
        val recorder = SkPictureRecorder()
        val canvas = recorder.beginRecording(4f, 4f)
        canvas.drawPaint(SkPaint().apply { color = SK_ColorBLUE })
        return recorder.finishRecordingAsPicture()
    }

    private fun makeOuterWith(sub: SkPicture): SkPicture {
        val recorder = SkPictureRecorder()
        val canvas = recorder.beginRecording(16f, 16f)
        canvas.drawPicture(sub)
        return recorder.finishRecordingAsPicture()
    }

    @Test
    fun `picture proc fires once per embedded sub-picture`() {
        val sub = makeSubPicture()
        val outer = makeOuterWith(sub)
        var invocations = 0
        val ctxToken = Any()
        var seenCtx: Any? = null
        var seenPicture: SkPicture? = null
        val procs = SkSerialProcs(
            picture = { p, ctx ->
                invocations++
                seenPicture = p
                seenCtx = ctx
                SkData.MakeWithCopy(byteArrayOf(1, 2, 3, 4, 5))
            },
            pictureCtx = ctxToken,
        )
        val data = outer.serialize(procs)
        assertEquals(1, invocations, "picture proc must fire once for one sub-picture")
        assertSame(sub, seenPicture, "proc must see the exact sub-picture reference")
        assertEquals(ctxToken, seenCtx, "pictureCtx must be threaded through")
        assertTrue(data.size > 0)
    }

    @Test
    fun `picture proc fires twice when the same sub-picture is drawn twice`() {
        // Identity-dedup is intentionally NOT applied for sub-pictures —
        // upstream's contract is one callback per occurrence.
        val sub = makeSubPicture()
        val recorder = SkPictureRecorder()
        val canvas = recorder.beginRecording(16f, 16f)
        canvas.drawPicture(sub)
        canvas.drawPicture(sub)
        val outer = recorder.finishRecordingAsPicture()
        var invocations = 0
        outer.serialize(SkSerialProcs(picture = { _, _ -> invocations++; null }))
        assertEquals(2, invocations)
    }

    @Test
    fun `MakeFromData invokes the deserialise picture proc on every embedded blob`() {
        val sub = makeSubPicture()
        val outer = makeOuterWith(sub)
        val expectedBlob = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        val serialised = outer.serialize(
            SkSerialProcs(picture = { _, _ -> SkData.MakeWithCopy(expectedBlob) })
        )

        var deserCalls = 0
        var seenBytes: ByteArray? = null
        val deserProcs = SkDeserialProcs(
            picture = { blob, _ ->
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
    fun `serialize with default procs recursively serialises the sub-picture`() {
        // No procs.picture → the default fall-back recursively serialises
        // the sub-picture. The resulting blob should be non-empty.
        val sub = makeSubPicture()
        val outer = makeOuterWith(sub)
        val data = outer.serialize()
        // The outer blob must carry at least the magic + opCount +
        // counts for images/pictures/typefaces. Plus a real sub-picture
        // blob (recursively serialised) for the one DrawPicture.
        assertTrue(data.size > 20, "expected substantial serialised output, got ${data.size}")
    }
}
