@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Test

/** Public byte-exact W6c evidence for the frozen separable morphology passes. */
class W6cMorphologySurfaceTest {
    @Test
    fun dilateExpandsOnePixelExactly() {
        // A radius-one rectangular Dilate grows the source center texel to all three output texels.
        val expected = opaqueRed(0, 1, 2, widthI32 = 3)
        val surface = Surface(3, 1)
        surface.canvas {
            saveLayer(SaveLayerRec(paint = Paint(imageFilter = ImageFilter.Dilate(1f, 0f))))
            drawRect(RectF32.ofLTRB(1f, 0f, 2f, 1f), Paint(ColorARGB.Red, antiAlias = false))
            restore()
        }

        assertContentEquals(expected, surface.render().pixels)
    }

    @Test
    fun erodeContractsOnePixelExactly() {
        // With transparent texels on both sides, radius-one Erode retains only the center of this run.
        val expected = opaqueRed(2)
        val surface = Surface(5, 1)
        surface.canvas {
            saveLayer(SaveLayerRec(paint = Paint(imageFilter = ImageFilter.Erode(1f, 0f))))
            drawRect(RectF32.ofLTRB(1f, 0f, 4f, 1f), Paint(ColorARGB.Red, antiAlias = false))
            restore()
        }

        assertContentEquals(expected, surface.render().pixels)
    }

    @Test
    fun asymmetricMorphologyRadiiRespectClip() {
        // Radius (1, 2) would occupy x=1..3/y=0..4; the public clip leaves only its 3x3 center.
        val expected = opaqueRed(
            6, 7, 8,
            11, 12, 13,
            16, 17, 18,
            widthI32 = 5,
            heightI32 = 5,
        )
        val surface = Surface(5, 5)
        surface.canvas {
            clipRect(RectF32.ofLTRB(1f, 1f, 4f, 4f), antiAlias = false)
            saveLayer(SaveLayerRec(paint = Paint(imageFilter = ImageFilter.Dilate(1f, 2f))))
            drawRect(RectF32.ofLTRB(2f, 2f, 3f, 3f), Paint(ColorARGB.Red, antiAlias = false))
            restore()
        }

        assertContentEquals(expected, surface.render().pixels)
    }

    @Test
    fun morphologyPictureMemoryAndWireReplayMatch() {
        // The fixed source center and radius-one X pass yield the literal all-red three-texel result.
        val expected = opaqueRed(0, 1, 2, widthI32 = 3)
        val picture = PictureRecorder().also { recorder ->
            recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 3f, 1f)).drawRect(
                RectF32.ofLTRB(1f, 0f, 2f, 1f),
                Paint(ColorARGB.Red, imageFilter = ImageFilter.Dilate(1f, 0f), antiAlias = false),
            )
        }.finishRecordingAsPicture()

        assertContentEquals(expected, render(picture))
        assertContentEquals(expected, render(assertNotNull(Picture.fromByteArray(picture.toByteArray()))))
    }

    private fun render(picture: Picture): UByteArray = Surface(3, 1).also { surface ->
        surface.canvas { picture.playback(this) }
    }.render().pixels

    /** Literal opaque-red texel positions; all omitted texels are transparent black. */
    private fun opaqueRed(vararg indicesI32: Int, widthI32: Int = 5, heightI32: Int = 1): UByteArray =
        UByteArray(widthI32 * heightI32 * 4).also { result ->
            indicesI32.forEach { indexI32 ->
                val offsetI32 = indexI32 * 4
                result[offsetI32] = 255u
                result[offsetI32 + 3] = 255u
            }
        }
}
