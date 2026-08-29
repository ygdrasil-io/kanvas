package org.graphiks.kanvas.gpu.renderer.commands

import java.security.MessageDigest
import java.util.Collections
import java.util.IdentityHashMap
import org.graphiks.kanvas.font.handoff.GlyphRunDescriptor
import org.graphiks.kanvas.glyph.gpu.GPUColorGlyphLayerPlan
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactGeneration
import org.graphiks.kanvas.gpu.renderer.filters.GPUFilterGraphDescriptor
import org.graphiks.kanvas.gpu.renderer.filters.GPUFilterSourcePlan
import org.graphiks.kanvas.gpu.renderer.filters.GPUSimpleFilterBounds
import org.graphiks.kanvas.gpu.renderer.filters.GPUFilterCropPlan
import org.graphiks.kanvas.gpu.renderer.filters.GPUFilterSamplingPlan
import org.graphiks.kanvas.gpu.renderer.filters.NormalizedMaskFilter
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageRequest
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.text.GPUTextDiagnostic
import org.graphiks.kanvas.gpu.renderer.text.GPUTextArtifactRef
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialProgram
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceAlphaClassification
import org.graphiks.kanvas.gpu.renderer.state.GPUPathSourceAuthority

private val IDENTITY_GRADIENT_LOCAL_MATRIX = listOf(
    1f, 0f, 0f,
    0f, 1f, 0f,
    0f, 0f, 1f,
)

private val IDENTITY_IMAGE_LOCAL_MATRIX = IDENTITY_GRADIENT_LOCAL_MATRIX

/** Canonical command identifier name used by the package layout target. */
@JvmInline
value class GPUDrawCommandID(val value: Int) {
    init {
        require(value >= 0) { "GPUDrawCommandID must be non-negative" }
    }
}

/** Compatibility alias for the earlier command identifier name. */
typealias GPUCommandId = GPUDrawCommandID

/** Draw command family marker used by analysis and route diagnostics. */
enum class GPUDrawCommandFamily {
    /** Rectangle draw family. */
    Rect,
    /** Rounded rectangle draw family. */
    RRect,
    /** Path draw family. */
    Path,
    /** Text draw family. */
    Text,
    /** Image draw family. */
    Image,
    /** Vertices draw family. */
    Vertices,
    /** Filter draw family. */
    Filter,
}

/** Stable adapter/source provenance for a normalized draw command. */
data class GPUDrawCommandProvenance(
    val adapter: String,
    val operation: String,
    val sourceLabel: String,
)

/** Paint-order and dependency token for normalized command ordering. */
@JvmInline
value class GPUDrawOrderingToken(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUDrawOrderingToken.value must not be blank" }
    }
}

/** Captured conservative command bounds before route analysis. */
data class GPUCommandBounds(
    val bounds: GPUBounds,
    val coordinateSpace: String,
    val conservative: Boolean,
)

/** Immutable capture record for normalized command input state. */
data class GPUCommandCapture(
    val commandId: GPUDrawCommandID,
    val family: GPUDrawCommandFamily,
    val provenance: GPUDrawCommandProvenance,
    val bounds: GPUCommandBounds,
    val stateHash: String,
)

/** First-slice draw kinds accepted by the normalized command surface. */
enum class GPUDrawKind {
    /** Filled rectangle command family. */
    FillRect,
    /** Filled rounded rectangle command family. */
    FillRRect,
    /** Filled outer rounded rectangle with a rounded-rectangle hole. */
    FillDRRect,
    /** Filled path command family with tessellated vertex buffers. */
    FillPath,
    /** Text run command family with prepared text stack artifacts. */
    DrawTextRun,
    /** Image draw command family with decoded pixel upload. */
    DrawImageRect,
    /** Prepared DrawVertices/DrawMesh semantic command family. */
    DrawPreparedVertices,
    /** Save-layer command family with offscreen target isolation and composite. */
    DrawLayer,
    /** Filter command family with GPU-native filter render node execution. */
    ApplyFilter,
}

/** Transform class captured by the command adapter before route analysis. */
enum class GPUTransformType {
    /** Identity transform with no coordinate remapping. */
    Identity,
    /** Pure translation transform that keeps rectangles axis-aligned. */
    Translate,
    /** Axis-aligned scale transform that needs route-specific coverage proof. */
    Scale,
    /** Non-perspective affine transform that needs route-specific coverage proof. */
    Affine,
    /** Perspective transform outside the first native route. */
    Perspective,
    /** Singular transform outside the first native route. */
    Singular,
}

/** Clip class captured by the command adapter before route analysis. */
enum class GPUClipKind {
    /** No effective clipping beyond the target. */
    WideOpen,
    /** A single device-space rectangle scissor clip. */
    DeviceRect,
    /** A clip stack that needs later stencil, mask, or analytic clip work. */
    ComplexStack,
}

/** Coarse material classification captured before material lowering. */
enum class GPUMaterialKind {
    /** Solid source color material. */
    SolidColor,
    /** Linear gradient source material. */
    LinearGradient,
    /** Radial gradient source material. */
    RadialGradient,
    /** Sweep gradient source material. */
    SweepGradient,
    /** Image/texture source material — no dispatch support (deferred). */
    ImageDraw,
    /** Runtime-effect (SkRuntimeEffect compatibility) source material — no dispatch support (dependency-gated). */
    RuntimeEffect,
    /** Two-point conical gradient source material. */
    TwoPointConical,
    /** Blend shader combining two child shaders (dst, src) via a blend mode. */
    ShaderBlend,
}

/** Closed prepared-mapping refusal reasons; no caller-provided diagnostic text is accepted. */
enum class GPUPreparedMaterialUnsupportedReason(
    val diagnosticCode: String,
    val diagnosticMessage: String,
) {
    IMAGE_CUBIC_SAMPLING(
        "unsupported.material.mapping.image_cubic_sampling",
        "Prepared image mapping does not implement cubic sampling",
    ),
    IMAGE_TILE_MODE(
        "unsupported.material.mapping.image_tile_mode",
        "Prepared image mapping only implements clamp/clamp tile modes",
    ),
    IMAGE_LOCAL_MATRIX_PERSPECTIVE(
        "unsupported.material.mapping.image_local_matrix_perspective",
        "Prepared image mapping does not implement perspective image local matrices",
    ),
    IMAGE_LOCAL_MATRIX_AFFINE(
        "unsupported.material.mapping.image_local_matrix_affine",
        "Prepared image mapping only implements finite bounded translation/scale local matrices",
    ),
    IMAGE_COLOR_TYPE(
        "unsupported.material.mapping.image_color_type",
        "Prepared image mapping cannot convert this color type exactly",
    ),
    IMAGE_ALPHA_TYPE(
        "unsupported.material.mapping.image_alpha_type",
        "Prepared image mapping cannot preserve this alpha type exactly",
    ),
    IMAGE_COLOR_SPACE(
        "unsupported.material.mapping.image_color_space",
        "Prepared image mapping cannot preserve this color space exactly",
    ),
    IMAGE_PIXEL_PAYLOAD(
        "unsupported.material.mapping.image_pixel_payload",
        "Prepared image mapping requires safe dimensions and an exact pixel payload",
    ),
    GRADIENT_INTERPOLATION(
        "unsupported.material.mapping.gradient_interpolation",
        "Prepared gradient mapping only implements sRGB interpolation",
    ),
    GRADIENT_STOP_COUNT(
        "unsupported.material.mapping.gradient_stop_count",
        "Prepared gradient mapping requires at least one stop",
    ),
    LINEAR_GRADIENT_TILE_MODE(
        "unsupported.material.mapping.linear_gradient_tile_mode",
        "Prepared linear gradient mapping only implements clamp tile mode",
    ),
    LINEAR_GRADIENT_STOP_COUNT(
        "unsupported.material.mapping.linear_gradient_stop_count",
        "Prepared linear gradient mapping exceeds the bounded route stop count",
    ),
    RADIAL_GRADIENT_STOP_COUNT(
        "unsupported.material.radial_gradient_stop_count",
        "Prepared radial gradient mapping exceeds the bounded route stop count",
    ),
    SWEEP_GRADIENT_STOP_COUNT(
        "unsupported.material.sweep_gradient_stop_count",
        "Prepared sweep gradient mapping exceeds the bounded route stop count",
    ),
    LINEAR_GRADIENT_NON_FINITE(
        "unsupported.material.mapping.linear_gradient_non_finite",
        "Prepared linear gradient mapping requires finite geometry, stops, and colors",
    ),
    LINEAR_GRADIENT_LOCAL_MATRIX_PERSPECTIVE(
        "unsupported.material.mapping.linear_gradient_local_matrix_perspective",
        "Prepared linear gradient mapping does not implement perspective local matrices",
    ),
    LINEAR_GRADIENT_LOCAL_MATRIX_AFFINE(
        "unsupported.material.mapping.linear_gradient_local_matrix_affine",
        "Prepared linear gradient mapping requires a finite bounded affine local matrix",
    ),
    RUNTIME_COLOR_FILTER_PLACEMENT(
        "unsupported.material.mapping.runtime_color_filter_placement",
        "Prepared mapping does not implement runtime-effect color-filter placement",
    ),
    COLOR_FILTER(
        "unsupported.material.mapping.color_filter",
        "Prepared mapping cannot apply this color filter exactly",
    ),
    SHADER_GRAPH_CYCLE(
        "unsupported.material.mapping.shader_graph_cycle",
        "Prepared mapping refuses a cyclic shader graph",
    ),
    SHADER_GRAPH_DEPTH(
        "unsupported.material.mapping.shader_graph_depth",
        "Prepared mapping refuses a shader graph beyond its active-depth safety budget",
    ),
    COLOR_FILTER_GRAPH_CYCLE(
        "unsupported.material.mapping.color_filter_graph_cycle",
        "Prepared mapping refuses a cyclic color-filter graph",
    ),
    COLOR_FILTER_GRAPH_DEPTH(
        "unsupported.material.mapping.color_filter_graph_depth",
        "Prepared mapping refuses a color-filter graph beyond its active-depth safety budget",
    ),
    LOCAL_MATRIX(
        "unsupported.material.mapping.local_matrix",
        "Prepared mapping does not implement shader local matrices",
    ),
    WORKING_COLOR_SPACE(
        "unsupported.material.mapping.working_color_space",
        "Prepared mapping does not implement working color-space wrappers",
    ),
    COORDINATE_CLAMP(
        "unsupported.material.mapping.coordinate_clamp",
        "Prepared mapping does not implement coordinate-clamp wrappers",
    ),
    NOISE_SHADER(
        "unsupported.material.mapping.noise_shader",
        "Prepared mapping does not implement this noise shader",
    ),
}

/**
 * Returns the prepared-material refusal reason for gradient facts not consumed by dispatch.
 *
 * CorePrimitive owns the legacy linear-gradient tile-mode ABI. Route analysis may therefore
 * defer only that validation to its concrete route, while all other prepared-material facts
 * remain closed.
 */
fun GPUMaterialDescriptor.gradientFactsRefusalReasonOrNull(
    deferLinearGradientTileModeToRoute: Boolean = false,
    allowThreeStopLinearGradient: Boolean = false,
    allowThreeStopRadialGradient: Boolean = false,
    allowThreeStopSweepGradient: Boolean = false,
): GPUPreparedMaterialUnsupportedReason? =
    when (this) {
        is GPUMaterialDescriptor.LinearGradient -> when {
            tileMode != "clamp" && !deferLinearGradientTileModeToRoute ->
                GPUPreparedMaterialUnsupportedReason.LINEAR_GRADIENT_TILE_MODE
            (allStopPositions?.size ?: 2) !in (if (allowThreeStopLinearGradient) 2..3 else 2..2) ->
                GPUPreparedMaterialUnsupportedReason.LINEAR_GRADIENT_STOP_COUNT
            !linearGradientFactsAreFinite() -> GPUPreparedMaterialUnsupportedReason.LINEAR_GRADIENT_NON_FINITE
            interpolation != "srgb" -> GPUPreparedMaterialUnsupportedReason.GRADIENT_INTERPOLATION
            else -> localMatrix.boundedAffineLocalMatrixRefusalOrNull(allowFullAffine = true)
                ?.toLinearGradientReason()
        }
        is GPUMaterialDescriptor.RadialGradient -> when {
            interpolation != "srgb" -> GPUPreparedMaterialUnsupportedReason.GRADIENT_INTERPOLATION
            localMatrix != IDENTITY_GRADIENT_LOCAL_MATRIX -> GPUPreparedMaterialUnsupportedReason.LOCAL_MATRIX
            (allStopPositions?.size ?: 2) !in
                (if (allowThreeStopRadialGradient) 1..3 else 1..2) ->
                GPUPreparedMaterialUnsupportedReason.RADIAL_GRADIENT_STOP_COUNT
            else -> null
        }
        is GPUMaterialDescriptor.SweepGradient -> when {
            interpolation != "srgb" -> GPUPreparedMaterialUnsupportedReason.GRADIENT_INTERPOLATION
            !localMatrix.isPositiveUniformScaleTranslateGradientLocalMatrix() ->
                GPUPreparedMaterialUnsupportedReason.LOCAL_MATRIX
            (allStopPositions?.size ?: 2) !in
                (if (allowThreeStopSweepGradient) 1..3 else 1..2) ->
                GPUPreparedMaterialUnsupportedReason.SWEEP_GRADIENT_STOP_COUNT
            else -> null
        }
        else -> null
    }

/**
 * Bounded affine subset whose sweep angles remain unchanged: identity or a positive uniform
 * scale plus translation. Skew, rotation, perspective, singular matrices, and non-finite
 * values remain outside the local-matrix contract.
 */
fun List<Float>.isPositiveUniformScaleTranslateGradientLocalMatrix(): Boolean {
    if (size != 9 || any { !it.isFinite() }) return false
    val scale = this[0]
    return scale > 0f &&
        this[1] == 0f &&
        this[3] == 0f &&
        this[4] == scale &&
        this[6] == 0f &&
        this[7] == 0f &&
        this[8] == 1f
}

/** Returns the closed refusal reason for image local-matrix facts outside the bounded route. */
fun GPUMaterialDescriptor.ImageDraw.imageLocalMatrixRefusalReasonOrNull(): GPUPreparedMaterialUnsupportedReason? {
    return when (localMatrix.boundedAffineLocalMatrixRefusalOrNull()) {
        null -> null
        BoundedAffineLocalMatrixRefusal.Perspective ->
            GPUPreparedMaterialUnsupportedReason.IMAGE_LOCAL_MATRIX_PERSPECTIVE
        BoundedAffineLocalMatrixRefusal.Affine ->
            GPUPreparedMaterialUnsupportedReason.IMAGE_LOCAL_MATRIX_AFFINE
    }
}

private enum class BoundedAffineLocalMatrixRefusal { Perspective, Affine }

private fun List<Float>.boundedAffineLocalMatrixRefusalOrNull(
    allowFullAffine: Boolean = false,
): BoundedAffineLocalMatrixRefusal? {
    if (size != 9 || any { !it.isFinite() }) return BoundedAffineLocalMatrixRefusal.Affine
    if (this[6] != 0f || this[7] != 0f || this[8] != 1f) {
        return BoundedAffineLocalMatrixRefusal.Perspective
    }
    if (
        kotlin.math.abs(this[0]) > MAX_BOUNDED_AFFINE_LINEAR ||
        kotlin.math.abs(this[1]) > MAX_BOUNDED_AFFINE_LINEAR ||
        kotlin.math.abs(this[3]) > MAX_BOUNDED_AFFINE_LINEAR ||
        kotlin.math.abs(this[4]) > MAX_BOUNDED_AFFINE_LINEAR ||
        kotlin.math.abs(this[2]) > MAX_BOUNDED_AFFINE_TRANSLATION ||
        kotlin.math.abs(this[5]) > MAX_BOUNDED_AFFINE_TRANSLATION
    ) return BoundedAffineLocalMatrixRefusal.Affine
    if (
        !allowFullAffine &&
        (this[1] != 0f || this[3] != 0f || this[0] <= 0f || this[4] <= 0f)
    ) return BoundedAffineLocalMatrixRefusal.Affine
    return null
}

private fun BoundedAffineLocalMatrixRefusal.toLinearGradientReason(): GPUPreparedMaterialUnsupportedReason =
    when (this) {
        BoundedAffineLocalMatrixRefusal.Perspective ->
            GPUPreparedMaterialUnsupportedReason.LINEAR_GRADIENT_LOCAL_MATRIX_PERSPECTIVE
        BoundedAffineLocalMatrixRefusal.Affine ->
            GPUPreparedMaterialUnsupportedReason.LINEAR_GRADIENT_LOCAL_MATRIX_AFFINE
    }

private fun GPUMaterialDescriptor.LinearGradient.linearGradientFactsAreFinite(): Boolean =
    listOf(
        startX, startY, endX, endY,
        startR, startG, startB, startA,
        endR, endG, endB, endA,
    ).all(Float::isFinite) &&
        (allStopPositions?.all(Float::isFinite) ?: true) &&
        (allStopColors?.all(Float::isFinite) ?: true)

private const val MAX_BOUNDED_AFFINE_LINEAR = 4096f
private const val MAX_BOUNDED_AFFINE_TRANSLATION = 16384f

/** Rectangle geometry in local command coordinates. */
data class GPURect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/** X/Y radii for one rounded-rectangle corner. */
data class GPURRectCornerRadii(
    val x: Float,
    val y: Float,
)

/** Rounded rectangle geometry in local command coordinates. */
data class GPURRect(
    val rect: GPURect,
    val topLeft: GPURRectCornerRadii,
    val topRight: GPURRectCornerRadii = topLeft,
    val bottomRight: GPURRectCornerRadii = topRight,
    val bottomLeft: GPURRectCornerRadii = topLeft,
) {
    /** Convenience constructor for uniform rrect radii used by first-slice fixtures. */
    constructor(
        rect: GPURect,
        radiusX: Float,
        radiusY: Float,
    ) : this(
        rect = rect,
        topLeft = GPURRectCornerRadii(x = radiusX, y = radiusY),
        topRight = GPURRectCornerRadii(x = radiusX, y = radiusY),
        bottomRight = GPURRectCornerRadii(x = radiusX, y = radiusY),
        bottomLeft = GPURRectCornerRadii(x = radiusX, y = radiusY),
    )
}

/** Source-compatible alias for the clip-owned scalar bounds transport. */
typealias GPUBounds = org.graphiks.kanvas.gpu.renderer.clips.GPUBounds

/** M15 path-fill facts captured from the legacy path fill before tessellation. */
data class GPUPathFacts(
    val pathKey: String,
    val verbCount: Int,
    val pointCount: Int,
    val fillRule: String,
    val inverseFill: Boolean,
    val finiteProof: String,
    val volatility: String,
    val transformClass: String,
    val edgeCount: Int,
    val sourceAuthority: GPUPathSourceAuthority = GPUPathSourceAuthority.Unknown,
)

/** Captured transform facts owned by commands and consumed by analysis without replaying Canvas state. */
data class GPUTransformFacts(
    val type: GPUTransformType,
    val translateX: Float = 0f,
    val translateY: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val skewX: Float = 0f,
    val skewY: Float = 0f,
) {
    /** Creates identity transform facts for first-slice fixtures. */
    companion object {
        /** Returns a transform fact record with identity classification. */
        fun identity(): GPUTransformFacts = GPUTransformFacts(GPUTransformType.Identity)

        /** Returns a transform fact record with translate-like classification. */
        fun translation(x: Float, y: Float): GPUTransformFacts =
            GPUTransformFacts(
                type = GPUTransformType.Translate,
                translateX = x,
                translateY = y,
            )

        /** Returns a scale transform fact record that routes must explicitly accept or refuse. */
        fun scale(x: Float, y: Float): GPUTransformFacts =
            GPUTransformFacts(
                type = GPUTransformType.Scale,
                scaleX = x,
                scaleY = y,
            )

        /** Returns a non-perspective affine transform fact record for route-specific validation. */
        fun affine(
            scaleX: Float,
            skewX: Float,
            skewY: Float,
            scaleY: Float,
            translateX: Float = 0f,
            translateY: Float = 0f,
        ): GPUTransformFacts =
            GPUTransformFacts(
                type = GPUTransformType.Affine,
                translateX = translateX,
                translateY = translateY,
                scaleX = scaleX,
                scaleY = scaleY,
                skewX = skewX,
                skewY = skewY,
            )

        /** Returns a transform fact record with perspective classification. */
        fun perspective(): GPUTransformFacts = GPUTransformFacts(GPUTransformType.Perspective)

        /** Returns a transform fact record with singular classification. */
        fun singular(): GPUTransformFacts = GPUTransformFacts(GPUTransformType.Singular)
    }
}

