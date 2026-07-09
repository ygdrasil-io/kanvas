package org.graphiks.kanvas.image

import java.util.ServiceLoader

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
    private val lock = Any()
    private val decoders = linkedMapOf<String, ImageDecoder>()
    private var providersLoaded = false

    fun all(): List<ImageDecoder> = synchronized(lock) {
        ensureProvidersLoadedLocked()
        return decoders.values.toList()
    }

    fun find(name: String): ImageDecoder? = synchronized(lock) {
        ensureProvidersLoadedLocked()
        return decoders[name]
    }

    fun register(decoder: ImageDecoder) = synchronized(lock) {
        ensureProvidersLoadedLocked()
        decoders[decoder.name] = decoder
    }

    fun unregister(name: String): Boolean = synchronized(lock) {
        ensureProvidersLoadedLocked()
        return decoders.remove(name) != null
    }

    fun decode(data: ByteArray, mimeType: String? = null): ImageDecodeResult {
        val snapshot = all()
        val decoder = snapshot.firstOrNull { it.matches(data) }
            ?: return ImageDecodeResult.Failure("image.decoder-unavailable")
        return decoder.decode(data)
    }

    private fun ensureProvidersLoadedLocked() {
        if (providersLoaded) return
        ServiceLoader.load(ImageDecoder::class.java).forEach { decoder ->
            decoders.putIfAbsent(decoder.name, decoder)
        }
        providersLoaded = true
    }
}
