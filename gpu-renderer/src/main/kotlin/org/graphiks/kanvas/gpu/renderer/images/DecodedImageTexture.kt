package org.graphiks.kanvas.gpu.renderer.images

import org.graphiks.kanvas.codec.Codec
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR

/** Real decoded image texture ready for GPU upload with RGBA8 pixel data and evidence. */
data class DecodedImageTexture(
    val rgba: ByteArray,
    val width: Int,
    val height: Int,
    val sourceId: String,
    val evidenceDumpLines: List<String>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DecodedImageTexture) return false
        return width == other.width && height == other.height &&
            rgba.contentEquals(other.rgba) && sourceId == other.sourceId
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + rgba.contentHashCode()
        result = 31 * result + sourceId.hashCode()
        return result
    }
}

/** Decodes PNG bytes via [Codec] (M17/M12 codec pipeline) into tightly packed RGBA8 pixel data. */
fun decodePngToRgba(pngBytes: ByteArray, sourceId: String): DecodedImageTexture? {
    val codec = Codec.MakeFromData(pngBytes) ?: return null
    val (bitmap, result) = codec.getImage()
    if (result != Codec.Result.kSuccess || bitmap == null) return null

    val w = bitmap.width
    val h = bitmap.height
    if (w <= 0 || h <= 0) return null

    val rgba = ByteArray(w * h * 4)
    for (y in 0 until h) {
        for (x in 0 until w) {
            val c = bitmap.getPixel(x, y)
            val base = (y * w + x) * 4
            rgba[base] = SkColorGetR(c).toByte()
            rgba[base + 1] = SkColorGetG(c).toByte()
            rgba[base + 2] = SkColorGetB(c).toByte()
            rgba[base + 3] = SkColorGetA(c).toByte()
        }
    }

    val uploadBytes = rgba.size.toLong()
    val evidence = listOf(
        "imageUpload:sourceId=$sourceId",
        "imageUpload:codec=png-kotlin",
        "imageUpload:dimensions=${w}x${h}",
        "imageUpload:format=RGBA8Unorm",
        "imageUpload:byteCount=$uploadBytes",
        "imageUpload:nonClaim=no-mipmaps",
    )

    return DecodedImageTexture(
        rgba = rgba,
        width = w,
        height = h,
        sourceId = sourceId,
        evidenceDumpLines = evidence,
    )
}
