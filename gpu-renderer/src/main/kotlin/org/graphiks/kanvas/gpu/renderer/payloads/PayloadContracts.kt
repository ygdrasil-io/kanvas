package org.graphiks.kanvas.gpu.renderer.payloads

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactKey
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.collections.immutableSet
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.state.GPUFrameProvenance

/** Opaque payload slot identifier. */
@JvmInline
value class GPUPayloadSlotID(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUPayloadSlotID.value must not be blank" }
    }
}

/** Stable payload fingerprint. */
@JvmInline
value class GPUPayloadFingerprint(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUPayloadFingerprint.value must not be blank" }
    }
}

/** Material payload facts gathered before upload. */
data class GPUMaterialPayload(
    val materialKeyHash: String,
    val payloadClass: String,
    val valueFacts: Map<String, String>,
    val resourceFacts: Map<String, String>,
    val diagnosticLabel: String,
)

/** Payload gather plan. */
data class GPUPayloadGatherPlan(
    val planHash: String,
    val commandFamily: String,
    val materialAssemblyHash: String,
    val renderStepIdentity: String,
    val writePlanHash: String,
    val bindingPlanHash: String,
    val uploadPlanHash: String,
    val dedupScope: String,
    val unsupportedReason: String? = null,
)

/** Payload write plan for one draw or pass. */
data class GPUPayloadWritePlan(
    val planHash: String,
    val packingPlanHash: String,
    val bindingLayoutHash: String,
    val fieldWriteOrder: List<String>,
    val sourceValuePaths: List<String>,
    val resourceBindingOrder: List<String>,
)

/**
 * Uniform payload field placement fact with byte-level zero-fill evidence.
 *
 * A field describes one contiguous range inside its owning [GPUUniformPayloadBlock].
 * [byteOffset] is measured from the start of that block and [byteSize] must be
 * positive unless a later ABI revision explicitly introduces zero-width markers.
 * Producers must keep every field range inside the block, avoid overlapping field
 * ranges, and encode values with the byte order required by the packing plan.
 * [zeroFilled] means all bytes in this field range are zero after packing; for
 * real value fields this is value evidence, while padding fields use it as ABI
 * evidence that unused bytes were cleared before upload. These placement
 * invariants are not enforced by the data-class constructor; payload producers
 * and validation/replay consumers must reject malformed ranges before upload or
 * promotion evidence is accepted.
 */
data class GPUUniformPayloadField(
    val fieldPath: String,
    val byteOffset: Long,
    val byteSize: Long,
    val valueClass: String,
    val zeroFilled: Boolean = false,
)

/**
 * Uniform payload block prepared for upload, before any resource or staging allocation.
 *
 * The block is CPU-owned evidence for the exact bytes that would be copied into a
 * uniform buffer. [byteSize] is the required upload size and, when [bytes] are
 * present, callers must preserve `bytes.size == byteSize`; each byte is stored as
 * an unsigned `0..255` value even though Kotlin represents it as [Int]. Multi-byte
 * scalar values follow the byte order named by the packing plan used to produce
 * [packingPlanHash] (the first-route solid rect packer writes little-endian
 * floats). [fields] must describe byte ranges within this block. [zeroedPadding]
 * is true only when padding bytes outside value fields are zero, so reviewers can
 * distinguish cleared ABI padding from real field values that happen to be zero.
 * The constructor snapshots none of these ABI invariants by itself; invalid
 * byte counts, out-of-range byte values, or overlapping fields must be refused by
 * the producer/consumer that interprets the block.
 */
data class GPUUniformPayloadBlock(
    val fingerprint: GPUPayloadFingerprint,
    val packingPlanHash: String,
    val byteSize: Long,
    val zeroedPadding: Boolean,
    val scope: String,
    val bytes: List<Int> = emptyList(),
    val fields: List<GPUUniformPayloadField> = emptyList(),
)

/**
 * Uniform payload slot binding.
 *
 * A slot names a pass-local byte range in a uniform payload block. [byteOffset] is expected to be
 * non-negative and aligned for the referenced ABI layout, and [fingerprint] must name a stable
 * block produced by the gatherer. These invariants are not constructor-enforced; upload and submit
 * producers must refuse invalid offsets or stale fingerprints before promotion evidence is accepted.
 */
data class GPUUniformPayloadSlot(
    val slotId: GPUPayloadSlotID,
    val fingerprint: GPUPayloadFingerprint,
    val byteOffset: Long,
)

/** Resource binding block prepared for a pass. */
data class GPUResourceBindingBlock(
    val fingerprint: GPUPayloadFingerprint,
    val bindingPlanHash: String,
    val bindingCount: Int,
    val resourceDescriptorLabels: List<String>,
    val dynamicOffsets: List<Long> = emptyList(),
    val bindingFacts: List<GPUResourceBindingFact> = emptyList(),
)

/** Resource binding kind described by a payload binding block. */
enum class GPUResourceBindingKind {
    /** The binding references the uploaded uniform payload buffer. */
    UniformBuffer,
    /** The binding references a storage payload buffer. */
    StorageBuffer,
    /** The binding references a sampled texture view. */
    SampledTexture,
    /** The binding references a sampler. */
    Sampler,
}

/**
 * Binding-level resource facts required before bind group materialization.
 *
 * These are descriptor and generation facts, not backend handles. Texture and
 * sampler bindings may be validated here, while live image/sampler ownership
 * remains gated by the later texture materialization lane.
 */
data class GPUResourceBindingFact(
    val bindingLabel: String,
    val kind: GPUResourceBindingKind,
    val descriptorHash: String,
    val requiredUsageLabels: Set<String>,
    val availableUsageLabels: Set<String>,
    val expectedResourceGeneration: Long,
    val actualResourceGeneration: Long,
    val evictedReason: String? = null,
) {
    init {
        require(bindingLabel.isNotBlank()) { "GPUResourceBindingFact.bindingLabel must not be blank" }
        require(descriptorHash.isNotBlank()) { "GPUResourceBindingFact.descriptorHash must not be blank" }
        require(requiredUsageLabels.none { label -> label.isBlank() }) {
            "GPUResourceBindingFact.requiredUsageLabels must not contain blank labels"
        }
        require(availableUsageLabels.none { label -> label.isBlank() }) {
            "GPUResourceBindingFact.availableUsageLabels must not contain blank labels"
        }
        require(expectedResourceGeneration >= 0L) {
            "GPUResourceBindingFact.expectedResourceGeneration must be non-negative"
        }
        require(actualResourceGeneration >= 0L) {
            "GPUResourceBindingFact.actualResourceGeneration must be non-negative"
        }
        require(evictedReason == null || evictedReason.isNotBlank()) {
            "GPUResourceBindingFact.evictedReason must not be blank"
        }
    }
}

/** Resource binding slot. */
data class GPUResourceBindingSlot(
    val slotId: GPUPayloadSlotID,
    val fingerprint: GPUPayloadFingerprint,
    val bindingIndex: Int,
)

/** Payload binding plan. */
data class GPUPayloadBindingPlan(
    val planHash: String,
    val bindGroupRole: String,
    val bindingOrder: List<String>,
    val resourceClasses: List<String>,
    val dynamicOffsetPolicy: String,
)

/** Payload upload plan. */
data class GPUPayloadUploadPlan(
    val planHash: String,
    val byteRanges: List<LongRange>,
    val stagingScope: String,
    val budgetClass: String,
    val beforeUseToken: String,
)

/** Gradient payload storage plan. */
data class GPUGradientPayloadStore(
    val fingerprint: GPUPayloadFingerprint,
    val stopCount: Int,
    val storageLayoutHash: String,
    val byteSize: Long,
    val passLocalOffset: Long,
    val uploadPlanHash: String,
)

/** Reference from a draw invocation to pass-local payload. */
data class GPUDrawPayloadRef(
    val commandIdValue: Int,
    val renderStepIdentity: String,
    val uniformSlot: GPUUniformPayloadSlot? = null,
    val resourceSlot: GPUResourceBindingSlot? = null,
    val gradientStore: GPUGradientPayloadStore? = null,
    val uniformBlock: GPUUniformPayloadBlock? = null,
    val resourceBlock: GPUResourceBindingBlock? = null,
)

/** Closed native atlas format accepted by the COLRv0 color-glyph payload. */
enum class GPUColorGlyphAtlasFormat(val gpuLabel: String) {
    R8Unorm("r8unorm"),
}

const val COLOR_GLYPH_RENDER_STEP_IDENTITY = "text.colrv0.composite"

/** Closed shader identities; each native route accepts and validates an explicit supported subset. */
enum class GPURegisteredUniformProgram(
    val wireId: String,
    val uniformByteSize: Int,
) {
    SolidColor("solid-color-v1", 16),
    LinearGradient("linear-gradient-2stop-v1", 64),
    RadialGradient("radial-gradient-2stop-v1", 48),
    SweepGradient("sweep-gradient-2stop-v1", 64),
    Blur("analytic-blur-v1", 48),
    ColorMatrix("color-matrix-v1", 96),
    Stroke("analytic-stroke-v1", 48),
    SimpleRuntimeEffect("simple-runtime-effect-v1", 16),
}

const val REGISTERED_UNIFORM_RECT_RENDER_STEP_IDENTITY = "rect.registered-uniform"
const val SEPARABLE_BLUR_RECT_RENDER_STEP_IDENTITY = "filter.blur.separable-rect"
const val CORE_PRIMITIVE_RENDER_STEP_IDENTITY = "core-primitive.device-geometry"

/** Closed Slice 12A source identities retained after one Canvas-state translation. */
enum class GPUCorePrimitiveSourceFamily {
    Color,
    PointLine,
    Rect,
    RRect,
    DRRect,
    Path,
}

/** Exact fill authority retained for path stencil-cover materialization. */
enum class GPUCorePrimitiveFillRule {
    Winding,
    EvenOdd,
}

/** Geometry preparation strategy already selected before native materialization. */
enum class GPUCorePrimitiveGeometryMode {
    DirectTriangles,
    StencilEdgeFan,
    StrokeStencilEdgeFan,
}

/** Geometry coverage authority retained independently from paint blending. */
enum class GPUCorePrimitiveCoverageMode {
    FullOrScissor,
    ScalarAA,
    Stencil1x,
    StencilAA,
}

/** Closed proof for the only stroke outlines currently demonstrated exact. */
enum class GPUCorePrimitiveStrokeLoweringProof {
    SingleSegmentButtV1,
    SingleSegmentSquareV1,
}

/** Exact source stroke facts plus the named lowering implementation that consumed them. */
data class GPUCorePrimitiveStrokeStyle(
    val width: Float,
    val cap: String,
    val join: String,
    val miterLimit: Float,
    val dashIntervals: List<Float>,
    val dashPhase: Float,
    val loweringProof: GPUCorePrimitiveStrokeLoweringProof,
) {
    init {
        require(width.isFinite() && width >= 0f) { "Core stroke width must be finite and non-negative" }
        require(cap in setOf("butt", "round", "square")) { "Core stroke cap must be butt, round, or square" }
        require(join in setOf("miter", "round", "bevel")) { "Core stroke join must be miter, round, or bevel" }
        require(miterLimit.isFinite() && miterLimit >= 0f) { "Core stroke miter limit must be finite and non-negative" }
        require(dashIntervals.all { it.isFinite() && it > 0f }) {
            "Core stroke dash intervals must be finite and positive"
        }
        require(dashPhase.isFinite()) { "Core stroke dash phase must be finite" }
    }
}