private fun GPUTransformFacts.affineDeterminant(): Float =
    scaleX * scaleY - skewX * skewY

/** Returns whether a captured Scale/Affine determinant is non-finite. */
fun GPUTransformFacts.isAffineDeterminantNonFinite(): Boolean =
    type in setOf(GPUTransformType.Scale, GPUTransformType.Affine) &&
        !affineDeterminant().isFinite()

/** Returns whether a captured Scale/Affine determinant is exactly singular. */
fun GPUTransformFacts.isAffineDeterminantSingular(): Boolean =
    type in setOf(GPUTransformType.Scale, GPUTransformType.Affine) &&
        affineDeterminant() == 0f

/** Captured clip facts owned by commands; complex stacks remain explicit refusal inputs for this slice. */
data class GPUClipFacts(
    val kind: GPUClipKind,
    val bounds: GPUBounds,
    val coverageRequest: GPUClipCoverageRequest? = null,
    val coveragePlan: GPUClipCoveragePlan? = null,
    val executionPlan: GPUClipExecutionPlan? = null,
    /** A Canvas clip captured under perspective, which the affine GPU route must refuse. */
    val perspectiveCaptureRefusal: Boolean = false,
) {
    /** Constructors for common clip fact records. */
    companion object {
        /** Returns a wide-open clip bounded by the provided conservative area. */
        fun wideOpen(bounds: GPUBounds): GPUClipFacts =
            GPUClipFacts(
                kind = GPUClipKind.WideOpen,
                bounds = bounds,
                coveragePlan = GPUClipCoveragePlan.NoClip,
                executionPlan = GPUClipExecutionPlan.NoClip,
            )

        /** Returns a single device-rectangle clip for first-route scissor fixtures. */
        fun deviceRect(bounds: GPUBounds): GPUClipFacts {
            val coordinates = listOf(bounds.left, bounds.top, bounds.right, bounds.bottom)
            val hasExactPixelBounds =
                coordinates.all { coordinate ->
                    coordinate.isFinite() && coordinate.toInt().toFloat() == coordinate
                } &&
                    bounds.left >= 0f &&
                    bounds.top >= 0f &&
                    bounds.right >= bounds.left &&
                    bounds.bottom >= bounds.top
            val pixelBounds = if (hasExactPixelBounds) {
                GPUPixelBounds(
                    bounds.left.toInt(),
                    bounds.top.toInt(),
                    bounds.right.toInt(),
                    bounds.bottom.toInt(),
                )
            } else {
                null
            }
            return GPUClipFacts(
                kind = GPUClipKind.DeviceRect,
                bounds = bounds,
                coveragePlan = pixelBounds?.let { GPUClipCoveragePlan.Scissor(bounds) },
                executionPlan = pixelBounds?.let(GPUClipExecutionPlan::ScissorOnly),
            )
        }

        /** Returns a complex clip stack fact record that must refuse in the first route. */
        fun complexStack(bounds: GPUBounds): GPUClipFacts =
            GPUClipFacts(kind = GPUClipKind.ComplexStack, bounds = bounds)
    }
}

/** Captured render-target facts needed for first-route validation without exposing backend texture handles. */
data class GPUTargetFacts(
    val width: Int,
    val height: Int,
    val colorFormat: String,
) {
    init {
        require(width > 0) { "GPUTargetFacts.width must be positive" }
        require(height > 0) { "GPUTargetFacts.height must be positive" }
        require(colorFormat.isNotBlank()) { "GPUTargetFacts.colorFormat must not be blank" }
    }
}

/** Layer scope classification captured before layer planning and offscreen materialization. */
enum class GPULayerScopeKind {
    /** Root target scope with no saveLayer isolation. */
    Root,
    /** saveLayer or equivalent offscreen scope requiring later proof. */
    SaveLayer,
}

/** Captured layer facts that keep layer/filter/destination-read requirements visible to analysis. */
data class GPULayerFacts(
    val target: GPUTargetFacts,
    val scopeKind: GPULayerScopeKind = GPULayerScopeKind.Root,
    val requiresFilter: Boolean = false,
    val requiresDestinationRead: Boolean = false,
) {
    /** Constructors for layer fact records. */
    companion object {
        /** Returns a root-layer fact record for the provided target. */
        fun root(target: GPUTargetFacts): GPULayerFacts = GPULayerFacts(target)

        /** Returns a saveLayer fact record that remains refused by the first route. */
        fun saveLayer(target: GPUTargetFacts): GPULayerFacts =
            GPULayerFacts(target = target, scopeKind = GPULayerScopeKind.SaveLayer)
    }
}

/** Non-routing facts captured before canonical blend specialization. */
data class GPUBlendFacts(
    val mode: GPUBlendMode,
    val sourceAlpha: GPUSourceAlphaClassification,
) {
    companion object {
        /** Returns the standard translucent source-over facts. */
        fun srcOver(): GPUBlendFacts =
            GPUBlendFacts(
                mode = GPUBlendMode.SRC_OVER,
                sourceAlpha = GPUSourceAlphaClassification.Translucent,
            )
    }
}

/** Exact registered runtime-effect child role; the role is part of descriptor identity. */
enum class GPURuntimeEffectChildRole {
    Shader,
    ColorFilter,
    Blender,
}

/** Closed prepared color-filter set accepted for registered MeshProgram children. */
sealed interface GPUPreparedColorFilterChildDescriptor {
    /** Exact 4x5 color matrix payload. */
    class Matrix(values: List<Float>) : GPUPreparedColorFilterChildDescriptor {
        val values: List<Float> = Collections.unmodifiableList(values.toList())

        override fun equals(other: Any?): Boolean =
            other is Matrix && values == other.values

        override fun hashCode(): Int = values.hashCode()

        override fun toString(): String = "Matrix(values=$values)"
    }

    /** Constant-color blend filter through the canonical blend-mode authority. */
    class Blend(
        rgba: List<Float>,
        val mode: GPUBlendMode,
    ) : GPUPreparedColorFilterChildDescriptor {
        val rgba: List<Float> = Collections.unmodifiableList(rgba.toList())

        override fun equals(other: Any?): Boolean =
            other is Blend && rgba == other.rgba && mode == other.mode

        override fun hashCode(): Int = 31 * rgba.hashCode() + mode.hashCode()

        override fun toString(): String = "Blend(rgba=$rgba, mode=$mode)"
    }

    /** Ordered outer-after-inner composition of accepted color-filter children. */
    class Compose(
        val outer: GPUPreparedColorFilterChildDescriptor,
        val inner: GPUPreparedColorFilterChildDescriptor,
    ) : GPUPreparedColorFilterChildDescriptor {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Compose) return false
            return GPUMaterialDescriptorEquality().equalColorFilter(this, other)
        }

        override fun hashCode(): Int =
            GPUMaterialDescriptorHasher().hashColorFilter(this)

        override fun toString(): String =
            GPUMaterialDescriptorCanonicalizer().colorFilterText(this)
    }

    /** Registered runtime-effect color filter; its registry ABI is validated. */
    class RegisteredRuntimeEffect private constructor(
        private val effectSnapshot: GPUMaterialDescriptor.RuntimeEffect,
        @Suppress("UNUSED_PARAMETER") snapshotToken: GPUMaterialDescriptorSnapshotToken,
    ) : GPUPreparedColorFilterChildDescriptor {
        constructor(effect: GPUMaterialDescriptor.RuntimeEffect) : this(
            effect.deepSnapshot() as GPUMaterialDescriptor.RuntimeEffect,
            GPUMaterialDescriptorSnapshotToken,
        )

        val effect: GPUMaterialDescriptor.RuntimeEffect
            get() = effectSnapshot.deepSnapshot() as GPUMaterialDescriptor.RuntimeEffect

        internal val storedEffect: GPUMaterialDescriptor.RuntimeEffect
            get() = effectSnapshot

        override fun equals(other: Any?): Boolean =
            other is RegisteredRuntimeEffect &&
                GPUMaterialDescriptorEquality().equalColorFilter(this, other)

        override fun hashCode(): Int =
            GPUMaterialDescriptorHasher().hashColorFilter(this)

        override fun toString(): String =
            GPUMaterialDescriptorCanonicalizer().colorFilterText(this)

        internal companion object {
            fun fromSnapshot(effect: GPUMaterialDescriptor.RuntimeEffect): RegisteredRuntimeEffect =
                RegisteredRuntimeEffect(effect, GPUMaterialDescriptorSnapshotToken)
        }
    }
}

/** Closed prepared blender set accepted for registered MeshProgram children. */
sealed interface GPUPreparedBlenderChildDescriptor {
    data class Mode(val mode: GPUBlendMode) : GPUPreparedBlenderChildDescriptor

    data class Arithmetic(
        val k1: Float,
        val k2: Float,
        val k3: Float,
        val k4: Float,
    ) : GPUPreparedBlenderChildDescriptor
}

/** Immutable typed runtime-effect child descriptor. */
sealed interface GPURuntimeEffectChildDescriptor {
    val role: GPURuntimeEffectChildRole

    class Shader private constructor(
        private val materialSnapshot: GPUMaterialDescriptor,
        @Suppress("UNUSED_PARAMETER") snapshotToken: GPUMaterialDescriptorSnapshotToken,
    ) : GPURuntimeEffectChildDescriptor {
        constructor(material: GPUMaterialDescriptor) : this(
            material.deepSnapshot(),
            GPUMaterialDescriptorSnapshotToken,
        )

        val material: GPUMaterialDescriptor
            get() = materialSnapshot.deepSnapshot()

        internal val storedMaterial: GPUMaterialDescriptor
            get() = materialSnapshot

        override val role: GPURuntimeEffectChildRole = GPURuntimeEffectChildRole.Shader

        override fun equals(other: Any?): Boolean =
            other is Shader && materialSnapshot == other.materialSnapshot

        override fun hashCode(): Int = materialSnapshot.hashCode()

        override fun toString(): String = "Shader(material=$materialSnapshot)"

        internal companion object {
            fun fromSnapshot(material: GPUMaterialDescriptor): Shader =
                Shader(material, GPUMaterialDescriptorSnapshotToken)
        }
    }

    data class ColorFilter(
        val filter: GPUPreparedColorFilterChildDescriptor,
    ) : GPURuntimeEffectChildDescriptor {
        override val role: GPURuntimeEffectChildRole = GPURuntimeEffectChildRole.ColorFilter
    }

    data class Blender(
        val blender: GPUPreparedBlenderChildDescriptor,
    ) : GPURuntimeEffectChildDescriptor {
        override val role: GPURuntimeEffectChildRole = GPURuntimeEffectChildRole.Blender
    }
}

/** Material descriptor captured before material-source lowering. */
sealed interface GPUMaterialDescriptor {
    /** Coarse material family used by tests and diagnostics. */
    val kind: GPUMaterialKind

    /** Solid color descriptor for the first GPU renderer slice. */
    data class SolidColor(
        val r: Float,
        val g: Float,
        val b: Float,
        val a: Float,
    ) : GPUMaterialDescriptor {
        override val kind: GPUMaterialKind = GPUMaterialKind.SolidColor
    }

    /** Immutable facts carried alongside gradient descriptors without changing their public constructors. */
    class GradientFacts(
        val interpolation: String = "srgb",
        localMatrix: List<Float> = IDENTITY_GRADIENT_LOCAL_MATRIX,
    ) {
        val localMatrix: List<Float> = Collections.unmodifiableList(localMatrix.toList())

        init {
            require(this.localMatrix.size == 9) {
                "GradientFacts.localMatrix must contain nine values"
            }
            require(this.localMatrix.all { value -> value.isFinite() }) {
                "GradientFacts.localMatrix must contain only finite values"
            }
        }

        override fun equals(other: Any?): Boolean =
            other is GradientFacts &&
                interpolation == other.interpolation &&
                localMatrix == other.localMatrix

        override fun hashCode(): Int = 31 * interpolation.hashCode() + localMatrix.hashCode()
    }

    /** Linear gradient descriptor with immutable stop and interpolation facts. */
    class LinearGradient private constructor(
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        val startR: Float,
        val startG: Float,
        val startB: Float,
        val startA: Float,
        val endR: Float,
        val endG: Float,
        val endB: Float,
        val endA: Float,
        val tileMode: String,
        stopPositions: FloatArray?,
        stopColors: FloatArray?,
        val snippetSourceHash: String? = null,
        val fragmentEntryPoint: String? = null,
        private val gradientFactsSnapshot: GradientFacts,
    ) : GPUMaterialDescriptor {
        private val allStopPositionsSnapshot: FloatArray? = stopPositions?.copyOf()
        private val allStopColorsSnapshot: FloatArray? = stopColors?.copyOf()

        val allStopPositions: FloatArray?
            get() = allStopPositionsSnapshot?.copyOf()

        val allStopColors: FloatArray?
            get() = allStopColorsSnapshot?.copyOf()

        constructor(
            startX: Float,
            startY: Float,
            endX: Float,
            endY: Float,
            startR: Float,
            startG: Float,
            startB: Float,
            startA: Float,
            endR: Float,
            endG: Float,
            endB: Float,
            endA: Float,
            tileMode: String = "clamp",
            allStopPositions: FloatArray? = null,
            allStopColors: FloatArray? = null,
            snippetSourceHash: String? = null,
            fragmentEntryPoint: String? = null,
        ) : this(
            startX, startY, endX, endY,
            startR, startG, startB, startA,
            endR, endG, endB, endA,
            tileMode, allStopPositions, allStopColors, snippetSourceHash, fragmentEntryPoint,
            GradientFacts(),
        )

        val interpolation: String
            get() = gradientFactsSnapshot.interpolation

        val localMatrix: List<Float>
            get() = gradientFactsSnapshot.localMatrix

        fun withGradientFacts(facts: GradientFacts): LinearGradient = LinearGradient(
            startX, startY, endX, endY,
            startR, startG, startB, startA,
            endR, endG, endB, endA,
            tileMode, allStopPositionsSnapshot, allStopColorsSnapshot, snippetSourceHash, fragmentEntryPoint,
            GradientFacts(facts.interpolation, facts.localMatrix),
        )

        fun copy(
            startX: Float = this.startX,
            startY: Float = this.startY,
            endX: Float = this.endX,
            endY: Float = this.endY,
            startR: Float = this.startR,
            startG: Float = this.startG,
            startB: Float = this.startB,
            startA: Float = this.startA,
            endR: Float = this.endR,
            endG: Float = this.endG,
            endB: Float = this.endB,
            endA: Float = this.endA,
            tileMode: String = this.tileMode,
            allStopPositions: FloatArray? = allStopPositionsSnapshot,
            allStopColors: FloatArray? = allStopColorsSnapshot,
            snippetSourceHash: String? = this.snippetSourceHash,
            fragmentEntryPoint: String? = this.fragmentEntryPoint,
        ): LinearGradient = LinearGradient(
            startX, startY, endX, endY,
            startR, startG, startB, startA,
            endR, endG, endB, endA,
            tileMode, allStopPositions, allStopColors, snippetSourceHash, fragmentEntryPoint,
            gradientFactsSnapshot,
        )

        operator fun component1(): Float = startX
        operator fun component2(): Float = startY
        operator fun component3(): Float = endX
        operator fun component4(): Float = endY
        operator fun component5(): Float = startR
        operator fun component6(): Float = startG
        operator fun component7(): Float = startB
        operator fun component8(): Float = startA
        operator fun component9(): Float = endR
        operator fun component10(): Float = endG
        operator fun component11(): Float = endB
        operator fun component12(): Float = endA
        operator fun component13(): String = tileMode
        operator fun component14(): FloatArray? = allStopPositions
        operator fun component15(): FloatArray? = allStopColors
        operator fun component16(): String? = snippetSourceHash
        operator fun component17(): String? = fragmentEntryPoint

        override fun equals(other: Any?): Boolean = this === other || (
            other is LinearGradient &&
                startX.rawBitsEqual(other.startX) && startY.rawBitsEqual(other.startY) &&
                endX.rawBitsEqual(other.endX) && endY.rawBitsEqual(other.endY) &&
                startR.rawBitsEqual(other.startR) && startG.rawBitsEqual(other.startG) &&
                startB.rawBitsEqual(other.startB) && startA.rawBitsEqual(other.startA) &&
                endR.rawBitsEqual(other.endR) && endG.rawBitsEqual(other.endG) &&
                endB.rawBitsEqual(other.endB) && endA.rawBitsEqual(other.endA) &&
                tileMode == other.tileMode &&
                allStopPositionsSnapshot.contentEqualsRawBitsNullable(other.allStopPositionsSnapshot) &&
                allStopColorsSnapshot.contentEqualsRawBitsNullable(other.allStopColorsSnapshot) &&
                snippetSourceHash == other.snippetSourceHash &&
                fragmentEntryPoint == other.fragmentEntryPoint &&
                gradientFactsSnapshot == other.gradientFactsSnapshot
            )

        override fun hashCode(): Int {
            var result = startX.toRawBits()
            result = 31 * result + startY.toRawBits()
            result = 31 * result + endX.toRawBits()
            result = 31 * result + endY.toRawBits()
            result = 31 * result + startR.toRawBits()
            result = 31 * result + startG.toRawBits()
            result = 31 * result + startB.toRawBits()
            result = 31 * result + startA.toRawBits()
            result = 31 * result + endR.toRawBits()
            result = 31 * result + endG.toRawBits()
            result = 31 * result + endB.toRawBits()
            result = 31 * result + endA.toRawBits()
            result = 31 * result + tileMode.hashCode()
            result = 31 * result + (allStopPositionsSnapshot?.rawBitsContentHashCode() ?: 0)
            result = 31 * result + (allStopColorsSnapshot?.rawBitsContentHashCode() ?: 0)
            result = 31 * result + (snippetSourceHash?.hashCode() ?: 0)
            result = 31 * result + (fragmentEntryPoint?.hashCode() ?: 0)
            return 31 * result + gradientFactsSnapshot.hashCode()
        }

        override fun toString(): String = GPUMaterialDescriptorCanonicalizer().text(this)

        override val kind: GPUMaterialKind = GPUMaterialKind.LinearGradient
    }

