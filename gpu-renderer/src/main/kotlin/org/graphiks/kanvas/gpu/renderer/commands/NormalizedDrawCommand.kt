package org.graphiks.kanvas.gpu.renderer.commands

import java.util.Collections
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
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceAlphaClassification

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
    /** Filled path command family with tessellated vertex buffers. */
    FillPath,
    /** Text run command family with prepared text stack artifacts. */
    DrawTextRun,
    /** Image draw command family with decoded pixel upload. */
    DrawImageRect,
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
    COLOR_FILTER_GRAPH_CYCLE(
        "unsupported.material.mapping.color_filter_graph_cycle",
        "Prepared mapping refuses a cyclic color-filter graph",
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

    /** Linear gradient descriptor with start/end colors and tile mode for the first GPU expansion slice. */
    data class LinearGradient(
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
        val tileMode: String = "clamp",
        val allStopPositions: FloatArray? = null,
        val allStopColors: FloatArray? = null,
        val snippetSourceHash: String? = null,
        val fragmentEntryPoint: String? = null,
    ) : GPUMaterialDescriptor {
        override val kind: GPUMaterialKind = GPUMaterialKind.LinearGradient
    }

    /** Radial gradient descriptor with center, radius, and tile mode for M14. */
    data class RadialGradient(
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
        val tileMode: String = "clamp",
        val allStopPositions: FloatArray? = null,
        val allStopColors: FloatArray? = null,
        val snippetSourceHash: String? = null,
        val fragmentEntryPoint: String? = null,
    ) : GPUMaterialDescriptor {
        override val kind: GPUMaterialKind = GPUMaterialKind.RadialGradient
    }

    /** Sweep gradient descriptor with center, start/end angles, and tile mode for M14. */
    data class SweepGradient(
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
        val tileMode: String = "clamp",
        val allStopPositions: FloatArray? = null,
        val allStopColors: FloatArray? = null,
        val snippetSourceHash: String? = null,
        val fragmentEntryPoint: String? = null,
    ) : GPUMaterialDescriptor {
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

    /** Placeholder image-draw descriptor — deferred; dispatch refuses via non-SolidColor material. */
    data class ImageDraw(
        val imageSourceId: String = "",
        val imageWidth: Int = 0,
        val imageHeight: Int = 0,
        val rgbaPixels: ByteArray = byteArrayOf(),
        val samplingFilterMode: String = "nearest",
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
    class RuntimeEffect(
        val effectId: String = "",
        val descriptorVersion: Int = 1,
        uniforms: Map<String, GPURuntimeEffectUniformValue> = emptyMap(),
        children: Map<String, GPUMaterialDescriptor> = emptyMap(),
    ) : GPUMaterialDescriptor {
        private val uniformSnapshot =
            LinkedHashMap(uniforms)
        private val childSnapshot =
            LinkedHashMap(
                children.mapValues { (_, child) -> child.deepSnapshot() },
            )

        val uniforms: Map<String, GPURuntimeEffectUniformValue>
            get() = Collections.unmodifiableMap(LinkedHashMap(uniformSnapshot))

        val children: Map<String, GPUMaterialDescriptor>
            get() = immutableChildSnapshot()

        override val kind: GPUMaterialKind = GPUMaterialKind.RuntimeEffect

        fun copy(
            effectId: String = this.effectId,
            descriptorVersion: Int = this.descriptorVersion,
            uniforms: Map<String, GPURuntimeEffectUniformValue> = this.uniforms,
            children: Map<String, GPUMaterialDescriptor> = this.children,
        ): RuntimeEffect =
            RuntimeEffect(effectId, descriptorVersion, uniforms, children)

        operator fun component1(): String = effectId
        operator fun component2(): Int = descriptorVersion
        operator fun component3(): Map<String, GPURuntimeEffectUniformValue> = uniforms
        operator fun component4(): Map<String, GPUMaterialDescriptor> = children

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is RuntimeEffect) return false
            if (
                effectId != other.effectId ||
                descriptorVersion != other.descriptorVersion ||
                uniformSnapshot != other.uniformSnapshot ||
                childSnapshot.keys != other.childSnapshot.keys
            ) {
                return false
            }
            return childSnapshot.all { (name, child) ->
                child.deepEquals(other.childSnapshot.getValue(name))
            }
        }

        override fun hashCode(): Int {
            var result = effectId.hashCode()
            result = 31 * result + descriptorVersion
            result = 31 * result + uniformSnapshot.hashCode()
            result = 31 * result + childSnapshot.entries.sumOf { (name, child) ->
                name.hashCode() xor child.deepHashCode()
            }
            return result
        }

        override fun toString(): String =
            "RuntimeEffect(" +
                "effectId=$effectId, " +
                "descriptorVersion=$descriptorVersion, " +
                "uniforms=${uniformSnapshot.deterministicUniformString()}, " +
                "children=${childSnapshot.deterministicChildString()}" +
                ")"

        private fun immutableChildSnapshot(): Map<String, GPUMaterialDescriptor> =
            Collections.unmodifiableMap(
                LinkedHashMap(
                    childSnapshot.mapValues { (_, child) -> child.deepSnapshot() },
                ),
            )
    }

    /** Blend shader descriptor combining two child shaders via a blend mode. */
    data class BlendShader(
        val mode: String,
        val dst: GPUMaterialDescriptor,
        val src: GPUMaterialDescriptor,
        val wgslCombined: String = "",
        val uniformBytes: ByteArray = byteArrayOf(),
    ) : GPUMaterialDescriptor {
        override val kind: GPUMaterialKind = GPUMaterialKind.ShaderBlend
    }

    /**
     * Typed fail-closed marker emitted only by the prepared mapper.
     *
     * [source] retains an exactly mapped base when one exists; it is evidence
     * only and is never compiled as a substitute for the refused operation.
     */
    data class Unsupported(
        val reason: GPUPreparedMaterialUnsupportedReason,
        val originalKind: GPUMaterialKind,
        val source: GPUMaterialDescriptor? = null,
        val evidence: GPUPreparedMaterialUnsupportedEvidence? = null,
    ) : GPUMaterialDescriptor {
        override val kind: GPUMaterialKind = originalKind
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
                "effectId=$effectId, " +
                "uniforms=${uniformSnapshot.deterministicUniformString()}, " +
                "childIdentities=${childIdentitySnapshot.deterministicString()}" +
                ")"
    }
}

