package org.graphiks.kanvas.codec

import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.image.ImageDecodeResult
import org.graphiks.kanvas.image.ImageDecoder
import org.graphiks.kanvas.types.ColorSpace
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import java.security.MessageDigest

public class CodecImageDecoder : ImageDecoder {
    override val name: String = "codec"

    override fun matches(data: ByteArray): Boolean =
        Codec.Decoders.all().any { it.matches(data) }

    override fun decode(data: ByteArray): ImageDecodeResult {
        val codec = Codec.MakeFromData(data)
            ?: return ImageDecodeResult.Failure("codec.decoder-unavailable")
        val (bitmap, result) = codec.getImage()
        if (bitmap == null || result != Codec.Result.kSuccess) {
            return ImageDecodeResult.Failure("codec.decode-failed:$result")
        }

        val pixels = ByteArray(bitmap.width * bitmap.height * 4)
        for (index in bitmap.pixels8888.indices) {
            val color = bitmap.pixels8888[index]
            val offset = index * 4
            pixels[offset] = SkColorGetR(color).toByte()
            pixels[offset + 1] = SkColorGetG(color).toByte()
            pixels[offset + 2] = SkColorGetB(color).toByte()
            pixels[offset + 3] = SkColorGetA(color).toByte()
        }

        return ImageDecodeResult.Success(
            Image(
                width = bitmap.width,
                height = bitmap.height,
                colorType = ColorType.RGBA_8888,
                sourceId = "codec:${codec.getEncodedFormat().name}:${contentHash(data)}",
                pixels = pixels,
                colorSpace = ColorSpace.SRGB,
            ),
        )
    }

    private fun contentHash(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return "${data.size}:${digest.joinToString("") { "%02x".format(it) }}"
    }
}
