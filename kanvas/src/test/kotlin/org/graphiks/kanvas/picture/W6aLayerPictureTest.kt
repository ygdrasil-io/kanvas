@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.picture

import java.nio.ByteBuffer
import java.util.Base64
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.surface.Surface
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class W6aLayerPictureTest {
    @Test
    fun currentWriterUsesPicture14Schema8() {
        val bytes = layerPicture().toByteArray()

        assertEquals(14, ByteBuffer.wrap(bytes).getInt(4))
        assertEquals(8, ByteBuffer.wrap(bytes).getInt(28))
    }

    @Test
    fun historicalV12LayerDefaultsPreviousToFalse() {
        val bytes = fixture("format-12-layer-default.base64")
        assertEquals(12, ByteBuffer.wrap(bytes).getInt(4))
        assertEquals(6, ByteBuffer.wrap(bytes).getInt(28))

        val picture = assertNotNull(Picture.fromByteArray(bytes))

        assertEquals(false, layerRecord(picture).initWithPrevious)
        assertPlaybackRenders(picture)
    }

    @Test
    fun historicalVersion8Schema1StillDecodes() {
        val bytes = fixture("format-8-schema-1-layer-default.base64")
        assertEquals(8, ByteBuffer.wrap(bytes).getInt(4))
        assertEquals(1, ByteBuffer.wrap(bytes).getInt(28))

        val picture = assertNotNull(Picture.fromByteArray(bytes))

        assertEquals(false, layerRecord(picture).initWithPrevious)
        assertPlaybackRenders(picture)
    }

    @Test
    fun writer14DistinguishesPreviousFalseAndTrue() {
        val falseBytes = layerPicture(initWithPrevious = false).toByteArray()
        val trueBytes = layerPicture(initWithPrevious = true).toByteArray()

        assertFalse(falseBytes.contentEquals(trueBytes))
    }

    @Test
    fun roundTripReencodesTheSamePreviousFlag() {
        val bytes = layerPicture(initWithPrevious = true).toByteArray()
        val decoded = assertNotNull(Picture.fromByteArray(bytes))

        assertEquals(true, layerRecord(decoded).initWithPrevious)
        assertContentEquals(bytes, decoded.toByteArray())
    }

    @Test
    fun postAppendMutationDoesNotChangeLayerBytes() {
        val bounds = RectF32.ofLTRB(1f, 1f, 7f, 7f)
        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 8f, 8f))
        canvas.saveLayer(SaveLayerRec(bounds = bounds, initWithPrevious = true))
        canvas.restore()
        val picture = recorder.finishRecordingAsPicture()
        val beforeMutation = picture.toByteArray()

        bounds.left = -100f
        bounds.right = 100f

        assertContentEquals(beforeMutation, picture.toByteArray())
    }

    @Test
    fun memoryAndWireReplayPreservePreviousPixels() {
        val expected = rgba(239, 51, 73)
        val memory = emptyPreviousPicture()
        val wire = assertNotNull(Picture.fromByteArray(memory.toByteArray()))

        listOf(memory, wire).forEach { picture ->
            val surface = Surface(1, 1)
            surface.canvas {
                drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.of(255, 239, 51, 73), antiAlias = false))
                picture.playback(this)
            }
            assertContentEquals(expected, surface.render().pixels)
        }
    }

    @Test
    fun drawPictureInsidePreviousLayerPreservesHostClip() {
        val expected = rgba(0, 0, 0, 0) + rgba(17, 61, 211) + rgba(17, 61, 211) + rgba(0, 0, 0, 0)
        val surface = Surface(4, 1)
        surface.canvas {
            clipRect(RectF32.ofLTRB(1f, 0f, 3f, 1f), antiAlias = false)
            previousPicture().playback(this)
        }

        assertContentEquals(expected, surface.render().pixels)
    }

    @Test
    fun translatedPictureLayerKeepsNonzeroOrigin() {
        val expected = rgba(239, 51, 73) + rgba(17, 61, 211) + rgba(239, 51, 73) + rgba(239, 51, 73)
        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 4f, 1f))
        canvas.translate(1f, 0f)
        canvas.saveLayer(SaveLayerRec(
            bounds = RectF32.ofLTRB(0f, 0f, 2f, 1f),
            paint = Paint(blendMode = BlendMode.SRC, antiAlias = false),
            initWithPrevious = true,
        ))
        canvas.drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false))
        canvas.restore()
        val picture = recorder.finishRecordingAsPicture()
        val surface = Surface(4, 1)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 4f, 1f), Paint(ColorARGB.of(255, 239, 51, 73), antiAlias = false))
            picture.playback(this)
        }

        assertContentEquals(expected, surface.render().pixels)
    }

    private fun layerPicture(initWithPrevious: Boolean = false): Picture {
        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 8f, 8f))
        canvas.saveLayer(
            SaveLayerRec(
                bounds = RectF32.ofLTRB(1f, 1f, 7f, 7f),
                paint = Paint(blendMode = BlendMode.SRC_OVER),
                initWithPrevious = initWithPrevious,
            ),
        )
        canvas.restore()
        return recorder.finishRecordingAsPicture()
    }

    private fun previousPicture(): Picture {
        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 4f, 1f))
        canvas.saveLayer(SaveLayerRec(initWithPrevious = true, paint = Paint(blendMode = BlendMode.SRC, antiAlias = false)))
        canvas.drawRect(RectF32.ofLTRB(0f, 0f, 4f, 1f), Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false))
        canvas.restore()
        return recorder.finishRecordingAsPicture()
    }

    private fun emptyPreviousPicture(): Picture {
        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 1f, 1f))
        canvas.saveLayer(SaveLayerRec(initWithPrevious = true, paint = Paint(blendMode = BlendMode.SRC, antiAlias = false)))
        canvas.restore()
        return recorder.finishRecordingAsPicture()
    }

    private fun rgba(red: Int, green: Int, blue: Int, alpha: Int = 255): UByteArray =
        ubyteArrayOf(red.toUByte(), green.toUByte(), blue.toUByte(), alpha.toUByte())

    private fun fixture(name: String): ByteArray = Base64.getDecoder().decode(
        requireNotNull(javaClass.getResource("/picture/$name")).readText().trim(),
    )

    private fun layerRecord(picture: Picture): SaveLayerRec {
        val layers = mutableListOf<SaveLayerRec>()
        picture.forEachOp { op -> if (op is DisplayOp.BeginLayer) layers += op.rec }
        return layers.single()
    }

    private fun assertPlaybackRenders(picture: Picture) {
        val recorder = PictureRecorder()
        picture.playback(recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 8f, 8f)))
        assertNotNull(Picture.fromByteArray(recorder.finishRecordingAsPicture().toByteArray()))
    }
}
