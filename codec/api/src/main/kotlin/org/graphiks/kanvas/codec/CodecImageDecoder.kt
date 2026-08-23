package org.graphiks.kanvas.codec

import org.graphiks.kanvas.color.ColorSpaceClassification
import org.graphiks.kanvas.color.ColorSpaceClassificationFailure
import org.graphiks.kanvas.color.classifyColorSpace
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.image.ImageDecodeResult
import org.graphiks.kanvas.image.ImageDecoder
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
        val colorSpace = bitmap.colorSpace.toColorSpaceOrNull()
            ?: return ImageDecodeResult.Failure(
                "codec.color-space-unsupported:${bitmap.colorSpace.refusalReason()}",
            )
        if (bitmap.colorType != ColorType.RGBA_8888) {
            return ImageDecodeResult.Failure("codec.decode-failed:${Codec.Result.kInvalidConversion}")
        }

        return ImageDecodeResult.Success(
            Image(
                width = bitmap.width,
                height = bitmap.height,
                colorType = ColorType.RGBA_8888,
                sourceId = "codec:${codec.getEncodedFormat().name}:${contentHash(data)}",
                pixels = bitmap.pixels.copyOf(),
                colorSpace = colorSpace,
                alphaType = bitmap.alphaType,
            ),
        )
    }

    private fun contentHash(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return "${data.size}:${digest.joinToString("") { "%02x".format(it) }}"
    }

    private fun org.graphiks.kanvas.color.ImageColorSpace.refusalReason(): String =
        profileRefusalCode ?: when (val classification = colorProfile.classifyColorSpace()) {
            is ColorSpaceClassification.Unsupported -> when (classification.reason) {
                ColorSpaceClassificationFailure.PROFILE -> "profile"
                ColorSpaceClassificationFailure.GAMUT -> "gamut"
                ColorSpaceClassificationFailure.TRANSFER -> "transfer"
            }
            is ColorSpaceClassification.Supported -> "profile"
        }
}