/** Handle-free device-space geometry consumed by the sole native core materializer. */
sealed interface GPUCorePrimitiveGeometry {
    val canonicalType: String

    class Rect internal constructor(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) : GPUCorePrimitiveGeometry {
        override val canonicalType: String = "Rect"
    }

    class RRect internal constructor(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        radii: List<Float>,
    ) : GPUCorePrimitiveGeometry {
        override val canonicalType: String = "RRect"
        val radii: List<Float> = immutableList(radii)
    }

    class TriangulatedPath internal constructor(
        vertices: List<Float>,
        indices: List<Int>,
        sourceContourStarts: List<Int>,
        val sourceVertexCount: Int,
        val coverBounds: GPUPixelBounds,
        val geometryMode: GPUCorePrimitiveGeometryMode,
        val fillRule: GPUCorePrimitiveFillRule,
        val inverseFill: Boolean,
        strokeStyle: GPUCorePrimitiveStrokeStyle?,
    ) : GPUCorePrimitiveGeometry {
        override val canonicalType: String = "TriangulatedPath"
        val vertices: List<Float> = immutableList(vertices)
        val indices: List<Int> = immutableList(indices)
        val sourceContourStarts: List<Int> = immutableList(sourceContourStarts)
        val strokeStyle: GPUCorePrimitiveStrokeStyle? = strokeStyle?.copy(
            dashIntervals = immutableList(strokeStyle.dashIntervals),
        )
    }
}

/** Construction input whose mutable collections are snapshotted by the gatherer. */
data class GPUCorePrimitivePayloadInput(
    val commandIdValue: Int,
    val sourceFamily: GPUCorePrimitiveSourceFamily,
    val geometry: GPUCorePrimitiveGeometryInput,
    val premultipliedRgba: List<Float>,
    val targetBounds: GPUPixelBounds,
    val scissorBounds: GPUPixelBounds,
    val clipCoveragePlan: GPUClipCoveragePlan,
    val blendPlanIdentity: String,
    val frameProvenance: GPUFrameProvenance,
    val coverageMode: GPUCorePrimitiveCoverageMode = GPUCorePrimitiveCoverageMode.FullOrScissor,
)

/** Closed geometry input; callers cannot smuggle backend handles into frame planning. */
sealed interface GPUCorePrimitiveGeometryInput {
    data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float) :
        GPUCorePrimitiveGeometryInput

    data class RRect(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val radii: List<Float>,
    ) : GPUCorePrimitiveGeometryInput

    data class TriangulatedPath(
        val vertices: List<Float>,
        val indices: List<Int>,
        val sourceContourStarts: List<Int>,
        val sourceVertexCount: Int,
        val coverBounds: GPUPixelBounds,
        val geometryMode: GPUCorePrimitiveGeometryMode = GPUCorePrimitiveGeometryMode.DirectTriangles,
        val fillRule: GPUCorePrimitiveFillRule = GPUCorePrimitiveFillRule.Winding,
        val inverseFill: Boolean = false,
        val strokeStyle: GPUCorePrimitiveStrokeStyle? = null,
    ) : GPUCorePrimitiveGeometryInput
}

/** Typed proof tying one layer to one exact packed atlas placement and strike. */
data class GPUColorGlyphAtlasPlacementProofInput(
    val atlasArtifactKey: GPUTextArtifactKey,
    val strikeGlyphId: Int,
    val strikeSize: Float,
    val strikeSubpixelX: Int,
    val strikeSubpixelY: Int,
    val atlasBounds: GPUPixelBounds,
)

/** Immutable placement proof retained by the semantic payload. */
class GPUColorGlyphAtlasPlacementProof internal constructor(input: GPUColorGlyphAtlasPlacementProofInput) {
    val atlasArtifactKey: GPUTextArtifactKey = input.atlasArtifactKey.copy()
    val strikeGlyphId: Int = input.strikeGlyphId
    val strikeSize: Float = input.strikeSize
    val strikeSubpixelX: Int = input.strikeSubpixelX
    val strikeSubpixelY: Int = input.strikeSubpixelY
    val atlasBounds: GPUPixelBounds = input.atlasBounds
}

/** Mutable-boundary layer input consumed and snapshotted by [GPUColorGlyphPayloadGatherer]. */
data class GPUColorGlyphLayerPayloadInput(
    /** Boundary proof that this ordered layer belongs to the enclosing color-glyph plan. */
    val planArtifactKey: GPUTextArtifactKey,
    val layerGlyphID: UInt,
    val paletteIndex: Int,
    val atlasBounds: GPUPixelBounds,
    val deviceBounds: GPUPixelBounds,
    val premultipliedRgba: FloatArray,
    val useForeground: Boolean,
    val foregroundResolved: Boolean,
    val placementProof: GPUColorGlyphAtlasPlacementProofInput,
)

/** Immutable, handle-free color-glyph layer retained through frame planning. */
class GPUColorGlyphLayerPayload internal constructor(input: GPUColorGlyphLayerPayloadInput) {
    val layerGlyphID: UInt = input.layerGlyphID
    val paletteIndex: Int = input.paletteIndex
    val atlasBounds: GPUPixelBounds = input.atlasBounds
    val deviceBounds: GPUPixelBounds = input.deviceBounds
    val premultipliedRgba: List<Float> = immutableList(input.premultipliedRgba.toList())
    val useForeground: Boolean = input.useForeground
    val foregroundResolved: Boolean = input.foregroundResolved
    val placementProof: GPUColorGlyphAtlasPlacementProof = GPUColorGlyphAtlasPlacementProof(input.placementProof)
}

/** Closed, handle-free semantic payload retained from gathering through preflight. */
sealed interface GPUDrawSemanticPayload {
    val canonicalType: String
    val payloadRef: GPUDrawPayloadRef

    /** Exact solid rectangle block packed by [GPUSolidPayloadGatherer]. */
    class SolidRect internal constructor(payloadRef: GPUDrawPayloadRef) : GPUDrawSemanticPayload {
        override val canonicalType: String = "SolidRect"
        override val payloadRef: GPUDrawPayloadRef = payloadRef.deepSnapshot()
    }

    /** Exact core geometry, material, target, and typed clip plan for Slice 12A. */
    class CorePrimitive internal constructor(
        payloadRef: GPUDrawPayloadRef,
        val sourceFamily: GPUCorePrimitiveSourceFamily,
        val geometry: GPUCorePrimitiveGeometry,
        premultipliedRgba: List<Float>,
        val targetBounds: GPUPixelBounds,
        val scissorBounds: GPUPixelBounds,
        val clipCoveragePlan: GPUClipCoveragePlan,
        val blendPlanIdentity: String,
        val frameProvenance: GPUFrameProvenance,
        val canonicalHash: String,
        val coverageMode: GPUCorePrimitiveCoverageMode = GPUCorePrimitiveCoverageMode.FullOrScissor,
    ) : GPUDrawSemanticPayload {
        override val canonicalType: String = "CorePrimitive"
        override val payloadRef: GPUDrawPayloadRef = payloadRef.deepSnapshot()
        val premultipliedRgba: List<Float> = immutableList(premultipliedRgba)

        internal fun hasCanonicalHashIntegrity(): Boolean =
            payloadRef.renderStepIdentity == CORE_PRIMITIVE_RENDER_STEP_IDENTITY &&
                payloadRef.uniformSlot?.fingerprint == payloadRef.uniformBlock?.fingerprint &&
                payloadRef.uniformBlock?.byteSize == CORE_PRIMITIVE_UNIFORM_BYTES.toLong() &&
                payloadRef.uniformBlock?.bytes?.size == CORE_PRIMITIVE_UNIFORM_BYTES &&
                premultipliedRgba.isPremultipliedRgba() &&
                targetBounds.containsRegisteredUniformRect(scissorBounds) &&
                clipCoveragePlan !is GPUClipCoveragePlan.Refused &&
                canonicalHash == sha256Hex(
                    listOf(
                        "type=CorePrimitive",
                        "command=${payloadRef.commandIdValue}",
                        "family=${sourceFamily.name}",
                        "fingerprint=${payloadRef.uniformBlock?.fingerprint?.value.orEmpty()}",
                        "geometry=${geometry.canonicalPreimage()}",
                        "color=${premultipliedRgba.joinToString(",")}",
                        "target=${targetBounds.canonicalBounds()}",
                        "scissor=${scissorBounds.canonicalBounds()}",
                        "clip=${clipCoveragePlan.canonicalPreimage()}",
                        "blend=$blendPlanIdentity",
                        "provenance=${frameProvenance.annotationValue}",
                        "coverage=${coverageMode.name}",
                    ).joinToString("\n"),
                )
    }

    /** Exact immutable uniform bytes for one shader from the closed prepared program registry. */
    class RegisteredUniformRect internal constructor(
        payloadRef: GPUDrawPayloadRef,
        val program: GPURegisteredUniformProgram,
        uniformBytes: List<Int>,
        val targetBounds: GPUPixelBounds,
        val scissorBounds: GPUPixelBounds,
        val canonicalHash: String,
    ) : GPUDrawSemanticPayload {
        override val canonicalType: String = "RegisteredUniformRect"
        override val payloadRef: GPUDrawPayloadRef = payloadRef.deepSnapshot()
        val uniformBytes: List<Int> = immutableList(uniformBytes)

        fun hasCanonicalHashIntegrity(): Boolean =
            payloadRef.uniformBlock?.let { block ->
                payloadRef.uniformSlot?.fingerprint == block.fingerprint &&
                    block.bytes == uniformBytes &&
                    block.byteSize == program.uniformByteSize.toLong() &&
                    uniformBytes.size == program.uniformByteSize &&
                    uniformBytes.all { it in 0..255 } &&
                    canonicalHash == sha256Hex(
                        registeredUniformRectCanonicalPreimage(
                            payloadRef,
                            program,
                            uniformBytes,
                            targetBounds,
                            scissorBounds,
                        ),
                    )
            } == true
    }

