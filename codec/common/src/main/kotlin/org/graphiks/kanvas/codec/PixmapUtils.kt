package org.graphiks.kanvas.codec

import org.graphiks.kanvas.image.Bitmap
import org.graphiks.kanvas.image.EncodedOrigin
import org.graphiks.kanvas.image.ImageInfo
import org.graphiks.kanvas.image.capabilities

/** Pixel-preserving EXIF orientation helpers for canonical Kanvas bitmaps. */
public object PixmapUtils {
    /**
     * Copies [src] to [dst], applying [origin]. Both bitmaps must have the
     * matching layout, alpha representation, and identical [ImageInfo.colorSpace].
     */
    public fun orient(dst: Bitmap, src: Bitmap, origin: EncodedOrigin): Boolean {
        if (!src.colorType.capabilities().cpuReadableWritable) return false
        if (dst.colorType != src.colorType || dst.alphaType != src.alphaType || dst.colorSpace !== src.colorSpace) return false
        if (origin.swapsWidthHeight()) {
            if (dst.width != src.height || dst.height != src.width) return false
        } else if (dst.width != src.width || dst.height != src.height) {
            return false
        }

        val bytesPerPixel = src.colorType.bytesPerPixel
        for (sy in 0 until src.height) {
            for (sx in 0 until src.width) {
                val (dx, dy) = when (origin) {
                    EncodedOrigin.TOP_LEFT -> sx to sy
                    EncodedOrigin.TOP_RIGHT -> src.width - 1 - sx to sy
                    EncodedOrigin.BOTTOM_RIGHT -> src.width - 1 - sx to src.height - 1 - sy
                    EncodedOrigin.BOTTOM_LEFT -> sx to src.height - 1 - sy
                    EncodedOrigin.LEFT_TOP -> sy to sx
                    EncodedOrigin.RIGHT_TOP -> src.height - 1 - sy to sx
                    EncodedOrigin.RIGHT_BOTTOM -> src.height - 1 - sy to src.width - 1 - sx
                    EncodedOrigin.LEFT_BOTTOM -> sy to src.width - 1 - sx
                }
                val srcOffset = (sy * src.width + sx) * bytesPerPixel
                val dstOffset = (dy * dst.width + dx) * bytesPerPixel
                src.pixels.copyInto(dst.pixels, dstOffset, srcOffset, srcOffset + bytesPerPixel)
            }
        }
        return true
    }

    /** Returns [info] with its width and height swapped. */
    public fun swapWidthHeight(info: ImageInfo): ImageInfo = info.makeWH(info.height, info.width)
}