private fun GPUMaterialDescriptor.deepSnapshot(): GPUMaterialDescriptor =
    when (this) {
        is GPUMaterialDescriptor.SolidColor -> copy()
        is GPUMaterialDescriptor.LinearGradient -> copy(
            allStopPositions = allStopPositions?.copyOf(),
            allStopColors = allStopColors?.copyOf(),
        )
        is GPUMaterialDescriptor.RadialGradient -> copy(
            allStopPositions = allStopPositions?.copyOf(),
            allStopColors = allStopColors?.copyOf(),
        )
        is GPUMaterialDescriptor.SweepGradient -> copy(
            allStopPositions = allStopPositions?.copyOf(),
            allStopColors = allStopColors?.copyOf(),
        )
        is GPUMaterialDescriptor.ConicalGradient -> copy(
            allStopPositions = allStopPositions?.copyOf(),
            allStopColors = allStopColors?.copyOf(),
        )
        is GPUMaterialDescriptor.ImageDraw -> copy(rgbaPixels = rgbaPixels.copyOf())
        is GPUMaterialDescriptor.RuntimeEffect ->
            GPUMaterialDescriptor.RuntimeEffect(
                effectId = effectId,
                descriptorVersion = descriptorVersion,
                uniforms = uniforms,
                children = children,
            )
        is GPUMaterialDescriptor.BlendShader -> copy(
            dst = dst.deepSnapshot(),
            src = src.deepSnapshot(),
            uniformBytes = uniformBytes.copyOf(),
        )
        is GPUMaterialDescriptor.Unsupported -> copy(
            source = source?.deepSnapshot(),
            evidence = evidence?.deepSnapshot(),
        )
    }

