package org.graphiks.kanvas.gpu.renderer.payloads

import io.ygdrasil.webgpu.GPUTextureFormat
import java.security.MessageDigest
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedImageUploadArtifact
import org.graphiks.kanvas.gpu.renderer.artifacts.preparedSdrColorContract
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.state.GPUFrameProvenance

/** Closed device-geometry ABI for a prepared sampled image. */
enum class GPUPreparedImageGeometryClass { Rect, Quad }

/** Closed sampler selection; sampler descriptors are deliberately not pipeline-key axes. */
enum class GPUPreparedImageSampling { Nearest, Linear }

/**
 * Declares the image capability selected by a caller before Surface lowering.
 *
 * The generic route retains the native linear/scaled image implementation.
 * W28 selects [BoundedNearest1To1] so its evidence is limited to the narrower
 * whole-image, integer 1:1, nearest contract rather than redefining that
 * generic route.
 */
enum class GPUPreparedImageRouteCapability { GenericNative, BoundedNearest1To1 }

/** Closed source blend choices for alpha-atlas colorization. */
enum class GPUPreparedAtlasSourceBlend { Src, Dst, SrcOver, Plus, Modulate }

/** One handle-free vertex retaining exact device position and texture coordinates. */
data class GPUPreparedImageVertex(val x: Float, val y: Float, val u: Float, val v: Float)

/**
 * Immutable four-corner image geometry. The quad is never reduced to an axis-aligned bounding box,
 * so rotation, reflection, scale, and skew preserve their exact position/UV correspondence.
 */
class GPUPreparedImageGeometry internal constructor(
    val geometryClass: GPUPreparedImageGeometryClass,
    vertices: List<GPUPreparedImageVertex>,
    indices: List<Int>,
) {
    val vertices: List<GPUPreparedImageVertex> = immutableList(vertices)
    val indices: List<Int> = immutableList(indices)

    init {
        require(this.vertices.size == 4) { "Prepared image geometry requires exactly four vertices" }
        require(this.vertices.all { it.x.isFinite() && it.y.isFinite() && it.u.isFinite() && it.v.isFinite() }) {
            "Prepared image geometry must be finite"
        }
        require(this.indices == GPU_PREPARED_IMAGE_FIXED_INDICES) {
            "Prepared image geometry requires canonical indices 0,1,2,0,2,3"
        }
    }
}

/** Structural specialization facts only; artifact identity and uniform/sampler values are excluded. */
data class GPUPreparedImagePipelineKey(
    val destinationBlendState: String,
    val targetFormat: String,
    val bindingLayoutHash: String,
)

/** Input gathered after image preparation and before any native resource is materialized. */
data class GPUPreparedImagePayloadInput(
    val payloadRef: GPUDrawPayloadRef,
    val artifact: GPUPreparedImageUploadArtifact,
    val geometry: GPUPreparedImageGeometry,
    val sampling: GPUPreparedImageSampling,
    val tintPremultipliedRgba: List<Float>,
    val atlasColorPremultipliedRgba: List<Float>?,
    val atlasSourceBlend: GPUPreparedAtlasSourceBlend?,
    val targetBounds: GPUPixelBounds,
    val scissorBounds: GPUPixelBounds,
    val blendPlanIdentity: String,
    val frameProvenance: GPUFrameProvenance,
)

/** Complete immutable snapshot retained by the semantic after the caller's mutable input is released. */
internal class GPUPreparedImagePayloadSnapshot(input: GPUPreparedImagePayloadInput) {
    val payloadRef: GPUDrawPayloadRef = input.payloadRef
    val artifact: GPUPreparedImageUploadArtifact = input.artifact
    val geometry: GPUPreparedImageGeometry = input.geometry
    val sampling: GPUPreparedImageSampling = input.sampling
    val tintPremultipliedRgba: List<Float> = immutableList(input.tintPremultipliedRgba)
    val atlasColorPremultipliedRgba: List<Float>? = input.atlasColorPremultipliedRgba?.let(::immutableList)
    val atlasSourceBlend: GPUPreparedAtlasSourceBlend? = input.atlasSourceBlend
    val targetBounds: GPUPixelBounds = input.targetBounds
    val scissorBounds: GPUPixelBounds = input.scissorBounds
    val blendPlanIdentity: String = input.blendPlanIdentity
    val frameProvenance: GPUFrameProvenance = input.frameProvenance
    val artifactUploadFormat: String = artifact.preparedImageUploadFormat()
    val artifactUploadEncoding: String =
        artifact.colorUploadEncoding?.name ?: "CoverageLinear"
    val shaderInterpretation: String =
        preparedSdrColorContract().shaderInterpretation.value

