package org.skia.codec.gif

import org.skia.codec.SkCodec
import org.skia.foundation.SkEncodedImageFormat
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImageInfo
import org.graphiks.math.SkIRect
import org.skia.foundation.skcms.SkcmsICCProfile
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import javax.imageio.metadata.IIOMetadata
import javax.imageio.metadata.IIOMetadataNode
import javax.imageio.stream.ImageInputStream

/**
 * GIF decoder — D3.3 implementation of the [SkCodec] facade.
 *
 * Mirrors Skia's
 * [`SkGifDecoder`](https://github.com/google/skia/blob/main/include/codec/SkGifDecoder.h),
 * delegating the bitstream parse to [javax.imageio.ImageIO]. GIF is a
 * palette-based format with a single (or per-pixel) transparent
 * colour ; ImageIO surfaces transparency through `getRGB`'s alpha
 * byte, so the decoder produces a [SkColorType.kRGBA_8888] +
 * [SkAlphaType.kUnpremul] bitmap that matches the PNG / JPEG paths
 * for downstream consumers.
 *
 * **Animation (R-final.5).** GIF files can carry multiple frames via
 * the Graphic Control Extension. The decoder eagerly parses every
 * frame on construction (via [javax.imageio.ImageReader.getNumImages]
 * and per-frame [javax.imageio.ImageReader.read]) so [getFrameCount],
 * [getFrameInfo], and [getPixels] (with [SkCodec.Options.frameIndex])
 * surface the multi-frame view of the stream. The reconstructed
 * frames honour ImageIO's "Disposal Method" tag (`none` /
 * `doNotDispose` / `restoreToBackgroundColor` / `restoreToPrevious`)
 * via the [composeFrames] state machine in the companion ; consumers
 * that pass [SkCodec.Options.priorFrame] can shortcut the pre-frame
 * reconstruction by handing the decoder a destination already filled
 * with a known prior frame.
 *
 * **No ICC profile handling.** GIF can technically carry colour
 * profile data via Application Extensions, but no widely-deployed
 * GIF in the wild uses them ; the codec tags every bitmap with sRGB
 * for parity with the rest of the D3 family.
 */