    /** Immutable input, kernel, and attachment facts for one bounded three-pass blur. */
    class SeparableBlurRect internal constructor(
        payloadRef: GPUDrawPayloadRef,
        sourcePremultipliedRgba: List<Float>,
        clearPremultipliedRgba: List<Float>,
        val sourceBounds: GPUPixelBounds,
        val targetBounds: GPUPixelBounds,
        val effectiveSigma: Float,
        val tapCount: Int,
        weights: List<Float>,
        val canonicalHash: String,
    ) : GPUDrawSemanticPayload {
        override val canonicalType: String = "SeparableBlurRect"
        override val payloadRef: GPUDrawPayloadRef = payloadRef.deepSnapshot()
        val sourcePremultipliedRgba: List<Float> = immutableList(sourcePremultipliedRgba)
        val clearPremultipliedRgba: List<Float> = immutableList(clearPremultipliedRgba)
        val weights: List<Float> = immutableList(weights)

        fun hasCanonicalHashIntegrity(): Boolean {
            val expectedBytes = separableBlurRectPayloadBytes(
                sourcePremultipliedRgba,
                clearPremultipliedRgba,
                effectiveSigma,
                tapCount,
                weights,
            )
            return sourcePremultipliedRgba.isPremultipliedRgba() &&
                clearPremultipliedRgba.isPremultipliedRgba() &&
                targetBounds.containsRegisteredUniformRect(sourceBounds) &&
                effectiveSigma.isFinite() && effectiveSigma in 0.5f..12f &&
                tapCount in 3..25 && tapCount % 2 == 1 &&
                weights.size == 25 && weights.all { it.isFinite() && it >= 0f } &&
                kotlin.math.abs(weights.take(tapCount).sum() - 1f) <= 0.00001f &&
                weights.drop(tapCount).all { it == 0f } &&
                payloadRef.renderStepIdentity == SEPARABLE_BLUR_RECT_RENDER_STEP_IDENTITY &&
                payloadRef.uniformSlot?.fingerprint == payloadRef.uniformBlock?.fingerprint &&
                payloadRef.uniformBlock?.bytes == expectedBytes &&
                canonicalHash == sha256Hex(
                    separableBlurRectCanonicalPreimage(
                        payloadRef,
                        sourcePremultipliedRgba,
                        clearPremultipliedRgba,
                        sourceBounds,
                        targetBounds,
                        effectiveSigma,
                        tapCount,
                        weights,
                    ),
                )
        }
    }

    /** Exact immutable COLRv0 atlas, layer, indexed-geometry, and uniform payload. */
    class ColorGlyph internal constructor(
        payloadRef: GPUDrawPayloadRef,
        planArtifactKey: GPUTextArtifactKey,
        atlasArtifactKey: GPUTextArtifactKey,
        atlasA8Bytes: List<Int>,
        val atlasWidth: Int,
        val atlasHeight: Int,
        val atlasFormat: GPUColorGlyphAtlasFormat,
        val atlasGeneration: Long,
        layers: List<GPUColorGlyphLayerPayload>,
        vertexData: List<Float>,
        indexData: List<Int>,
        uniformBytes: List<Int>,
        val targetBounds: GPUPixelBounds,
        val scissorBounds: GPUPixelBounds,
        val canonicalHash: String,
    ) : GPUDrawSemanticPayload {
        override val canonicalType: String = "ColorGlyph"
        override val payloadRef: GPUDrawPayloadRef = payloadRef.deepSnapshot()
        val planArtifactKey: GPUTextArtifactKey = planArtifactKey.copy()
        val atlasArtifactKey: GPUTextArtifactKey = atlasArtifactKey.copy()
        val atlasA8Bytes: List<Int> = immutableList(atlasA8Bytes)
        /** SHA-256 derived from the exact immutable A8 snapshot, independent of caller artifact labels. */
        val atlasBytesSha256: String = sha256BytesHex(this.atlasA8Bytes)
        val layers: List<GPUColorGlyphLayerPayload> = immutableList(layers)
        val vertexData: List<Float> = immutableList(vertexData)
        val indexData: List<Int> = immutableList(indexData)
        val uniformBytes: List<Int> = immutableList(uniformBytes)

        fun stableDumpLines(): List<String> = immutableList(
            listOf(
                "payload.color-glyph hash=$canonicalHash command=${payloadRef.commandIdValue} " +
                    "step=${payloadRef.renderStepIdentity} atlas=${atlasWidth}x$atlasHeight " +
                    "format=${atlasFormat.gpuLabel} generation=$atlasGeneration " +
                    "atlasBytesSha256=$atlasBytesSha256 " +
                    "planArtifact=${planArtifactKey.dumpIdentity()} " +
                    "atlasArtifact=${atlasArtifactKey.dumpIdentity()} " +
                    "atlasBytes=${atlasA8Bytes.size} vertices=${vertexData.size / 4} " +
                    "indices=${indexData.size} uniformBytes=${uniformBytes.size} " +
                    "target=$targetBounds scissor=$scissorBounds",
            ) + layers.mapIndexed { index, layer ->
                "payload.color-glyph.layer index=$index " +
                    "glyph=${layer.layerGlyphID} palette=${layer.paletteIndex} " +
                    "atlasBounds=${layer.atlasBounds} " +
                    "deviceBounds=${layer.deviceBounds.canonicalBounds()} " +
                    "color=${layer.premultipliedRgba.joinToString(",")} " +
                    "foreground=${layer.useForeground}:${layer.foregroundResolved} " +
                    "strike=${layer.placementProof.strikeGlyphId}@${layer.placementProof.strikeSize}:" +
                    "${layer.placementProof.strikeSubpixelX},${layer.placementProof.strikeSubpixelY}"
            },
        )

        internal fun hasCanonicalHashIntegrity(): Boolean =
            hasCanonicalColorGlyphUniformIntegrity(payloadRef, uniformBytes) &&
                canonicalHash == sha256Hex(
                    colorGlyphCanonicalPreimage(
                        payloadRef,
                        planArtifactKey,
                        atlasArtifactKey,
                        atlasA8Bytes,
                        atlasWidth,
                        atlasHeight,
                        atlasFormat,
                        atlasGeneration,
                        atlasBytesSha256,
                        layers,
                        vertexData,
                        indexData,
                        uniformBytes,
                        targetBounds,
                        scissorBounds,
                    ),
                )
    }
}

/** Gathers one immutable Slice 12A semantic without allocating native resources. */
class GPUCorePrimitivePayloadGatherer {
    fun gatherSemantic(input: GPUCorePrimitivePayloadInput): GPUDrawSemanticPayload.CorePrimitive {
        require(input.commandIdValue >= 0) { "Core primitive command id must be non-negative" }
        require(input.premultipliedRgba.isPremultipliedRgba()) {
            "Core primitive color must be finite premultiplied RGBA"
        }
        require(input.targetBounds.left == 0 && input.targetBounds.top == 0 &&
            input.targetBounds.right > 0 && input.targetBounds.bottom > 0) {
            "Core primitive target must be a non-empty zero-origin target"
        }
        require(input.targetBounds.containsRegisteredUniformRect(input.scissorBounds)) {
            "Core primitive scissor must be non-empty and contained by its target"
        }
        require(input.clipCoveragePlan !is GPUClipCoveragePlan.Refused) {
            "Refused clip coverage cannot enter a core semantic payload"
        }
        require(input.blendPlanIdentity.isNotBlank()) {
            "Core primitive blend identity must not be blank"
        }

        val geometry = input.geometry.snapshotAndValidate(input.targetBounds)
        val color = input.premultipliedRgba.toList()
        val uniformBytes = ByteBuffer.allocate(CORE_PRIMITIVE_UNIFORM_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
            putFloat(input.targetBounds.width.toFloat())
            putFloat(input.targetBounds.height.toFloat())
            putFloat(0f)
            putFloat(0f)
            color.forEach(::putFloat)
        }.array().map { it.toInt() and 0xff }
        val fingerprint = GPUPayloadFingerprint(
            sha256Hex(
                listOf(
                    "kind=core-primitive",
                    "command=${input.commandIdValue}",
                    "family=${input.sourceFamily.name}",
                    "geometry=${geometry.canonicalPreimage()}",
                    "color=${color.joinToString(",")}",
                    "target=${input.targetBounds.canonicalBounds()}",
                ).joinToString("\n"),
            ),
        )
        val block = GPUUniformPayloadBlock(
            fingerprint = fingerprint,
            packingPlanHash = "core-primitive.uniform32-v1",
            byteSize = CORE_PRIMITIVE_UNIFORM_BYTES.toLong(),
            zeroedPadding = true,
            scope = "pass.core-primitive.prepared",
            bytes = uniformBytes,
            fields = listOf(
                GPUUniformPayloadField("target.size", 0L, 8L, "float32x2"),
                GPUUniformPayloadField("target.padding", 8L, 8L, "padding", zeroFilled = true),
                GPUUniformPayloadField("material.premul-rgba", 16L, 16L, "float32x4"),
            ),
        )
        val ref = GPUDrawPayloadRef(
            commandIdValue = input.commandIdValue,
            renderStepIdentity = CORE_PRIMITIVE_RENDER_STEP_IDENTITY,
            uniformSlot = GPUUniformPayloadSlot(
                slotId = GPUPayloadSlotID("core-primitive:${input.commandIdValue}"),
                fingerprint = fingerprint,
                byteOffset = 0L,
            ),
            uniformBlock = block,
        )
        return GPUDrawSemanticPayload.CorePrimitive(
            payloadRef = ref,
            sourceFamily = input.sourceFamily,
            geometry = geometry,
            premultipliedRgba = color,
            targetBounds = input.targetBounds,
            scissorBounds = input.scissorBounds,
            clipCoveragePlan = input.clipCoveragePlan.snapshot(),
            blendPlanIdentity = input.blendPlanIdentity,
            frameProvenance = input.frameProvenance,
            coverageMode = input.coverageMode,
            canonicalHash = sha256Hex(
                listOf(
                    "type=CorePrimitive",
                    "command=${input.commandIdValue}",
                    "family=${input.sourceFamily.name}",
                    "fingerprint=${fingerprint.value}",
                    "geometry=${geometry.canonicalPreimage()}",
                    "color=${color.joinToString(",")}",
                    "target=${input.targetBounds.canonicalBounds()}",
                    "scissor=${input.scissorBounds.canonicalBounds()}",
                    "clip=${input.clipCoveragePlan.canonicalPreimage()}",
                    "blend=${input.blendPlanIdentity}",
                    "provenance=${input.frameProvenance.annotationValue}",
                    "coverage=${input.coverageMode.name}",
                ).joinToString("\n"),
            ),
        )
    }
}

