package org.graphiks.kanvas.codec

import org.graphiks.math.geometry.RectI32
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.Bitmap
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.EncodedImageFormat
import org.graphiks.kanvas.image.ImageInfo
import org.graphiks.kanvas.color.icc.IccProfile
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

class AnimatedImageOwnershipTest {

    @Test
    fun `animated image delegates frame ownership to supplied codec`() {
        val codec = RecordingAnimatedCodec(
            frames = listOf(RED, BLUE),
            delaysMs = listOf(40, 70),
        )

        val animated = AnimatedImage.MakeFromCodec(codec)
        assertNotNull(animated)
        animated!!

        assertEquals(listOf(0), codec.decodedFrameIndexes)
        assertEquals(listOf(Codec.Options(frameIndex = 0, priorFrame = Codec.kNoFrame)), codec.decodedOptions)
        assertEquals(2, animated.getFrameCount())
        assertEquals(40, animated.currentFrameDuration())
        assertEquals(RED, animated.getCurrentFrame().getArgb(0, 0))

        assertEquals(70, animated.decodeNextFrame())
        assertEquals(listOf(0, 1), codec.decodedFrameIndexes)
        assertEquals(
            listOf(
                Codec.Options(frameIndex = 0, priorFrame = Codec.kNoFrame),
                Codec.Options(frameIndex = 1, priorFrame = Codec.kNoFrame),
            ),
            codec.decodedOptions,
        )
        assertEquals(BLUE, animated.getCurrentFrame().getArgb(0, 0))

        assertEquals(AnimatedImage.kFinished, animated.decodeNextFrame())
    }

    @Test
    fun `codec options carry explicit frame and prior frame indexes`() {
        val codec = RecordingAnimatedCodec(
            frames = listOf(RED, BLUE),
            delaysMs = listOf(40, 70),
        )
        val dst = Bitmap(codec.getInfo())

        val result = codec.getPixels(
            codec.getInfo(),
            dst,
            Codec.Options(frameIndex = 1, priorFrame = 0),
        )

        assertEquals(Codec.Result.kSuccess, result)
        assertEquals(BLUE, dst.getPixel(0, 0))
        assertEquals(listOf(Codec.Options(frameIndex = 1, priorFrame = 0)), codec.decodedOptions)
    }

    @Test
    fun `post process picture is rasterized into current frame`() {
        val codec = RecordingAnimatedCodec(frames = listOf(RED), delaysMs = listOf(40))
        val recorder = PictureRecorder()
        recorder.beginRecording(Rect.fromXYWH(0f, 0f, 1f, 1f)).drawRect(
            Rect.fromXYWH(0f, 0f, 1f, 1f),
            Paint(color = Color.BLUE),
        )
        val animated = AnimatedImage.Make(
            codec = AndroidCodec.MakeFromCodec(codec),
            info = codec.getInfo(),
            cropRect = RectI32.ofSize(1, 1),
            postProcess = recorder.finishRecordingAsPicture(),
        )!!

        assertEquals(BLUE, animated.getCurrentFrame().getArgb(0, 0))
    }

    private class RecordingAnimatedCodec(
        private val frames: List<Int>,
        private val delaysMs: List<Int>,
    ) : Codec() {
        val decodedFrameIndexes = mutableListOf<Int>()
        val decodedOptions = mutableListOf<Options>()
        private val info = ImageInfo.make(
            width = 1,
            height = 1,
            colorType = ColorType.RGBA_8888,
            alphaType = AlphaType.UNPREMUL,
        )

        override fun getInfo(): ImageInfo = info
        override fun getEncodedFormat(): EncodedImageFormat = EncodedImageFormat.GIF
        override fun getICCProfile(): IccProfile? = null
        override fun getFrameCount(): Int = frames.size

        override fun getFrameInfo(): List<FrameInfo> =
            delaysMs.mapIndexed { index, delayMs ->
                FrameInfo(
                    requiredFrame = if (index == 0) kNoFrame else index - 1,
                    durationMs = delayMs,
                    frameRect = RectI32.ofSize(info.width, info.height),
                )
            }

        override fun getPixels(info: ImageInfo, dst: Bitmap): Result =
            getPixels(info, dst, Options())

        override fun getPixels(info: ImageInfo, dst: Bitmap, opts: Options): Result {
            if (info != this.info || dst.width != 1 || dst.height != 1) {
                return Result.kInvalidParameters
            }
            decodedOptions += opts
            decodedFrameIndexes += opts.frameIndex
            dst.setArgb(0, 0, frames[opts.frameIndex])
            return Result.kSuccess
        }
    }

    private companion object {
        private const val RED = -0x10000
        private const val BLUE = -0xffff01
    }
}