    /** Radial gradient descriptor with center, radius, and tile mode for M14. */
    class RadialGradient private constructor(
        val centerX: Float,
        val centerY: Float,
        val radius: Float,
        val startR: Float,
        val startG: Float,
        val startB: Float,
        val startA: Float,
        val endR: Float,
        val endG: Float,
        val endB: Float,
        val endA: Float,
        val tileMode: String,
        stopPositions: FloatArray?,
        stopColors: FloatArray?,
        val snippetSourceHash: String?,
        val fragmentEntryPoint: String?,
        private val gradientFactsSnapshot: GradientFacts,
    ) : GPUMaterialDescriptor {
        private val allStopPositionsSnapshot: FloatArray? = stopPositions?.copyOf()
        private val allStopColorsSnapshot: FloatArray? = stopColors?.copyOf()

        val allStopPositions: FloatArray?
            get() = allStopPositionsSnapshot?.copyOf()

        val allStopColors: FloatArray?
            get() = allStopColorsSnapshot?.copyOf()

        constructor(
            centerX: Float,
            centerY: Float,
            radius: Float,
            startR: Float,
            startG: Float,
            startB: Float,
            startA: Float,
            endR: Float,
            endG: Float,
            endB: Float,
            endA: Float,
            tileMode: String = "clamp",
            allStopPositions: FloatArray? = null,
            allStopColors: FloatArray? = null,
            snippetSourceHash: String? = null,
            fragmentEntryPoint: String? = null,
        ) : this(
            centerX,
            centerY,
            radius,
            startR,
            startG,
            startB,
            startA,
            endR,
            endG,
            endB,
            endA,
            tileMode,
            allStopPositions,
            allStopColors,
            snippetSourceHash,
            fragmentEntryPoint,
            GradientFacts(),
        )

        val interpolation: String
            get() = gradientFactsSnapshot.interpolation

        val localMatrix: List<Float>
            get() = gradientFactsSnapshot.localMatrix

        fun withGradientFacts(facts: GradientFacts): RadialGradient = RadialGradient(
            centerX,
            centerY,
            radius,
            startR,
            startG,
            startB,
            startA,
            endR,
            endG,
            endB,
            endA,
            tileMode,
            allStopPositions,
            allStopColors,
            snippetSourceHash,
            fragmentEntryPoint,
            GradientFacts(facts.interpolation, facts.localMatrix),
        )

        fun copy(
            centerX: Float = this.centerX,
            centerY: Float = this.centerY,
            radius: Float = this.radius,
            startR: Float = this.startR,
            startG: Float = this.startG,
            startB: Float = this.startB,
            startA: Float = this.startA,
            endR: Float = this.endR,
            endG: Float = this.endG,
            endB: Float = this.endB,
            endA: Float = this.endA,
            tileMode: String = this.tileMode,
            allStopPositions: FloatArray? = allStopPositionsSnapshot,
            allStopColors: FloatArray? = allStopColorsSnapshot,
            snippetSourceHash: String? = this.snippetSourceHash,
            fragmentEntryPoint: String? = this.fragmentEntryPoint,
        ): RadialGradient = RadialGradient(
            centerX,
            centerY,
            radius,
            startR,
            startG,
            startB,
            startA,
            endR,
            endG,
            endB,
            endA,
            tileMode,
            allStopPositions,
            allStopColors,
            snippetSourceHash,
            fragmentEntryPoint,
            gradientFactsSnapshot,
        )

        operator fun component1(): Float = centerX
        operator fun component2(): Float = centerY
        operator fun component3(): Float = radius
        operator fun component4(): Float = startR
        operator fun component5(): Float = startG
        operator fun component6(): Float = startB
        operator fun component7(): Float = startA
        operator fun component8(): Float = endR
        operator fun component9(): Float = endG
        operator fun component10(): Float = endB
        operator fun component11(): Float = endA
        operator fun component12(): String = tileMode
        operator fun component13(): FloatArray? = allStopPositions
        operator fun component14(): FloatArray? = allStopColors
        operator fun component15(): String? = snippetSourceHash
        operator fun component16(): String? = fragmentEntryPoint

        internal fun gradientFactsSnapshot(): GradientFacts = gradientFactsSnapshot

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is RadialGradient) return false
            return centerX.rawBitsEqual(other.centerX) &&
                centerY.rawBitsEqual(other.centerY) &&
                radius.rawBitsEqual(other.radius) &&
                startR.rawBitsEqual(other.startR) &&
                startG.rawBitsEqual(other.startG) &&
                startB.rawBitsEqual(other.startB) &&
                startA.rawBitsEqual(other.startA) &&
                endR.rawBitsEqual(other.endR) &&
                endG.rawBitsEqual(other.endG) &&
                endB.rawBitsEqual(other.endB) &&
                endA.rawBitsEqual(other.endA) &&
                tileMode == other.tileMode &&
                allStopPositionsSnapshot.contentEqualsRawBitsNullable(other.allStopPositionsSnapshot) &&
                allStopColorsSnapshot.contentEqualsRawBitsNullable(other.allStopColorsSnapshot) &&
                snippetSourceHash == other.snippetSourceHash &&
                fragmentEntryPoint == other.fragmentEntryPoint &&
                gradientFactsSnapshot == other.gradientFactsSnapshot
        }

        override fun hashCode(): Int {
            var result = centerX.toRawBits()
            result = 31 * result + centerY.toRawBits()
            result = 31 * result + radius.toRawBits()
            result = 31 * result + startR.toRawBits()
            result = 31 * result + startG.toRawBits()
            result = 31 * result + startB.toRawBits()
            result = 31 * result + startA.toRawBits()
            result = 31 * result + endR.toRawBits()
            result = 31 * result + endG.toRawBits()
            result = 31 * result + endB.toRawBits()
            result = 31 * result + endA.toRawBits()
            result = 31 * result + tileMode.hashCode()
            result = 31 * result + (allStopPositionsSnapshot?.rawBitsContentHashCode() ?: 0)
            result = 31 * result + (allStopColorsSnapshot?.rawBitsContentHashCode() ?: 0)
            result = 31 * result + (snippetSourceHash?.hashCode() ?: 0)
            result = 31 * result + (fragmentEntryPoint?.hashCode() ?: 0)
            return 31 * result + gradientFactsSnapshot.hashCode()
        }

        override fun toString(): String = GPUMaterialDescriptorCanonicalizer().text(this)

        override val kind: GPUMaterialKind = GPUMaterialKind.RadialGradient
    }

    /** Sweep gradient descriptor with center, start/end angles, and tile mode for M14. */
    class SweepGradient private constructor(
        val centerX: Float,
        val centerY: Float,
        val startAngle: Float,
        val endAngle: Float,
        val startR: Float,
        val startG: Float,
        val startB: Float,
        val startA: Float,
        val endR: Float,
        val endG: Float,
        val endB: Float,
        val endA: Float,
        val tileMode: String,
        stopPositions: FloatArray?,
        stopColors: FloatArray?,
        val snippetSourceHash: String?,
        val fragmentEntryPoint: String?,
        private val gradientFactsSnapshot: GradientFacts,
    ) : GPUMaterialDescriptor {
        private val allStopPositionsSnapshot: FloatArray? = stopPositions?.copyOf()
        private val allStopColorsSnapshot: FloatArray? = stopColors?.copyOf()

        val allStopPositions: FloatArray?
            get() = allStopPositionsSnapshot?.copyOf()

        val allStopColors: FloatArray?
            get() = allStopColorsSnapshot?.copyOf()

        constructor(
            centerX: Float,
            centerY: Float,
            startAngle: Float,
            endAngle: Float,
            startR: Float,
            startG: Float,
            startB: Float,
            startA: Float,
            endR: Float,
            endG: Float,
            endB: Float,
            endA: Float,
            tileMode: String = "clamp",
            allStopPositions: FloatArray? = null,
            allStopColors: FloatArray? = null,
            snippetSourceHash: String? = null,
            fragmentEntryPoint: String? = null,
        ) : this(
            centerX,
            centerY,
            startAngle,
            endAngle,
            startR,
            startG,
            startB,
            startA,
            endR,
            endG,
            endB,
            endA,
            tileMode,
            allStopPositions,
            allStopColors,
            snippetSourceHash,
            fragmentEntryPoint,
            GradientFacts(),
        )

        val interpolation: String
            get() = gradientFactsSnapshot.interpolation

        val localMatrix: List<Float>
            get() = gradientFactsSnapshot.localMatrix

        fun withGradientFacts(facts: GradientFacts): SweepGradient = SweepGradient(
            centerX,
            centerY,
            startAngle,
            endAngle,
            startR,
            startG,
            startB,
            startA,
            endR,
            endG,
            endB,
            endA,
            tileMode,
            allStopPositions,
            allStopColors,
            snippetSourceHash,
            fragmentEntryPoint,
            GradientFacts(facts.interpolation, facts.localMatrix),
        )

        fun copy(
            centerX: Float = this.centerX,
            centerY: Float = this.centerY,
            startAngle: Float = this.startAngle,
            endAngle: Float = this.endAngle,
            startR: Float = this.startR,
            startG: Float = this.startG,
            startB: Float = this.startB,
            startA: Float = this.startA,
            endR: Float = this.endR,
            endG: Float = this.endG,
            endB: Float = this.endB,
            endA: Float = this.endA,
            tileMode: String = this.tileMode,
            allStopPositions: FloatArray? = allStopPositionsSnapshot,
            allStopColors: FloatArray? = allStopColorsSnapshot,
            snippetSourceHash: String? = this.snippetSourceHash,
            fragmentEntryPoint: String? = this.fragmentEntryPoint,
        ): SweepGradient = SweepGradient(
            centerX,
            centerY,
            startAngle,
            endAngle,
            startR,
            startG,
            startB,
            startA,
            endR,
            endG,
            endB,
            endA,
            tileMode,
            allStopPositions,
            allStopColors,
            snippetSourceHash,
            fragmentEntryPoint,
            gradientFactsSnapshot,
        )

        operator fun component1(): Float = centerX
        operator fun component2(): Float = centerY
        operator fun component3(): Float = startAngle
        operator fun component4(): Float = endAngle
        operator fun component5(): Float = startR
        operator fun component6(): Float = startG
        operator fun component7(): Float = startB
        operator fun component8(): Float = startA
        operator fun component9(): Float = endR
        operator fun component10(): Float = endG
        operator fun component11(): Float = endB
        operator fun component12(): Float = endA
        operator fun component13(): String = tileMode
        operator fun component14(): FloatArray? = allStopPositions
        operator fun component15(): FloatArray? = allStopColors
        operator fun component16(): String? = snippetSourceHash
        operator fun component17(): String? = fragmentEntryPoint

        internal fun gradientFactsSnapshot(): GradientFacts = gradientFactsSnapshot

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SweepGradient) return false
            return centerX.rawBitsEqual(other.centerX) &&
                centerY.rawBitsEqual(other.centerY) &&
                startAngle.rawBitsEqual(other.startAngle) &&
                endAngle.rawBitsEqual(other.endAngle) &&
                startR.rawBitsEqual(other.startR) &&
                startG.rawBitsEqual(other.startG) &&
                startB.rawBitsEqual(other.startB) &&
                startA.rawBitsEqual(other.startA) &&
                endR.rawBitsEqual(other.endR) &&
                endG.rawBitsEqual(other.endG) &&
                endB.rawBitsEqual(other.endB) &&
                endA.rawBitsEqual(other.endA) &&
                tileMode == other.tileMode &&
                allStopPositionsSnapshot.contentEqualsRawBitsNullable(other.allStopPositionsSnapshot) &&
                allStopColorsSnapshot.contentEqualsRawBitsNullable(other.allStopColorsSnapshot) &&
                snippetSourceHash == other.snippetSourceHash &&
                fragmentEntryPoint == other.fragmentEntryPoint &&
                gradientFactsSnapshot == other.gradientFactsSnapshot
        }

        override fun hashCode(): Int {
            var result = centerX.toRawBits()
            result = 31 * result + centerY.toRawBits()
            result = 31 * result + startAngle.toRawBits()
            result = 31 * result + endAngle.toRawBits()
            result = 31 * result + startR.toRawBits()
            result = 31 * result + startG.toRawBits()
            result = 31 * result + startB.toRawBits()
            result = 31 * result + startA.toRawBits()
            result = 31 * result + endR.toRawBits()
            result = 31 * result + endG.toRawBits()
            result = 31 * result + endB.toRawBits()
            result = 31 * result + endA.toRawBits()
            result = 31 * result + tileMode.hashCode()
            result = 31 * result + (allStopPositionsSnapshot?.rawBitsContentHashCode() ?: 0)
            result = 31 * result + (allStopColorsSnapshot?.rawBitsContentHashCode() ?: 0)
            result = 31 * result + (snippetSourceHash?.hashCode() ?: 0)
            result = 31 * result + (fragmentEntryPoint?.hashCode() ?: 0)
            return 31 * result + gradientFactsSnapshot.hashCode()
        }

        override fun toString(): String = GPUMaterialDescriptorCanonicalizer().text(this)

        override val kind: GPUMaterialKind = GPUMaterialKind.SweepGradient
    }

    /** Two-point conical gradient descriptor with start/end centers, radii, and tile mode. */
    data class ConicalGradient(
        val startX: Float, val startY: Float,
        val endX: Float, val endY: Float,
        val startRadius: Float, val endRadius: Float,
        val startR: Float, val startG: Float, val startB: Float, val startA: Float,
        val endR: Float, val endG: Float, val endB: Float, val endA: Float,
        val tileMode: String = "clamp",
        val allStopPositions: FloatArray? = null,
        val allStopColors: FloatArray? = null,
        val snippetSourceHash: String? = null,
        val fragmentEntryPoint: String? = null,
    ) : GPUMaterialDescriptor {
        override val kind: GPUMaterialKind = GPUMaterialKind.TwoPointConical
    }

    /** Image shader descriptor with exact pixels and a bounded local sampling transform. */
    data class ImageDraw(
        val imageSourceId: String = "",
        val imageWidth: Int = 0,
        val imageHeight: Int = 0,
        val rgbaPixels: ByteArray = byteArrayOf(),
        val samplingFilterMode: String = "nearest",
        val localMatrix: List<Float> = IDENTITY_IMAGE_LOCAL_MATRIX,
        val alphaOnly: Boolean = false,
        val tintR: Float = 1f,
        val tintG: Float = 1f,
        val tintB: Float = 1f,
        val tintA: Float = 1f,
    ) : GPUMaterialDescriptor {
        override val kind: GPUMaterialKind = GPUMaterialKind.ImageDraw
    }

    /**
     * Runtime-effect descriptor — dependency-gated; dispatch refuses via
     * non-SolidColor material so a runtime-effect paint is never silently
     * solid-filled. `SkRuntimeEffect` stays a registered Kotlin/WGSL
     * compatibility facade (see AGENTS.md); real GPU support is gated by
     * KGPU-M11-008.
     */
    class RuntimeEffect private constructor(
        val effectId: String = "",
        val descriptorVersion: Int = 1,
        private val uniformSnapshot: LinkedHashMap<String, GPURuntimeEffectUniformValue>,
        private val childDescriptorSnapshot: LinkedHashMap<String, GPURuntimeEffectChildDescriptor>,
        private val orderedChildDescriptors: Boolean,
        private val assemblyToken: GPUMaterialDescriptorAssemblyToken?,
        @Suppress("UNUSED_PARAMETER")
        snapshotToken: GPUMaterialDescriptorSnapshotToken,
    ) : GPUMaterialDescriptor {
        constructor(
            effectId: String = "",
            descriptorVersion: Int = 1,
            uniforms: Map<String, GPURuntimeEffectUniformValue> = emptyMap(),
            children: Map<String, GPUMaterialDescriptor> = emptyMap(),
        ) : this(
            effectId = effectId,
            descriptorVersion = descriptorVersion,
            uniformSnapshot = LinkedHashMap(uniforms),
            childDescriptorSnapshot = children.toShaderChildDescriptorMap(),
            orderedChildDescriptors = false,
            assemblyToken = null,
            snapshotToken = GPUMaterialDescriptorSnapshotToken,
        )

        val uniforms: Map<String, GPURuntimeEffectUniformValue>
            get() = Collections.unmodifiableMap(LinkedHashMap(uniformSnapshot))

        /** Legacy shader-only view retained for source compatibility. */
        val children: Map<String, GPUMaterialDescriptor>
            get() = immutableLegacyShaderChildSnapshot()

        /** Authoritative ordered typed child descriptors. */
        val childDescriptors: Map<String, GPURuntimeEffectChildDescriptor>
            get() = Collections.unmodifiableMap(LinkedHashMap(childDescriptorSnapshot))

        internal val storedChildDescriptors: Map<String, GPURuntimeEffectChildDescriptor>
            get() = childDescriptorSnapshot

        override val kind: GPUMaterialKind = GPUMaterialKind.RuntimeEffect

        fun copy(
            effectId: String = this.effectId,
            descriptorVersion: Int = this.descriptorVersion,
            uniforms: Map<String, GPURuntimeEffectUniformValue> = uniformSnapshot,
            children: Map<String, GPUMaterialDescriptor>? = null,
        ): RuntimeEffect =
            if (children == null) {
                fromChildDescriptors(
                    effectId = effectId,
                    descriptorVersion = descriptorVersion,
                    uniforms = uniforms,
                    childDescriptors = childDescriptorSnapshot,
                    orderedChildDescriptors = orderedChildDescriptors,
                )
            } else {
                RuntimeEffect(effectId, descriptorVersion, uniforms, children)
            }

        fun copyWithChildDescriptors(
            effectId: String = this.effectId,
            descriptorVersion: Int = this.descriptorVersion,
            uniforms: Map<String, GPURuntimeEffectUniformValue> = uniformSnapshot,
            childDescriptors: Map<String, GPURuntimeEffectChildDescriptor> =
                childDescriptorSnapshot,
        ): RuntimeEffect =
            withChildDescriptors(effectId, descriptorVersion, uniforms, childDescriptors)

        operator fun component1(): String = effectId
        operator fun component2(): Int = descriptorVersion
        operator fun component3(): Map<String, GPURuntimeEffectUniformValue> = uniforms
        operator fun component4(): Map<String, GPUMaterialDescriptor> = children

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is RuntimeEffect) return false
            return GPUMaterialDescriptorEquality().equal(this, other)
        }

        override fun hashCode(): Int =
            GPUMaterialDescriptorHasher().hash(this)

        override fun toString(): String =
            GPUMaterialDescriptorCanonicalizer().text(this)

        internal fun valueEqualsWith(
            other: RuntimeEffect,
            equalRuntimeChild: (
                GPURuntimeEffectChildDescriptor,
                GPURuntimeEffectChildDescriptor,
            ) -> Boolean,
        ): Boolean =
            effectId == other.effectId &&
                descriptorVersion == other.descriptorVersion &&
                uniformSnapshot == other.uniformSnapshot &&
                orderedChildDescriptors == other.orderedChildDescriptors &&
                childNamesEqual(other) &&
                childDescriptorSnapshot.all { (name, child) ->
                    equalRuntimeChild(
                        child,
                        other.childDescriptorSnapshot.getValue(name),
                    )
                }

        internal fun valueHashWith(
            hashChild: (GPUMaterialDescriptor) -> Int,
            hashRuntimeChild: (GPURuntimeEffectChildDescriptor) -> Int,
        ): Int {
            var result = effectId.hashCode()
            result = 31 * result + descriptorVersion
            result = 31 * result + uniformSnapshot.hashCode()
            if (orderedChildDescriptors) {
                childDescriptorSnapshot.forEach { (name, child) ->
                    result = 31 * result + name.hashCode()
                    result = 31 * result + hashRuntimeChild(child)
                }
            } else {
                result = 31 * result + childDescriptorSnapshot.entries.sumOf { (name, child) ->
                    name.hashCode() xor
                        (child as GPURuntimeEffectChildDescriptor.Shader)
                            .storedMaterial.let(hashChild)
                }
            }
            return result
        }

        internal fun canonicalTextWith(
            childIdentity: (GPUMaterialDescriptor) -> String,
            runtimeChildIdentity: (GPURuntimeEffectChildDescriptor) -> String,
        ): String {
            val children = if (orderedChildDescriptors) {
                childDescriptorSnapshot.entries.joinToString(prefix = "[", postfix = "]") {
                    (name, child) ->
                    "${name.canonicalValue()}=${runtimeChildIdentity(child)}"
                }
            } else {
                legacyStoredShaderChildren().canonicalLegacyChildIdentityString(childIdentity)
            }
            return "RuntimeEffect(" +
                "effectId=${effectId.canonicalValue()}, " +
                "descriptorVersion=$descriptorVersion, " +
                "uniforms=${uniformSnapshot.canonicalUniformString()}, " +
                "children=$children" +
                ")"
        }

        internal fun snapshotWith(
            snapshotRuntimeChild: (
                GPURuntimeEffectChildDescriptor,
            ) -> GPURuntimeEffectChildDescriptor,
        ): RuntimeEffect =
            RuntimeEffect(
                effectId = effectId,
                descriptorVersion = descriptorVersion,
                uniformSnapshot = LinkedHashMap(uniformSnapshot),
                childDescriptorSnapshot = LinkedHashMap(
                    childDescriptorSnapshot.mapValues { (_, child) ->
                        snapshotRuntimeChild(child)
                    },
                ),
                orderedChildDescriptors = orderedChildDescriptors,
                assemblyToken = null,
                snapshotToken = GPUMaterialDescriptorSnapshotToken,
            )

        internal fun wasAssembledWith(
            token: GPUMaterialDescriptorAssemblyToken,
        ): Boolean = assemblyToken === token

        private fun immutableLegacyShaderChildSnapshot(): Map<String, GPUMaterialDescriptor> =
            Collections.unmodifiableMap(
                legacyStoredShaderChildren().deepSnapshotMap(),
            )

        private fun legacyStoredShaderChildren(): LinkedHashMap<String, GPUMaterialDescriptor> =
            LinkedHashMap<String, GPUMaterialDescriptor>().also { result ->
                childDescriptorSnapshot.forEach { (name, child) ->
                    if (child is GPURuntimeEffectChildDescriptor.Shader) {
                        result[name] = child.storedMaterial
                    }
                }
            }

        private fun childNamesEqual(other: RuntimeEffect): Boolean =
            if (orderedChildDescriptors) {
                childDescriptorSnapshot.keys.toList() ==
                    other.childDescriptorSnapshot.keys.toList()
            } else {
                childDescriptorSnapshot.keys == other.childDescriptorSnapshot.keys
            }

        internal companion object {
            fun withChildDescriptors(
                effectId: String = "",
                descriptorVersion: Int = 1,
                uniforms: Map<String, GPURuntimeEffectUniformValue> = emptyMap(),
                childDescriptors: Map<String, GPURuntimeEffectChildDescriptor> = emptyMap(),
            ): RuntimeEffect =
                fromChildDescriptors(
                    effectId = effectId,
                    descriptorVersion = descriptorVersion,
                    uniforms = uniforms,
                    childDescriptors = childDescriptors,
                    orderedChildDescriptors = true,
                )

            private fun fromChildDescriptors(
                effectId: String,
                descriptorVersion: Int,
                uniforms: Map<String, GPURuntimeEffectUniformValue>,
                childDescriptors: Map<String, GPURuntimeEffectChildDescriptor>,
                orderedChildDescriptors: Boolean,
            ): RuntimeEffect {
                val snapshotter = GPUMaterialDescriptorSnapshotter()
                return RuntimeEffect(
                    effectId = effectId,
                    descriptorVersion = descriptorVersion,
                    uniformSnapshot = LinkedHashMap(uniforms),
                    childDescriptorSnapshot = snapshotter.snapshotRuntimeChildren(
                        childDescriptors,
                    ),
                    orderedChildDescriptors = orderedChildDescriptors,
                    assemblyToken = null,
                    snapshotToken = GPUMaterialDescriptorSnapshotToken,
                )
            }

            fun assembled(
                effectId: String,
                descriptorVersion: Int,
                uniforms: Map<String, GPURuntimeEffectUniformValue>,
                children: LinkedHashMap<String, GPUMaterialDescriptor>,
                token: GPUMaterialDescriptorAssemblyToken,
            ): RuntimeEffect =
                RuntimeEffect(
                    effectId = effectId,
                    descriptorVersion = descriptorVersion,
                    uniformSnapshot = LinkedHashMap(uniforms),
                    childDescriptorSnapshot = children.toShaderChildDescriptorMap(),
                    orderedChildDescriptors = false,
                    assemblyToken = token,
                    snapshotToken = GPUMaterialDescriptorSnapshotToken,
                )

            fun assembledWithChildDescriptors(
                effectId: String,
                descriptorVersion: Int,
                uniforms: Map<String, GPURuntimeEffectUniformValue>,
                childDescriptors: LinkedHashMap<String, GPURuntimeEffectChildDescriptor>,
                token: GPUMaterialDescriptorAssemblyToken,
            ): RuntimeEffect =
                RuntimeEffect(
                    effectId = effectId,
                    descriptorVersion = descriptorVersion,
                    uniformSnapshot = LinkedHashMap(uniforms),
                    childDescriptorSnapshot = childDescriptors,
                    orderedChildDescriptors = true,
                    assemblyToken = token,
                    snapshotToken = GPUMaterialDescriptorSnapshotToken,
                )
        }
    }

    /**
     * Blend shader descriptor combining two defensively snapshotted child shaders.
     *
     * This is intentionally no longer Kotlin `data class` metadata: private
     * snapshot storage is required to keep the public child and byte getters
     * defensive. The prior constructor, getters, `copy`, `component1` through
     * `component5`, and value-method JVM signatures remain available.
     */
    class BlendShader private constructor(
        val mode: String,
        private val dstSnapshot: GPUMaterialDescriptor,
        private val srcSnapshot: GPUMaterialDescriptor,
        val wgslCombined: String,
        private val uniformByteSnapshot: ByteArray,
        private val assemblyToken: GPUMaterialDescriptorAssemblyToken?,
        @Suppress("UNUSED_PARAMETER")
        snapshotToken: GPUMaterialDescriptorSnapshotToken,
    ) : GPUMaterialDescriptor {
        constructor(
            mode: String,
            dst: GPUMaterialDescriptor,
            src: GPUMaterialDescriptor,
            wgslCombined: String = "",
            uniformBytes: ByteArray = byteArrayOf(),
        ) : this(
            mode = mode,
            dstSnapshot = dst.deepSnapshot(),
            srcSnapshot = src.deepSnapshot(),
            wgslCombined = wgslCombined,
            uniformByteSnapshot = uniformBytes.copyOf(),
            assemblyToken = null,
            snapshotToken = GPUMaterialDescriptorSnapshotToken,
        )

        val dst: GPUMaterialDescriptor
            get() = dstSnapshot.deepSnapshot()

        val src: GPUMaterialDescriptor
            get() = srcSnapshot.deepSnapshot()

        val uniformBytes: ByteArray
            get() = uniformByteSnapshot.copyOf()

        override val kind: GPUMaterialKind = GPUMaterialKind.ShaderBlend

        fun copy(
            mode: String = this.mode,
            dst: GPUMaterialDescriptor = dstSnapshot,
            src: GPUMaterialDescriptor = srcSnapshot,
            wgslCombined: String = this.wgslCombined,
            uniformBytes: ByteArray = uniformByteSnapshot,
        ): BlendShader =
            BlendShader(mode, dst, src, wgslCombined, uniformBytes)

        operator fun component1(): String = mode
        operator fun component2(): GPUMaterialDescriptor = dst
        operator fun component3(): GPUMaterialDescriptor = src
        operator fun component4(): String = wgslCombined
        operator fun component5(): ByteArray = uniformBytes

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is BlendShader) return false
            return GPUMaterialDescriptorEquality().equal(this, other)
        }

        override fun hashCode(): Int =
            GPUMaterialDescriptorHasher().hash(this)

        override fun toString(): String =
            GPUMaterialDescriptorCanonicalizer().text(this)

        internal val storedDst: GPUMaterialDescriptor
            get() = dstSnapshot

        internal val storedSrc: GPUMaterialDescriptor
            get() = srcSnapshot

        internal fun valueEqualsWith(
            other: BlendShader,
            equalChild: (GPUMaterialDescriptor, GPUMaterialDescriptor) -> Boolean,
        ): Boolean =
            mode == other.mode &&
                wgslCombined == other.wgslCombined &&
                uniformByteSnapshot.contentEquals(other.uniformByteSnapshot) &&
                equalChild(dstSnapshot, other.dstSnapshot) &&
                equalChild(srcSnapshot, other.srcSnapshot)

        internal fun valueHashWith(
            hashChild: (GPUMaterialDescriptor) -> Int,
        ): Int {
            var result = mode.hashCode()
            result = 31 * result + hashChild(dstSnapshot)
            result = 31 * result + hashChild(srcSnapshot)
            result = 31 * result + wgslCombined.hashCode()
            result = 31 * result + uniformByteSnapshot.contentHashCode()
            return result
        }

        internal fun canonicalTextWith(
            childIdentity: (GPUMaterialDescriptor) -> String,
        ): String =
            "BlendShader(" +
                "mode=${mode.canonicalValue()}, " +
                "dst=${childIdentity(dstSnapshot)}, " +
                "src=${childIdentity(srcSnapshot)}, " +
                "wgslCombined=${wgslCombined.canonicalValue()}, " +
                "uniformBytes=${uniformByteSnapshot.canonicalValue()}" +
                ")"

        internal fun snapshotWith(
            snapshotChild: (GPUMaterialDescriptor) -> GPUMaterialDescriptor,
        ): BlendShader =
            BlendShader(
                mode = mode,
                dstSnapshot = snapshotChild(dstSnapshot),
                srcSnapshot = snapshotChild(srcSnapshot),
                wgslCombined = wgslCombined,
                uniformByteSnapshot = uniformByteSnapshot.copyOf(),
                assemblyToken = null,
                snapshotToken = GPUMaterialDescriptorSnapshotToken,
            )

        internal fun wasAssembledWith(
            token: GPUMaterialDescriptorAssemblyToken,
        ): Boolean = assemblyToken === token

        internal companion object {
            fun assembled(
                mode: String,
                dst: GPUMaterialDescriptor,
                src: GPUMaterialDescriptor,
                wgslCombined: String,
                uniformBytes: ByteArray,
                token: GPUMaterialDescriptorAssemblyToken,
            ): BlendShader =
                BlendShader(
                    mode = mode,
                    dstSnapshot = dst,
                    srcSnapshot = src,
                    wgslCombined = wgslCombined,
                    uniformByteSnapshot = uniformBytes.copyOf(),
                    assemblyToken = token,
                    snapshotToken = GPUMaterialDescriptorSnapshotToken,
                )
        }
    }

    /**
     * Typed fail-closed marker emitted only by the prepared mapper.
     *
     * [source] retains an exactly mapped base when one exists; it is evidence
     * only and is never compiled as a substitute for the refused operation.
     */
    class Unsupported private constructor(
        val reason: GPUPreparedMaterialUnsupportedReason,
        val originalKind: GPUMaterialKind,
        private val sourceSnapshot: GPUMaterialDescriptor?,
        private val evidenceSnapshot: GPUPreparedMaterialUnsupportedEvidence?,
        private val assemblyToken: GPUMaterialDescriptorAssemblyToken?,
        @Suppress("UNUSED_PARAMETER")
        snapshotToken: GPUMaterialDescriptorSnapshotToken,
    ) : GPUMaterialDescriptor {
        constructor(
            reason: GPUPreparedMaterialUnsupportedReason,
            originalKind: GPUMaterialKind,
            source: GPUMaterialDescriptor? = null,
            evidence: GPUPreparedMaterialUnsupportedEvidence? = null,
        ) : this(
            reason = reason,
            originalKind = originalKind,
            sourceSnapshot = source?.deepSnapshot(),
            evidenceSnapshot = evidence?.deepSnapshot(),
            assemblyToken = null,
            snapshotToken = GPUMaterialDescriptorSnapshotToken,
        )

        val source: GPUMaterialDescriptor?
            get() = sourceSnapshot?.deepSnapshot()

        val evidence: GPUPreparedMaterialUnsupportedEvidence?
            get() = evidenceSnapshot?.deepSnapshot()

        internal val storedSource: GPUMaterialDescriptor?
            get() = sourceSnapshot

        override val kind: GPUMaterialKind = originalKind

        fun copy(
            reason: GPUPreparedMaterialUnsupportedReason = this.reason,
            originalKind: GPUMaterialKind = this.originalKind,
            source: GPUMaterialDescriptor? = sourceSnapshot,
            evidence: GPUPreparedMaterialUnsupportedEvidence? = evidenceSnapshot,
        ): Unsupported =
            Unsupported(reason, originalKind, source, evidence)

        operator fun component1(): GPUPreparedMaterialUnsupportedReason = reason
        operator fun component2(): GPUMaterialKind = originalKind
        operator fun component3(): GPUMaterialDescriptor? = source
        operator fun component4(): GPUPreparedMaterialUnsupportedEvidence? = evidence

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Unsupported) return false
            return GPUMaterialDescriptorEquality().equal(this, other)
        }

        override fun hashCode(): Int =
            GPUMaterialDescriptorHasher().hash(this)

        override fun toString(): String =
            GPUMaterialDescriptorCanonicalizer().text(this)

        internal fun valueEqualsWith(
            other: Unsupported,
            equalChild: (GPUMaterialDescriptor, GPUMaterialDescriptor) -> Boolean,
        ): Boolean =
            reason == other.reason &&
                originalKind == other.originalKind &&
                when {
                    sourceSnapshot == null -> other.sourceSnapshot == null
                    other.sourceSnapshot == null -> false
                    else -> equalChild(sourceSnapshot, other.sourceSnapshot)
                } &&
                evidenceSnapshot == other.evidenceSnapshot

        internal fun valueHashWith(
            hashChild: (GPUMaterialDescriptor) -> Int,
        ): Int {
            var result = reason.hashCode()
            result = 31 * result + originalKind.hashCode()
            result = 31 * result + (sourceSnapshot?.let(hashChild) ?: 0)
            result = 31 * result + (evidenceSnapshot?.hashCode() ?: 0)
            return result
        }

        internal fun canonicalTextWith(
            childIdentity: (GPUMaterialDescriptor) -> String,
        ): String =
            "Unsupported(" +
                "reason=$reason, " +
                "originalKind=$originalKind, " +
                "source=${sourceSnapshot?.let(childIdentity)}, " +
                "evidence=${evidenceSnapshot?.canonicalString()}" +
                ")"

        internal fun snapshotWith(
            snapshotChild: (GPUMaterialDescriptor) -> GPUMaterialDescriptor,
            snapshotEvidence: (
                GPUPreparedMaterialUnsupportedEvidence,
            ) -> GPUPreparedMaterialUnsupportedEvidence,
        ): Unsupported =
            Unsupported(
                reason = reason,
                originalKind = originalKind,
                sourceSnapshot = sourceSnapshot?.let(snapshotChild),
                evidenceSnapshot = evidenceSnapshot?.let(snapshotEvidence),
                assemblyToken = null,
                snapshotToken = GPUMaterialDescriptorSnapshotToken,
            )

        internal fun wasAssembledWith(
            token: GPUMaterialDescriptorAssemblyToken,
        ): Boolean = assemblyToken === token

        internal companion object {
            fun assembled(
                reason: GPUPreparedMaterialUnsupportedReason,
                originalKind: GPUMaterialKind,
                source: GPUMaterialDescriptor?,
                evidence: GPUPreparedMaterialUnsupportedEvidence?,
                token: GPUMaterialDescriptorAssemblyToken,
            ): Unsupported =
                Unsupported(
                    reason = reason,
                    originalKind = originalKind,
                    sourceSnapshot = source,
                    evidenceSnapshot = evidence,
                    assemblyToken = token,
                    snapshotToken = GPUMaterialDescriptorSnapshotToken,
                )
        }
    }
}