private fun GPUMaterialDescriptor.deepEquals(other: GPUMaterialDescriptor): Boolean =
    when {
        this is GPUMaterialDescriptor.SolidColor && other is GPUMaterialDescriptor.SolidColor ->
            this == other
        this is GPUMaterialDescriptor.LinearGradient && other is GPUMaterialDescriptor.LinearGradient ->
            copy(allStopPositions = null, allStopColors = null) ==
                other.copy(allStopPositions = null, allStopColors = null) &&
                allStopPositions.contentEqualsNullable(other.allStopPositions) &&
                allStopColors.contentEqualsNullable(other.allStopColors)
        this is GPUMaterialDescriptor.RadialGradient && other is GPUMaterialDescriptor.RadialGradient ->
            copy(allStopPositions = null, allStopColors = null) ==
                other.copy(allStopPositions = null, allStopColors = null) &&
                allStopPositions.contentEqualsNullable(other.allStopPositions) &&
                allStopColors.contentEqualsNullable(other.allStopColors)
        this is GPUMaterialDescriptor.SweepGradient && other is GPUMaterialDescriptor.SweepGradient ->
            copy(allStopPositions = null, allStopColors = null) ==
                other.copy(allStopPositions = null, allStopColors = null) &&
                allStopPositions.contentEqualsNullable(other.allStopPositions) &&
                allStopColors.contentEqualsNullable(other.allStopColors)
        this is GPUMaterialDescriptor.ConicalGradient && other is GPUMaterialDescriptor.ConicalGradient ->
            copy(allStopPositions = null, allStopColors = null) ==
                other.copy(allStopPositions = null, allStopColors = null) &&
                allStopPositions.contentEqualsNullable(other.allStopPositions) &&
                allStopColors.contentEqualsNullable(other.allStopColors)
        this is GPUMaterialDescriptor.ImageDraw && other is GPUMaterialDescriptor.ImageDraw ->
            copy(rgbaPixels = DEEP_COMPARE_EMPTY_BYTES) ==
                other.copy(rgbaPixels = DEEP_COMPARE_EMPTY_BYTES) &&
                rgbaPixels.contentEquals(other.rgbaPixels)
        this is GPUMaterialDescriptor.RuntimeEffect && other is GPUMaterialDescriptor.RuntimeEffect ->
            this == other
        this is GPUMaterialDescriptor.BlendShader && other is GPUMaterialDescriptor.BlendShader ->
            mode == other.mode &&
                wgslCombined == other.wgslCombined &&
                uniformBytes.contentEquals(other.uniformBytes) &&
                dst.deepEquals(other.dst) &&
                src.deepEquals(other.src)
        this is GPUMaterialDescriptor.Unsupported && other is GPUMaterialDescriptor.Unsupported ->
            reason == other.reason &&
                originalKind == other.originalKind &&
                source.deepEqualsNullable(other.source) &&
                evidence == other.evidence
        else -> false
    }

private fun GPUMaterialDescriptor.deepHashCode(): Int =
    when (this) {
        is GPUMaterialDescriptor.SolidColor -> hashCode()
        is GPUMaterialDescriptor.LinearGradient ->
            31 * (
                31 * copy(allStopPositions = null, allStopColors = null).hashCode() +
                    (allStopPositions?.contentHashCode() ?: 0)
                ) + (allStopColors?.contentHashCode() ?: 0)
        is GPUMaterialDescriptor.RadialGradient ->
            31 * (
                31 * copy(allStopPositions = null, allStopColors = null).hashCode() +
                    (allStopPositions?.contentHashCode() ?: 0)
                ) + (allStopColors?.contentHashCode() ?: 0)
        is GPUMaterialDescriptor.SweepGradient ->
            31 * (
                31 * copy(allStopPositions = null, allStopColors = null).hashCode() +
                    (allStopPositions?.contentHashCode() ?: 0)
                ) + (allStopColors?.contentHashCode() ?: 0)
        is GPUMaterialDescriptor.ConicalGradient ->
            31 * (
                31 * copy(allStopPositions = null, allStopColors = null).hashCode() +
                    (allStopPositions?.contentHashCode() ?: 0)
                ) + (allStopColors?.contentHashCode() ?: 0)
        is GPUMaterialDescriptor.ImageDraw ->
            31 * copy(rgbaPixels = DEEP_COMPARE_EMPTY_BYTES).hashCode() +
                rgbaPixels.contentHashCode()
        is GPUMaterialDescriptor.RuntimeEffect -> hashCode()
        is GPUMaterialDescriptor.BlendShader -> {
            var result = mode.hashCode()
            result = 31 * result + dst.deepHashCode()
            result = 31 * result + src.deepHashCode()
            result = 31 * result + wgslCombined.hashCode()
            result = 31 * result + uniformBytes.contentHashCode()
            result
        }
        is GPUMaterialDescriptor.Unsupported -> {
            var result = reason.hashCode()
            result = 31 * result + originalKind.hashCode()
            result = 31 * result + (source?.deepHashCode() ?: 0)
            result = 31 * result + (evidence?.hashCode() ?: 0)
            result
        }
    }

private fun FloatArray?.contentEqualsNullable(other: FloatArray?): Boolean =
    when {
        this == null -> other == null
        other == null -> false
        else -> contentEquals(other)
    }

private fun GPUMaterialDescriptor?.deepEqualsNullable(other: GPUMaterialDescriptor?): Boolean =
    when {
        this == null -> other == null
        other == null -> false
        else -> deepEquals(other)
    }

private fun Map<String, GPURuntimeEffectUniformValue>.deterministicUniformString(): String =
    keys.sorted().joinToString(prefix = "{", postfix = "}") { name ->
        "$name=${getValue(name).deterministicString()}"
    }

