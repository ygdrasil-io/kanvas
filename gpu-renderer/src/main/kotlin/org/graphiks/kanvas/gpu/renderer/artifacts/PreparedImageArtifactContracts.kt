package org.graphiks.kanvas.gpu.renderer.artifacts

import io.ygdrasil.webgpu.GPUTextureFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation

/** Stable identity of one immutable image upload artifact. */
@JvmInline
value class GPUImageUploadArtifactKey(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUImageUploadArtifactKey.value must not be blank" }
    }
}

/** Immutable source and normalized row layout retained by a prepared image artifact. */
data class GPUPreparedImagePixelLayout(
    val sourceRowBytes: Long,
    val normalizedRgba8RowBytes: Long,
    val rowCount: Int,
)

/** Canonical upload encoding for prepared SDR color pixels. */
internal enum class GPUPreparedColorUploadEncoding {
    StraightEncodedSrgb,
}

/** Closed SDR texture and color-interpretation contract shared by planning and execution. */
internal data class GPUPreparedSdrColorContract(
    val colorSourceTextureFormat: GPUTextureFormat,
    val coverageSourceTextureFormat: GPUTextureFormat,
    val colorUploadEncoding: GPUPreparedColorUploadEncoding,
    val targetTextureFormat: GPUTextureFormat,
    val shaderInterpretation: GPUColorInterpretation,
    val readbackInterpretation: GPUColorInterpretation,
)

/** Returns the single prepared-image SDR contract without depending on image semantics. */
internal fun preparedSdrColorContract(): GPUPreparedSdrColorContract =
    GPUPreparedSdrColorContract(
        colorSourceTextureFormat = GPUTextureFormat.RGBA8UnormSrgb,
        coverageSourceTextureFormat = GPUTextureFormat.RGBA8Unorm,
        colorUploadEncoding = GPUPreparedColorUploadEncoding.StraightEncodedSrgb,
        targetTextureFormat = GPUTextureFormat.RGBA8UnormSrgb,
        shaderInterpretation = GPUColorInterpretation.LinearPremul,
        readbackInterpretation = GPUColorInterpretation.EncodedPremulSrgb,
    )

/** Immutable normalized RGBA8 upload artifact shared across handle-free planning boundaries. */
class GPUPreparedImageUploadArtifact internal constructor(
    val key: GPUImageUploadArtifactKey,
    val width: Int,
    val height: Int,
    val pixelLayout: GPUPreparedImagePixelLayout,
    val sourceGeneration: Long,
    val contentHash: String,
    val alphaOnly: Boolean,
    val colorInterpretation: String,
    internal val colorUploadEncoding: GPUPreparedColorUploadEncoding?,
    internal val colorUploadInterpretation: String,
    rgba8UploadBytes: ByteArray,
) {
    private val snapshot = rgba8UploadBytes.copyOf()

    fun tightRgba8BytesForUpload(): ByteArray = snapshot.copyOf()
}