private fun GPUCorePrimitiveGeometryInput.snapshotAndValidate(
    target: GPUPixelBounds,
): GPUCorePrimitiveGeometry = when (this) {
    is GPUCorePrimitiveGeometryInput.Rect -> {
        require(listOf(left, top, right, bottom).all(Float::isFinite) && left < right && top < bottom) {
            "Core Rect geometry must have finite non-empty bounds"
        }
        GPUCorePrimitiveGeometry.Rect(left, top, right, bottom)
    }
    is GPUCorePrimitiveGeometryInput.RRect -> {
        require(listOf(left, top, right, bottom).all(Float::isFinite) && left < right && top < bottom) {
            "Core RRect geometry must have finite non-empty bounds"
        }
        require(radii.size == 8 && radii.all { it.isFinite() && it >= 0f }) {
            "Core RRect geometry requires four finite non-negative xy radii pairs"
        }
        GPUCorePrimitiveGeometry.RRect(left, top, right, bottom, radii.toList())
    }
    is GPUCorePrimitiveGeometryInput.TriangulatedPath -> {
        require(vertices.size >= 6 && vertices.size % 2 == 0 && vertices.all(Float::isFinite)) {
            "Core path geometry requires at least three finite xy vertices"
        }
        val vertexCount = vertices.size / 2
        require(indices.size >= 3 && indices.size % 3 == 0 && indices.all { it in 0 until vertexCount }) {
            "Core path geometry requires complete in-range triangles"
        }
        require(sourceVertexCount > 0 && sourceContourStarts.isNotEmpty() && sourceContourStarts.first() == 0 &&
            sourceContourStarts.zipWithNext().all { (left, right) -> left < right } &&
            sourceContourStarts.last() < sourceVertexCount) {
            "Core path source contour starts must be strictly ordered within the source geometry"
        }
        require(target.containsRegisteredUniformRect(coverBounds)) {
            "Core path cover bounds must be contained by its target"
        }
        val stroke = strokeStyle?.copy(dashIntervals = dashIntervalsSnapshot(strokeStyle.dashIntervals))
        when (geometryMode) {
            GPUCorePrimitiveGeometryMode.DirectTriangles -> require(stroke == null) {
                "Direct core triangles cannot retain stroke lowering facts"
            }
            GPUCorePrimitiveGeometryMode.StencilEdgeFan -> {
                require(stroke == null) {
                    "Fill stencil edge fans cannot retain stroke lowering facts"
                }
                require(sourceVertexCount <= CORE_PRIMITIVE_STENCIL_EDGE_FAN_SOURCE_VERTEX_BUDGET) {
                    CORE_PRIMITIVE_STENCIL_EDGE_FAN_BUDGET_DIAGNOSTIC
                }
                require(sourceContourStarts.hasCanonicalContourLengths(sourceVertexCount)) {
                    "Core stencil edge fan contours must each retain at least two source vertices"
                }
                require(
                    hasCanonicalStencilEdgeFanTopology(
                        vertices = vertices,
                        indices = indices,
                        sourceContourStarts = sourceContourStarts,
                        sourceVertexCount = sourceVertexCount,
                    ),
                ) {
                    "Core stencil edge fan topology must exactly match its source contour metadata"
                }
            }
            GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan -> {
                require(stroke != null) {
                    "Stroke stencil edge fans require exact stroke lowering facts"
                }
                require(sourceContourStarts == listOf(0) && sourceVertexCount == 2) {
                    "Core single-segment stroke proof requires exactly one two-vertex source contour"
                }
                require(fillRule == GPUCorePrimitiveFillRule.Winding && !inverseFill) {
                    "Core single-segment stroke proof requires non-inverse winding fill"
                }
                require(stroke.dashIntervals.isEmpty()) {
                    "Core single-segment stroke proof does not support dashes"
                }
                require(
                    when (stroke.loweringProof) {
                        GPUCorePrimitiveStrokeLoweringProof.SingleSegmentButtV1 -> stroke.cap == "butt"
                        GPUCorePrimitiveStrokeLoweringProof.SingleSegmentSquareV1 -> stroke.cap == "square"
                    },
                ) {
                    "Core single-segment stroke cap must match its closed lowering proof"
                }
            }
        }
        GPUCorePrimitiveGeometry.TriangulatedPath(
            vertices.toList(),
            indices.toList(),
            sourceContourStarts.toList(),
            sourceVertexCount,
            coverBounds,
            geometryMode,
            fillRule,
            inverseFill,
            stroke,
        )
    }
}

private const val CORE_PRIMITIVE_STENCIL_EDGE_FAN_SOURCE_VERTEX_BUDGET = 256
private const val CORE_PRIMITIVE_STENCIL_EDGE_FAN_BUDGET_DIAGNOSTIC =
    "unsupported.core_primitive.stencil_edge_fan_budget"

private fun List<Int>.hasCanonicalContourLengths(sourceVertexCount: Int): Boolean =
    indices.all { contourIndex ->
        val start = this[contourIndex]
        val end = getOrElse(contourIndex + 1) { sourceVertexCount }
        end - start >= 2
    }

private fun hasCanonicalStencilEdgeFanTopology(
    vertices: List<Float>,
    indices: List<Int>,
    sourceContourStarts: List<Int>,
    sourceVertexCount: Int,
): Boolean {
    val emittedVertexCount = vertices.size / 2
    if (emittedVertexCount != sourceVertexCount * 3 || indices != indices.indices.toList()) return false

    fun samePoint(leftFloatIndex: Int, rightFloatIndex: Int): Boolean =
        vertices[leftFloatIndex].toRawBits() == vertices[rightFloatIndex].toRawBits() &&
            vertices[leftFloatIndex + 1].toRawBits() == vertices[rightFloatIndex + 1].toRawBits()

    if ((1 until sourceVertexCount).any { sourceIndex -> !samePoint(0, sourceIndex * 6) }) return false
    sourceContourStarts.forEachIndexed { contourIndex, start ->
        val end = sourceContourStarts.getOrElse(contourIndex + 1) { sourceVertexCount }
        for (sourceIndex in start until end) {
            val nextSourceIndex = if (sourceIndex + 1 == end) start else sourceIndex + 1
            val edgeNextPoint = sourceIndex * 6 + 4
            val nextEdgePoint = nextSourceIndex * 6 + 2
            if (!samePoint(edgeNextPoint, nextEdgePoint)) return false
        }
    }
    return true
}

private fun GPUCorePrimitiveGeometry.canonicalPreimage(): String = when (this) {
    is GPUCorePrimitiveGeometry.Rect -> "$canonicalType:$left,$top,$right,$bottom"
    is GPUCorePrimitiveGeometry.RRect -> "$canonicalType:$left,$top,$right,$bottom:${radii.joinToString(",")}" 
    is GPUCorePrimitiveGeometry.TriangulatedPath ->
        "$canonicalType:${vertices.joinToString(",")}:${indices.joinToString(",")}:" +
            "${sourceContourStarts.joinToString(",")}:$sourceVertexCount:${coverBounds.canonicalBounds()}:" +
            "${geometryMode.name}:${fillRule.name}:$inverseFill:" +
            strokeStyle.canonicalPreimage()
}

private fun GPUCorePrimitiveStrokeStyle?.canonicalPreimage(): String = this?.let { stroke ->
    listOf(
        stroke.width,
        stroke.cap,
        stroke.join,
        stroke.miterLimit,
        stroke.dashIntervals.joinToString(","),
        stroke.dashPhase,
        stroke.loweringProof.name,
    ).joinToString(":")
} ?: "fill"

private fun dashIntervalsSnapshot(intervals: List<Float>): List<Float> = immutableList(intervals)

private fun GPUClipCoveragePlan.snapshot(): GPUClipCoveragePlan = when (this) {
    GPUClipCoveragePlan.NoClip -> this
    is GPUClipCoveragePlan.Scissor -> copy(bounds = bounds.copy())
    is GPUClipCoveragePlan.Mask -> copy(elements = elements.toList())
    is GPUClipCoveragePlan.Refused -> copy()
}

private fun GPUClipCoveragePlan.canonicalPreimage(): String = when (this) {
    GPUClipCoveragePlan.NoClip -> "none"
    is GPUClipCoveragePlan.Scissor ->
        "scissor:${bounds.left.toRawBits()}:${bounds.top.toRawBits()}:" +
            "${bounds.right.toRawBits()}:${bounds.bottom.toRawBits()}"
    is GPUClipCoveragePlan.Mask -> buildString {
        append("mask:").append(contentKey)
        append(":size=").append(width).append('x').append(height)
        append(":samples=").append(sampleCount)
        append(":resolvedBytes=").append(resolvedBytes)
        append(":requiredBytes=").append(requiredBytes)
        elements.forEach { element ->
            append(':').append(element.operation.name).append('/').append(element.kind.name)
            append("/vertices=").append(element.vertexCount)
            append("/aa=").append(element.antiAlias).append("/fill=").append(element.fillRule.name)
            append("/inverse=").append(element.inverseFill).append("/values=")
            append(element.values.joinToString(",") { value -> value.toRawBits().toString() })
        }
    }
    is GPUClipCoveragePlan.Refused -> "refused:$code"
}

private const val CORE_PRIMITIVE_UNIFORM_BYTES = 32

/** Packs one closed registered shader payload without carrying source code or native handles. */
class GPURegisteredUniformRectPayloadGatherer {
    fun gatherSemantic(
        commandIdValue: Int,
        program: GPURegisteredUniformProgram,
        uniformBytes: ByteArray,
        targetBounds: GPUPixelBounds,
        scissorBounds: GPUPixelBounds,
    ): GPUDrawSemanticPayload.RegisteredUniformRect {
        require(commandIdValue >= 0) { "Registered uniform command id must be non-negative" }
        require(uniformBytes.size == program.uniformByteSize) {
            "${program.wireId} requires exactly ${program.uniformByteSize} uniform bytes"
        }
        require(targetBounds.containsRegisteredUniformRect(scissorBounds)) {
            "Registered uniform scissor must be non-empty and contained by the target"
        }
        val bytes = uniformBytes.map { it.toInt() and 0xff }
        val packingPlanHash = "registered-uniform.${program.wireId}.abi${program.uniformByteSize}"
        val fingerprint = GPUPayloadFingerprint(
            sha256Hex(
                listOf(
                    "kind=registered-uniform-rect",
                    "program=${program.wireId}",
                    "command=$commandIdValue",
                    "packing=$packingPlanHash",
                    "bytes=${bytes.joinToString(",")}",
                ).joinToString("\n"),
            ),
        )
        val block = GPUUniformPayloadBlock(
            fingerprint = fingerprint,
            packingPlanHash = packingPlanHash,
            byteSize = program.uniformByteSize.toLong(),
            zeroedPadding = false,
            scope = "pass.registered-uniform.prepared",
            bytes = bytes,
            fields = listOf(
                GPUUniformPayloadField(
                    fieldPath = "program.${program.wireId}.uniforms",
                    byteOffset = 0L,
                    byteSize = program.uniformByteSize.toLong(),
                    valueClass = "registered-uniform-bytes",
                    zeroFilled = bytes.all { it == 0 },
                ),
            ),
        )
        val ref = GPUDrawPayloadRef(
            commandIdValue = commandIdValue,
            renderStepIdentity = REGISTERED_UNIFORM_RECT_RENDER_STEP_IDENTITY,
            uniformSlot = GPUUniformPayloadSlot(
                slotId = GPUPayloadSlotID("registered-uniform:$commandIdValue"),
                fingerprint = fingerprint,
                byteOffset = 0L,
            ),
            uniformBlock = block,
        )
        return GPUDrawSemanticPayload.RegisteredUniformRect(
            payloadRef = ref,
            program = program,
            uniformBytes = bytes,
            targetBounds = targetBounds,
            scissorBounds = scissorBounds,
            canonicalHash = sha256Hex(
                registeredUniformRectCanonicalPreimage(
                    ref,
                    program,
                    bytes,
                    targetBounds,
                    scissorBounds,
                ),
            ),
        )
    }
}