/**
 * Closed, immutable evidence retained for a prepared-mapping refusal.
 *
 * Evidence is diagnostic identity only. The prepared compiler never treats it
 * as executable source or as a replacement for [GPUMaterialDescriptor.Unsupported.source].
 */
sealed interface GPUPreparedMaterialUnsupportedEvidence {
    /**
     * Exact identity evidence for an unsupported runtime-effect color filter.
     *
     * Child values are canonical identities of each complete child-filter
     * graph, keyed by the exact runtime-effect child name.
     */
    class RuntimeColorFilter(
        val effectId: String,
        uniforms: Map<String, GPURuntimeEffectUniformValue>,
        childIdentities: Map<String, String>,
    ) : GPUPreparedMaterialUnsupportedEvidence {
        private val uniformSnapshot = LinkedHashMap(uniforms)
        private val childIdentitySnapshot = LinkedHashMap(childIdentities)

        val uniforms: Map<String, GPURuntimeEffectUniformValue>
            get() = Collections.unmodifiableMap(LinkedHashMap(uniformSnapshot))

        val childIdentities: Map<String, String>
            get() = Collections.unmodifiableMap(LinkedHashMap(childIdentitySnapshot))

        override fun equals(other: Any?): Boolean =
            this === other ||
                (
                    other is RuntimeColorFilter &&
                        effectId == other.effectId &&
                        uniformSnapshot == other.uniformSnapshot &&
                        childIdentitySnapshot == other.childIdentitySnapshot
                    )

        override fun hashCode(): Int {
            var result = effectId.hashCode()
            result = 31 * result + uniformSnapshot.hashCode()
            result = 31 * result + childIdentitySnapshot.hashCode()
            return result
        }

        override fun toString(): String =
            "RuntimeColorFilter(" +
                "effectId=${effectId.canonicalValue()}, " +
                "uniforms=${uniformSnapshot.canonicalUniformString()}, " +
                "childIdentities=${childIdentitySnapshot.canonicalStringMap()}" +
                ")"
    }
}

/**
 * One defensive descriptor-assembly operation.
 *
 * Arbitrary caller descriptors are snapshotted through one identity cache.
 * Only deeply defensive composites created by this exact session may bypass
 * another recursive snapshot.
 *
 * A session is a single-threaded, one-operation authority. Create it at the
 * start of one prepared mapping operation, do not share it across threads or
 * operations, and discard it when that operation completes.
 */
class GPUMaterialDescriptorAssemblySession {
    private val token = GPUMaterialDescriptorAssemblyToken()
    private val snapshotter = GPUMaterialDescriptorSnapshotter()
    private var assembledDescriptorCount = 0
    private var assembledChildEdgeCount = 0

    fun runtimeEffect(
        effectId: String = "",
        descriptorVersion: Int = 1,
        uniforms: Map<String, GPURuntimeEffectUniformValue> = emptyMap(),
        children: Map<String, GPUMaterialDescriptor> = emptyMap(),
    ): GPUMaterialDescriptor.RuntimeEffect {
        val childSnapshots = LinkedHashMap<String, GPUMaterialDescriptor>()
        children.forEach { (name, child) ->
            childSnapshots[name] = snapshotForAssembly(child)
        }
        assembledDescriptorCount += 1
        assembledChildEdgeCount += children.size
        return GPUMaterialDescriptor.RuntimeEffect.assembled(
            effectId = effectId,
            descriptorVersion = descriptorVersion,
            uniforms = uniforms,
            children = childSnapshots,
            token = token,
        )
    }

    /** Assembles one ordered typed runtime-effect child set for MeshProgram mapping. */
    fun runtimeEffectWithChildDescriptors(
        effectId: String = "",
        descriptorVersion: Int = 1,
        uniforms: Map<String, GPURuntimeEffectUniformValue> = emptyMap(),
        childDescriptors: Map<String, GPURuntimeEffectChildDescriptor> = emptyMap(),
    ): GPUMaterialDescriptor.RuntimeEffect {
        val childSnapshots = snapshotter.snapshotRuntimeChildren(childDescriptors)
        assembledDescriptorCount += 1
        assembledChildEdgeCount += childDescriptors.size
        return GPUMaterialDescriptor.RuntimeEffect.assembledWithChildDescriptors(
            effectId = effectId,
            descriptorVersion = descriptorVersion,
            uniforms = uniforms,
            childDescriptors = childSnapshots,
            token = token,
        )
    }

