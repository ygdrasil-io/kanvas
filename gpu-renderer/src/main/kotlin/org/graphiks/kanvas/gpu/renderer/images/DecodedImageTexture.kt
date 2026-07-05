package org.graphiks.kanvas.gpu.renderer.images

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

/** Decodes PNG bytes through the injected GPU image decoder registry. */
fun decodePngToRgba(
    pngBytes: ByteArray,
    sourceId: String,
    decoderRegistry: GPUEncodedImageDecoderRegistry = GPUEncodedImageDecoders,
): DecodedImageTexture? {
    val plan = GPUImageDecodePlanner(decoderRegistry).plan(pngBytes, "image/png")
    val accepted = plan as? GPUImageDecodePlan.Accepted ?: return null
    val w = accepted.width
    val h = accepted.height
    if (w <= 0 || h <= 0 || accepted.colorType.lowercase() != "rgba8unorm") return null

    val rgba = accepted.pixelBytes.copyOf()

    val uploadBytes = rgba.size.toLong()
    val evidence = listOf(
        "imageUpload:sourceId=$sourceId",
        "imageUpload:decoder=registered",
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