/** Snapshots the complete CPU-owned input for one bounded separable blur rectangle. */
class GPUSeparableBlurRectPayloadGatherer {
    fun gatherSemantic(
        commandIdValue: Int,
        sourcePremultipliedRgba: FloatArray,
        clearPremultipliedRgba: FloatArray,
        sourceBounds: GPUPixelBounds,
        targetBounds: GPUPixelBounds,
        effectiveSigma: Float,
        tapCount: Int,
        weights: FloatArray,
    ): GPUDrawSemanticPayload.SeparableBlurRect {
        require(commandIdValue >= 0) { "Separable blur command id must be non-negative" }
        require(sourcePremultipliedRgba.toList().isPremultipliedRgba()) {
            "Separable blur source color must be finite premultiplied RGBA"
        }
        require(clearPremultipliedRgba.toList().isPremultipliedRgba()) {
            "Separable blur clear color must be finite premultiplied RGBA"
        }
        require(targetBounds.containsRegisteredUniformRect(sourceBounds)) {
            "Separable blur source bounds must be contained by the target"
        }
        require(effectiveSigma.isFinite() && effectiveSigma in 0.5f..12f) {
            "Separable blur effective sigma must be in 0.5..12"
        }
        require(tapCount in 3..25 && tapCount % 2 == 1) {
            "Separable blur tap count must be an odd value in 3..25"
        }
        require(weights.size == 25 && weights.all { it.isFinite() && it >= 0f }) {
            "Separable blur weights must contain 25 finite non-negative values"
        }
        require(kotlin.math.abs(weights.take(tapCount).sum() - 1f) <= 0.00001f) {
            "Separable blur active weights must be normalized"
        }
        require(weights.drop(tapCount).all { it == 0f }) {
            "Separable blur padded weights must be zero"
        }
        val source = sourcePremultipliedRgba.toList()
        val clear = clearPremultipliedRgba.toList()
        val kernel = weights.toList()
        val bytes = separableBlurRectPayloadBytes(source, clear, effectiveSigma, tapCount, kernel)
        val fingerprint = GPUPayloadFingerprint(
            sha256Hex(
                listOf(
                    "kind=separable-blur-rect",
                    "command=$commandIdValue",
                    "bytes=${bytes.joinToString(",")}",
                ).joinToString("\n"),
            ),
        )
        val block = GPUUniformPayloadBlock(
            fingerprint = fingerprint,
            packingPlanHash = "separable-blur-rect.input-v1",
            byteSize = SEPARABLE_BLUR_RECT_PAYLOAD_BYTES.toLong(),
            zeroedPadding = true,
            scope = "pass.separable-blur.prepared",
            bytes = bytes,
            fields = listOf(
                GPUUniformPayloadField("source.premul-rgba", 0L, 16L, "float32x4"),
                GPUUniformPayloadField("clear.premul-rgba", 16L, 16L, "float32x4"),
                GPUUniformPayloadField("kernel.sigma", 32L, 4L, "float32"),
                GPUUniformPayloadField("kernel.tap-count", 36L, 4L, "uint32"),
                GPUUniformPayloadField("kernel.weights", 40L, 100L, "float32x25"),
                GPUUniformPayloadField("padding", 140L, 4L, "padding", zeroFilled = true),
            ),
        )
        val ref = GPUDrawPayloadRef(
            commandIdValue = commandIdValue,
            renderStepIdentity = SEPARABLE_BLUR_RECT_RENDER_STEP_IDENTITY,
            uniformSlot = GPUUniformPayloadSlot(
                GPUPayloadSlotID("separable-blur:$commandIdValue"),
                fingerprint,
                0L,
            ),
            uniformBlock = block,
        )
        return GPUDrawSemanticPayload.SeparableBlurRect(
            payloadRef = ref,
            sourcePremultipliedRgba = source,
            clearPremultipliedRgba = clear,
            sourceBounds = sourceBounds,
            targetBounds = targetBounds,
            effectiveSigma = effectiveSigma,
            tapCount = tapCount,
            weights = kernel,
            canonicalHash = sha256Hex(
                separableBlurRectCanonicalPreimage(
                    ref,
                    source,
                    clear,
                    sourceBounds,
                    targetBounds,
                    effectiveSigma,
                    tapCount,
                    kernel,
                ),
            ),
        )
    }
}

private fun separableBlurRectPayloadBytes(
    sourcePremultipliedRgba: List<Float>,
    clearPremultipliedRgba: List<Float>,
    effectiveSigma: Float,
    tapCount: Int,
    weights: List<Float>,
): List<Int> = ByteBuffer.allocate(SEPARABLE_BLUR_RECT_PAYLOAD_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
    sourcePremultipliedRgba.forEach(::putFloat)
    clearPremultipliedRgba.forEach(::putFloat)
    putFloat(effectiveSigma)
    putInt(tapCount)
    weights.forEach(::putFloat)
    putInt(0)
}.array().map { it.toInt() and 0xff }

private fun separableBlurRectCanonicalPreimage(
    ref: GPUDrawPayloadRef,
    sourcePremultipliedRgba: List<Float>,
    clearPremultipliedRgba: List<Float>,
    sourceBounds: GPUPixelBounds,
    targetBounds: GPUPixelBounds,
    effectiveSigma: Float,
    tapCount: Int,
    weights: List<Float>,
): String = listOf(
    "type=SeparableBlurRect",
    "command=${ref.commandIdValue}",
    "step=${ref.renderStepIdentity}",
    "fingerprint=${ref.uniformBlock?.fingerprint?.value.orEmpty()}",
    "source=${sourcePremultipliedRgba.joinToString(",")}",
    "clear=${clearPremultipliedRgba.joinToString(",")}",
    "sourceBounds=${sourceBounds.left},${sourceBounds.top},${sourceBounds.right},${sourceBounds.bottom}",
    "targetBounds=${targetBounds.left},${targetBounds.top},${targetBounds.right},${targetBounds.bottom}",
    "sigma=$effectiveSigma",
    "tapCount=$tapCount",
    "weights=${weights.joinToString(",")}",
).joinToString("\n")

private fun List<Float>.isPremultipliedRgba(): Boolean =
    size == 4 && all { it.isFinite() && it in 0f..1f } &&
        this[0] <= this[3] && this[1] <= this[3] && this[2] <= this[3]

private const val SEPARABLE_BLUR_RECT_PAYLOAD_BYTES = 144

private fun registeredUniformRectCanonicalPreimage(
    ref: GPUDrawPayloadRef,
    program: GPURegisteredUniformProgram,
    uniformBytes: List<Int>,
    targetBounds: GPUPixelBounds,
    scissorBounds: GPUPixelBounds,
): String = listOf(
    "type=RegisteredUniformRect",
    "command=${ref.commandIdValue}",
    "step=${ref.renderStepIdentity}",
    "program=${program.wireId}",
    "fingerprint=${ref.uniformBlock?.fingerprint?.value.orEmpty()}",
    "packing=${ref.uniformBlock?.packingPlanHash.orEmpty()}",
    "bytes=${uniformBytes.joinToString(",")}",
    "target=${targetBounds.left},${targetBounds.top},${targetBounds.right},${targetBounds.bottom}",
    "scissor=${scissorBounds.left},${scissorBounds.top},${scissorBounds.right},${scissorBounds.bottom}",
).joinToString("\n")

private fun GPUPixelBounds.containsRegisteredUniformRect(other: GPUPixelBounds): Boolean =
    other.right > other.left && other.bottom > other.top &&
        other.left >= left && other.top >= top && other.right <= right && other.bottom <= bottom

private fun GPUDrawPayloadRef.deepSnapshot(): GPUDrawPayloadRef = copy(
    uniformBlock = uniformBlock?.copy(
        bytes = immutableList(uniformBlock.bytes),
        fields = immutableList(uniformBlock.fields),
    ),
    resourceBlock = resourceBlock?.copy(
        resourceDescriptorLabels = immutableList(resourceBlock.resourceDescriptorLabels),
        dynamicOffsets = immutableList(resourceBlock.dynamicOffsets),
        bindingFacts = immutableList(
            resourceBlock.bindingFacts.map { fact ->
                fact.copy(
                    requiredUsageLabels = immutableSet(fact.requiredUsageLabels),
                    availableUsageLabels = immutableSet(fact.availableUsageLabels),
                )
            },
        ),
    ),
)