public class SkGifCodec internal constructor(
    private val frames: List<DecodedFrame>,
    private val canvasWidth: Int,
    private val canvasHeight: Int,
) : SkCodec() {

    /**
     * Per-frame snapshot built at construction time. [pixels] is the
     * fully-composed (post-disposal, post-blend) frame at this index —
     * decoding `frameIndex = i` is a copy of `frames[i].pixels` into
     * the destination bitmap.
     */
    internal class DecodedFrame(
        val pixels: IntArray,
        val durationMs: Int,
        val requiredFrame: Int,
        val alphaType: SkAlphaType,
        val frameRect: SkIRect,
    )

    private val cachedInfo: SkImageInfo by lazy {
        SkImageInfo.Make(
            width = canvasWidth,
            height = canvasHeight,
            colorType = SkColorType.kRGBA_8888,
            alphaType = SkAlphaType.kUnpremul,
            colorSpace = SkColorSpace.makeSRGB(),
        )
    }

    override fun getInfo(): SkImageInfo = cachedInfo

    override fun getEncodedFormat(): SkEncodedImageFormat = SkEncodedImageFormat.kGIF

    override fun getICCProfile(): SkcmsICCProfile? = null

    override fun getFrameCount(): Int = frames.size

    override fun getFrameInfo(): List<FrameInfo> = frames.map { f ->
        FrameInfo(
            requiredFrame = f.requiredFrame,
            durationMs = f.durationMs,
            alphaType = f.alphaType,
            frameRect = f.frameRect,
        )
    }

    override fun getPixels(info: SkImageInfo, dst: SkBitmap): Result =
        getPixels(info, dst, Options())

    override fun getPixels(info: SkImageInfo, dst: SkBitmap, opts: Options): Result {
        if (dst.width != info.width || dst.height != info.height) {
            return Result.kInvalidParameters
        }
        if (dst.colorType != info.colorType) {
            return Result.kInvalidParameters
        }
        if (info.colorType != SkColorType.kRGBA_8888) {
            return Result.kInvalidConversion
        }
        val idx = opts.frameIndex
        if (idx < 0 || idx >= frames.size) return Result.kInvalidParameters
        // R-final.5 : `priorFrame` is honoured by trusting the caller's
        // dst-bitmap state. Our decoder pre-composes every frame at
        // construction, so we don't need the prior-frame hint to skip
        // intermediate decodes ; the field is accepted for upstream
        // signature parity. Future work (lazy on-demand decode) can
        // exploit the hint.
        System.arraycopy(frames[idx].pixels, 0, dst.pixels, 0, frames[idx].pixels.size)
        return Result.kSuccess
    }

    internal companion object Decoder : SkCodec.Decoder {

        override val name: String = "gif"

        /**
         * GIF87a (`47 49 46 38 37 61`) and GIF89a (`47 49 46 38 39 61`)
         * are the two header magics defined by the GIF spec. We accept
         * both ; ImageIO's GIF reader handles both transparently.
         */
        private val SIGNATURE_PREFIX = byteArrayOf(0x47, 0x49, 0x46, 0x38)
        private val SIGNATURE_SUFFIX_BYTE = 0x61.toByte()

        override fun matches(data: ByteArray): Boolean {
            if (data.size < 6) return false
            for (i in SIGNATURE_PREFIX.indices) {
                if (data[i] != SIGNATURE_PREFIX[i]) return false
            }
            // Byte 4 is '7' or '9' ; we don't pin it.
            val v = data[4]
            if (v != 0x37.toByte() && v != 0x39.toByte()) return false
            return data[5] == SIGNATURE_SUFFIX_BYTE
        }

        override fun make(data: ByteArray): SkCodec? {
            return try {
                decodeAllFrames(data)
            } catch (_: Throwable) {
                null
            }
        }

        /**
         * Walk every sub-image in the GIF stream via the JVM's stock
         * GIF `ImageReader`, applying the per-frame disposal method to
         * reconstruct each frame as a self-contained ARGB bitmap.
         *
         * Disposal handling matches the GIF89a spec :
         *  - `none` / `doNotDispose` (1) — the new frame paints over
         *    the previous composed frame.
         *  - `restoreToBackgroundColor` (2) — the prior frame's
         *    sub-rect is cleared to transparent before painting.
         *  - `restoreToPrevious` (3) — the canvas state from before
         *    the prior frame is restored before painting.
         *
         * Frame durations are surfaced through the GIF's Graphic
         * Control Extension `delayTime` (units of 10 ms — converted
         * to milliseconds for [FrameInfo.durationMs]).
         */
        private fun decodeAllFrames(data: ByteArray): SkGifCodec? {
            val readers = ImageIO.getImageReadersByFormatName("gif")
            if (!readers.hasNext()) return null
            val reader = readers.next()
            val iis: ImageInputStream =
                ImageIO.createImageInputStream(ByteArrayInputStream(data)) ?: return null
            try {
                reader.input = iis
                val frameCount = reader.getNumImages(true)
                if (frameCount <= 0) return null

                // Determine the canvas (logical screen) dimensions
                // from the first frame — ImageIO normalises every
                // frame's coordinate space against the screen.
                val first = reader.read(0)
                val canvasW = maxOf(reader.getWidth(0), first.width)
                val canvasH = maxOf(reader.getHeight(0), first.height)

                val composed = ArrayList<DecodedFrame>(frameCount)
                // Working canvas — the post-blend image at the *current* frame.
                var canvas = BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB)
                // Snapshot of the canvas *before* the most recent frame
                // was painted — used to restore for disposal=3.
                var preFrame: BufferedImage? = null

                for (i in 0 until frameCount) {
                    val frameImage = if (i == 0) first else reader.read(i)
                    val meta = reader.getImageMetadata(i)
                    val (offsetX, offsetY) = readImageOffset(meta)
                    val (delayMs, disposal) = readGifControl(meta)

                    // Snapshot canvas before painting (for disposal=3).
                    preFrame = copyImage(canvas)

                    val g: Graphics2D = canvas.createGraphics()
                    try {
                        g.drawImage(frameImage, offsetX, offsetY, null)
                    } finally {
                        g.dispose()
                    }

                    // Snapshot the composed frame.
                    val pixels = IntArray(canvasW * canvasH)
                    canvas.getRGB(0, 0, canvasW, canvasH, pixels, 0, canvasW)
                    // Convert ARGB → RGBA (kanvas-skia stores 8888 in
                    // host-LE Pascal-Argb, which getRGB already returns
                    // — both `SkColorSetARGB` and `BufferedImage.getRGB`
                    // pack as 0xAARRGGBB on the JVM, so no swap needed).
                    composed += DecodedFrame(
                        pixels = pixels,
                        durationMs = delayMs,
                        requiredFrame = if (i == 0) SkCodec.kNoFrame else i - 1,
                        alphaType = SkAlphaType.kUnpremul,
                        frameRect = SkIRect.MakeXYWH(
                            offsetX, offsetY, frameImage.width, frameImage.height,
                        ),
                    )

                    // Apply disposal in preparation for the next frame.
                    when (disposal) {
                        DISPOSAL_RESTORE_BACKGROUND -> {
                            val gc = canvas.createGraphics()
                            try {
                                gc.composite = java.awt.AlphaComposite.Src
                                gc.color = java.awt.Color(0, 0, 0, 0)
                                gc.fillRect(offsetX, offsetY, frameImage.width, frameImage.height)
                            } finally {
                                gc.dispose()
                            }
                        }
                        DISPOSAL_RESTORE_PREVIOUS -> {
                            canvas = preFrame!!
                        }
                        // DISPOSAL_NONE / DISPOSAL_DO_NOT_DISPOSE — leave canvas as is.
                        else -> { /* no-op */ }
                    }
                }
                return SkGifCodec(composed, canvasW, canvasH)
            } finally {
                reader.dispose()
                iis.close()
            }
        }

        /**
         * Pull `imageLeftPosition` / `imageTopPosition` from the
         * standard GIF image-descriptor metadata node. ImageIO names
         * the node `ImageDescriptor` under `javax_imageio_gif_image_1.0`.
         */
        private fun readImageOffset(meta: IIOMetadata): Pair<Int, Int> {
            val nativeFormat = meta.nativeMetadataFormatName ?: return 0 to 0
            val root = meta.getAsTree(nativeFormat) as? IIOMetadataNode ?: return 0 to 0
            val descriptors = root.getElementsByTagName("ImageDescriptor")
            if (descriptors.length == 0) return 0 to 0
            val node = descriptors.item(0) as IIOMetadataNode
            val left = node.getAttribute("imageLeftPosition")?.toIntOrNull() ?: 0
            val top = node.getAttribute("imageTopPosition")?.toIntOrNull() ?: 0
            return left to top
        }

        /**
         * Pull `delayTime` (centiseconds → ms) and `disposalMethod`
         * (string) from the Graphic Control Extension node. Returns
         * the ms duration paired with the canonical disposal-method
         * code [DISPOSAL_NONE]…[DISPOSAL_RESTORE_PREVIOUS].
         */
        private fun readGifControl(meta: IIOMetadata): Pair<Int, Int> {
            val nativeFormat = meta.nativeMetadataFormatName ?: return 0 to DISPOSAL_NONE
            val root = meta.getAsTree(nativeFormat) as? IIOMetadataNode
                ?: return 0 to DISPOSAL_NONE
            val gce = root.getElementsByTagName("GraphicControlExtension")
            if (gce.length == 0) return 0 to DISPOSAL_NONE
            val node = gce.item(0) as IIOMetadataNode
            val delayCs = node.getAttribute("delayTime")?.toIntOrNull() ?: 0
            val disposalStr = node.getAttribute("disposalMethod") ?: ""
            val disposal = when (disposalStr) {
                "doNotDispose" -> DISPOSAL_DO_NOT_DISPOSE
                "restoreToBackgroundColor" -> DISPOSAL_RESTORE_BACKGROUND
                "restoreToPrevious" -> DISPOSAL_RESTORE_PREVIOUS
                else -> DISPOSAL_NONE
            }
            return (delayCs * 10) to disposal
        }

        private fun copyImage(src: BufferedImage): BufferedImage {
            val out = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_ARGB)
            val g = out.createGraphics()
            try {
                g.composite = java.awt.AlphaComposite.Src
                g.drawImage(src, 0, 0, null)
            } finally {
                g.dispose()
            }
            return out
        }

        /** GIF89a disposal-method codes (ImageIO surfaces them as strings). */
        private const val DISPOSAL_NONE: Int = 0
        private const val DISPOSAL_DO_NOT_DISPOSE: Int = 1
        private const val DISPOSAL_RESTORE_BACKGROUND: Int = 2
        private const val DISPOSAL_RESTORE_PREVIOUS: Int = 3
    }
}
