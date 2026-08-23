package org.graphiks.kanvas.codec

import org.graphiks.math.geometry.RectI32
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.Bitmap
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.EncodedImageFormat
import org.graphiks.kanvas.image.EncodedOrigin
import org.graphiks.kanvas.image.ImageInfo
import org.graphiks.kanvas.color.icc.IccProfile
import org.graphiks.kanvas.color.ColorProfiles
import org.graphiks.kanvas.color.ColorModel
import org.graphiks.kanvas.color.ColorProfile
import org.graphiks.kanvas.color.ImageColorSpace
import org.graphiks.math.color.ColorTransferFunction
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
        assertEquals(AlphaType.UNPREMUL, animated.getCurrentFrame().alphaType)
        assertEquals(codec.getInfo().colorSpace, animated.getCurrentFrame().colorSpace)

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
        assertEquals(BLUE, dst.getArgb(0, 0))
        assertEquals(listOf(Codec.Options(frameIndex = 1, priorFrame = 0)), codec.decodedOptions)
    }

    @Test
    fun `post process picture is rasterized into current frame`() {
        val codec = RecordingAnimatedCodec(
            frames = listOf(RED),
            delaysMs = listOf(40),
            alphaType = AlphaType.PREMUL,
        )
        val animated = AnimatedImage.Make(
            codec = AndroidCodec.MakeFromCodec(codec),
            info = codec.getInfo(),
            cropRect = RectI32.ofSize(1, 1),
            postProcess = bluePostProcess(),
        )!!

        assertEquals(BLUE, animated.getCurrentFrame().getArgb(0, 0))
    }

    @Test
    fun `post process rejects metadata that Surface cannot preserve`() {
        val unpremul = RecordingAnimatedCodec(frames = listOf(RED), delaysMs = listOf(40))
        assertPostProcessRejectedBeforeDecode(unpremul)

        val rgb565 = RecordingAnimatedCodec(
            frames = listOf(RED),
            delaysMs = listOf(40),
            colorType = ColorType.RGB_565,
            alphaType = AlphaType.PREMUL,
        )
        assertPostProcessRejectedBeforeDecode(rgb565)

        val displayP3 = ImageColorSpace.fromColorProfile(ColorProfiles.displayP3())
        val p3 = RecordingAnimatedCodec(
            frames = listOf(RED),
            delaysMs = listOf(40),
            alphaType = AlphaType.PREMUL,
            colorSpace = displayP3,
        )
        assertPostProcessRejectedBeforeDecode(p3)

        val nonClassifiableIcc = RecordingAnimatedCodec(
            frames = listOf(RED),
            delaysMs = listOf(40),
            alphaType = AlphaType.PREMUL,
            colorSpace = supportedNonClassifiableIccColorSpace(),
        )
        assertPostProcessRejectedBeforeDecode(nonClassifiableIcc)
    }

    @Test
    fun `without post process preserves Display P3 and supported unclassified ICC metadata`() {
        val displayP3 = ImageColorSpace.fromColorProfile(ColorProfiles.displayP3())
        assertNoPostProcessPreserves(
            RecordingAnimatedCodec(
                frames = listOf(RED),
                delaysMs = listOf(40),
                alphaType = AlphaType.PREMUL,
                colorSpace = displayP3,
            ),
        )

        val nonClassifiableIcc = supportedNonClassifiableIccColorSpace()
        assertNull(nonClassifiableIcc.toColorSpaceOrNull())
        assertNoPostProcessPreserves(
            RecordingAnimatedCodec(
                frames = listOf(RED),
                delaysMs = listOf(40),
                alphaType = AlphaType.UNPREMUL,
                colorSpace = nonClassifiableIcc,
            ),
        )
    }

    @Test
    fun `without post process retains F16 pixels through scaling`() {
        val codec = RecordingAnimatedCodec(
            frames = listOf(RED),
            delaysMs = listOf(40),
            width = 2,
            height = 1,
            origin = EncodedOrigin.TOP_RIGHT,
            colorType = ColorType.RGBA_F16,
            alphaType = AlphaType.PREMUL,
            f16Pixel = floatArrayOf(1.5f, 0.25f, 2f, 0.5f),
        )
        val animated = requireNotNull(
            AnimatedImage.Make(
                AndroidCodec.MakeFromCodec(codec),
                ImageInfo.make(1, 1, ColorType.RGBA_F16, AlphaType.PREMUL),
                RectI32.ofSize(1, 1),
                postProcess = null,
            ),
        )
        val frame = animated.getCurrentFrame()
        val components = FloatArray(4)

        assertEquals(ColorType.RGBA_F16, frame.colorType)
        assertEquals(AlphaType.PREMUL, frame.alphaType)
        assertTrue(frame.getPremulRgbaF16(0, 0, components))
        assertEquals(1.5f, components[0], 0.001f)
        assertEquals(0.25f, components[1], 0.001f)
        assertEquals(2f, components[2], 0.001f)
        assertEquals(0.5f, components[3], 0.001f)
    }

    @Test
    fun `without post process retains RGB 565 pixels through orientation`() {
        val codec = RecordingAnimatedCodec(
            frames = listOf(RED),
            delaysMs = listOf(40),
            width = 2,
            height = 1,
            origin = EncodedOrigin.TOP_RIGHT,
            colorType = ColorType.RGB_565,
            alphaType = AlphaType.OPAQUE,
            rawPixels = byteArrayOf(0x00, 0x08, 0x00, 0x08),
        )
        val animated = requireNotNull(
            AnimatedImage.Make(
                AndroidCodec.MakeFromCodec(codec),
                ImageInfo.make(2, 1, ColorType.RGB_565, AlphaType.OPAQUE),
                RectI32.ofSize(2, 1),
                postProcess = null,
            ),
        )
        val frame = animated.getCurrentFrame()

        assertEquals(ColorType.RGB_565, frame.colorType)
        assertEquals(AlphaType.OPAQUE, frame.alphaType)
        assertEquals(0, frame.pixels[0].toInt() and 0xFF)
        assertEquals(8, frame.pixels[1].toInt() and 0xFF)
        assertEquals(0, frame.pixels[2].toInt() and 0xFF)
        assertEquals(8, frame.pixels[3].toInt() and 0xFF)
    }

    @Test
    fun `without post process retains exact ARGB 4444 pixels through orientation`() {
        val codec = RecordingAnimatedCodec(
            frames = listOf(RED),
            delaysMs = listOf(40),
            width = 2,
            height = 1,
            origin = EncodedOrigin.TOP_RIGHT,
            colorType = ColorType.ARGB_4444,
            alphaType = AlphaType.PREMUL,
            rawPixels = byteArrayOf(0x5A, 0xB4.toByte(), 0x5A, 0xB4.toByte()),
        )
        val animated = requireNotNull(
            AnimatedImage.Make(
                AndroidCodec.MakeFromCodec(codec),
                ImageInfo.make(2, 1, ColorType.ARGB_4444, AlphaType.PREMUL),
                RectI32.ofSize(2, 1),
                postProcess = null,
            ),
        )

        assertEquals(listOf(0x5A, 0xB4, 0x5A, 0xB4), animated.getCurrentFrame().pixels.map { it.toInt() and 0xFF })
    }

    @Test
    fun `factory rejects metadata conversion before decoding`() {
        val codec = RecordingAnimatedCodec(frames = listOf(RED), delaysMs = listOf(40))

        assertNull(
            AnimatedImage.Make(
                AndroidCodec.MakeFromCodec(codec),
                ImageInfo.make(1, 1, ColorType.RGB_565, AlphaType.OPAQUE),
                RectI32.ofSize(1, 1),
                postProcess = null,
            ),
        )
        assertEquals(emptyList<Int>(), codec.decodedFrameIndexes)
    }

    private fun supportedNonClassifiableIccColorSpace(): ImageColorSpace =
        ImageColorSpace.fromIccProfile(
            IccProfile.fromMatrixTrc(
                ColorProfile(
                    ColorModel.RGB,
                    requireNotNull(ColorProfiles.displayP3().toXyzD50),
                    ColorTransferFunction.parametric(
                        g = 1.8f,
                        a = 1f,
                        b = 0f,
                        c = 1f,
                        d = 0f,
                        e = 0f,
                        f = 0f,
                    ),
                ),
            ),
        )

    private fun bluePostProcess() = PictureRecorder().let { recorder ->
        recorder.beginRecording(Rect.fromXYWH(0f, 0f, 1f, 1f)).drawRect(
            Rect.fromXYWH(0f, 0f, 1f, 1f),
            Paint(color = Color.BLUE),
        )
        recorder.finishRecordingAsPicture()
    }

    private fun assertNoPostProcessPreserves(codec: RecordingAnimatedCodec) {
        val animated = requireNotNull(AnimatedImage.MakeFromCodec(codec))
        val frameInfo = animated.getCurrentFrame().info
        assertEquals(codec.getInfo().alphaType, frameInfo.alphaType)
        assertEquals(codec.getInfo().colorSpace, frameInfo.colorSpace)
    }

    private fun assertPostProcessRejectedBeforeDecode(codec: RecordingAnimatedCodec) {
        assertNull(
            AnimatedImage.Make(
                AndroidCodec.MakeFromCodec(codec),
                codec.getInfo(),
                RectI32.ofSize(1, 1),
                bluePostProcess(),
            ),
        )
        assertEquals(emptyList<Int>(), codec.decodedFrameIndexes)
        assertEquals(emptyList<Codec.Options>(), codec.decodedOptions)
    }

    private class RecordingAnimatedCodec(
        private val frames: List<Int>,
        private val delaysMs: List<Int>,
        private val width: Int = 1,
        private val height: Int = 1,
        private val origin: EncodedOrigin = EncodedOrigin.TOP_LEFT,
        private val colorType: ColorType = ColorType.RGBA_8888,
        private val alphaType: AlphaType = AlphaType.UNPREMUL,
        private val colorSpace: ImageColorSpace = ImageColorSpace.sRGB(),
        private val rawPixels: ByteArray? = null,
        private val f16Pixel: FloatArray? = null,
    ) : Codec() {
        val decodedFrameIndexes = mutableListOf<Int>()
        val decodedOptions = mutableListOf<Options>()
        private val info = ImageInfo.make(
            width = width,
            height = height,
            colorType = colorType,
            alphaType = alphaType,
            colorSpace = colorSpace,
        )

        override fun getInfo(): ImageInfo = info
        override fun getEncodedFormat(): EncodedImageFormat = EncodedImageFormat.GIF
        override fun getICCProfile(): IccProfile? = null
        override fun getOrigin(): EncodedOrigin = origin
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
            if (info != this.info || dst.width != width || dst.height != height) {
                return Result.kInvalidParameters
            }
            decodedOptions += opts
            decodedFrameIndexes += opts.frameIndex
            when {
                rawPixels != null -> rawPixels.copyInto(dst.pixels)
                f16Pixel != null -> for (y in 0 until height) for (x in 0 until width) {
                    dst.setPremulRgbaF16(x, y, f16Pixel[0], f16Pixel[1], f16Pixel[2], f16Pixel[3])
                }
                else -> for (y in 0 until height) for (x in 0 until width) {
                    dst.setArgb(x, y, frames[opts.frameIndex])
                }
            }
            return Result.kSuccess
        }
    }

    private companion object {
        private const val RED = -0x10000
        private const val BLUE = -0xffff01
    }
}