private fun Map<String, String>.deterministicString(): String =
    keys.sorted().joinToString(prefix = "{", postfix = "}") { name ->
        "$name=${getValue(name)}"
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

private fun Map<String, GPUMaterialDescriptor>.deterministicChildString(): String =
    keys.sorted().joinToString(prefix = "{", postfix = "}") { name ->
        "$name=${getValue(name).deterministicString()}"
    }

private fun GPURuntimeEffectUniformValue.deterministicString(): String =
    when (this) {
        is GPURuntimeEffectUniformValue.Float1 -> "Float1($value)"
        is GPURuntimeEffectUniformValue.Float2 -> "Float2($x, $y)"
        is GPURuntimeEffectUniformValue.Float3 -> "Float3($x, $y, $z)"
        is GPURuntimeEffectUniformValue.Float4 -> "Float4($x, $y, $z, $w)"
        is GPURuntimeEffectUniformValue.Int1 -> "Int1($value)"
        is GPURuntimeEffectUniformValue.Matrix3x3 -> "Matrix3x3($values)"
        is GPURuntimeEffectUniformValue.Matrix4x4 -> "Matrix4x4($values)"
    }

private fun GPUMaterialDescriptor.deterministicString(): String =
    when (this) {
        is GPUMaterialDescriptor.SolidColor ->
            "SolidColor(r=$r, g=$g, b=$b, a=$a)"
        is GPUMaterialDescriptor.LinearGradient ->
            "LinearGradient(start=($startX,$startY), end=($endX,$endY), " +
                "startColor=($startR,$startG,$startB,$startA), " +
                "endColor=($endR,$endG,$endB,$endA), tileMode=$tileMode, " +
                "positions=${allStopPositions?.contentToString()}, " +
                "colors=${allStopColors?.contentToString()}, " +
                "snippetSourceHash=$snippetSourceHash, fragmentEntryPoint=$fragmentEntryPoint)"
        is GPUMaterialDescriptor.RadialGradient ->
            "RadialGradient(center=($centerX,$centerY), radius=$radius, " +
                "startColor=($startR,$startG,$startB,$startA), " +
                "endColor=($endR,$endG,$endB,$endA), tileMode=$tileMode, " +
                "positions=${allStopPositions?.contentToString()}, " +
                "colors=${allStopColors?.contentToString()}, " +
                "snippetSourceHash=$snippetSourceHash, fragmentEntryPoint=$fragmentEntryPoint)"
        is GPUMaterialDescriptor.SweepGradient ->
            "SweepGradient(center=($centerX,$centerY), angles=($startAngle,$endAngle), " +
                "startColor=($startR,$startG,$startB,$startA), " +
                "endColor=($endR,$endG,$endB,$endA), tileMode=$tileMode, " +
                "positions=${allStopPositions?.contentToString()}, " +
                "colors=${allStopColors?.contentToString()}, " +
                "snippetSourceHash=$snippetSourceHash, fragmentEntryPoint=$fragmentEntryPoint)"
        is GPUMaterialDescriptor.ConicalGradient ->
            "ConicalGradient(start=($startX,$startY,$startRadius), " +
                "end=($endX,$endY,$endRadius), " +
                "startColor=($startR,$startG,$startB,$startA), " +
                "endColor=($endR,$endG,$endB,$endA), tileMode=$tileMode, " +
                "positions=${allStopPositions?.contentToString()}, " +
                "colors=${allStopColors?.contentToString()}, " +
                "snippetSourceHash=$snippetSourceHash, fragmentEntryPoint=$fragmentEntryPoint)"
        is GPUMaterialDescriptor.ImageDraw ->
            "ImageDraw(source=$imageSourceId, size=${imageWidth}x$imageHeight, " +
                "pixels=${rgbaPixels.contentToString()}, sampling=$samplingFilterMode, " +
                "alphaOnly=$alphaOnly, tint=($tintR,$tintG,$tintB,$tintA))"
        is GPUMaterialDescriptor.RuntimeEffect -> toString()
        is GPUMaterialDescriptor.BlendShader ->
            "BlendShader(mode=$mode, dst=${dst.deterministicString()}, " +
                "src=${src.deterministicString()}, wgslCombined=$wgslCombined, " +
                "uniformBytes=${uniformBytes.contentToString()})"
        is GPUMaterialDescriptor.Unsupported ->
            "Unsupported(reason=$reason, originalKind=$originalKind, " +
                "source=${source?.deterministicString()}, evidence=$evidence)"
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

/** Source adapter information used by diagnostics and dumps. */
data class GPUCommandSource(
    val adapter: String,
    val operation: String,
    val frameProvenance: GPUFrameProvenance = GPUFrameProvenance.None,
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
    /** Captured material descriptor. */
    val material: GPUMaterialDescriptor
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
     * Blend facts are retained to satisfy the shared normalized-command contract,
     * but recording still refuses text runs until a text GPU route is promoted.
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
        override val material: GPUMaterialDescriptor,
        override val blend: GPUBlendFacts = GPUBlendFacts.srcOver(),
        override val bounds: GPUBounds,
        override val ordering: GPUOrderingFacts,
        override val source: GPUCommandSource,
    ) : NormalizedDrawCommand {
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