/** Gathers the exact immutable CPU-owned payload required by the native COLRv0 materializer. */
class GPUColorGlyphPayloadGatherer {
    fun gatherSemantic(
        commandIdValue: Int,
        renderStepIdentity: String,
        planArtifactKey: GPUTextArtifactKey,
        atlasArtifactKey: GPUTextArtifactKey,
        atlasA8Bytes: ByteArray,
        atlasWidth: Int,
        atlasHeight: Int,
        atlasFormat: String,
        atlasGeneration: Long,
        layers: List<GPUColorGlyphLayerPayloadInput>,
        vertexData: FloatArray,
        indexData: IntArray,
        uniformBytes: ByteArray,
        targetBounds: GPUPixelBounds,
        scissorBounds: GPUPixelBounds,
    ): GPUDrawSemanticPayload.ColorGlyph {
        require(commandIdValue >= 0) { "Color-glyph command id must be non-negative" }
        requireDumpSafeIdentity(renderStepIdentity, "Color-glyph render step")
        require(renderStepIdentity == COLOR_GLYPH_RENDER_STEP_IDENTITY) {
            "Color-glyph render step must be $COLOR_GLYPH_RENDER_STEP_IDENTITY"
        }
        requireArtifactIdentity(planArtifactKey, "Color-glyph plan artifact")
        requireArtifactIdentity(atlasArtifactKey, "Color-glyph atlas artifact")
        require(planArtifactKey != atlasArtifactKey) {
            "Color-glyph plan and atlas must have distinct artifact identities"
        }
        require(atlasWidth > 0 && atlasHeight > 0) { "Color-glyph atlas dimensions must be positive" }
        val expectedAtlasBytes = atlasWidth.toLong() * atlasHeight.toLong()
        require(expectedAtlasBytes <= Int.MAX_VALUE && atlasA8Bytes.size.toLong() == expectedAtlasBytes) {
            "Color-glyph A8 atlas must contain exactly width*height bytes"
        }
        val closedFormat = GPUColorGlyphAtlasFormat.entries.singleOrNull { it.gpuLabel == atlasFormat }
        requireNotNull(closedFormat) { "Color-glyph atlas format must be r8unorm" }
        require(atlasGeneration >= 0L) { "Color-glyph atlas generation must be non-negative" }
        require(atlasArtifactKey.generation.value.toLong() == atlasGeneration) {
            "Color-glyph atlas artifact generation must match the atlas generation"
        }
        require(layers.size in 1..MAX_COLOR_GLYPH_LAYERS) {
            "Color-glyph payload must contain 1..$MAX_COLOR_GLYPH_LAYERS layers"
        }
        require(targetBounds.left == 0 && targetBounds.top == 0 && !targetBounds.isEmpty) {
            "Color-glyph target bounds must be a non-empty zero-origin extent"
        }
        require(!scissorBounds.isEmpty && scissorBounds.isContainedBy(targetBounds)) {
            "Color-glyph scissor bounds must be non-empty and contained by the target"
        }
        layers.forEachIndexed { index, layer ->
            require(layer.planArtifactKey == planArtifactKey) {
                "Color-glyph layer $index plan identity must match the enclosing plan artifact"
            }
            val proof = layer.placementProof
            require(proof.atlasArtifactKey == atlasArtifactKey) {
                "Color-glyph layer $index placement atlas identity must match the enclosing atlas artifact"
            }
            require(proof.strikeGlyphId >= 0 && layer.layerGlyphID == proof.strikeGlyphId.toUInt()) {
                "Color-glyph layer $index glyph identity must match its atlas strike placement"
            }
            require(proof.strikeSize.isFinite() && proof.strikeSize > 0f) {
                "Color-glyph layer $index strike size must be finite and positive"
            }
            require(proof.atlasBounds == layer.atlasBounds) {
                "Color-glyph layer $index bounds must match its atlas placement proof"
            }
            require(layer.paletteIndex >= 0) { "Color-glyph layer $index palette index must be non-negative" }
            require(!layer.atlasBounds.isEmpty && layer.atlasBounds.isContainedByAtlas(atlasWidth, atlasHeight)) {
                "Color-glyph layer $index bounds must be inside the atlas"
            }
            require(!layer.deviceBounds.isEmpty && layer.deviceBounds.isContainedBy(targetBounds)) {
                "Color-glyph layer $index device bounds must be non-empty and inside the target"
            }
            require(!layer.useForeground || layer.foregroundResolved) {
                "Color-glyph foreground layer $index must be resolved before gathering"
            }
            require(layer.premultipliedRgba.size == 4) {
                "Color-glyph layer $index color must contain four channels"
            }
            require(layer.premultipliedRgba.all { it.isFinite() && it in 0f..1f }) {
                "Color-glyph layer $index color channels must be finite and normalized"
            }
            val alpha = layer.premultipliedRgba[3]
            require(layer.premultipliedRgba.take(3).all { it <= alpha }) {
                "Color-glyph layer $index color must be premultiplied"
            }
        }
        require(layers.map { it.placementProof }.distinctBy { proof ->
            listOf(
                proof.strikeGlyphId,
                proof.strikeSize.toRawBits(),
                proof.strikeSubpixelX,
                proof.strikeSubpixelY,
                proof.atlasBounds,
            )
        }.size == layers.size) {
            "Color-glyph atlas placement proofs must be unique"
        }
        require(layers.indices.none { first ->
            (first + 1 until layers.size).any { second ->
                layers[first].atlasBounds.overlaps(layers[second].atlasBounds)
            }
        }) { "Color-glyph atlas placements must not overlap" }
        require(vertexData.size == COLOR_GLYPH_QUAD_FLOATS) {
            "Color-glyph first-slice geometry must contain exactly four packed position/UV vertices"
        }
        require(vertexData.all(Float::isFinite)) { "Color-glyph vertices must be finite" }
        require(vertexData.indices.filter { it % COLOR_GLYPH_VERTEX_FLOATS >= 2 }.all { vertexData[it] in 0f..1f }) {
            "Color-glyph vertex UV coordinates must be normalized"
        }
        val vertexCount = vertexData.size / COLOR_GLYPH_VERTEX_FLOATS
        require(indexData.contentEquals(COLOR_GLYPH_QUAD_INDICES)) {
            "Color-glyph first-slice indices must describe the canonical two-triangle quad"
        }
        require(indexData.all { it in 0 until vertexCount }) {
            "Color-glyph indices must reference the provided vertices"
        }
        require(uniformBytes.size == COLOR_GLYPH_UNIFORM_BYTES) {
            "Color-glyph uniform ABI must contain exactly $COLOR_GLYPH_UNIFORM_BYTES bytes"
        }
        validateColorGlyphUniform(uniformBytes, targetBounds, atlasWidth, atlasHeight, layers)

        val atlasSnapshot = atlasA8Bytes.map { it.toInt() and 0xff }
        val layerSnapshots = layers.map(::GPUColorGlyphLayerPayload)
        val vertexSnapshot = vertexData.toList()
        val indexSnapshot = indexData.toList()
        val uniformSnapshot = uniformBytes.map { it.toInt() and 0xff }
        val uniformFields = colorGlyphUniformFields()
        val uniformScope = "color-glyph:$commandIdValue"
        val fingerprint = GPUPayloadFingerprint(
            sha256Hex(
                colorGlyphUniformIntegrityPreimage(
                    packingPlanHash = COLOR_GLYPH_UNIFORM_LAYOUT,
                    byteSize = COLOR_GLYPH_UNIFORM_BYTES.toLong(),
                    zeroedPadding = true,
                    scope = uniformScope,
                    bytes = uniformSnapshot,
                    fields = uniformFields,
                ),
            ),
        )
        val uniformBlock = GPUUniformPayloadBlock(
            fingerprint = fingerprint,
            packingPlanHash = COLOR_GLYPH_UNIFORM_LAYOUT,
            byteSize = COLOR_GLYPH_UNIFORM_BYTES.toLong(),
            zeroedPadding = true,
            scope = uniformScope,
            bytes = uniformSnapshot,
            fields = uniformFields,
        )
        val payloadRef = GPUDrawPayloadRef(
            commandIdValue = commandIdValue,
            renderStepIdentity = renderStepIdentity,
            uniformSlot = GPUUniformPayloadSlot(
                GPUPayloadSlotID("color-glyph:$commandIdValue:uniform"),
                fingerprint,
                0L,
            ),
            uniformBlock = uniformBlock,
        )
        val atlasBytesSha256 = sha256BytesHex(atlasSnapshot)
        val canonicalHash = sha256Hex(
            colorGlyphCanonicalPreimage(
                payloadRef,
                planArtifactKey,
                atlasArtifactKey,
                atlasSnapshot,
                atlasWidth,
                atlasHeight,
                closedFormat,
                atlasGeneration,
                atlasBytesSha256,
                layerSnapshots,
                vertexSnapshot,
                indexSnapshot,
                uniformSnapshot,
                targetBounds,
                scissorBounds,
            ),
        )
        return GPUDrawSemanticPayload.ColorGlyph(
            payloadRef = payloadRef,
            planArtifactKey = planArtifactKey,
            atlasArtifactKey = atlasArtifactKey,
            atlasA8Bytes = atlasSnapshot,
            atlasWidth = atlasWidth,
            atlasHeight = atlasHeight,
            atlasFormat = closedFormat,
            atlasGeneration = atlasGeneration,
            layers = layerSnapshots,
            vertexData = vertexSnapshot,
            indexData = indexSnapshot,
            uniformBytes = uniformSnapshot,
            targetBounds = targetBounds,
            scissorBounds = scissorBounds,
            canonicalHash = canonicalHash,
        )
    }
}

private fun GPUPixelBounds.isContainedBy(container: GPUPixelBounds): Boolean =
    left >= container.left && top >= container.top && right <= container.right && bottom <= container.bottom

private fun GPUPixelBounds.isContainedByAtlas(width: Int, height: Int): Boolean =
    left >= 0 && top >= 0 && right <= width && bottom <= height

private fun validateColorGlyphUniform(
    bytes: ByteArray,
    targetBounds: GPUPixelBounds,
    atlasWidth: Int,
    atlasHeight: Int,
    layers: List<GPUColorGlyphLayerPayloadInput>,
) {
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    require(buffer.float.rawEquals(targetBounds.width.toFloat())) { "Color-glyph uniform target width mismatch" }
    require(buffer.float.rawEquals(targetBounds.height.toFloat())) { "Color-glyph uniform target height mismatch" }
    require(buffer.int == layers.size) { "Color-glyph uniform layer count mismatch" }
    require(buffer.int == 0) { "Color-glyph uniform reserved header must be zero" }
    repeat(MAX_COLOR_GLYPH_LAYERS) { index ->
        val expected = layers.getOrNull(index)?.premultipliedRgba ?: ZERO_COLOR
        repeat(4) { component ->
            require(buffer.float.rawEquals(expected[component])) {
                "Color-glyph uniform layer $index color mismatch"
            }
        }
    }
    repeat(MAX_COLOR_GLYPH_LAYERS) { index ->
        val bounds = layers.getOrNull(index)?.atlasBounds
        val expected = if (bounds == null) {
            ZERO_COLOR
        } else {
            floatArrayOf(
                bounds.left.toFloat() / atlasWidth,
                bounds.top.toFloat() / atlasHeight,
                bounds.width.toFloat() / atlasWidth,
                bounds.height.toFloat() / atlasHeight,
            )
        }
        repeat(4) { component ->
            require(buffer.float.rawEquals(expected[component])) {
                "Color-glyph uniform layer $index atlas bounds mismatch"
            }
        }
    }
    repeat(MAX_COLOR_GLYPH_LAYERS) { index ->
        val bounds = layers.getOrNull(index)?.deviceBounds
        val expected = if (bounds == null) {
            ZERO_COLOR
        } else {
            floatArrayOf(
                bounds.left.toFloat(),
                bounds.top.toFloat(),
                bounds.width.toFloat(),
                bounds.height.toFloat(),
            )
        }
        repeat(4) { component ->
            require(buffer.float.rawEquals(expected[component])) {
                "Color-glyph uniform layer $index device bounds mismatch"
            }
        }
    }
}

private fun Float.rawEquals(other: Float): Boolean = toRawBits() == other.toRawBits()

private fun colorGlyphCanonicalPreimage(
    payloadRef: GPUDrawPayloadRef,
    planArtifactKey: GPUTextArtifactKey,
    atlasArtifactKey: GPUTextArtifactKey,
    atlasBytes: List<Int>,
    atlasWidth: Int,
    atlasHeight: Int,
    atlasFormat: GPUColorGlyphAtlasFormat,
    atlasGeneration: Long,
    atlasBytesSha256: String,
    layers: List<GPUColorGlyphLayerPayload>,
    vertices: List<Float>,
    indices: List<Int>,
    uniformBytes: List<Int>,
    targetBounds: GPUPixelBounds,
    scissorBounds: GPUPixelBounds,
): String = buildString {
    appendCanonicalField("type", "ColorGlyph")
    appendCanonicalField("payloadRef", colorGlyphPayloadRefCanonicalPreimage(payloadRef))
    appendCanonicalField("planArtifact", planArtifactKey.canonicalIdentity())
    appendCanonicalField("atlasArtifact", atlasArtifactKey.canonicalIdentity())
    appendCanonicalField("atlas", "${atlasWidth}x$atlasHeight:${atlasFormat.gpuLabel}:$atlasGeneration")
    appendCanonicalField("atlasBytesSha256", atlasBytesSha256)
    layers.forEachIndexed { index, layer ->
        appendCanonicalField(
            "layer",
            "$index:${layer.layerGlyphID}:${layer.paletteIndex}:" +
                "${layer.atlasBounds.canonicalBounds()}:${layer.deviceBounds.canonicalBounds()}:" +
                "${layer.premultipliedRgba.joinToString(",") { it.toRawBits().toString() }}:" +
                "${layer.useForeground}:${layer.foregroundResolved}:" +
                "${layer.placementProof.atlasArtifactKey.canonicalIdentity()}:" +
                "${layer.placementProof.strikeGlyphId}:${layer.placementProof.strikeSize.toRawBits()}:" +
                "${layer.placementProof.strikeSubpixelX}:${layer.placementProof.strikeSubpixelY}:" +
                layer.placementProof.atlasBounds.canonicalBounds(),
        )
    }
    appendCanonicalField("vertices", vertices.joinToString(",") { it.toRawBits().toString() })
    appendCanonicalField("indices", indices.joinToString(","))
    appendCanonicalField("uniform", uniformBytes.joinToString(","))
    appendCanonicalField("target", targetBounds.canonicalBounds())
    appendCanonicalField("scissor", scissorBounds.canonicalBounds())
}

