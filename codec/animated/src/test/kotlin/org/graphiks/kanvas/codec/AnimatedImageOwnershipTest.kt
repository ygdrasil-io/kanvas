package org.graphiks.kanvas.codec

import org.graphiks.math.SkIRect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import org.skia.foundation.SkEncodedImageFormat
import org.skia.foundation.SkImageInfo
import org.skia.foundation.skcms.SkcmsICCProfile

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
        assertEquals(RED, animated.getCurrentFrame().peekPixel(0, 0))

        assertEquals(70, animated.decodeNextFrame())
        assertEquals(listOf(0, 1), codec.decodedFrameIndexes)
        assertEquals(
            listOf(
                Codec.Options(frameIndex = 0, priorFrame = Codec.kNoFrame),
                Codec.Options(frameIndex = 1, priorFrame = Codec.kNoFrame),
            ),
            codec.decodedOptions,
        )
        assertEquals(BLUE, animated.getCurrentFrame().peekPixel(0, 0))

        assertEquals(AnimatedImage.kFinished, animated.decodeNextFrame())
    }

    @Test
    fun `codec options carry explicit frame and prior frame indexes`() {
        val codec = RecordingAnimatedCodec(
            frames = listOf(RED, BLUE),
            delaysMs = listOf(40, 70),
        )
        val dst = SkBitmap(1, 1, codec.getInfo().colorSpace, codec.getInfo().colorType)

        val result = codec.getPixels(
            codec.getInfo(),
            dst,
            Codec.Options(frameIndex = 1, priorFrame = 0),
        )

        assertEquals(Codec.Result.kSuccess, result)
        assertEquals(BLUE, dst.getPixel(0, 0))
        assertEquals(listOf(Codec.Options(frameIndex = 1, priorFrame = 0)), codec.decodedOptions)
    }

    private class RecordingAnimatedCodec(
        private val frames: List<Int>,
        private val delaysMs: List<Int>,
    ) : Codec() {
        val decodedFrameIndexes = mutableListOf<Int>()
        val decodedOptions = mutableListOf<Options>()
        private val info = SkImageInfo.Make(
            width = 1,
            height = 1,
            colorType = SkColorType.kRGBA_8888,
            alphaType = SkAlphaType.kUnpremul,
        )

        override fun getInfo(): SkImageInfo = info
        override fun getEncodedFormat(): SkEncodedImageFormat = SkEncodedImageFormat.kGIF
        override fun getICCProfile(): SkcmsICCProfile? = null
        override fun getFrameCount(): Int = frames.size

        override fun getFrameInfo(): List<FrameInfo> =
            delaysMs.mapIndexed { index, delayMs ->
                FrameInfo(
                    requiredFrame = if (index == 0) kNoFrame else index - 1,
                    durationMs = delayMs,
                    frameRect = SkIRect.MakeWH(info.width, info.height),
                )
            }

        override fun getPixels(info: SkImageInfo, dst: SkBitmap): Result =
            getPixels(info, dst, Options())

        override fun getPixels(info: SkImageInfo, dst: SkBitmap, opts: Options): Result {
            if (info != this.info || dst.width != 1 || dst.height != 1) {
                return Result.kInvalidParameters
            }
            decodedOptions += opts
            decodedFrameIndexes += opts.frameIndex
            dst.setPixel(0, 0, frames[opts.frameIndex])
            return Result.kSuccess
        }
    }

    private companion object {
        private const val RED = -0x10000
        private const val BLUE = -0xffff01
    }
}