    fun blendShader(
        mode: String,
        dst: GPUMaterialDescriptor,
        src: GPUMaterialDescriptor,
        wgslCombined: String = "",
        uniformBytes: ByteArray = byteArrayOf(),
    ): GPUMaterialDescriptor.BlendShader {
        val destinationSnapshot = snapshotForAssembly(dst)
        val sourceSnapshot = snapshotForAssembly(src)
        assembledDescriptorCount += 1
        assembledChildEdgeCount += 2
        return GPUMaterialDescriptor.BlendShader.assembled(
            mode = mode,
            dst = destinationSnapshot,
            src = sourceSnapshot,
            wgslCombined = wgslCombined,
            uniformBytes = uniformBytes,
            token = token,
        )
    }

    fun unsupported(
        reason: GPUPreparedMaterialUnsupportedReason,
        originalKind: GPUMaterialKind,
        source: GPUMaterialDescriptor? = null,
        evidence: GPUPreparedMaterialUnsupportedEvidence? = null,
    ): GPUMaterialDescriptor.Unsupported {
        val sourceSnapshot = source?.let(::snapshotForAssembly)
        val evidenceSnapshot = evidence?.let(snapshotter::snapshotEvidence)
        assembledDescriptorCount += 1
        if (source != null) assembledChildEdgeCount += 1
        return GPUMaterialDescriptor.Unsupported.assembled(
            reason = reason,
            originalKind = originalKind,
            source = sourceSnapshot,
            evidence = evidenceSnapshot,
            token = token,
        )
    }

    internal val snapshotStatistics: GPUMaterialDescriptorSnapshotStatistics
        get() = GPUMaterialDescriptorSnapshotStatistics(
            assembledDescriptorCount = assembledDescriptorCount,
            assembledChildEdgeCount = assembledChildEdgeCount,
            sourceSnapshotCount = snapshotter.snapshotCount,
            evidenceSnapshotCount = snapshotter.evidenceSnapshotCount,
        )

    private fun snapshotForAssembly(
        descriptor: GPUMaterialDescriptor,
    ): GPUMaterialDescriptor =
        if (descriptor.wasAssembledWith(token)) {
            descriptor
        } else {
            snapshotter.snapshot(descriptor)
        }
}

internal data class GPUMaterialDescriptorSnapshotStatistics(
    val assembledDescriptorCount: Int,
    val assembledChildEdgeCount: Int,
    val sourceSnapshotCount: Int,
    val evidenceSnapshotCount: Int,
)

internal class GPUMaterialDescriptorAssemblyToken

private fun GPUMaterialDescriptor.wasAssembledWith(
    token: GPUMaterialDescriptorAssemblyToken,
): Boolean =
    when (this) {
        is GPUMaterialDescriptor.RuntimeEffect -> wasAssembledWith(token)
        is GPUMaterialDescriptor.BlendShader -> wasAssembledWith(token)
        is GPUMaterialDescriptor.Unsupported -> wasAssembledWith(token)
        else -> false
    }

private object GPUMaterialDescriptorSnapshotToken

private const val MAX_RUNTIME_EFFECT_CHILD_DESCRIPTOR_DEPTH = 64

private fun GPUMaterialDescriptor.deepSnapshot(): GPUMaterialDescriptor =
    GPUMaterialDescriptorSnapshotter().snapshot(this)

private fun Map<String, GPUMaterialDescriptor>.deepSnapshotMap():
    LinkedHashMap<String, GPUMaterialDescriptor> {
    val snapshotter = GPUMaterialDescriptorSnapshotter()
    return LinkedHashMap<String, GPUMaterialDescriptor>().also { result ->
        forEach { (name, child) ->
            result[name] = snapshotter.snapshot(child)
        }
    }
}

private fun Map<String, GPUMaterialDescriptor>.toShaderChildDescriptorMap():
    LinkedHashMap<String, GPURuntimeEffectChildDescriptor> {
    val snapshotter = GPUMaterialDescriptorSnapshotter()
    return LinkedHashMap<String, GPURuntimeEffectChildDescriptor>().also { result ->
        forEach { (name, child) ->
            result[name] = GPURuntimeEffectChildDescriptor.Shader.fromSnapshot(
                snapshotter.snapshot(child),
            )
        }
    }
}

private class GPUMaterialDescriptorGraphValidator(
    private val operation: String,
    private val exception: (String) -> RuntimeException,
) {
    private val materialHeights = IdentityHashMap<GPUMaterialDescriptor, Int>()
    private val runtimeChildHeights =
        IdentityHashMap<GPURuntimeEffectChildDescriptor, Int>()
    private val colorFilterHeights =
        IdentityHashMap<GPUPreparedColorFilterChildDescriptor, Int>()
    private val activeMaterials =
        Collections.newSetFromMap(IdentityHashMap<GPUMaterialDescriptor, Boolean>())
    private val activeRuntimeChildren =
        Collections.newSetFromMap(
            IdentityHashMap<GPURuntimeEffectChildDescriptor, Boolean>(),
        )
    private val activeColorFilters =
        Collections.newSetFromMap(
            IdentityHashMap<GPUPreparedColorFilterChildDescriptor, Boolean>(),
        )

    fun validateMaterial(descriptor: GPUMaterialDescriptor) {
        materialHeight(descriptor, depth = 1)
    }

    fun validateRuntimeChildren(
        children: Map<String, GPURuntimeEffectChildDescriptor>,
    ) {
        children.values.forEach { child -> runtimeChildHeight(child, depth = 2) }
    }

    fun validateColorFilter(filter: GPUPreparedColorFilterChildDescriptor) {
        colorFilterHeight(filter, depth = 1)
    }

    private fun materialHeight(
        descriptor: GPUMaterialDescriptor,
        depth: Int,
    ): Int {
        ensureDepth(depth, height = 1)
        materialHeights[descriptor]?.let { height ->
            ensureDepth(depth, height)
            return height
        }
        if (!activeMaterials.add(descriptor)) {
            throw exception("runtime-effect $operation material cycle detected")
        }
        val height = try {
            when (descriptor) {
                is GPUMaterialDescriptor.RuntimeEffect ->
                    1 + (descriptor.storedChildDescriptors.values.maxOfOrNull { child ->
                        runtimeChildHeight(child, depth + 1)
                    } ?: 0)
                is GPUMaterialDescriptor.BlendShader ->
                    1 + maxOf(
                        materialHeight(descriptor.storedDst, depth + 1),
                        materialHeight(descriptor.storedSrc, depth + 1),
                    )
                is GPUMaterialDescriptor.Unsupported ->
                    1 + (descriptor.storedSource?.let { source ->
                        materialHeight(source, depth + 1)
                    } ?: 0)
                else -> 1
            }
        } finally {
            activeMaterials.remove(descriptor)
        }
        ensureDepth(depth, height)
        materialHeights[descriptor] = height
        return height
    }

    private fun runtimeChildHeight(
        child: GPURuntimeEffectChildDescriptor,
        depth: Int,
    ): Int {
        ensureDepth(depth, height = 1)
        runtimeChildHeights[child]?.let { height ->
            ensureDepth(depth, height)
            return height
        }
        if (!activeRuntimeChildren.add(child)) {
            throw exception("runtime-effect $operation child cycle detected")
        }
        val height = try {
            when (child) {
                is GPURuntimeEffectChildDescriptor.Shader ->
                    materialHeight(child.storedMaterial, depth)
                is GPURuntimeEffectChildDescriptor.ColorFilter ->
                    colorFilterHeight(child.filter, depth)
                is GPURuntimeEffectChildDescriptor.Blender -> 1
            }
        } finally {
            activeRuntimeChildren.remove(child)
        }
        ensureDepth(depth, height)
        runtimeChildHeights[child] = height
        return height
    }

    private fun colorFilterHeight(
        filter: GPUPreparedColorFilterChildDescriptor,
        depth: Int,
    ): Int {
        ensureDepth(depth, height = 1)
        colorFilterHeights[filter]?.let { height ->
            ensureDepth(depth, height)
            return height
        }
        if (!activeColorFilters.add(filter)) {
            throw exception("runtime-effect $operation color-filter cycle detected")
        }
        val height = try {
            when (filter) {
                is GPUPreparedColorFilterChildDescriptor.Compose ->
                    1 + maxOf(
                        colorFilterHeight(filter.outer, depth + 1),
                        colorFilterHeight(filter.inner, depth + 1),
                    )
                is GPUPreparedColorFilterChildDescriptor.RegisteredRuntimeEffect ->
                    materialHeight(filter.storedEffect, depth)
                else -> 1
            }
        } finally {
            activeColorFilters.remove(filter)
        }
        ensureDepth(depth, height)
        colorFilterHeights[filter] = height
        return height
    }

    private fun ensureDepth(depth: Int, height: Int) {
        if (depth > MAX_RUNTIME_EFFECT_CHILD_DESCRIPTOR_DEPTH ||
            height > MAX_RUNTIME_EFFECT_CHILD_DESCRIPTOR_DEPTH - depth + 1
        ) {
            throw exception("runtime-effect $operation graph depth exceeded")
        }
    }
}

/**
 * Returns whether this graph contains a terminal prepared-material refusal.
 * Invalid depth or cycles are treated as unsupported so callers stay fail-closed.
 */
fun GPUMaterialDescriptor.containsUnsupportedMaterial(): Boolean =
    try {
        GPUMaterialDescriptorGraphValidator(
            operation = "unsupported traversal",
            exception = ::IllegalStateException,
        ).validateMaterial(this)
        GPUMaterialUnsupportedAnalyzer().containsMaterial(this)
    } catch (_: RuntimeException) {
        true
    }

private class GPUMaterialUnsupportedAnalyzer {
    private val materialResults = IdentityHashMap<GPUMaterialDescriptor, Boolean>()
    private val runtimeChildResults =
        IdentityHashMap<GPURuntimeEffectChildDescriptor, Boolean>()
    private val colorFilterResults =
        IdentityHashMap<GPUPreparedColorFilterChildDescriptor, Boolean>()
    private val activeMaterials =
        Collections.newSetFromMap(IdentityHashMap<GPUMaterialDescriptor, Boolean>())
    private val activeRuntimeChildren =
        Collections.newSetFromMap(
            IdentityHashMap<GPURuntimeEffectChildDescriptor, Boolean>(),
        )
    private val activeColorFilters =
        Collections.newSetFromMap(
            IdentityHashMap<GPUPreparedColorFilterChildDescriptor, Boolean>(),
        )

    fun containsMaterial(descriptor: GPUMaterialDescriptor): Boolean {
        materialResults[descriptor]?.let { return it }
        if (!activeMaterials.add(descriptor)) return true
        val result = try {
            when (descriptor) {
                is GPUMaterialDescriptor.Unsupported -> true
                is GPUMaterialDescriptor.BlendShader ->
                    containsMaterial(descriptor.storedDst) ||
                        containsMaterial(descriptor.storedSrc)
                is GPUMaterialDescriptor.RuntimeEffect ->
                    descriptor.storedChildDescriptors.values.any(::containsRuntimeChild)
                else -> false
            }
        } finally {
            activeMaterials.remove(descriptor)
        }
        materialResults[descriptor] = result
        return result
    }

    private fun containsRuntimeChild(
        child: GPURuntimeEffectChildDescriptor,
    ): Boolean {
        runtimeChildResults[child]?.let { return it }
        if (!activeRuntimeChildren.add(child)) return true
        val result = try {
            when (child) {
                is GPURuntimeEffectChildDescriptor.Shader ->
                    containsMaterial(child.storedMaterial)
                is GPURuntimeEffectChildDescriptor.ColorFilter ->
                    containsColorFilter(child.filter)
                is GPURuntimeEffectChildDescriptor.Blender -> false
            }
        } finally {
            activeRuntimeChildren.remove(child)
        }
        runtimeChildResults[child] = result
        return result
    }

    private fun containsColorFilter(
        filter: GPUPreparedColorFilterChildDescriptor,
    ): Boolean {
        colorFilterResults[filter]?.let { return it }
        if (!activeColorFilters.add(filter)) return true
        val result = try {
            when (filter) {
                is GPUPreparedColorFilterChildDescriptor.Compose ->
                    containsColorFilter(filter.outer) ||
                        containsColorFilter(filter.inner)
                is GPUPreparedColorFilterChildDescriptor.RegisteredRuntimeEffect ->
                    containsMaterial(filter.storedEffect)
                else -> false
            }
        } finally {
            activeColorFilters.remove(filter)
        }
        colorFilterResults[filter] = result
        return result
    }
}

private class GPUMaterialDescriptorSnapshotter {
    private val snapshots =
        IdentityHashMap<GPUMaterialDescriptor, GPUMaterialDescriptor>()
    private val runtimeChildSnapshots =
        IdentityHashMap<
            GPURuntimeEffectChildDescriptor,
            GPURuntimeEffectChildDescriptor,
            >()
    private val colorFilterSnapshots =
        IdentityHashMap<
            GPUPreparedColorFilterChildDescriptor,
            GPUPreparedColorFilterChildDescriptor,
            >()
    private val evidenceSnapshots =
        IdentityHashMap<
            GPUPreparedMaterialUnsupportedEvidence,
            GPUPreparedMaterialUnsupportedEvidence,
            >()
    var snapshotCount: Int = 0
        private set
    var evidenceSnapshotCount: Int = 0
        private set

    fun snapshot(descriptor: GPUMaterialDescriptor): GPUMaterialDescriptor {
        snapshotValidator().validateMaterial(descriptor)
        return snapshotValidated(descriptor)
    }

    fun snapshotRuntimeChildren(
        children: Map<String, GPURuntimeEffectChildDescriptor>,
    ): LinkedHashMap<String, GPURuntimeEffectChildDescriptor> {
        snapshotValidator().validateRuntimeChildren(children)
        return LinkedHashMap<String, GPURuntimeEffectChildDescriptor>().also { result ->
            children.forEach { (name, child) ->
                result[name] = snapshotRuntimeChildValidated(child)
            }
        }
    }

    private fun snapshotValidated(
        descriptor: GPUMaterialDescriptor,
    ): GPUMaterialDescriptor {
        snapshots[descriptor]?.let { return it }
        snapshotCount += 1
        val result = when (descriptor) {
            is GPUMaterialDescriptor.SolidColor -> descriptor.copy()
            is GPUMaterialDescriptor.LinearGradient -> descriptor.copy()
            is GPUMaterialDescriptor.RadialGradient -> descriptor.copy(
                allStopPositions = descriptor.allStopPositions?.copyOf(),
                allStopColors = descriptor.allStopColors?.copyOf(),
            ).withGradientFacts(descriptor.gradientFactsSnapshot())
            is GPUMaterialDescriptor.SweepGradient -> descriptor.copy(
                allStopPositions = descriptor.allStopPositions?.copyOf(),
                allStopColors = descriptor.allStopColors?.copyOf(),
            ).withGradientFacts(descriptor.gradientFactsSnapshot())
            is GPUMaterialDescriptor.ConicalGradient -> descriptor.copy(
                allStopPositions = descriptor.allStopPositions?.copyOf(),
                allStopColors = descriptor.allStopColors?.copyOf(),
            )
            is GPUMaterialDescriptor.ImageDraw ->
                descriptor.copy(
                    rgbaPixels = descriptor.rgbaPixels.copyOf(),
                    localMatrix = descriptor.localMatrix.toList(),
                )
            is GPUMaterialDescriptor.RuntimeEffect ->
                descriptor.snapshotWith(::snapshotRuntimeChildValidated)
            is GPUMaterialDescriptor.BlendShader ->
                descriptor.snapshotWith(::snapshotValidated)
            is GPUMaterialDescriptor.Unsupported ->
                descriptor.snapshotWith(::snapshotValidated, ::snapshotEvidence)
        }
        snapshots[descriptor] = result
        return result
    }

    private fun snapshotRuntimeChildValidated(
        child: GPURuntimeEffectChildDescriptor,
    ): GPURuntimeEffectChildDescriptor {
        runtimeChildSnapshots[child]?.let { return it }
        val result = when (child) {
            is GPURuntimeEffectChildDescriptor.Shader ->
                GPURuntimeEffectChildDescriptor.Shader.fromSnapshot(
                    snapshotValidated(child.storedMaterial),
                )
            is GPURuntimeEffectChildDescriptor.ColorFilter ->
                GPURuntimeEffectChildDescriptor.ColorFilter(
                    snapshotColorFilterValidated(child.filter),
                )
            is GPURuntimeEffectChildDescriptor.Blender ->
                GPURuntimeEffectChildDescriptor.Blender(child.blender)
        }
        runtimeChildSnapshots[child] = result
        return result
    }

    private fun snapshotColorFilterValidated(
        filter: GPUPreparedColorFilterChildDescriptor,
    ): GPUPreparedColorFilterChildDescriptor {
        colorFilterSnapshots[filter]?.let { return it }
        val result = when (filter) {
            is GPUPreparedColorFilterChildDescriptor.Matrix ->
                GPUPreparedColorFilterChildDescriptor.Matrix(filter.values)
            is GPUPreparedColorFilterChildDescriptor.Blend ->
                GPUPreparedColorFilterChildDescriptor.Blend(filter.rgba, filter.mode)
            is GPUPreparedColorFilterChildDescriptor.Compose ->
                GPUPreparedColorFilterChildDescriptor.Compose(
                    snapshotColorFilterValidated(filter.outer),
                    snapshotColorFilterValidated(filter.inner),
                )
            is GPUPreparedColorFilterChildDescriptor.RegisteredRuntimeEffect ->
                GPUPreparedColorFilterChildDescriptor.RegisteredRuntimeEffect.fromSnapshot(
                    snapshotValidated(filter.storedEffect) as GPUMaterialDescriptor.RuntimeEffect,
                )
        }
        colorFilterSnapshots[filter] = result
        return result
    }

    fun snapshotEvidence(
        evidence: GPUPreparedMaterialUnsupportedEvidence,
    ): GPUPreparedMaterialUnsupportedEvidence {
        evidenceSnapshots[evidence]?.let { return it }
        evidenceSnapshotCount += 1
        return evidence.deepSnapshot().also { evidenceSnapshots[evidence] = it }
    }

    private fun snapshotValidator(): GPUMaterialDescriptorGraphValidator =
        GPUMaterialDescriptorGraphValidator(
            operation = "snapshot",
            exception = ::IllegalArgumentException,
        )
}

private class GPUMaterialDescriptorEquality {
    private val results =
        IdentityHashMap<
            GPUMaterialDescriptor,
            IdentityHashMap<GPUMaterialDescriptor, Boolean>,
            >()
    private val runtimeChildResults =
        IdentityHashMap<
            GPURuntimeEffectChildDescriptor,
            IdentityHashMap<GPURuntimeEffectChildDescriptor, Boolean>,
            >()
    private val colorFilterResults =
        IdentityHashMap<
            GPUPreparedColorFilterChildDescriptor,
            IdentityHashMap<GPUPreparedColorFilterChildDescriptor, Boolean>,
            >()

    fun equal(
        left: GPUMaterialDescriptor,
        right: GPUMaterialDescriptor,
    ): Boolean {
        val validator = equalityValidator()
        validator.validateMaterial(left)
        validator.validateMaterial(right)
        return equalValidated(left, right)
    }

    fun equalColorFilter(
        left: GPUPreparedColorFilterChildDescriptor,
        right: GPUPreparedColorFilterChildDescriptor,
    ): Boolean {
        val validator = equalityValidator()
        validator.validateColorFilter(left)
        validator.validateColorFilter(right)
        return equalColorFilterValidated(left, right)
    }

