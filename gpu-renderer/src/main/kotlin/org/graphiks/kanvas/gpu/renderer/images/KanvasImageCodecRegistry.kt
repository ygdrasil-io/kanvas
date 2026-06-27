package org.graphiks.kanvas.gpu.renderer.images

/**
 * Concrete image codec registry wired to planned pure-Kotlin codec descriptors.
 * Provides descriptors for PNG, JPEG, WebP, and GIF formats.
 */
class KanvasImageCodecRegistry : GPUImageCodecRegistry {
    private val codecs: List<GPUImageCodecDescriptor> = listOf(
        GPUImageCodecDescriptor(
            codecName = "kanvas-png-kotlin",
            codecVersion = "descriptor:v1",
            supportedFormats = setOf("png"),
            colorManagementPolicy = "descriptor-only",
            implementationKind = "planned-pure-kotlin",
            deterministic = true,
            dependencyGate = "dependency.image.codec.png.delivery",
        ),
        GPUImageCodecDescriptor(
            codecName = "kanvas-jpeg-kotlin",
            codecVersion = "descriptor:v1",
            supportedFormats = setOf("jpeg", "jpg"),
            colorManagementPolicy = "descriptor-only",
            implementationKind = "planned-pure-kotlin",
            deterministic = true,
            dependencyGate = "dependency.image.codec.jpeg.delivery",
        ),
        GPUImageCodecDescriptor(
            codecName = "kanvas-webp-kotlin",
            codecVersion = "descriptor:v1",
            supportedFormats = setOf("webp"),
            colorManagementPolicy = "descriptor-only",
            implementationKind = "planned-pure-kotlin",
            deterministic = true,
            dependencyGate = "dependency.image.codec.webp.delivery",
        ),
        GPUImageCodecDescriptor(
            codecName = "kanvas-gif-kotlin",
            codecVersion = "descriptor:v1",
            supportedFormats = setOf("gif"),
            colorManagementPolicy = "descriptor-only",
            implementationKind = "planned-pure-kotlin",
            deterministic = true,
            dependencyGate = "dependency.image.codec.gif.delivery",
        ),
    )

    override fun findCodec(source: GPUEncodedImageSource): GPUImageCodecDescriptor? =
        codecs.find { descriptor ->
            source.containerFormat.lowercase() in descriptor.supportedFormats.map { format -> format.lowercase() }
        }
}
