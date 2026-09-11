package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.BlendMode
import org.graphiks.kanvas.render.ir.BlendNode

/** Monotonic destination version used by W5 destination-read planning. */
@JvmInline
public value class DestinationVersionI64(public val valueI64: Long) {
    init { require(valueI64 >= 0L) { "Destination version must be non-negative" } }
}

public enum class BlendFactorV1 {
    Zero, One, SrcAlpha, OneMinusSrcAlpha, DstAlpha, OneMinusDstAlpha, SrcColor, OneMinusSrcColor,
}

public enum class BlendOperationV1 { Add }
public enum class BlendCoverageEncodingV1 { FullOrScissor, ScalarCoverageInShader }

/** Authenticated target clamp fact; an unavailable fact is never inferred as a clamp. */
public enum class BlendTargetClampV1 { Unavailable, UnitInterval }

/** Derives the target fact from the sealed logical target format, never a device default. */
public fun PlanLogicalColorFormat.blendTargetClampV1(): BlendTargetClampV1 =
    if (clampsNormalizedColorWrites) BlendTargetClampV1.UnitInterval else BlendTargetClampV1.Unavailable

/** Handle-free final target composition selected before a graph becomes Ready. */
public sealed interface BlendPlan {
    public val canonicalLabel: String
    public data object LegacySrcOverV1 : BlendPlan { override val canonicalLabel: String = "legacy-src-over-v1" }

    public data class FixedFunctionV1(
        public val mode: BlendMode,
        public val colorSource: BlendFactorV1,
        public val colorDestination: BlendFactorV1,
        public val alphaSource: BlendFactorV1,
        public val alphaDestination: BlendFactorV1,
        public val operation: BlendOperationV1 = BlendOperationV1.Add,
        public val coverage: BlendCoverageEncodingV1 = BlendCoverageEncodingV1.FullOrScissor,
    ) : BlendPlan { override val canonicalLabel: String = "fixed-${mode.name.lowercase()}-${coverage.name}" }

    public data class DestinationReadV1(
        public val mode: BlendMode,
        public val formulaIdentity: String,
        public val coverage: BlendCoverageEncodingV1,
        public val requiredDestinationVersion: DestinationVersionI64,
        public val snapshotResource: PlanResourceId? = null,
        public val compositionAbiI32: Int = 3,
    ) : BlendPlan { override val canonicalLabel: String = "destination-read-$formulaIdentity" }

    public data object NoOpV1 : BlendPlan { override val canonicalLabel: String = "no-op-dst-v1" }

    public companion object {
        /** Transitional spelling retained solely for existing W3/W4 witnesses. */
        public val SrcOver: BlendPlan get() = LegacySrcOverV1
    }
}

/** Backend-neutral classifier. Renderers lower an already-selected [BlendPlan] only. */
public enum class BlendCoverageApplicationV1 { SourceMultiplication, DestinationInterpolation }

public object FinalBlendPlanner {
    public fun plan(
        blend: BlendNode,
        coverage: CoveragePlan,
        sample: SamplePlan,
        targetClamp: BlendTargetClampV1,
        coverageApplication: BlendCoverageApplicationV1 = BlendCoverageApplicationV1.DestinationInterpolation,
        coverageEncoding: BlendCoverageEncodingV1? = null,
    ): BlendPlan? {
        val mode = when (blend) {
            BlendNode.SrcOver -> BlendMode.SRC_OVER
            is BlendNode.Mode -> blend.mode
            is BlendNode.Paint -> if (blend.blender == null) blend.mode else return null
            is BlendNode.Custom -> return null
        }
        if (mode == BlendMode.DST) return BlendPlan.NoOpV1
        val effectiveCoverage = coverageEncoding ?: if (coverage == CoveragePlan.FullOrScissor && sample == SamplePlan.SingleSample) {
            BlendCoverageEncodingV1.FullOrScissor
        } else {
            BlendCoverageEncodingV1.ScalarCoverageInShader
        }
        if (effectiveCoverage == BlendCoverageEncodingV1.FullOrScissor ||
            sample == SamplePlan.SingleSample &&
                coverageApplication == BlendCoverageApplicationV1.SourceMultiplication) {
            fixed(mode, effectiveCoverage, targetClamp)?.let { return it }
        }
        return BlendPlan.DestinationReadV1(
            mode = mode,
            formulaIdentity = if (mode == BlendMode.PLUS) "plus_exact@v1" else "${mode.name.lowercase()}@v1",
            coverage = effectiveCoverage,
            requiredDestinationVersion = DestinationVersionI64(0L),
        )
    }