private fun colorGlyphPayloadRefCanonicalPreimage(ref: GPUDrawPayloadRef): String = buildString {
    appendCanonicalField("command", ref.commandIdValue.toString())
    appendCanonicalField("step", ref.renderStepIdentity)
    val slot = ref.uniformSlot
    appendCanonicalField("uniformSlot.present", (slot != null).toString())
    if (slot != null) {
        appendCanonicalField("uniformSlot.id", slot.slotId.value)
        appendCanonicalField("uniformSlot.fingerprint", slot.fingerprint.value)
        appendCanonicalField("uniformSlot.offset", slot.byteOffset.toString())
    }
    val block = ref.uniformBlock
    appendCanonicalField("uniformBlock.present", (block != null).toString())
    if (block != null) {
        appendCanonicalField("uniformBlock.fingerprint", block.fingerprint.value)
        appendCanonicalField("uniformBlock.abi", colorGlyphUniformIntegrityPreimage(block))
    }
    appendCanonicalField("resourceSlot.present", (ref.resourceSlot != null).toString())
    appendCanonicalField("gradientStore.present", (ref.gradientStore != null).toString())
    appendCanonicalField("resourceBlock.present", (ref.resourceBlock != null).toString())
}

private fun colorGlyphUniformIntegrityPreimage(block: GPUUniformPayloadBlock): String =
    colorGlyphUniformIntegrityPreimage(
        packingPlanHash = block.packingPlanHash,
        byteSize = block.byteSize,
        zeroedPadding = block.zeroedPadding,
        scope = block.scope,
        bytes = block.bytes,
        fields = block.fields,
    )

private fun colorGlyphUniformIntegrityPreimage(
    packingPlanHash: String,
    byteSize: Long,
    zeroedPadding: Boolean,
    scope: String,
    bytes: List<Int>,
    fields: List<GPUUniformPayloadField>,
): String = buildString {
    appendCanonicalField("packingPlanHash", packingPlanHash)
    appendCanonicalField("byteSize", byteSize.toString())
    appendCanonicalField("zeroedPadding", zeroedPadding.toString())
    appendCanonicalField("scope", scope)
    appendCanonicalField("bytes", bytes.joinToString(","))
    fields.forEachIndexed { index, field ->
        appendCanonicalField(
            "field",
            "$index:${field.fieldPath}:${field.byteOffset}:${field.byteSize}:${field.valueClass}:${field.zeroFilled}",
        )
    }
}

private fun colorGlyphUniformFields(): List<GPUUniformPayloadField> = listOf(
    GPUUniformPayloadField("targetSize.layerCount", 0, 16, "header"),
    GPUUniformPayloadField("layers.premultipliedRgba", 16, 256, "array<vec4f,16>"),
    GPUUniformPayloadField("layers.atlasBounds", 272, 256, "array<vec4f,16>"),
    GPUUniformPayloadField("layers.deviceBounds", 528, 256, "array<vec4f,16>"),
)

private fun hasCanonicalColorGlyphUniformIntegrity(
    ref: GPUDrawPayloadRef,
    uniformBytes: List<Int>,
): Boolean {
    val slot = ref.uniformSlot ?: return false
    val block = ref.uniformBlock ?: return false
    if (ref.resourceSlot != null || ref.gradientStore != null || ref.resourceBlock != null) return false
    if (slot.slotId != GPUPayloadSlotID("color-glyph:${ref.commandIdValue}:uniform") || slot.byteOffset != 0L) {
        return false
    }
    if (block.packingPlanHash != COLOR_GLYPH_UNIFORM_LAYOUT ||
        block.byteSize != COLOR_GLYPH_UNIFORM_BYTES.toLong() ||
        !block.zeroedPadding ||
        block.scope != "color-glyph:${ref.commandIdValue}" ||
        block.bytes != uniformBytes ||
        block.bytes.size != COLOR_GLYPH_UNIFORM_BYTES ||
        block.bytes.any { it !in 0..255 } ||
        block.fields != colorGlyphUniformFields()
    ) {
        return false
    }
    val expectedFingerprint = GPUPayloadFingerprint(sha256Hex(colorGlyphUniformIntegrityPreimage(block)))
    return block.fingerprint == expectedFingerprint && slot.fingerprint == expectedFingerprint
}