    fun toInput(): GPUPreparedImagePayloadInput = GPUPreparedImagePayloadInput(
        payloadRef = payloadRef,
        artifact = artifact,
        geometry = geometry,
        sampling = sampling,
        tintPremultipliedRgba = tintPremultipliedRgba,
        atlasColorPremultipliedRgba = atlasColorPremultipliedRgba,
        atlasSourceBlend = atlasSourceBlend,
        targetBounds = targetBounds,
        scissorBounds = scissorBounds,
        blendPlanIdentity = blendPlanIdentity,
        frameProvenance = frameProvenance,
    )
}

/** Produces the closed sampled-image semantic without inspecting or creating a native handle. */
class GPUPreparedImagePayloadGatherer {
    fun gatherSemantic(input: GPUPreparedImagePayloadInput): GPUDrawSemanticPayload.SampledImage {
        require(input.payloadRef.commandIdValue >= 0) { "Prepared image command id must be non-negative" }
        require(input.payloadRef.renderStepIdentity == GPU_PREPARED_IMAGE_RENDER_STEP_IDENTITY) {
            "Prepared image render step identity must be $GPU_PREPARED_IMAGE_RENDER_STEP_IDENTITY"
        }
        require(input.tintPremultipliedRgba.isFinitePremultipliedRgba()) {
            "Prepared image tint must be finite premultiplied RGBA"
        }
        require(input.atlasColorPremultipliedRgba == null || input.atlasColorPremultipliedRgba.isFinitePremultipliedRgba()) {
            "Prepared image atlas color must be finite premultiplied RGBA"
        }
        require((input.atlasColorPremultipliedRgba == null) == (input.atlasSourceBlend == null)) {
            "Prepared image atlas color and source blend must be specified together"
        }
        require(!input.targetBounds.isEmpty && input.targetBounds.isZeroOrigin()) {
            "Prepared image target bounds must be a non-empty zero-origin target"
        }
        require(!input.scissorBounds.isEmpty && input.targetBounds.contains(input.scissorBounds)) {
            "Prepared image scissor bounds must be non-empty and contained in the target"
        }
        require(input.blendPlanIdentity.isNotBlank()) { "Prepared image blend plan identity must not be blank" }
        require(input.artifact.width > 0 && input.artifact.height > 0) { "Prepared image artifact dimensions must be positive" }
        require(input.artifact.sourceGeneration >= 0L && input.artifact.contentHash.isNotBlank()) {
            "Prepared image artifact identity must be complete"
        }
        return GPUDrawSemanticPayload.SampledImage(input)
    }
}

internal const val GPU_PREPARED_IMAGE_RENDER_STEP_IDENTITY = "image.draw.texture_upload"
internal val GPU_PREPARED_IMAGE_TARGET_FORMAT: String =
    preparedSdrColorContract().targetTextureFormat.preparedImageFormatLabel()
internal val GPU_PREPARED_IMAGE_FIXED_INDICES: List<Int> = listOf(0, 1, 2, 0, 2, 3)

internal fun preparedImageScissorAuthority(bounds: GPUPixelBounds): String =
    "prepared-image-scissor.${bounds.left}.${bounds.top}.${bounds.right}.${bounds.bottom}"

internal fun GPUPreparedImagePayloadInput.pipelineKey(): GPUPreparedImagePipelineKey = GPUPreparedImagePipelineKey(
    destinationBlendState = blendPlanIdentity,
    targetFormat = GPU_PREPARED_IMAGE_TARGET_FORMAT,
    bindingLayoutHash = GPUPreparedImageBindingLayoutTopology.IDENTITY,
)

internal fun GPUPreparedImagePayloadInput.canonicalHash(): String = preparedImageSha256Hex(
    buildString {
        append("prepared-image-semantic-v1;")
        append("command=").append(payloadRef.commandIdValue).append(';')
        append("step=").append(payloadRef.renderStepIdentity).append(';')
        append("artifactKey=").append(artifact.key.value).append(';')
        append("artifactContentHash=").append(artifact.contentHash).append(';')
        append("artifactDimensions=").append(artifact.width).append('x').append(artifact.height).append(';')
        append("artifactRows=").append(artifact.pixelLayout.sourceRowBytes).append(',')
            .append(artifact.pixelLayout.normalizedRgba8RowBytes).append(',').append(artifact.pixelLayout.rowCount).append(';')
        append("artifactGeneration=").append(artifact.sourceGeneration).append(';')
        append("artifactAlphaOnly=").append(artifact.alphaOnly).append(';')
        append("artifactColorInterpretation=").append(artifact.colorInterpretation).append(';')
        append("artifactUploadEncoding=").append(artifact.colorUploadEncoding?.name ?: "CoverageLinear").append(';')
        append("artifactUploadInterpretation=").append(artifact.colorUploadInterpretation).append(';')
        append("artifactUploadFormat=").append(artifact.preparedImageUploadFormat()).append(';')
        append("targetFormat=").append(GPU_PREPARED_IMAGE_TARGET_FORMAT).append(';')
        append("shaderInterpretation=").append(preparedSdrColorContract().shaderInterpretation.value).append(';')
        append("readbackInterpretation=").append(preparedSdrColorContract().readbackInterpretation.value).append(';')
        append("geometryClass=").append(geometry.geometryClass.name).append(';')
        geometry.vertices.forEachIndexed { index, vertex ->
            append("vertex").append(index).append('=').append(vertex.x.toRawBits()).append(',')
                .append(vertex.y.toRawBits()).append(',').append(vertex.u.toRawBits()).append(',').append(vertex.v.toRawBits()).append(';')
        }
        append("indices=").append(geometry.indices.joinToString(",")).append(';')
        append("sampling=").append(sampling.name).append(';')
        append("tint=").append(tintPremultipliedRgba.joinToString(",") { it.toRawBits().toString() }).append(';')
        append("atlasColor=").append(atlasColorPremultipliedRgba?.joinToString(",") { it.toRawBits().toString() } ?: "none").append(';')
        append("atlasSourceBlend=").append(atlasSourceBlend?.name ?: "none").append(';')
        append("target=").append(targetBounds.left).append(',').append(targetBounds.top).append(',').append(targetBounds.right).append(',').append(targetBounds.bottom).append(';')
        append("scissor=").append(scissorBounds.left).append(',').append(scissorBounds.top).append(',').append(scissorBounds.right).append(',').append(scissorBounds.bottom).append(';')
        append("blend=").append(blendPlanIdentity).append(';')
        append("provenance=").append(frameProvenance.annotationValue).append(';')
    },
)