    private fun fixed(
        mode: BlendMode,
        coverage: BlendCoverageEncodingV1,
        targetClamp: BlendTargetClampV1,
    ): BlendPlan.FixedFunctionV1? {
        if (coverage != BlendCoverageEncodingV1.FullOrScissor && mode !in setOf(
                BlendMode.SRC_OVER, BlendMode.DST_OVER, BlendMode.DST_OUT,
                BlendMode.SRC_ATOP, BlendMode.XOR, BlendMode.SCREEN,
            )) return null
        fun state(source: BlendFactorV1, destination: BlendFactorV1) = BlendPlan.FixedFunctionV1(
            mode, source, destination, source, destination, coverage = coverage,
        )
        return when (mode) {
            BlendMode.CLEAR -> state(BlendFactorV1.Zero, BlendFactorV1.Zero)
            BlendMode.SRC -> state(BlendFactorV1.One, BlendFactorV1.Zero)
            BlendMode.SRC_OVER -> state(BlendFactorV1.One, BlendFactorV1.OneMinusSrcAlpha)
            BlendMode.DST_OVER -> state(BlendFactorV1.OneMinusDstAlpha, BlendFactorV1.One)
            BlendMode.SRC_IN -> state(BlendFactorV1.DstAlpha, BlendFactorV1.Zero)
            BlendMode.DST_IN -> state(BlendFactorV1.Zero, BlendFactorV1.SrcAlpha)
            BlendMode.SRC_OUT -> state(BlendFactorV1.OneMinusDstAlpha, BlendFactorV1.Zero)
            BlendMode.DST_OUT -> state(BlendFactorV1.Zero, BlendFactorV1.OneMinusSrcAlpha)
            BlendMode.SRC_ATOP -> state(BlendFactorV1.DstAlpha, BlendFactorV1.OneMinusSrcAlpha)
            BlendMode.DST_ATOP -> state(BlendFactorV1.OneMinusDstAlpha, BlendFactorV1.SrcAlpha)
            BlendMode.XOR -> state(BlendFactorV1.OneMinusDstAlpha, BlendFactorV1.OneMinusSrcAlpha)
            BlendMode.MODULATE -> BlendPlan.FixedFunctionV1(
                mode, BlendFactorV1.Zero, BlendFactorV1.SrcColor,
                BlendFactorV1.Zero, BlendFactorV1.SrcAlpha,
            )
            BlendMode.SCREEN -> BlendPlan.FixedFunctionV1(
                mode, BlendFactorV1.One, BlendFactorV1.OneMinusSrcColor,
                BlendFactorV1.One, BlendFactorV1.OneMinusSrcAlpha,
                coverage = coverage,
            )
            BlendMode.PLUS -> if (targetClamp == BlendTargetClampV1.UnitInterval) {
                state(BlendFactorV1.One, BlendFactorV1.One)
            } else null
            BlendMode.MULTIPLY, BlendMode.OVERLAY, BlendMode.DARKEN, BlendMode.LIGHTEN,
            BlendMode.COLOR_DODGE, BlendMode.COLOR_BURN, BlendMode.HARD_LIGHT, BlendMode.SOFT_LIGHT,
            BlendMode.DIFFERENCE, BlendMode.EXCLUSION, BlendMode.HUE, BlendMode.SATURATION,
            BlendMode.COLOR, BlendMode.LUMINOSITY, BlendMode.DST,
            -> null
        }
    }
}