    private fun equalValidated(
        left: GPUMaterialDescriptor,
        right: GPUMaterialDescriptor,
    ): Boolean {
        if (left === right) return true
        results[left]?.get(right)?.let { return it }
        val result = when {
            left is GPUMaterialDescriptor.SolidColor &&
                right is GPUMaterialDescriptor.SolidColor ->
                left == right
            left is GPUMaterialDescriptor.LinearGradient &&
                right is GPUMaterialDescriptor.LinearGradient ->
                left == right
            left is GPUMaterialDescriptor.RadialGradient &&
                right is GPUMaterialDescriptor.RadialGradient ->
                left.copy(allStopPositions = null, allStopColors = null) ==
                    right.copy(allStopPositions = null, allStopColors = null) &&
                    left.allStopPositions.contentEqualsRawBitsNullable(right.allStopPositions) &&
                    left.allStopColors.contentEqualsRawBitsNullable(right.allStopColors) &&
                    left.gradientFactsSnapshot() == right.gradientFactsSnapshot()
            left is GPUMaterialDescriptor.SweepGradient &&
                right is GPUMaterialDescriptor.SweepGradient ->
                left.copy(allStopPositions = null, allStopColors = null) ==
                    right.copy(allStopPositions = null, allStopColors = null) &&
                    left.allStopPositions.contentEqualsRawBitsNullable(right.allStopPositions) &&
                    left.allStopColors.contentEqualsRawBitsNullable(right.allStopColors) &&
                    left.gradientFactsSnapshot() == right.gradientFactsSnapshot()
            left is GPUMaterialDescriptor.ConicalGradient &&
                right is GPUMaterialDescriptor.ConicalGradient ->
                left.copy(allStopPositions = null, allStopColors = null) ==
                    right.copy(allStopPositions = null, allStopColors = null) &&
                    left.allStopPositions.contentEqualsRawBitsNullable(right.allStopPositions) &&
                    left.allStopColors.contentEqualsRawBitsNullable(right.allStopColors)
            left is GPUMaterialDescriptor.ImageDraw &&
                right is GPUMaterialDescriptor.ImageDraw ->
                left.copy(rgbaPixels = DEEP_COMPARE_EMPTY_BYTES) ==
                    right.copy(rgbaPixels = DEEP_COMPARE_EMPTY_BYTES) &&
                    left.rgbaPixels.contentEquals(right.rgbaPixels)
            left is GPUMaterialDescriptor.RuntimeEffect &&
                right is GPUMaterialDescriptor.RuntimeEffect ->
                left.valueEqualsWith(
                    right,
                    ::equalRuntimeChildValidated,
                )
            left is GPUMaterialDescriptor.BlendShader &&
                right is GPUMaterialDescriptor.BlendShader ->
                left.valueEqualsWith(right, ::equalValidated)
            left is GPUMaterialDescriptor.Unsupported &&
                right is GPUMaterialDescriptor.Unsupported ->
                left.valueEqualsWith(right, ::equalValidated)
            else -> false
        }
        results.getOrPut(left) { IdentityHashMap() }[right] = result
        return result
    }

    private fun equalRuntimeChildValidated(
        left: GPURuntimeEffectChildDescriptor,
        right: GPURuntimeEffectChildDescriptor,
    ): Boolean {
        if (left === right) return true
        runtimeChildResults[left]?.get(right)?.let { return it }
        val result = when {
            left is GPURuntimeEffectChildDescriptor.Shader &&
                right is GPURuntimeEffectChildDescriptor.Shader ->
                equalValidated(left.storedMaterial, right.storedMaterial)
            left is GPURuntimeEffectChildDescriptor.ColorFilter &&
                right is GPURuntimeEffectChildDescriptor.ColorFilter ->
                equalColorFilterValidated(left.filter, right.filter)
            left is GPURuntimeEffectChildDescriptor.Blender &&
                right is GPURuntimeEffectChildDescriptor.Blender ->
                left.blender == right.blender
            else -> false
        }
        runtimeChildResults.getOrPut(left) { IdentityHashMap() }[right] = result
        return result
    }

    private fun equalColorFilterValidated(
        left: GPUPreparedColorFilterChildDescriptor,
        right: GPUPreparedColorFilterChildDescriptor,
    ): Boolean {
        if (left === right) return true
        colorFilterResults[left]?.get(right)?.let { return it }
        val result = when {
            left is GPUPreparedColorFilterChildDescriptor.Matrix &&
                right is GPUPreparedColorFilterChildDescriptor.Matrix ->
                left.values == right.values
            left is GPUPreparedColorFilterChildDescriptor.Blend &&
                right is GPUPreparedColorFilterChildDescriptor.Blend ->
                left.rgba == right.rgba && left.mode == right.mode
            left is GPUPreparedColorFilterChildDescriptor.Compose &&
                right is GPUPreparedColorFilterChildDescriptor.Compose ->
                equalColorFilterValidated(left.outer, right.outer) &&
                    equalColorFilterValidated(left.inner, right.inner)
            left is GPUPreparedColorFilterChildDescriptor.RegisteredRuntimeEffect &&
                right is GPUPreparedColorFilterChildDescriptor.RegisteredRuntimeEffect ->
                equalValidated(left.storedEffect, right.storedEffect)
            else -> false
        }
        colorFilterResults.getOrPut(left) { IdentityHashMap() }[right] = result
        return result
    }

    private fun equalityValidator(): GPUMaterialDescriptorGraphValidator =
        GPUMaterialDescriptorGraphValidator(
            operation = "equality",
            exception = ::IllegalStateException,
        )
}

private class GPUMaterialDescriptorHasher {
    private val hashes = IdentityHashMap<GPUMaterialDescriptor, Int>()
    private val runtimeChildHashes =
        IdentityHashMap<GPURuntimeEffectChildDescriptor, Int>()
    private val colorFilterHashes =
        IdentityHashMap<GPUPreparedColorFilterChildDescriptor, Int>()

    fun hash(descriptor: GPUMaterialDescriptor): Int {
        hashValidator().validateMaterial(descriptor)
        return hashValidated(descriptor)
    }

    fun hashColorFilter(filter: GPUPreparedColorFilterChildDescriptor): Int {
        hashValidator().validateColorFilter(filter)
        return hashColorFilterValidated(filter)
    }

    private fun hashValidated(descriptor: GPUMaterialDescriptor): Int {
        hashes[descriptor]?.let { return it }
        val result = when (descriptor) {
            is GPUMaterialDescriptor.SolidColor -> descriptor.hashCode()
            is GPUMaterialDescriptor.LinearGradient -> descriptor.hashCode()
            is GPUMaterialDescriptor.RadialGradient ->
                31 * (
                    31 * descriptor.copy(
                        allStopPositions = null,
                        allStopColors = null,
                    ).hashCode() +
                        (descriptor.allStopPositions?.rawBitsContentHashCode() ?: 0)
                    ) + (descriptor.allStopColors?.rawBitsContentHashCode() ?: 0) +
                    descriptor.gradientFactsSnapshot().hashCode()
            is GPUMaterialDescriptor.SweepGradient ->
                31 * (
                    31 * descriptor.copy(
                        allStopPositions = null,
                        allStopColors = null,
                    ).hashCode() +
                        (descriptor.allStopPositions?.rawBitsContentHashCode() ?: 0)
                    ) + (descriptor.allStopColors?.rawBitsContentHashCode() ?: 0) +
                    descriptor.gradientFactsSnapshot().hashCode()
            is GPUMaterialDescriptor.ConicalGradient ->
                31 * (
                    31 * descriptor.copy(
                        allStopPositions = null,
                        allStopColors = null,
                    ).hashCode() +
                        (descriptor.allStopPositions?.rawBitsContentHashCode() ?: 0)
                    ) + (descriptor.allStopColors?.rawBitsContentHashCode() ?: 0)
            is GPUMaterialDescriptor.ImageDraw ->
                31 * descriptor.copy(rgbaPixels = DEEP_COMPARE_EMPTY_BYTES).hashCode() +
                    descriptor.rgbaPixels.contentHashCode()
            is GPUMaterialDescriptor.RuntimeEffect ->
                descriptor.valueHashWith(
                    ::hashValidated,
                    ::hashRuntimeChildValidated,
                )
            is GPUMaterialDescriptor.BlendShader ->
                descriptor.valueHashWith(::hashValidated)
            is GPUMaterialDescriptor.Unsupported ->
                descriptor.valueHashWith(::hashValidated)
        }
        hashes[descriptor] = result
        return result
    }

    private fun hashRuntimeChildValidated(
        child: GPURuntimeEffectChildDescriptor,
    ): Int {
        runtimeChildHashes[child]?.let { return it }
        val result = when (child) {
            is GPURuntimeEffectChildDescriptor.Shader ->
                31 * child.role.hashCode() + hashValidated(child.storedMaterial)
            is GPURuntimeEffectChildDescriptor.ColorFilter ->
                31 * child.role.hashCode() + hashColorFilterValidated(child.filter)
            is GPURuntimeEffectChildDescriptor.Blender ->
                31 * child.role.hashCode() + child.blender.hashCode()
        }
        runtimeChildHashes[child] = result
        return result
    }

    private fun hashColorFilterValidated(
        filter: GPUPreparedColorFilterChildDescriptor,
    ): Int {
        colorFilterHashes[filter]?.let { return it }
        val result = when (filter) {
            is GPUPreparedColorFilterChildDescriptor.Matrix -> filter.values.hashCode()
            is GPUPreparedColorFilterChildDescriptor.Blend ->
                31 * filter.rgba.hashCode() + filter.mode.hashCode()
            is GPUPreparedColorFilterChildDescriptor.Compose ->
                31 * hashColorFilterValidated(filter.outer) +
                    hashColorFilterValidated(filter.inner)
            is GPUPreparedColorFilterChildDescriptor.RegisteredRuntimeEffect ->
                hashValidated(filter.storedEffect)
        }
        colorFilterHashes[filter] = result
        return result
    }

    private fun hashValidator(): GPUMaterialDescriptorGraphValidator =
        GPUMaterialDescriptorGraphValidator(
            operation = "hash",
            exception = ::IllegalStateException,
        )
}

private data class GPUMaterialDescriptorCanonicalForm(
    val text: String,
    val identity: String,
)

private class GPUMaterialDescriptorCanonicalizer {
    private val forms =
        IdentityHashMap<GPUMaterialDescriptor, GPUMaterialDescriptorCanonicalForm>()
    private val runtimeChildIdentities =
        IdentityHashMap<GPURuntimeEffectChildDescriptor, String>()
    private val colorFilterIdentities =
        IdentityHashMap<GPUPreparedColorFilterChildDescriptor, String>()

    fun text(descriptor: GPUMaterialDescriptor): String {
        canonicalValidator().validateMaterial(descriptor)
        return formValidated(descriptor).text
    }

    fun colorFilterText(filter: GPUPreparedColorFilterChildDescriptor): String {
        canonicalValidator().validateColorFilter(filter)
        return colorFilterIdentityValidated(filter)
    }

    private fun identityValidated(descriptor: GPUMaterialDescriptor): String =
        formValidated(descriptor).identity

    private fun formValidated(
        descriptor: GPUMaterialDescriptor,
    ): GPUMaterialDescriptorCanonicalForm {
        forms[descriptor]?.let { return it }
        val text = when (descriptor) {
            is GPUMaterialDescriptor.SolidColor ->
                "SolidColor(" +
                    "r=${descriptor.r.canonicalValue()}, " +
                    "g=${descriptor.g.canonicalValue()}, " +
                    "b=${descriptor.b.canonicalValue()}, " +
                    "a=${descriptor.a.canonicalValue()}" +
                    ")"
            is GPUMaterialDescriptor.LinearGradient ->
                "LinearGradient(" +
                    "start=(${descriptor.startX.canonicalValue()}," +
                    "${descriptor.startY.canonicalValue()}), " +
                    "end=(${descriptor.endX.canonicalValue()}," +
                    "${descriptor.endY.canonicalValue()}), " +
                    "startColor=${descriptor.startColorCanonicalValue()}, " +
                    "endColor=${descriptor.endColorCanonicalValue()}, " +
                    "tileMode=${descriptor.tileMode.canonicalValue()}, " +
                    "positions=${descriptor.allStopPositions.canonicalValue()}, " +
                    "colors=${descriptor.allStopColors.canonicalValue()}, " +
                    "interpolation=${descriptor.interpolation.canonicalValue()}, " +
                    "localMatrix=${descriptor.localMatrix.canonicalFloatList()}, " +
                    "snippetSourceHash=${descriptor.snippetSourceHash.canonicalNullableValue()}, " +
                    "fragmentEntryPoint=${descriptor.fragmentEntryPoint.canonicalNullableValue()}" +
                    ")"
            is GPUMaterialDescriptor.RadialGradient ->
                "RadialGradient(" +
                    "center=(${descriptor.centerX.canonicalValue()}," +
                    "${descriptor.centerY.canonicalValue()}), " +
                    "radius=${descriptor.radius.canonicalValue()}, " +
                    "startColor=${descriptor.startColorCanonicalValue()}, " +
                    "endColor=${descriptor.endColorCanonicalValue()}, " +
                    "tileMode=${descriptor.tileMode.canonicalValue()}, " +
                    "positions=${descriptor.allStopPositions.canonicalValue()}, " +
                    "colors=${descriptor.allStopColors.canonicalValue()}, " +
                    "interpolation=${descriptor.interpolation.canonicalValue()}, " +
                    "localMatrix=${descriptor.localMatrix.canonicalFloatList()}, " +
                    "snippetSourceHash=${descriptor.snippetSourceHash.canonicalNullableValue()}, " +
                    "fragmentEntryPoint=${descriptor.fragmentEntryPoint.canonicalNullableValue()}" +
                    ")"
            is GPUMaterialDescriptor.SweepGradient ->
                "SweepGradient(" +
                    "center=(${descriptor.centerX.canonicalValue()}," +
                    "${descriptor.centerY.canonicalValue()}), " +
                    "angles=(${descriptor.startAngle.canonicalValue()}," +
                    "${descriptor.endAngle.canonicalValue()}), " +
                    "startColor=${descriptor.startColorCanonicalValue()}, " +
                    "endColor=${descriptor.endColorCanonicalValue()}, " +
                    "tileMode=${descriptor.tileMode.canonicalValue()}, " +
                    "positions=${descriptor.allStopPositions.canonicalValue()}, " +
                    "colors=${descriptor.allStopColors.canonicalValue()}, " +
                    "interpolation=${descriptor.interpolation.canonicalValue()}, " +
                    "localMatrix=${descriptor.localMatrix.canonicalFloatList()}, " +
                    "snippetSourceHash=${descriptor.snippetSourceHash.canonicalNullableValue()}, " +
                    "fragmentEntryPoint=${descriptor.fragmentEntryPoint.canonicalNullableValue()}" +
                    ")"
            is GPUMaterialDescriptor.ConicalGradient ->
                "ConicalGradient(" +
                    "start=(${descriptor.startX.canonicalValue()}," +
                    "${descriptor.startY.canonicalValue()}," +
                    "${descriptor.startRadius.canonicalValue()}), " +
                    "end=(${descriptor.endX.canonicalValue()}," +
                    "${descriptor.endY.canonicalValue()}," +
                    "${descriptor.endRadius.canonicalValue()}), " +
                    "startColor=${descriptor.startColorCanonicalValue()}, " +
                    "endColor=${descriptor.endColorCanonicalValue()}, " +
                    "tileMode=${descriptor.tileMode.canonicalValue()}, " +
                    "positions=${descriptor.allStopPositions.canonicalValue()}, " +
                    "colors=${descriptor.allStopColors.canonicalValue()}, " +
                    "snippetSourceHash=${descriptor.snippetSourceHash.canonicalNullableValue()}, " +
                    "fragmentEntryPoint=${descriptor.fragmentEntryPoint.canonicalNullableValue()}" +
                    ")"
            is GPUMaterialDescriptor.ImageDraw ->
                "ImageDraw(" +
                    "source=${descriptor.imageSourceId.canonicalValue()}, " +
                    "size=${descriptor.imageWidth}x${descriptor.imageHeight}, " +
                    "pixels=${descriptor.rgbaPixels.canonicalValue()}, " +
                    "sampling=${descriptor.samplingFilterMode.canonicalValue()}, " +
                    "localMatrix=${descriptor.localMatrix.canonicalFloatList()}, " +
                    "alphaOnly=${descriptor.alphaOnly}, " +
                    "tint=(${descriptor.tintR.canonicalValue()}," +
                    "${descriptor.tintG.canonicalValue()}," +
                    "${descriptor.tintB.canonicalValue()}," +
                    "${descriptor.tintA.canonicalValue()})" +
                    ")"
            is GPUMaterialDescriptor.RuntimeEffect ->
                descriptor.canonicalTextWith(
                    ::identityValidated,
                    ::runtimeChildIdentityValidated,
                )
            is GPUMaterialDescriptor.BlendShader ->
                descriptor.canonicalTextWith(::identityValidated)
            is GPUMaterialDescriptor.Unsupported ->
                descriptor.canonicalTextWith(::identityValidated)
        }
        return GPUMaterialDescriptorCanonicalForm(
            text = text,
            identity = "sha256:${text.sha256Hex()}",
        ).also { forms[descriptor] = it }
    }

    private fun runtimeChildIdentityValidated(
        child: GPURuntimeEffectChildDescriptor,
    ): String {
        runtimeChildIdentities[child]?.let { return it }
        val result = when (child) {
            is GPURuntimeEffectChildDescriptor.Shader ->
                "Shader(${identityValidated(child.storedMaterial)})"
            is GPURuntimeEffectChildDescriptor.ColorFilter ->
                "ColorFilter(${colorFilterIdentityValidated(child.filter)})"
            is GPURuntimeEffectChildDescriptor.Blender ->
                "Blender(${child.blender.canonicalString()})"
        }
        runtimeChildIdentities[child] = result
        return result
    }

    private fun colorFilterIdentityValidated(
        filter: GPUPreparedColorFilterChildDescriptor,
    ): String {
        colorFilterIdentities[filter]?.let { return it }
        val result = when (filter) {
            is GPUPreparedColorFilterChildDescriptor.Matrix ->
                "Matrix(${filter.values.canonicalFloatList()})"
            is GPUPreparedColorFilterChildDescriptor.Blend ->
                "Blend(rgba=${filter.rgba.canonicalFloatList()},mode=${filter.mode.name})"
            is GPUPreparedColorFilterChildDescriptor.Compose ->
                "Compose(outer=${colorFilterIdentityValidated(filter.outer)}," +
                    "inner=${colorFilterIdentityValidated(filter.inner)})"
            is GPUPreparedColorFilterChildDescriptor.RegisteredRuntimeEffect ->
                "RegisteredRuntimeEffect(${identityValidated(filter.storedEffect)})"
        }
        colorFilterIdentities[filter] = result
        return result
    }

    private fun canonicalValidator(): GPUMaterialDescriptorGraphValidator =
        GPUMaterialDescriptorGraphValidator(
            operation = "canonical",
            exception = ::IllegalStateException,
        )
}

private fun FloatArray?.contentEqualsNullable(other: FloatArray?): Boolean =
    when {
        this == null -> other == null
        other == null -> false
        else -> contentEquals(other)
    }

private fun Float.rawBitsEqual(other: Float): Boolean = toRawBits() == other.toRawBits()

private fun FloatArray?.contentEqualsRawBitsNullable(other: FloatArray?): Boolean {
    val left = this ?: return other == null
    val right = other ?: return false
    return left.size == right.size && left.indices.all { index ->
        left[index].rawBitsEqual(right[index])
    }
}

private fun FloatArray.rawBitsContentHashCode(): Int {
    var result = 1
    for (value in this) {
        result = 31 * result + value.toRawBits()
    }
    return result
}

private fun GPUPreparedMaterialUnsupportedEvidence.deepSnapshot():
    GPUPreparedMaterialUnsupportedEvidence =
    when (this) {
        is GPUPreparedMaterialUnsupportedEvidence.RuntimeColorFilter ->
            GPUPreparedMaterialUnsupportedEvidence.RuntimeColorFilter(
                effectId = effectId,
                uniforms = uniforms,
                childIdentities = childIdentities,
            )
    }

private fun Map<String, GPURuntimeEffectUniformValue>.canonicalUniformString(): String =
    keys.sorted().joinToString(prefix = "{", postfix = "}") { name ->
        "${name.canonicalValue()}=${getValue(name).canonicalString()}"
    }