internal fun GPUPreparedImagePayloadInput.stableDumpLine(canonicalHash: String): String =
    "payload.sampled-image hash=$canonicalHash artifact=${artifact.key.value} content=${artifact.contentHash} " +
        "artifactDimensions=${artifact.width}x${artifact.height} artifactGeneration=${artifact.sourceGeneration} " +
        "artifactFormat=$GPU_PREPARED_IMAGE_TARGET_FORMAT artifactLayout=${artifact.pixelLayout.sourceRowBytes}," +
        "${artifact.pixelLayout.normalizedRgba8RowBytes},${artifact.pixelLayout.rowCount} " +
        "artifactAlphaOnly=${artifact.alphaOnly} artifactInterpretation=${artifact.colorInterpretation} " +
        "artifactUploadEncoding=${artifact.colorUploadEncoding?.name ?: "CoverageLinear"} " +
        "artifactUploadInterpretation=${artifact.colorUploadInterpretation} " +
        "artifactUploadFormat=${artifact.preparedImageUploadFormat()} " +
        "targetFormat=$GPU_PREPARED_IMAGE_TARGET_FORMAT " +
        "shaderInterpretation=${preparedSdrColorContract().shaderInterpretation.value} " +
        "readbackInterpretation=${preparedSdrColorContract().readbackInterpretation.value} " +
        "geometry=${geometry.geometryClass.name} " +
        geometry.vertices.mapIndexed { index, vertex ->
            "vertex$index=${vertex.x.toRawBits()},${vertex.y.toRawBits()},${vertex.u.toRawBits()},${vertex.v.toRawBits()}"
        }.joinToString(" ") + " indices=${geometry.indices.joinToString(",")} " +
        "sampling=${sampling.name} tint=${tintPremultipliedRgba.joinToString(",") { it.toRawBits().toString() }} " +
        "atlasColor=${atlasColorPremultipliedRgba?.joinToString(",") { it.toRawBits().toString() } ?: "none"} " +
        "atlasBlend=${atlasSourceBlend?.name ?: "none"} " +
        "blend=$blendPlanIdentity target=$targetBounds scissor=$scissorBounds provenance=${frameProvenance.annotationValue}"

private fun List<Float>.isFinitePremultipliedRgba(): Boolean =
    size == 4 && all { it.isFinite() && it in 0f..1f } && this[0] <= this[3] && this[1] <= this[3] && this[2] <= this[3]

private fun GPUPixelBounds.isZeroOrigin(): Boolean = left == 0 && top == 0
private fun GPUPixelBounds.contains(other: GPUPixelBounds): Boolean =
    other.left >= left && other.top >= top && other.right <= right && other.bottom <= bottom

private fun preparedImageSha256Hex(input: String): String =
    MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

private fun GPUPreparedImageUploadArtifact.preparedImageUploadFormat(): String {
    val contract = preparedSdrColorContract()
    return (
        if (alphaOnly) contract.coverageSourceTextureFormat else contract.colorSourceTextureFormat
    ).preparedImageFormatLabel()
}

private fun GPUTextureFormat.preparedImageFormatLabel(): String = when (this) {
    GPUTextureFormat.RGBA8Unorm -> "RGBA8Unorm"
    GPUTextureFormat.RGBA8UnormSrgb -> "RGBA8UnormSrgb"
    else -> error("Prepared-image SDR contract selected unsupported texture format")
}
