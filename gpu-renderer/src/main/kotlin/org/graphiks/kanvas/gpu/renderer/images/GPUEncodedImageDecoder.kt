package org.graphiks.kanvas.gpu.renderer.images

interface GPUEncodedImageDecoder {
    val name: String
    val supportedMimeTypes: Set<String>

    fun matches(bytes: ByteArray, mimeType: String): Boolean =
        mimeType.lowercase() in supportedMimeTypes.map { it.lowercase() }

    fun decode(bytes: ByteArray, mimeType: String): GPUImageDecodePlan
}

fun interface GPUEncodedImageDecoderRegistry {
    fun all(): List<GPUEncodedImageDecoder>

    fun find(bytes: ByteArray, mimeType: String): GPUEncodedImageDecoder? =
        all().firstOrNull { decoder -> decoder.matches(bytes, mimeType) }
}

object GPUEncodedImageDecoders : GPUEncodedImageDecoderRegistry {
    private val entries = linkedMapOf<String, GPUEncodedImageDecoder>()

    @Synchronized
    override fun all(): List<GPUEncodedImageDecoder> = entries.values.toList()

    @Synchronized
    fun register(decoder: GPUEncodedImageDecoder) {
        entries[decoder.name] = decoder
    }

    @Synchronized
    fun unregister(name: String): Boolean = entries.remove(name) != null
}