private fun Map<String, GPUMaterialDescriptor>.canonicalLegacyChildIdentityString(
    materialIdentity: (GPUMaterialDescriptor) -> String,
): String =
    keys.sorted().joinToString(prefix = "{", postfix = "}") { name ->
        "${name.canonicalValue()}=${materialIdentity(getValue(name))}"
    }

private fun GPUPreparedBlenderChildDescriptor.canonicalString(): String =
    when (this) {
        is GPUPreparedBlenderChildDescriptor.Mode -> "Mode(${mode.name})"
        is GPUPreparedBlenderChildDescriptor.Arithmetic ->
            "Arithmetic(${k1.canonicalValue()},${k2.canonicalValue()}," +
                "${k3.canonicalValue()},${k4.canonicalValue()})"
    }

private fun GPUPreparedMaterialUnsupportedEvidence.canonicalString(): String =
    when (this) {
        is GPUPreparedMaterialUnsupportedEvidence.RuntimeColorFilter ->
            "RuntimeColorFilter(" +
                "effectId=${effectId.canonicalValue()}, " +
                "uniforms=${uniforms.canonicalUniformString()}, " +
                "childIdentities=${childIdentities.canonicalStringMap()}" +
                ")"
    }

private fun Map<String, String>.canonicalStringMap(): String =
    keys.sorted().joinToString(prefix = "{", postfix = "}") { name ->
        "${name.canonicalValue()}=${getValue(name).canonicalValue()}"
    }

private fun GPURuntimeEffectUniformValue.canonicalString(): String =
    when (this) {
        is GPURuntimeEffectUniformValue.Float1 ->
            "Float1(${value.canonicalValue()})"
        is GPURuntimeEffectUniformValue.Float2 ->
            "Float2(${x.canonicalValue()},${y.canonicalValue()})"
        is GPURuntimeEffectUniformValue.Float3 ->
            "Float3(${x.canonicalValue()},${y.canonicalValue()},${z.canonicalValue()})"
        is GPURuntimeEffectUniformValue.Float4 ->
            "Float4(" +
                "${x.canonicalValue()},${y.canonicalValue()}," +
                "${z.canonicalValue()},${w.canonicalValue()}" +
                ")"
        is GPURuntimeEffectUniformValue.Int1 -> "Int1($value)"
        is GPURuntimeEffectUniformValue.Matrix3x3 ->
            "Matrix3x3(${values.canonicalFloatList()})"
        is GPURuntimeEffectUniformValue.Matrix4x4 ->
            "Matrix4x4(${values.canonicalFloatList()})"
    }

private fun GPUMaterialDescriptor.LinearGradient.startColorCanonicalValue(): String =
    "(${startR.canonicalValue()},${startG.canonicalValue()}," +
        "${startB.canonicalValue()},${startA.canonicalValue()})"

private fun GPUMaterialDescriptor.LinearGradient.endColorCanonicalValue(): String =
    "(${endR.canonicalValue()},${endG.canonicalValue()}," +
        "${endB.canonicalValue()},${endA.canonicalValue()})"

private fun GPUMaterialDescriptor.RadialGradient.startColorCanonicalValue(): String =
    "(${startR.canonicalValue()},${startG.canonicalValue()}," +
        "${startB.canonicalValue()},${startA.canonicalValue()})"

private fun GPUMaterialDescriptor.RadialGradient.endColorCanonicalValue(): String =
    "(${endR.canonicalValue()},${endG.canonicalValue()}," +
        "${endB.canonicalValue()},${endA.canonicalValue()})"

private fun GPUMaterialDescriptor.SweepGradient.startColorCanonicalValue(): String =
    "(${startR.canonicalValue()},${startG.canonicalValue()}," +
        "${startB.canonicalValue()},${startA.canonicalValue()})"

private fun GPUMaterialDescriptor.SweepGradient.endColorCanonicalValue(): String =
    "(${endR.canonicalValue()},${endG.canonicalValue()}," +
        "${endB.canonicalValue()},${endA.canonicalValue()})"

private fun GPUMaterialDescriptor.ConicalGradient.startColorCanonicalValue(): String =
    "(${startR.canonicalValue()},${startG.canonicalValue()}," +
        "${startB.canonicalValue()},${startA.canonicalValue()})"

private fun GPUMaterialDescriptor.ConicalGradient.endColorCanonicalValue(): String =
    "(${endR.canonicalValue()},${endG.canonicalValue()}," +
        "${endB.canonicalValue()},${endA.canonicalValue()})"

private fun Float.canonicalValue(): String =
    Integer.toUnsignedString(toRawBits(), 16)
        .padStart(8, '0')

private fun FloatArray?.canonicalValue(): String =
    this?.joinToString(prefix = "[", postfix = "]") { it.canonicalValue() }
        ?: "null"

private fun List<Float>.canonicalFloatList(): String =
    joinToString(prefix = "[", postfix = "]") { it.canonicalValue() }

private fun ByteArray.canonicalValue(): String =
    joinToString(prefix = "[", postfix = "]") { byte ->
        Integer.toHexString(byte.toInt() and 0xff).padStart(2, '0')
    }

private fun String.canonicalValue(): String =
    buildString(length * 4 + 16) {
        append("utf16:")
        append(this@canonicalValue.length)
        append(':')
        this@canonicalValue.forEach { character ->
            append(character.code.toString(16).padStart(4, '0'))
        }
    }

private fun String?.canonicalNullableValue(): String =
    this?.let { "value:${it.canonicalValue()}" } ?: "null"

private fun String.sha256Hex(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte ->
            Integer.toHexString(byte.toInt() and 0xff).padStart(2, '0')
        }

private val DEEP_COMPARE_EMPTY_BYTES = byteArrayOf()

/**
 * Typed runtime-effect uniform value captured before registered-program lookup.
 *
 * No source text, layout, module hash, or packed byte payload is accepted here;
 * those facts come only from the registered Kanvas runtime-effect authority.
 */
sealed interface GPURuntimeEffectUniformValue {
    data class Float1(val value: Float) : GPURuntimeEffectUniformValue
    data class Float2(val x: Float, val y: Float) : GPURuntimeEffectUniformValue
    data class Float3(val x: Float, val y: Float, val z: Float) : GPURuntimeEffectUniformValue
    data class Float4(
        val x: Float,
        val y: Float,
        val z: Float,
        val w: Float,
    ) : GPURuntimeEffectUniformValue
    data class Int1(val value: Int) : GPURuntimeEffectUniformValue

    class Matrix3x3(values: List<Float>) : GPURuntimeEffectUniformValue {
        val values: List<Float> = Collections.unmodifiableList(values.toList())

        init {
            require(this.values.size == 9) { "Matrix3x3 requires exactly 9 values" }
        }

        override fun equals(other: Any?): Boolean =
            other is Matrix3x3 && values == other.values

        override fun hashCode(): Int = values.hashCode()

        override fun toString(): String = "Matrix3x3(values=$values)"
    }

    class Matrix4x4(values: List<Float>) : GPURuntimeEffectUniformValue {
        val values: List<Float> = Collections.unmodifiableList(values.toList())

        init {
            require(this.values.size == 16) { "Matrix4x4 requires exactly 16 values" }
        }

        override fun equals(other: Any?): Boolean =
            other is Matrix4x4 && values == other.values

        override fun hashCode(): Int = values.hashCode()

        override fun toString(): String = "Matrix4x4(values=$values)"
    }
}

/** Captured ordering facts for normalized draw commands. */
data class GPUOrderingFacts(
    val paintOrder: Int,
    val dependsOnDestination: Boolean,
    val requiresBarrier: Boolean,
) {
    init {
        require(paintOrder >= 0) { "GPUOrderingFacts.paintOrder must be non-negative" }
    }
}

/** Compatibility alias for frame provenance owned by the foundation state package. */
typealias GPUFrameProvenance = org.graphiks.kanvas.gpu.renderer.state.GPUFrameProvenance

/** Closed source identity used by route admission without parsing diagnostic strings. */
enum class GPUCommandSourceKind {
    Generic,
    PublicFillRect,
    AnalyticStrokeRectBand,
    AnalyticStrokeRectTranslatedBand,
    AnalyticStrokeRectTranslatedThreeStopBand,
    AnalyticStrokeRectUniformScaleBand,
    AnalyticStrokeRectUniformScaleThreeStopBand,
    AnalyticStrokeRectUniformScaleSweepTwoStopBand,
    AnalyticStrokeRectUniformScaleSweepThreeStopBand,
    AnalyticStrokeRectUniformScaleRadialTwoStopBand,
    AnalyticStrokeRectUniformScaleRadialThreeStopBand,
}

/** Source adapter information used by diagnostics and dumps. */
data class GPUCommandSource(
    val adapter: String,
    val operation: String,
    val frameProvenance: GPUFrameProvenance = GPUFrameProvenance.None,
    val kind: GPUCommandSourceKind = GPUCommandSourceKind.Generic,
) {
    init {
        require(adapter.isNotBlank()) { "GPUCommandSource.adapter must not be blank" }
        require(operation.isNotBlank()) { "GPUCommandSource.operation must not be blank" }
    }
}

/** Builds Kanvas-owned first-route FillRect commands from already-normalized facts. */
object GPUFillRectCommandBuilder {
    /**
     * Builds an immutable FillRect command from facts already captured by the caller.
     *
     * Ownership stays with the command package: this builder records geometry,
     * transform, clip, layer, material, blend, ordering, and source provenance
     * without lowering materials, allocating resources, or choosing a backend.
     * Defaults are deliberately narrow: root layer, source-over blend, identity
     * transform, and a wide-open clip derived from the rectangle bounds. Invalid
     * or unsupported facts are preserved so analysis can return stable terminal
     * diagnostics instead of hiding failures during command construction.
     */
    fun build(
        commandId: GPUDrawCommandID,
        rect: GPURect,
        target: GPUTargetFacts,
        material: GPUMaterialDescriptor,
        transform: GPUTransformFacts = GPUTransformFacts.identity(),
        clip: GPUClipFacts? = null,
        layer: GPULayerFacts? = null,
        blend: GPUBlendFacts = GPUBlendFacts.srcOver(),
        paintOrder: Int = 0,
        source: GPUCommandSource = GPUCommandSource(adapter = "gpu-renderer", operation = "fillRect"),
        stroke: Boolean = false,
    ): NormalizedDrawCommand.FillRect {
        val bounds = rect.toBounds()
        val resolvedClip = clip ?: GPUClipFacts.wideOpen(bounds = bounds)
        return NormalizedDrawCommand.FillRect(
            commandId = commandId,
            rect = rect,
            transform = transform,
            clip = resolvedClip,
            layer = layer ?: GPULayerFacts.root(target = target),
            material = material,
            blend = blend,
            bounds = bounds,
            ordering = GPUOrderingFacts(
                paintOrder = paintOrder,
                dependsOnDestination = false,
                requiresBarrier = false,
            ),
            source = source,
            stroke = stroke,
        )
    }
}

/** Builds Kanvas-owned first-expansion FillRRect commands from already-normalized facts. */
object GPUFillRRectCommandBuilder {
    /**
     * Builds an immutable FillRRect command from facts already captured by the caller.
     *
     * This mirrors [GPUFillRectCommandBuilder] while keeping rrect radii as
     * command-owned geometry facts. Radius normalization and route acceptance
     * remain analysis responsibilities so unsupported rounded rectangles can
     * refuse with stable diagnostics instead of being approximated.
     */
    fun build(
        commandId: GPUDrawCommandID,
        rrect: GPURRect,
        target: GPUTargetFacts,
        material: GPUMaterialDescriptor,
        transform: GPUTransformFacts = GPUTransformFacts.identity(),
        clip: GPUClipFacts? = null,
        layer: GPULayerFacts? = null,
        blend: GPUBlendFacts = GPUBlendFacts.srcOver(),
        paintOrder: Int = 0,
        source: GPUCommandSource = GPUCommandSource(adapter = "gpu-renderer", operation = "fillRRect"),
        stroke: Boolean = false,
    ): NormalizedDrawCommand.FillRRect {
        val bounds = rrect.rect.toBounds()
        val resolvedClip = clip ?: GPUClipFacts.wideOpen(bounds = bounds)
        return NormalizedDrawCommand.FillRRect(
            commandId = commandId,
            rrect = rrect,
            transform = transform,
            clip = resolvedClip,
            layer = layer ?: GPULayerFacts.root(target = target),
            material = material,
            blend = blend,
            bounds = bounds,
            ordering = GPUOrderingFacts(
                paintOrder = paintOrder,
                dependsOnDestination = false,
                requiresBarrier = false,
            ),
            source = source,
            stroke = stroke,
        )
    }
}

/** Builds Kanvas-owned first-expansion LinearGradient rect commands from already-normalized facts. */
object GPULinearGradientCommandBuilder {
    /**
     * Builds an immutable FillRect command with a linear gradient material descriptor.
     *
     * The builder reuses the FillRect command family with a
     * [GPUMaterialDescriptor.LinearGradient] material so the planner can accept
     * gradient rects without a new command family. Gradient-specific validation
     * (non-degenerate endpoints, finite colors, valid tile mode) is deferred to
     * analysis-time refusal checks.
     */
    fun build(
        commandId: GPUDrawCommandID,
        rect: GPURect,
        target: GPUTargetFacts,
        material: GPUMaterialDescriptor.LinearGradient,
        transform: GPUTransformFacts = GPUTransformFacts.identity(),
        clip: GPUClipFacts? = null,
        layer: GPULayerFacts? = null,
        blend: GPUBlendFacts = GPUBlendFacts.srcOver(),
        paintOrder: Int = 0,
        source: GPUCommandSource = GPUCommandSource(adapter = "gpu-renderer", operation = "linearGradientRect"),
    ): NormalizedDrawCommand.FillRect {
        val bounds = rect.toBounds()
        val resolvedClip = clip ?: GPUClipFacts.wideOpen(bounds = bounds)
        return NormalizedDrawCommand.FillRect(
            commandId = commandId,
            rect = rect,
            transform = transform,
            clip = resolvedClip,
            layer = layer ?: GPULayerFacts.root(target = target),
            material = material,
            blend = blend,
            bounds = bounds,
            ordering = GPUOrderingFacts(
                paintOrder = paintOrder,
                dependsOnDestination = false,
                requiresBarrier = false,
            ),
            source = source,
        )
    }
}

/** Builds Kanvas-owned M15 path-fill commands from tessellated vertex buffers. */
object GPUFillPathCommandBuilder {
    /** Builds a FillPath normalized command with tessellation facts bound to the target and material. */
    fun build(
        commandId: GPUDrawCommandID,
        pathKey: String,
        pathDescriptor: GPUPathFacts,
        tessellatedVertices: List<Float>,
        contourStarts: List<Int>,
        edgeCount: Int,
        target: GPUTargetFacts,
        material: GPUMaterialDescriptor,
        transform: GPUTransformFacts = GPUTransformFacts.identity(),
        clip: GPUClipFacts? = null,
        layer: GPULayerFacts? = null,
        blend: GPUBlendFacts = GPUBlendFacts.srcOver(),
        paintOrder: Int = 0,
        source: GPUCommandSource = GPUCommandSource(adapter = "gpu-renderer", operation = "fillPath.shadow"),
        stroke: Boolean = false,
        strokeWidth: Float = 1f,
        dashIntervals: FloatArray? = null,
        dashPhase: Float = 0f,
        strokeCap: String = "butt",
        strokeJoin: String = "miter",
        antiAlias: Boolean = true,
        maskFilter: NormalizedMaskFilter? = null,
    ): NormalizedDrawCommand.FillPath {
        val vertexCount = tessellatedVertices.size / 2
        val minBounds = if (vertexCount > 0) {
            var minX = Float.POSITIVE_INFINITY; var minY = Float.POSITIVE_INFINITY
            var maxX = Float.NEGATIVE_INFINITY; var maxY = Float.NEGATIVE_INFINITY
            var i = 0
            while (i < tessellatedVertices.size) {
                val x = tessellatedVertices[i]; val y = tessellatedVertices[i + 1]
                minX = minOf(minX, x); minY = minOf(minY, y)
                maxX = maxOf(maxX, x); maxY = maxOf(maxY, y)
                i += 2
            }
            GPUBounds(minX, minY, maxX, maxY)
        } else {
            GPUBounds(0f, 0f, 0f, 0f)
        }
        val resolvedClip = clip ?: GPUClipFacts.wideOpen(bounds = minBounds)
        return NormalizedDrawCommand.FillPath(
            commandId = commandId,
            pathKey = pathKey,
            pathDescriptor = pathDescriptor,
            tessellatedVertices = tessellatedVertices,
            contourStarts = contourStarts,
            totalVertexCount = vertexCount,
            edgeCount = edgeCount,
            transform = transform,
            clip = resolvedClip,
            layer = layer ?: GPULayerFacts.root(target = target),
            material = material,
            blend = blend,
            bounds = minBounds,
            ordering = GPUOrderingFacts(
                paintOrder = paintOrder,
                dependsOnDestination = false,
                requiresBarrier = false,
            ),
            source = source,
            stroke = stroke,
            strokeWidth = strokeWidth,
            dashIntervals = dashIntervals,
            dashPhase = dashPhase,
            strokeCap = strokeCap,
            strokeJoin = strokeJoin,
            antiAlias = antiAlias,
            maskFilter = maskFilter,
        )
    }
}

/** Builds Kanvas-owned first-expansion DrawLayer commands from already-normalized facts. */
object GPUDrawLayerCommandBuilder {
    /**
     * Builds an immutable DrawLayer command from facts already captured by the caller.
     *
     * Ownership stays with the command package: this builder records save/restore state,
     * layer bounds, and standard command facts without lowering materials, allocating
     * offscreen targets, or choosing a backend. Defaults are deliberately narrow: root
     * layer facts, source-over blend, identity transform, and a wide-open clip derived
     * from the provided bounds.
     */
    fun build(
        commandId: GPUDrawCommandID,
        scopeId: String,
        target: GPUTargetFacts,
        bounds: GPUBounds,
        childCommandIds: List<String> = emptyList(),
        parentScopeId: String? = null,
        initWithPrevious: Boolean = false,
        backdropRequired: Boolean = false,
        sourceFilterCount: Int = 0,
        restoreBlendMode: String = "srcOver",
        cpuFallbackRequested: Boolean = false,
        preserveLCDText: Boolean = false,
        f16Requested: Boolean = false,
        requiresFilter: Boolean = sourceFilterCount > 0,
        requiresDestinationRead: Boolean = false,
        transform: GPUTransformFacts = GPUTransformFacts.identity(),
        clip: GPUClipFacts? = null,
        layer: GPULayerFacts? = null,
        blend: GPUBlendFacts = GPUBlendFacts.srcOver(),
        paintOrder: Int = 0,
        source: GPUCommandSource = GPUCommandSource(adapter = "gpu-renderer", operation = "drawLayer"),
        stroke: Boolean = false,
    ): NormalizedDrawCommand.DrawLayer {
        val resolvedClip = clip ?: GPUClipFacts.wideOpen(bounds = bounds)
        val resolvedLayer = layer ?: GPULayerFacts(
            target = target,
            scopeKind = GPULayerScopeKind.SaveLayer,
            requiresFilter = requiresFilter,
            requiresDestinationRead = requiresDestinationRead,
        )
        return NormalizedDrawCommand.DrawLayer(
            commandId = commandId,
            scopeId = scopeId,
            parentScopeId = parentScopeId,
            childCommandIds = childCommandIds,
            initWithPrevious = initWithPrevious,
            backdropRequired = backdropRequired,
            sourceFilterCount = sourceFilterCount,
            restoreBlendMode = restoreBlendMode,
            cpuFallbackRequested = cpuFallbackRequested,
            preserveLCDText = preserveLCDText,
            f16Requested = f16Requested,
            transform = transform,
            clip = resolvedClip,
            layer = resolvedLayer,
            material = GPUMaterialDescriptor.SolidColor(r = 1f, g = 1f, b = 1f, a = 1f),
            blend = blend,
            bounds = bounds,
            ordering = GPUOrderingFacts(
                paintOrder = paintOrder,
                dependsOnDestination = false,
                requiresBarrier = false,
            ),
            source = source,
            stroke = stroke,
        )
    }
}

