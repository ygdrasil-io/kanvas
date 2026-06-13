package org.graphiks.kanvas.gpu.renderer.images

/** Image upload artifact key. */
@JvmInline
value class GPUImageUploadArtifactKey(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUImageUploadArtifactKey.value must not be blank" }
    }
}

/** Image source descriptor. */
data class GPUImageSourceDescriptor(
    val sourceId: String,
    val sourceKind: String,
    val sizeLabel: String,
    val colorProfileLabel: String? = null,
    val provenance: String,
)

/** Encoded image source descriptor. */
data class GPUEncodedImageSource(
    val sourceId: String,
    val byteHash: String,
    val containerFormat: String,
    val frameCount: Int,
)

/** Image codec descriptor. */
data class GPUImageCodecDescriptor(
    val codecName: String,
    val supportedFormats: Set<String>,
    val colorManagementPolicy: String,
    val dependencyGate: String? = null,
)

/** Image codec registry contract. */
interface GPUImageCodecRegistry {
    /** Finds a codec descriptor for an encoded source. */
    fun findCodec(source: GPUEncodedImageSource): GPUImageCodecDescriptor? = TODO("Wire GPUImageCodecRegistry to real codec delivery")
}

/** Image decode request. */
data class GPUImageDecodeRequest(
    val requestId: String,
    val source: GPUEncodedImageSource,
    val frameSelection: GPUImageFrameSelection,
    val targetColorLabel: String,
)

/** Image decode plan. */
data class GPUImageDecodePlan(
    val request: GPUImageDecodeRequest,
    val codec: GPUImageCodecDescriptor,
    val outputPixelPlan: GPUImagePixelPlan,
    val diagnostics: List<GPUImageDiagnostic> = emptyList(),
)

/** Image decode result descriptor. */
sealed interface GPUImageDecodeResult {
    /** Decode produced a typed pixel artifact. */
    data class Decoded(val pixelPlan: GPUImagePixelPlan, val artifactKey: GPUImageUploadArtifactKey) : GPUImageDecodeResult

    /** Decode is dependency-gated. */
    data class DependencyGated(val diagnostic: GPUImageDiagnostic) : GPUImageDecodeResult

    /** Decode was refused. */
    data class Refused(val diagnostic: GPUImageDiagnostic) : GPUImageDecodeResult
}

/** Animated image plan. */
data class GPUAnimatedImagePlan(
    val sourceId: String,
    val frameCount: Int,
    val timingPolicy: String,
    val selectedFrame: GPUImageFrameSelection,
)

/** Image frame info. */
data class GPUImageFrameInfo(
    val frameIndex: Int,
    val durationMillis: Long,
    val boundsLabel: String,
    val disposalMode: String,
)

/** Image frame selection. */
data class GPUImageFrameSelection(
    val frameIndex: Int,
    val timeMillis: Long? = null,
)

/** Image color decode plan. */
data class GPUImageColorDecodePlan(
    val sourceProfileLabel: String,
    val targetProfileLabel: String,
    val conversionPolicy: String,
)

/** Image orientation plan. */
data class GPUImageOrientationPlan(
    val orientation: String,
    val transformHash: String,
    val swapsDimensions: Boolean,
)

/** Image pixel layout plan. */
data class GPUImagePixelPlan(
    val width: Int,
    val height: Int,
    val format: String,
    val rowBytes: Long,
    val alphaType: String,
)

/** Image mipmap plan. */
data class GPUImageMipmapPlan(
    val generateMipmaps: Boolean,
    val levelCount: Int,
    val filterPolicy: String,
)

/** Image upload plan. */
data class GPUImageUploadPlan(
    val artifactKey: GPUImageUploadArtifactKey,
    val pixelPlan: GPUImagePixelPlan,
    val mipmapPlan: GPUImageMipmapPlan,
    val uploadBudgetClass: String,
)

/** Image pipeline plan. */
data class GPUImagePipelinePlan(
    val source: GPUImageSourceDescriptor,
    val decodePlan: GPUImageDecodePlan? = null,
    val orientationPlan: GPUImageOrientationPlan? = null,
    val colorDecodePlan: GPUImageColorDecodePlan? = null,
    val uploadPlan: GPUImageUploadPlan? = null,
    val diagnostics: List<GPUImageDiagnostic> = emptyList(),
)

/** Uploaded texture artifact descriptor. */
data class UploadedTextureArtifact(
    val artifactKey: GPUImageUploadArtifactKey,
    val pixelPlan: GPUImagePixelPlan,
    val uploadPlan: GPUImageUploadPlan,
    val generation: Long,
    val lifetimeClass: String,
    val diagnostics: List<GPUImageDiagnostic> = emptyList(),
)

/** Image diagnostic. */
data class GPUImageDiagnostic(
    val code: String,
    val sourceId: String? = null,
    val message: String,
    val terminal: Boolean,
)