private fun sha256BytesHex(bytes: List<Int>): String {
    if (bytes.any { it !in 0..255 }) return ""
    return MessageDigest.getInstance("SHA-256")
        .digest(ByteArray(bytes.size) { index -> bytes[index].toByte() })
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

private fun StringBuilder.appendCanonicalField(name: String, value: String) {
    append(name).append('#').append(value.length).append(':').append(value).append('\n')
}

private fun GPUTextArtifactKey.canonicalIdentity(): String =
    "${artifactID.value}:${generation.value}:${contentFingerprint.length}:$contentFingerprint"

private fun GPUTextArtifactKey.dumpIdentity(): String =
    "${artifactID.value}@${generation.value}/$contentFingerprint"

private fun GPUPixelBounds.canonicalBounds(): String = "$left,$top,$right,$bottom"

private fun GPUPixelBounds.overlaps(other: GPUPixelBounds): Boolean =
    left < other.right && other.left < right && top < other.bottom && other.top < bottom

private fun requireArtifactIdentity(key: GPUTextArtifactKey, label: String) {
    require(key.generation.value >= 0) { "$label generation must be non-negative" }
    requireDumpSafeIdentity(key.contentFingerprint, "$label content fingerprint")
}

private fun requireDumpSafeIdentity(value: String, label: String) {
    require(value.isNotBlank()) { "$label must not be blank" }
    require(value.all { character -> character.code in 0x21..0x7e && character !in DUMP_IDENTITY_DELIMITERS }) {
        "$label must contain only dump-safe identity characters"
    }
}

private const val MAX_COLOR_GLYPH_LAYERS = 16
private const val COLOR_GLYPH_VERTEX_FLOATS = 4
private const val COLOR_GLYPH_QUAD_FLOATS = 16
private const val COLOR_GLYPH_UNIFORM_BYTES = 784
private const val COLOR_GLYPH_UNIFORM_LAYOUT = "color-glyph-composite-uniform-v2"
private val ZERO_COLOR = floatArrayOf(0f, 0f, 0f, 0f)
private val DUMP_IDENTITY_DELIMITERS = setOf('=', ':', ',', '|', '@', '/')
private val COLOR_GLYPH_QUAD_INDICES = intArrayOf(0, 1, 2, 0, 2, 3)

/** Payload gathering contract. */
interface GPUPayloadGatherer {
    /** Gathers one payload reference without uploading it. */
    fun gather(plan: GPUPayloadGatherPlan, payload: GPUMaterialPayload): GPUDrawPayloadRef

    /** Resets pass-local state for a payload scope. */
    fun reset(scopeId: String)
}

/**
 * Minimal pass-local gatherer for first-slice solid rect payloads.
 *
 * This gatherer owns only CPU-side payload value validation and deterministic
 * slot facts. It refuses malformed required values before fingerprinting, does
 * not upload buffers, and does not create resource bindings or backend handles.
 */
class GPUSolidPayloadGatherer : GPUPayloadGatherer {
    private val uniformSlots = LinkedHashMap<GPUPayloadFingerprint, GPUUniformPayloadSlot>()
    private var currentScopeId: String? = null

    override fun gather(plan: GPUPayloadGatherPlan, payload: GPUMaterialPayload): GPUDrawPayloadRef {
        require(plan.unsupportedReason == null) { "Cannot gather unsupported payload plan ${plan.planHash}" }
        require(payload.payloadClass == solidPayloadClass) {
            "GPUSolidPayloadGatherer only supports $solidPayloadClass payloads"
        }
        enterScope(plan.dedupScope)

        val fieldValues = solidRectFieldValues(payload.valueFacts)
        val bytes = solidRectPayloadBytes(fieldValues)
        val fingerprint = GPUPayloadFingerprint(
            sha256Hex(
                listOf(
                    "kind=solid-rect-uniform",
                    "material=${payload.materialKeyHash}",
                    "write=${plan.writePlanHash}",
                    "bytes=${bytes.joinToString(",")}",
                ).joinToString("\n"),
            ),
        )
        val block = GPUUniformPayloadBlock(
            fingerprint = fingerprint,
            packingPlanHash = solidRectPackingPlanHash,
            byteSize = solidRectByteSize.toLong(),
            zeroedPadding = bytes.drop(solidRectUsedByteSize).all { it == 0 },
            scope = plan.dedupScope,
            bytes = bytes,
            fields = solidRectFields(fieldValues),
        )
        val slot = uniformSlots.getOrPut(fingerprint) {
            GPUUniformPayloadSlot(
                slotId = GPUPayloadSlotID("${plan.dedupScope}:uniform:${uniformSlots.size}"),
                fingerprint = fingerprint,
                byteOffset = 0L,
            )
        }

        return GPUDrawPayloadRef(
            commandIdValue = payload.valueFacts.requiredInt("command.id"),
            renderStepIdentity = plan.renderStepIdentity,
            uniformSlot = slot,
            uniformBlock = block,
        )
    }

    /** Packs once through [gather] and closes the resulting deeply immutable semantic value. */
    fun gatherSemantic(
        plan: GPUPayloadGatherPlan,
        payload: GPUMaterialPayload,
    ): GPUDrawSemanticPayload.SolidRect = GPUDrawSemanticPayload.SolidRect(gather(plan, payload)).also { semantic ->
        check(semanticValidationFailure(semantic.payloadRef) == null) {
            "GPUSolidPayloadGatherer produced an invalid semantic payload"
        }
    }

    override fun reset(scopeId: String) {
        enterScope(scopeId)
        uniformSlots.clear()
    }

    private fun enterScope(scopeId: String) {
        require(scopeId.isNotBlank()) { "scopeId must not be blank" }
        if (currentScopeId != scopeId) {
            uniformSlots.clear()
            currentScopeId = scopeId
        }
    }

    private fun solidRectFieldValues(valueFacts: Map<String, String>): List<Pair<String, Float>> =
        solidRectFloatFields.map { fieldPath ->
            fieldPath to valueFacts.requiredFiniteFloat(fieldPath)
        }

    private fun solidRectPayloadBytes(fieldValues: List<Pair<String, Float>>): List<Int> {
        val buffer = ByteBuffer.allocate(solidRectByteSize).order(ByteOrder.LITTLE_ENDIAN)
        fieldValues.forEach { (_, value) ->
            buffer.putFloat(value)
        }
        return buffer.array().map { byte -> byte.toInt() and 0xff }
    }

    private fun solidRectFields(fieldValues: List<Pair<String, Float>>): List<GPUUniformPayloadField> =
        fieldValues.mapIndexed { index, (fieldPath, value) ->
            GPUUniformPayloadField(
                fieldPath = fieldPath,
                byteOffset = index * Float.SIZE_BYTES.toLong(),
                byteSize = Float.SIZE_BYTES.toLong(),
                valueClass = "f32",
                zeroFilled = value.toRawBits() == 0,
            )
        } + GPUUniformPayloadField(
            fieldPath = "padding.reserved",
            byteOffset = solidRectUsedByteSize.toLong(),
            byteSize = (solidRectByteSize - solidRectUsedByteSize).toLong(),
            valueClass = "padding",
            zeroFilled = true,
        )

    private fun Map<String, String>.requiredFiniteFloat(fieldPath: String): Float {
        val rawValue = this[fieldPath]
        require(rawValue != null) { "Payload field $fieldPath is required" }
        val value = rawValue.toFloatOrNull()
        require(value != null) { "Payload field $fieldPath must be a float" }
        require(value.isFinite()) { "Payload field $fieldPath must be finite" }
        if (fieldPath.startsWith("color.")) {
            require(value in 0f..1f) { "Payload field $fieldPath must be in 0..1" }
        }
        if (fieldPath.startsWith("radii.")) {
            require(value >= 0f) { "Payload field $fieldPath must be non-negative" }
        }
        return value
    }

    private fun Map<String, String>.requiredInt(fieldPath: String): Int {
        val rawValue = this[fieldPath]
        require(rawValue != null) { "Payload field $fieldPath is required" }
        return requireNotNull(rawValue.toIntOrNull()) { "Payload field $fieldPath must be an integer" }
    }

    companion object {
        private const val solidPayloadClass = "solid-rgba-rect"
        private const val solidRectPackingPlanHash = "solid-rect-layout-v1"
        private const val solidRectByteSize = 64
        private const val solidRectUsedByteSize = 48

        private val solidRectFloatFields = listOf(
            "rect.left",
            "rect.top",
            "rect.right",
            "rect.bottom",
            "radii.topLeft",
            "radii.topRight",
            "radii.bottomRight",
            "radii.bottomLeft",
            "color.r",
            "color.g",
            "color.b",
            "color.a",
        )

        /** Revalidates the gatherer's exact ABI without repacking or reconstructing source values. */
        internal fun semanticValidationFailure(ref: GPUDrawPayloadRef): String? {
            val slot = ref.uniformSlot ?: return "invalid.preflight.solid_semantic_uniform_missing"
            val block = ref.uniformBlock ?: return "invalid.preflight.solid_semantic_uniform_missing"
            if (ref.resourceSlot != null || ref.resourceBlock != null || ref.gradientStore != null) {
                return "invalid.preflight.solid_semantic_layout"
            }
            if (slot.fingerprint != block.fingerprint) {
                return "invalid.preflight.solid_semantic_fingerprint_mismatch"
            }
            if (slot.byteOffset != 0L || block.packingPlanHash != solidRectPackingPlanHash || block.scope.isBlank()) {
                return "invalid.preflight.solid_semantic_layout"
            }
            if (block.byteSize != solidRectByteSize.toLong() || block.bytes.size != solidRectByteSize ||
                block.bytes.any { it !in 0..255 }
            ) {
                return "invalid.preflight.solid_semantic_byte_count"
            }
            val expectedFields = solidRectFloatFields.mapIndexed { index, fieldPath ->
                Triple(fieldPath, index * Float.SIZE_BYTES.toLong(), "f32")
            } + Triple("padding.reserved", solidRectUsedByteSize.toLong(), "padding")
            val rangesValid = block.fields.size == expectedFields.size && block.fields.indices.all { index ->
                val field = block.fields[index]
                val expected = expectedFields[index]
                val expectedSize = if (index < solidRectFloatFields.size) {
                    Float.SIZE_BYTES.toLong()
                } else {
                    (solidRectByteSize - solidRectUsedByteSize).toLong()
                }
                field.fieldPath == expected.first && field.byteOffset == expected.second &&
                    field.byteSize == expectedSize && field.valueClass == expected.third &&
                    field.byteOffset >= 0L && field.byteSize > 0L &&
                    field.byteOffset <= block.byteSize - field.byteSize
            } && block.fields.zipWithNext().all { (left, right) ->
                left.byteOffset + left.byteSize <= right.byteOffset
            }
            if (!rangesValid) return "invalid.preflight.solid_semantic_field_ranges"

            if (!block.zeroedPadding || block.bytes.drop(solidRectUsedByteSize).any { it != 0 }) {
                return "invalid.preflight.solid_semantic_padding"
            }
            val fieldZeroFactsValid = block.fields.all { field ->
                val start = field.byteOffset.toInt()
                val end = (field.byteOffset + field.byteSize).toInt()
                field.zeroFilled == block.bytes.subList(start, end).all { it == 0 }
            }
            if (!fieldZeroFactsValid) return "invalid.preflight.solid_semantic_field_metadata"

            val byteArray = ByteArray(block.bytes.size) { index -> block.bytes[index].toByte() }
            val floatBuffer = ByteBuffer.wrap(byteArray).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
            val values = List(solidRectFloatFields.size) { index -> floatBuffer.get(index) }
            if (values.any { !it.isFinite() }) return "invalid.preflight.solid_semantic_non_finite"
            if (values.subList(4, 8).any { it < 0f } || values.subList(8, 12).any { it !in 0f..1f }) {
                return "invalid.preflight.solid_semantic_value_range"
            }
            return null
        }

    }
}

/**
 * Minimal pass-local gatherer for first-expansion 2-stop linear gradient payloads.
 *
 * This gatherer packs start/end points, colors, and tile mode into a uniform block
 * following the same pattern as [GPUSolidPayloadGatherer]. It supports only
 * clamp tile mode and 2-stop gradients for the first expansion slice.
 */
class GPULinearGradientPayloadGatherer : GPUPayloadGatherer {
    private val uniformSlots = LinkedHashMap<GPUPayloadFingerprint, GPUUniformPayloadSlot>()
    private var currentScopeId: String? = null

    override fun gather(plan: GPUPayloadGatherPlan, payload: GPUMaterialPayload): GPUDrawPayloadRef {
        require(plan.unsupportedReason == null) { "Cannot gather unsupported payload plan ${plan.planHash}" }
        require(payload.payloadClass == linearGradientPayloadClass) {
            "GPULinearGradientPayloadGatherer only supports $linearGradientPayloadClass payloads"
        }
        enterScope(plan.dedupScope)

        val fieldValues = linearGradientFieldValues(payload.valueFacts)
        val bytes = linearGradientPayloadBytes(fieldValues)
        val fingerprint = GPUPayloadFingerprint(
            sha256Hex(
                listOf(
                    "kind=linear-gradient-uniform",
                    "material=${payload.materialKeyHash}",
                    "write=${plan.writePlanHash}",
                    "bytes=${bytes.joinToString(",")}",
                ).joinToString("\n"),
            ),
        )
        val block = GPUUniformPayloadBlock(
            fingerprint = fingerprint,
            packingPlanHash = linearGradientPackingPlanHash,
            byteSize = linearGradientByteSize.toLong(),
            zeroedPadding = bytes.drop(linearGradientUsedByteSize).all { it == 0 },
            scope = plan.dedupScope,
            bytes = bytes,
            fields = linearGradientFields(fieldValues),
        )
        val slot = uniformSlots.getOrPut(fingerprint) {
            GPUUniformPayloadSlot(
                slotId = GPUPayloadSlotID("${plan.dedupScope}:uniform:${uniformSlots.size}"),
                fingerprint = fingerprint,
                byteOffset = 0L,
            )
        }

        return GPUDrawPayloadRef(
            commandIdValue = payload.valueFacts.requiredInt("command.id"),
            renderStepIdentity = plan.renderStepIdentity,
            uniformSlot = slot,
            uniformBlock = block,
        )
    }

    override fun reset(scopeId: String) {
        enterScope(scopeId)
        uniformSlots.clear()
    }

    private fun enterScope(scopeId: String) {
        require(scopeId.isNotBlank()) { "scopeId must not be blank" }
        if (currentScopeId != scopeId) {
            uniformSlots.clear()
            currentScopeId = scopeId
        }
    }

    private fun linearGradientFieldValues(valueFacts: Map<String, String>): List<Pair<String, Float>> =
        linearGradientFloatFields.map { fieldPath ->
            fieldPath to valueFacts.requiredFiniteFloat(fieldPath)
        }

    private fun linearGradientPayloadBytes(fieldValues: List<Pair<String, Float>>): List<Int> {
        val buffer = ByteBuffer.allocate(linearGradientByteSize).order(ByteOrder.LITTLE_ENDIAN)
        fieldValues.forEach { (_, value) ->
            buffer.putFloat(value)
        }
        return buffer.array().map { byte -> byte.toInt() and 0xff }
    }

    private fun linearGradientFields(fieldValues: List<Pair<String, Float>>): List<GPUUniformPayloadField> =
        fieldValues.mapIndexed { index, (fieldPath, value) ->
            GPUUniformPayloadField(
                fieldPath = fieldPath,
                byteOffset = index * Float.SIZE_BYTES.toLong(),
                byteSize = Float.SIZE_BYTES.toLong(),
                valueClass = "f32",
                zeroFilled = value.toRawBits() == 0,
            )
        } + GPUUniformPayloadField(
            fieldPath = "padding.reserved",
            byteOffset = linearGradientUsedByteSize.toLong(),
            byteSize = (linearGradientByteSize - linearGradientUsedByteSize).toLong(),
            valueClass = "padding",
            zeroFilled = true,
        )

    private fun Map<String, String>.requiredFiniteFloat(fieldPath: String): Float {
        val rawValue = this[fieldPath]
        require(rawValue != null) { "Payload field $fieldPath is required" }
        val value = rawValue.toFloatOrNull()
        require(value != null) { "Payload field $fieldPath must be a float" }
        require(value.isFinite()) { "Payload field $fieldPath must be finite" }
        if (fieldPath.startsWith("color.") || fieldPath.startsWith("startColor.") || fieldPath.startsWith("endColor.")) {
            require(value in 0f..1f) { "Payload field $fieldPath must be in 0..1" }
        }
        return value
    }

    private fun Map<String, String>.requiredInt(fieldPath: String): Int {
        val rawValue = this[fieldPath]
        require(rawValue != null) { "Payload field $fieldPath is required" }
        return requireNotNull(rawValue.toIntOrNull()) { "Payload field $fieldPath must be an integer" }
    }

    private companion object {
        private const val linearGradientPayloadClass = "linear-gradient-2stop"
        private const val linearGradientPackingPlanHash = "linear-gradient-layout-v1"
        private const val linearGradientByteSize = 64
        private const val linearGradientUsedByteSize = 40

        private val linearGradientFloatFields = listOf(
            "start.x",
            "start.y",
            "end.x",
            "end.y",
            "startColor.r",
            "startColor.g",
            "startColor.b",
            "startColor.a",
            "endColor.r",
            "endColor.g",
            "endColor.b",
            "endColor.a",
        )

    }
}

/** Payload diagnostic. */
data class GPUPayloadDiagnostic(
    val code: String,
    val planHash: String? = null,
    val slotId: GPUPayloadSlotID? = null,
    val field: String? = null,
    val terminal: Boolean,
)

private fun sha256Hex(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { byte -> "%02x".format(byte) }
}
