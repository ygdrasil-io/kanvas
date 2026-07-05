package org.graphiks.kanvas.image

interface ImageDecoder {
    val name: String
    fun matches(data: ByteArray): Boolean
    fun decode(data: ByteArray): ImageDecodeResult
}

sealed interface ImageDecodeResult {
    data class Success(val image: Image) : ImageDecodeResult
    data class Failure(val reason: String) : ImageDecodeResult
}

object ImageDecoderRegistry {
    private val decoders = linkedMapOf<String, ImageDecoder>()

    fun all(): List<ImageDecoder> = decoders.values.toList()

    fun find(name: String): ImageDecoder? = decoders[name]

    fun register(decoder: ImageDecoder) {
        decoders[decoder.name] = decoder
    }

    fun unregister(name: String): Boolean = decoders.remove(name) != null

    fun decode(data: ByteArray, mimeType: String? = null): ImageDecodeResult {
        val decoder = decoders.values.firstOrNull { it.matches(data) }
            ?: return ImageDecodeResult.Failure("image.decoder-unavailable")
        return decoder.decode(data)
    }
}