/** Builds Kanvas-owned first-expansion DrawImageRect commands from already-normalized facts. */
object GPUDrawImageRectCommandBuilder {
    /**
     * Builds an immutable DrawImageRect command from facts already captured by the caller.
     *
     * Ownership stays with the command package: this builder records source/destination
     * rectangles, sampling parameters, decoded pixel facts, and standard command facts
     * without lowering materials, allocating resources, or choosing a backend.
     */
    fun build(
        commandId: GPUDrawCommandID,
        imageSourceId: String,
        src: GPURect,
        dst: GPURect,
        target: GPUTargetFacts,
        material: GPUMaterialDescriptor,
        samplingTileModeX: String = "clamp",
        samplingTileModeY: String = "clamp",
        samplingFilterMode: String = "linear",
        samplingMipmapMode: String = "none",
        samplingAnisotropy: Int = 1,
        pixelsWidth: Int = 0,
        pixelsHeight: Int = 0,
        pixelsFormat: String = "RGBA8Unorm",
        pixelsRowBytes: Long = 0,
        pixelsAlphaType: String = "Premul",
        pixelsColorProfileLabel: String = "srgb",
        pixelsOrientationState: String = "Applied",
        pixelsGeneration: Long = 0,
        pixelsContentHash: String = "",
        pixelsProvenance: String = "",
        transform: GPUTransformFacts = GPUTransformFacts.identity(),
        clip: GPUClipFacts? = null,
        layer: GPULayerFacts? = null,
        blend: GPUBlendFacts = GPUBlendFacts.srcOver(),
        paintOrder: Int = 0,
        source: GPUCommandSource = GPUCommandSource(adapter = "gpu-renderer", operation = "drawImageRect"),
        stroke: Boolean = false,
    ): NormalizedDrawCommand.DrawImageRect {
        val bounds = dst.toBounds()
        val resolvedClip = clip ?: GPUClipFacts.wideOpen(bounds = bounds)
        return NormalizedDrawCommand.DrawImageRect(
            commandId = commandId,
            imageSourceId = imageSourceId,
            src = src,
            dst = dst,
            samplingTileModeX = samplingTileModeX,
            samplingTileModeY = samplingTileModeY,
            samplingFilterMode = samplingFilterMode,
            samplingMipmapMode = samplingMipmapMode,
            samplingAnisotropy = samplingAnisotropy,
            pixelsWidth = pixelsWidth,
            pixelsHeight = pixelsHeight,
            pixelsFormat = pixelsFormat,
            pixelsRowBytes = pixelsRowBytes,
            pixelsAlphaType = pixelsAlphaType,
            pixelsColorProfileLabel = pixelsColorProfileLabel,
            pixelsOrientationState = pixelsOrientationState,
            pixelsGeneration = pixelsGeneration,
            pixelsContentHash = pixelsContentHash,
            pixelsProvenance = pixelsProvenance,
            transform = transform,
            clip = resolvedClip,
            layer = layer ?: GPULayerFacts.root(target = target),
            material = material,
            blend = blend,
            bounds = bounds,
            ordering = GPUOrderingFacts(
                paintOrder = paintOrder,
                dependsOnDestination = false,
                requiresBarrier = false,
            ),
            source = source,
            stroke = stroke,
        )
    }
}

/** Builds Kanvas-owned first-expansion ApplyFilter commands from already-normalized facts. */
object GPUApplyFilterCommandBuilder {
    /**
     * Builds an immutable ApplyFilter command from facts already captured by the caller.
     *
     * Ownership stays with the command package: this builder records filter graph,
     * source, bounds, crop, and sampling plans plus standard command facts without
     * lowering materials, allocating resources, or choosing a backend.
     */
    fun build(
        commandId: GPUDrawCommandID,
        filterGraph: GPUFilterGraphDescriptor,
        filterSource: GPUFilterSourcePlan,
        filterBounds: GPUSimpleFilterBounds,
        target: GPUTargetFacts,
        material: GPUMaterialDescriptor,
        filterCrop: GPUFilterCropPlan = GPUFilterCropPlan(
            cropLabel = filterBounds.outputBoundsLabel,
            tilePolicy = org.graphiks.kanvas.gpu.renderer.filters.GPUFilterTilePlan(
                tileModeX = "decal",
                tileModeY = "decal",
                decalOutsideCrop = true,
            ),
        ),
        filterSampling: GPUFilterSamplingPlan = GPUFilterSamplingPlan(
            filterMode = "nearest",
            mipmapMode = "none",
            coordinateSpaceLabel = "layer",
        ),
        transform: GPUTransformFacts = GPUTransformFacts.identity(),
        clip: GPUClipFacts? = null,
        layer: GPULayerFacts? = null,
        blend: GPUBlendFacts = GPUBlendFacts.srcOver(),
        paintOrder: Int = 0,
        source: GPUCommandSource = GPUCommandSource(adapter = "gpu-renderer", operation = "applyFilter"),
    ): NormalizedDrawCommand.ApplyFilter {
        val commandBounds = GPUBounds(
            left = 0f,
            top = 0f,
            right = filterBounds.width.toFloat(),
            bottom = filterBounds.height.toFloat(),
        )
        val resolvedClip = clip ?: GPUClipFacts.wideOpen(bounds = commandBounds)
        return NormalizedDrawCommand.ApplyFilter(
            commandId = commandId,
            filterGraph = filterGraph,
            filterSource = filterSource,
            filterBounds = filterBounds,
            filterCrop = filterCrop,
            filterSampling = filterSampling,
            transform = transform,
            clip = resolvedClip,
            layer = layer ?: GPULayerFacts.root(target = target),
            material = material,
            blend = blend,
            bounds = commandBounds,
            ordering = GPUOrderingFacts(
                paintOrder = paintOrder,
                dependsOnDestination = false,
                requiresBarrier = false,
            ),
            source = source,
        )
    }
}

/** High-level draw command after legacy state has been captured and normalized. */
sealed interface NormalizedDrawCommand {
    /** Recording-local command identifier. */
    val commandId: GPUDrawCommandID
    /** Coarse draw command family. */
    val drawKind: GPUDrawKind
    /** Captured transform facts. */
    val transform: GPUTransformFacts
    /** Captured clip facts. */
    val clip: GPUClipFacts
    /** Captured layer facts. */
    val layer: GPULayerFacts
    /**
     * Captured legacy material descriptor.
     *
     * Prepared text carries its already-compiled immutable program instead, so
     * this value is null only for that explicit command variant.
     */
    val material: GPUMaterialDescriptor?
    /** Captured blend facts. */
    val blend: GPUBlendFacts
    /** Conservative command bounds. */
    val bounds: GPUBounds
    /** Captured ordering facts. */
    val ordering: GPUOrderingFacts
    /** Source adapter provenance. */
    val source: GPUCommandSource

    /** Stable diagnostic label for route and analysis reports. */
    val diagnosticName: String
        get() = "${source.adapter}:${source.operation}#${commandId.value}"

    /** First-slice filled rectangle command with captured state. */
    data class FillRect(
        override val commandId: GPUDrawCommandID,
        val rect: GPURect,
        override val transform: GPUTransformFacts,
        override val clip: GPUClipFacts,
        override val layer: GPULayerFacts,
        override val material: GPUMaterialDescriptor,
        override val blend: GPUBlendFacts = GPUBlendFacts.srcOver(),
        override val bounds: GPUBounds,
        override val ordering: GPUOrderingFacts,
        override val source: GPUCommandSource,
        /**
         * `true` when the originating paint requested a stroke (or
         * stroke-and-fill) style. Stroke draws are refused with
         * `unsupported_stroke` instead of being silently filled. Defaults to
         * `false` so all existing fill callers keep fill behavior.
         */
        val stroke: Boolean = false,
        val antiAlias: Boolean = true,
        /** Mask filter descriptor for post-processing the fill output. Null when no mask filter is active. */
        val maskFilter: NormalizedMaskFilter? = null,
    ) : NormalizedDrawCommand {
        override val drawKind: GPUDrawKind = GPUDrawKind.FillRect
    }

    /** First-expansion filled rounded rectangle command with captured state. */
    data class FillRRect(
        override val commandId: GPUDrawCommandID,
        val rrect: GPURRect,
        override val transform: GPUTransformFacts,
        override val clip: GPUClipFacts,
        override val layer: GPULayerFacts,
        override val material: GPUMaterialDescriptor,
        override val blend: GPUBlendFacts = GPUBlendFacts.srcOver(),
        override val bounds: GPUBounds,
        override val ordering: GPUOrderingFacts,
        override val source: GPUCommandSource,
        /** See [FillRect.stroke]. Stroke rrect draws refuse instead of filling. */
        val stroke: Boolean = false,
        val antiAlias: Boolean = true,
        /** Mask filter descriptor for post-processing the fill output. Null when no mask filter is active. */
        val maskFilter: NormalizedMaskFilter? = null,
    ) : NormalizedDrawCommand {
        override val drawKind: GPUDrawKind = GPUDrawKind.FillRRect
    }

    /** Bounded analytic double-rounded-rectangle command retained before any path lowering. */
    data class FillDRRect(
        override val commandId: GPUDrawCommandID,
        val outer: GPURRect,
        val inner: GPURRect,
        override val transform: GPUTransformFacts,
        override val clip: GPUClipFacts,
        override val layer: GPULayerFacts,
        override val material: GPUMaterialDescriptor,
        override val blend: GPUBlendFacts = GPUBlendFacts.srcOver(),
        override val bounds: GPUBounds,
        override val ordering: GPUOrderingFacts,
        override val source: GPUCommandSource,
        val stroke: Boolean = false,
        val antiAlias: Boolean = true,
        val maskFilter: NormalizedMaskFilter? = null,
    ) : NormalizedDrawCommand {
        override val drawKind: GPUDrawKind = GPUDrawKind.FillDRRect
    }

    /** M15 path-fill command with tessellated vertex buffers from the shadow adapter. */
    data class FillPath(
        override val commandId: GPUDrawCommandID,
        val pathKey: String,
        val pathDescriptor: GPUPathFacts,
        val tessellatedVertices: List<Float>,
        val contourStarts: List<Int>,
        val totalVertexCount: Int,
        val edgeCount: Int,
        override val transform: GPUTransformFacts,
        override val clip: GPUClipFacts,
        override val layer: GPULayerFacts,
        override val material: GPUMaterialDescriptor,
        override val blend: GPUBlendFacts = GPUBlendFacts.srcOver(),
        override val bounds: GPUBounds,
        override val ordering: GPUOrderingFacts,
        override val source: GPUCommandSource,
        /** See [FillRect.stroke]. Stroke path draws refuse instead of filling. */
        val stroke: Boolean = false,
        /** Stroke width used when [stroke] is true. Default 1f. */
        val strokeWidth: Float = 1f,
        /** Dash intervals for path effects. Null means no dashing. */
        val dashIntervals: FloatArray? = null,
        /** Dash phase offset. */
        val dashPhase: Float = 0f,
        /** Original non-dash path effect retained so bounded stroke lowering can refuse it. */
        val pathEffectKind: String? = null,
        /** Stroke cap style: "butt", "round", "square". */
        val strokeCap: String = "butt",
        /** Stroke join style: "miter", "round", "bevel". */
        val strokeJoin: String = "miter",
        /** Source miter limit retained until canonical stroke lowering consumes it. */
        val strokeMiterLimit: Float = 4f,
        val antiAlias: Boolean = true,
        /** Mask filter descriptor for post-processing the fill output. Null when no mask filter is active. */
        val maskFilter: NormalizedMaskFilter? = null,
    ) : NormalizedDrawCommand {
        override val drawKind: GPUDrawKind = GPUDrawKind.FillPath
    }

    /**
     * Text run command with only dumpable text-stack artifact references.
     *
     * Legacy callers retain exactly one descriptor. Prepared callers retain
     * exactly one already-compiled, handle-free material program and never
     * reconstruct the descriptor or its sampled pixels during recording.
     */
    data class DrawTextRun(
        override val commandId: GPUDrawCommandID,
        val textLayoutResultId: String?,
        val glyphRunId: String?,
        val glyphRunDescriptorRefs: List<String>,
        val glyphRunDescriptor: GlyphRunDescriptor? = null,
        val colorGlyphPlans: List<GPUColorGlyphLayerPlan> = emptyList(),
        val artifactRefs: List<GPUTextArtifactRef>,
        val artifactKeyHashes: List<String>,
        val atlasGenerations: List<GPUTextArtifactGeneration>,
        val uploadDependencyFacts: List<String>,
        val routeDiagnostics: List<GPUTextDiagnostic>,
        override val transform: GPUTransformFacts,
        override val clip: GPUClipFacts,
        override val layer: GPULayerFacts,
        override val material: GPUMaterialDescriptor? = null,
        val preparedMaterial: GPUPreparedMaterialProgram? = null,
        override val blend: GPUBlendFacts = GPUBlendFacts.srcOver(),
        /** Exact prepared blend authority when this command came from a prepared text sub-run. */
        val preparedBlendPlan: GPUBlendPlan? = null,
        override val bounds: GPUBounds,
        override val ordering: GPUOrderingFacts,
        override val source: GPUCommandSource,
    ) : NormalizedDrawCommand {
        init {
            require((material == null) != (preparedMaterial == null)) {
                "DrawTextRun requires exactly one legacy descriptor or prepared material program"
            }
        }

        override val drawKind: GPUDrawKind = GPUDrawKind.DrawTextRun
    }

    /**
     * Save-layer command with offscreen target scope isolation and composite facts
     * captured before analysis and layer planning.
     *
     * The command holds save/restore state facts as primitive fields so it avoids
     * importing layer-specific types into the commands package. Layer-type
     * contracts (isolated target, composite plan, destination read) remain owned
     * by the layers package and are reconstituted during analysis-time route planning.
     */
    data class DrawLayer(
        override val commandId: GPUDrawCommandID,
        val scopeId: String,
        val parentScopeId: String?,
        val childCommandIds: List<String>,
        val initWithPrevious: Boolean,
        val backdropRequired: Boolean,
        val sourceFilterCount: Int,
        val restoreBlendMode: String,
        val cpuFallbackRequested: Boolean,
        val preserveLCDText: Boolean,
        val f16Requested: Boolean,
        override val transform: GPUTransformFacts,
        override val clip: GPUClipFacts,
        override val layer: GPULayerFacts,
        override val material: GPUMaterialDescriptor,
        override val blend: GPUBlendFacts = GPUBlendFacts.srcOver(),
        override val bounds: GPUBounds,
        override val ordering: GPUOrderingFacts,
        override val source: GPUCommandSource,
        val stroke: Boolean = false,
    ) : NormalizedDrawCommand {
        override val drawKind: GPUDrawKind = GPUDrawKind.DrawLayer
    }

    /**
     * Image draw command with decoded pixel facts captured before analysis
     * and upload planning.
     *
     * The command holds source/destination rectangles and sampled decoded-pixel
     * facts as primitive fields so it avoids importing image-specific types
     * into the commands package. Image-type contracts (decoded pixels
     * descriptor, sampling plan) remain owned by the images package and are
     * reconstituted during analysis-time route planning.
     */
    data class DrawImageRect(
        override val commandId: GPUDrawCommandID,
        val imageSourceId: String,
        val src: GPURect,
        val dst: GPURect,
        val imageFilterPlan: GPUImageFilterPlan = GPUImageFilterPlan.None,
        val samplingTileModeX: String = "clamp",
        val samplingTileModeY: String = "clamp",
        val samplingFilterMode: String = "linear",
        val samplingMipmapMode: String = "none",
        val samplingAnisotropy: Int = 1,
        val pixelsWidth: Int = 0,
        val pixelsHeight: Int = 0,
        val pixelsFormat: String = "RGBA8Unorm",
        val pixelsRowBytes: Long = 0,
        val pixelsAlphaType: String = "Premul",
        val pixelsColorProfileLabel: String = "srgb",
        val pixelsOrientationState: String = "Applied",
        val pixelsGeneration: Long = 0,
        val pixelsContentHash: String = "",
        val pixelsProvenance: String = "",
        override val transform: GPUTransformFacts,
        override val clip: GPUClipFacts,
        override val layer: GPULayerFacts,
        override val material: GPUMaterialDescriptor,
        override val blend: GPUBlendFacts = GPUBlendFacts.srcOver(),
        override val bounds: GPUBounds,
        override val ordering: GPUOrderingFacts,
        override val source: GPUCommandSource,
        /** See [FillRect.stroke]. Stroke image draws refuse instead of drawing. */
        val stroke: Boolean = false,
    ) : NormalizedDrawCommand {
        override val drawKind: GPUDrawKind = GPUDrawKind.DrawImageRect
    }

    /**
     * Handle-free normalized command for one already-lowered DrawVertices or DrawMesh operation.
     * Upload offsets, cache facts, native resources, and shader assembly are deliberately absent.
     */
    class DrawPreparedVertices(
        override val commandId: GPUDrawCommandID,
        val artifactKey: String,
        val topologyIdentity: String,
        val layoutIdentity: String,
        val materialIdentity: String,
        transformBytes: List<Int>,
        override val transform: GPUTransformFacts,
        override val clip: GPUClipFacts,
        override val layer: GPULayerFacts,
        override val blend: GPUBlendFacts,
        val preparedBlendPlan: GPUBlendPlan,
        override val bounds: GPUBounds,
        override val ordering: GPUOrderingFacts,
        override val source: GPUCommandSource,
        val clipIdentity: String,
        val clipCoverageIdentity: String,
        val primitiveColorPresent: Boolean,
        val primitiveBlendIdentity: String?,
        val capabilitySnapshotHash: String,
        val drawProvenance: String,
    ) : NormalizedDrawCommand {
        val transformBytes: List<Int> = Collections.unmodifiableList(ArrayList(transformBytes))

        init {
            require(artifactKey.isNotBlank() && topologyIdentity.isNotBlank() &&
                layoutIdentity.isNotBlank() && materialIdentity.isNotBlank())
            require(this.transformBytes.size == 9)
        }
        override val drawKind: GPUDrawKind = GPUDrawKind.DrawPreparedVertices
        override val material: GPUMaterialDescriptor? = null
    }

    /**
     * Filter command with a bounded single-node filter DAG to be executed as
     * a GPU-native render node.
     *
     * The command owns the filter graph, source, bounds, crop, and sampling
     * plans needed by the simple filter render-node planner. The analysis
     * planner validates visibility (node kind, DAG size, material), bounds,
     * and capabilities before converting into a native or prepared filter
     * route with a render-step pass.
     */
    data class ApplyFilter(
        override val commandId: GPUDrawCommandID,
        val filterGraph: GPUFilterGraphDescriptor,
        val filterSource: GPUFilterSourcePlan,
        val filterBounds: GPUSimpleFilterBounds,
        val filterCrop: GPUFilterCropPlan,
        val filterSampling: GPUFilterSamplingPlan,
        override val transform: GPUTransformFacts,
        override val clip: GPUClipFacts,
        override val layer: GPULayerFacts,
        override val material: GPUMaterialDescriptor,
        override val blend: GPUBlendFacts = GPUBlendFacts.srcOver(),
        override val bounds: GPUBounds,
        override val ordering: GPUOrderingFacts,
        override val source: GPUCommandSource,
    ) : NormalizedDrawCommand {
        override val drawKind: GPUDrawKind = GPUDrawKind.ApplyFilter
    }
}

/** Converts rectangle geometry to conservative command bounds. */
private fun GPURect.toBounds(): GPUBounds =
    GPUBounds(left = left, top = top, right = right, bottom = bottom)
