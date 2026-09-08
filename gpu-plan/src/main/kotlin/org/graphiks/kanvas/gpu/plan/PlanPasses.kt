package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.color.ColorF32
import org.graphiks.math.geometry.PathFillGeometryF32
import org.graphiks.math.geometry.PathStrokeGeometryF32
import org.graphiks.math.geometry.PathStrokeDrawMode
import org.graphiks.math.geometry.PathStrokeStyleF64
import org.graphiks.math.geometry.PathStrokeCap
import org.graphiks.math.geometry.PathStrokeJoin
import org.graphiks.math.geometry.PathStrokeWidthF64
import org.graphiks.kanvas.render.ir.DrawOrigin
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.ClipGeometryF32
import org.graphiks.math.geometry.InversePathGeometryF32

public enum class CoveragePlan { FullOrScissor, AnalyticScalarAA, StencilAA4, BinaryMaskCover4 }
public enum class SamplePlan { SingleSample, Multisample4 }
public enum class BlendPlan { SrcOver }
public enum class AttachmentLoadPlan { ClearTransparent, Load }
public enum class AttachmentStorePlan { Store }
public enum class PlanDepthStencilLoadStore { ClearZeroStore, LoadStoreTestReset }
public enum class PlanDepthStencilAccess { Write, ReadWrite }
public enum class PlanPassRole {
    MainRender,
    PathMaskClear,
    PathRender,
    StencilProducer,
    StencilCover,
    TextureCopy,
    Filter,
    Resolve,
    Readback,
    ClipMaskInitialize,
    ClipMaskProducer,
    ClipMaskFold,
}
public enum class ClipCombineOperation { Intersect, Difference }

/** The clip realization selected for one consumer draw. */
public sealed interface ClipPlanStrategy {
    public class Scissor(domainI32: RectI32) : ClipPlanStrategy {
        private val domainSnapshotI32: RectI32 = domainI32.copy()
        public fun copyDomainI32(): RectI32 = domainSnapshotI32.copy()
    }
    public class Stencil(public val depthStencil: PlanResourceId) : ClipPlanStrategy
    public class Mask(public val resource: PlanResourceId) : ClipPlanStrategy
    public class InverseMask(
        public val geometryF32: InversePathGeometryF32,
        public val resource: PlanResourceId,
    ) : ClipPlanStrategy
}
public enum class PathFillStrategy { DirectTriangle, StencilCover }
public enum class PathRenderPhase {
    SingleSampleDirectColor,
    SingleSampleStencilProducer,
    SingleSampleStencilColorCover,
    MultisampleDirectColor,
    MultisampleStencilProducer,
    MultisampleStencilColorCover,
    HardEdgeMaskProducer,
    HardEdgeMaskStencilProducer,
    HardEdgeMaskStencilCover,
    HardEdgeBinaryColorCover,
}
public enum class BinaryMaskFetchPlan { TextureLoadUnfiltered }

/** Immutable geometry authority for a path draw, retained by `:math`. */
public sealed interface PathDrawGeometry {
    public data class Fill(public val valueF32: PathFillGeometryF32) : PathDrawGeometry
    public data class Stroke(public val valueF32: PathStrokeGeometryF32) : PathDrawGeometry
}

public sealed interface PlanDraw {
    public val commandIndex: Int
    public val color: ColorF32
    public val coverage: CoveragePlan
    public val sample: SamplePlan
    public val blend: BlendPlan
}

/** A visual draw whose coverage is constrained by a separately planned clip strategy. */
public class ClippedPlanDraw private constructor(
    public val source: PlanDraw,
    public val strategy: ClipPlanStrategy,
) : PlanDraw {
    override public val commandIndex: Int get() = source.commandIndex
    override public val color: ColorF32 get() = source.color
    override public val coverage: CoveragePlan get() = source.coverage
    override public val sample: SamplePlan get() = source.sample
    override public val blend: BlendPlan get() = source.blend

    public companion object {
        public fun of(source: PlanDraw, strategy: ClipPlanStrategy): ClippedPlanDraw =
            ClippedPlanDraw(source, strategy)
    }
}

/** Common sealed contract for W4c fills and W4d stroke snapshots. */
public sealed interface PathDraw : PlanDraw {
    public val strategy: PathFillStrategy
    public fun copyPathGeometry(): PathDrawGeometry
    public fun copyScissorI32(): RectI32
}

/** Typed W4d.2 path draw snapshot for a single-sample hard producer or a four-sample AA color draw. */
public class GeneralPathDraw private constructor(
    override public val commandIndex: Int,
    override public val color: ColorF32,
    geometry: PathDrawGeometry,
    override public val strategy: PathFillStrategy,
    scissorI32: RectI32,
    override public val coverage: CoveragePlan,
    override public val sample: SamplePlan,
) : PathRenderDraw {
    private val geometrySnapshot: PathDrawGeometry = geometry
    private val scissorSnapshotI32 = scissorI32.copy()

    override public val blend: BlendPlan = BlendPlan.SrcOver

    override fun copyPathGeometry(): PathDrawGeometry = geometrySnapshot

    override fun copyScissorI32(): RectI32 = scissorSnapshotI32.copy()

    public companion object {
        public fun of(
            commandIndex: Int,
            color: ColorF32,
            geometry: PathDrawGeometry,
            strategy: PathFillStrategy,
            scissorI32: RectI32,
            coverage: CoveragePlan,
            sample: SamplePlan,
        ): GeneralPathDraw {
            require(commandIndex >= 0) { "Command index must not be negative" }
            require(!scissorI32.isEmpty) { "General path scissor must be non-empty" }
            require(
                (coverage == CoveragePlan.FullOrScissor && sample == SamplePlan.SingleSample) ||
                    (coverage == CoveragePlan.StencilAA4 && sample == SamplePlan.Multisample4),
            ) { "General path draws require an explicit hard or four-sample AA contract" }
            requirePathRenderGeometryForStrategy(geometry, strategy)
            return GeneralPathDraw(commandIndex, color, geometry, strategy, scissorI32, coverage, sample)
        }
    }
}

/** A four-sample color cover driven by one unfiltered binary texel from a single-sample mask. */
public class BinaryMaskedPathDraw private constructor(
    public val producer: GeneralPathDraw,
    public val mask: PlanResourceId,
) : PathRenderDraw {
    override public val commandIndex: Int = producer.commandIndex
    override public val color: ColorF32 = producer.color
    override public val strategy: PathFillStrategy = producer.strategy
    override public val coverage: CoveragePlan = CoveragePlan.BinaryMaskCover4
    override public val sample: SamplePlan = SamplePlan.Multisample4
    override public val blend: BlendPlan = BlendPlan.SrcOver
    public val maskFetch: BinaryMaskFetchPlan = BinaryMaskFetchPlan.TextureLoadUnfiltered
    public val broadcastSampleCountI32: Int = 4

    override fun copyPathGeometry(): PathDrawGeometry = producer.copyPathGeometry()

    override fun copyScissorI32(): RectI32 = producer.copyScissorI32()

    public companion object {
        public fun of(source: GeneralPathDraw, mask: PlanResourceId): BinaryMaskedPathDraw {
            require(source.coverage == CoveragePlan.FullOrScissor && source.sample == SamplePlan.SingleSample) {
                "Binary mask covers require a single-sample hard-edge producer"
            }
            return BinaryMaskedPathDraw(source, mask)
        }
    }
}

/** A typed AA4 hard-edge path consumer with both binary and ordered clip-mask coverage. */
public class ClippedBinaryMaskedPathDraw private constructor(
    public val source: BinaryMaskedPathDraw,
    public val clip: ClipPlanStrategy,
) : PathRenderDraw {
    override public val commandIndex: Int get() = source.commandIndex
    override public val color: ColorF32 get() = source.color
    override public val strategy: PathFillStrategy get() = source.strategy
    override public val coverage: CoveragePlan get() = source.coverage
    override public val sample: SamplePlan get() = source.sample
    override public val blend: BlendPlan get() = source.blend
    public val binarySampleCountI32: Int get() = source.broadcastSampleCountI32

    override fun copyPathGeometry(): PathDrawGeometry = source.copyPathGeometry()
    override fun copyScissorI32(): RectI32 = source.copyScissorI32()

    public companion object {
        public fun of(source: BinaryMaskedPathDraw, clip: ClipPlanStrategy): ClippedBinaryMaskedPathDraw =
            ClippedBinaryMaskedPathDraw(source, clip)
    }
}

/** Common typed path draw contract owned by W4d.2 path render passes. */
public sealed interface PathRenderDraw : PlanDraw {
    public val strategy: PathFillStrategy
    public fun copyPathGeometry(): PathDrawGeometry
    public fun copyScissorI32(): RectI32
}

public class SolidRectDraw private constructor(
    override public val commandIndex: Int,
    override public val color: ColorF32,
    visibleBounds: RectI32,
    scissor: RectI32,
    override public val coverage: CoveragePlan,
    override public val sample: SamplePlan,
    override public val blend: BlendPlan,
) : PlanDraw {
    private val storedVisibleBounds = visibleBounds.copy()
    private val storedScissor = scissor.copy()

    public fun copyVisibleBounds(): RectI32 = storedVisibleBounds.copy()
    public fun copyScissor(): RectI32 = storedScissor.copy()

    public companion object {
        public fun of(
            commandIndex: Int,
            color: ColorF32,
            visibleBounds: RectI32,
            scissor: RectI32,
            coverage: CoveragePlan = CoveragePlan.FullOrScissor,
            sample: SamplePlan = SamplePlan.SingleSample,
            blend: BlendPlan = BlendPlan.SrcOver,
        ): SolidRectDraw {
            require(commandIndex >= 0) { "Command index must be non-negative" }
            require(!visibleBounds.isEmpty && !scissor.isEmpty) { "Draw rectangles must be non-empty" }
            return SolidRectDraw(commandIndex, color, visibleBounds, scissor, coverage, sample, blend)
        }
    }
}

public class AnalyticRectDraw private constructor(
    override public val commandIndex: Int,
    override public val color: ColorF32,
    deviceBounds: RectF32,
    rasterBounds: RectI32,
    scissor: RectI32,
) : PlanDraw {
    override public val coverage: CoveragePlan = CoveragePlan.AnalyticScalarAA
    override public val sample: SamplePlan = SamplePlan.SingleSample
    override public val blend: BlendPlan = BlendPlan.SrcOver
    private val storedDeviceBounds = deviceBounds.copy()
    private val storedRasterBounds = rasterBounds.copy()
    private val storedScissor = scissor.copy()

    public fun copyDeviceBounds(): RectF32 = storedDeviceBounds.copy()
    public fun copyRasterBounds(): RectI32 = storedRasterBounds.copy()
    public fun copyScissor(): RectI32 = storedScissor.copy()

    public companion object {
        public fun of(
            commandIndex: Int,
            color: ColorF32,
            deviceBounds: RectF32,
            rasterBounds: RectI32,
            scissor: RectI32,
        ): AnalyticRectDraw {
            require(commandIndex >= 0) { "Command index must be non-negative" }
            require(!deviceBounds.isEmpty && !rasterBounds.isEmpty && !scissor.isEmpty) {
                "Draw rectangles must be non-empty"
            }
            return AnalyticRectDraw(commandIndex, color, deviceBounds, rasterBounds, scissor)
        }
    }
}

public class AnalyticRRectDraw private constructor(
    override public val commandIndex: Int,
    override public val color: ColorF32,
    public val origin: DrawOrigin,
    deviceShape: RRectF32,
    rasterBounds: RectI32,
    scissor: RectI32,
) : PlanDraw {
    override public val coverage: CoveragePlan = CoveragePlan.AnalyticScalarAA
    override public val sample: SamplePlan = SamplePlan.SingleSample
    override public val blend: BlendPlan = BlendPlan.SrcOver
    private val storedDeviceShape = RRectF32.of(
        deviceShape.rect.copy(),
        deviceShape.topLeft,
        deviceShape.topRight,
        deviceShape.bottomRight,
        deviceShape.bottomLeft,
    )
    private val storedRasterBounds = rasterBounds.copy()
    private val storedScissor = scissor.copy()

    public fun copyDeviceShape(): RRectF32 = RRectF32.of(
        storedDeviceShape.rect.copy(),
        storedDeviceShape.topLeft,
        storedDeviceShape.topRight,
        storedDeviceShape.bottomRight,
        storedDeviceShape.bottomLeft,
    )
    public fun copyRasterBounds(): RectI32 = storedRasterBounds.copy()
    public fun copyScissor(): RectI32 = storedScissor.copy()

    public companion object {
        public fun of(
            commandIndex: Int,
            color: ColorF32,
            origin: DrawOrigin,
            deviceShape: RRectF32,
            rasterBounds: RectI32,
            scissor: RectI32,
        ): AnalyticRRectDraw {
            require(commandIndex >= 0) { "Command index must be non-negative" }
            require(origin == DrawOrigin.RECT || origin == DrawOrigin.RRECT) {
                "Analytic rrect draws require RECT or RRECT origin"
            }
            require(!deviceShape.rect.isEmpty && !rasterBounds.isEmpty && !scissor.isEmpty) {
                "Draw rectangles must be non-empty"
            }
            return AnalyticRRectDraw(commandIndex, color, origin, deviceShape, rasterBounds, scissor)
        }
    }
}

/** A sealed W4c path-fill draw whose geometry authority remains owned by `:math`. */
public class PathFillDraw private constructor(
    override public val commandIndex: Int,
    override public val color: ColorF32,
    geometryF32: PathFillGeometryF32,
    override public val strategy: PathFillStrategy,
    scissorI32: RectI32,
) : PathDraw {
    override public val coverage: CoveragePlan = CoveragePlan.FullOrScissor
    override public val sample: SamplePlan = SamplePlan.SingleSample
    override public val blend: BlendPlan = BlendPlan.SrcOver
    private val geometrySnapshotF32: PathFillGeometryF32 = geometryF32
    private val scissorSnapshotI32 = scissorI32.copy()

    public fun copyGeometryF32(): PathFillGeometryF32 = geometrySnapshotF32

    override fun copyPathGeometry(): PathDrawGeometry = PathDrawGeometry.Fill(geometrySnapshotF32)

    override fun copyScissorI32(): RectI32 = scissorSnapshotI32.copy()

    public companion object {
        public fun of(
            commandIndex: Int,
            color: ColorF32,
            geometryF32: PathFillGeometryF32,
            strategy: PathFillStrategy,
            scissorI32: RectI32,
        ): PathFillDraw {
            require(commandIndex >= 0) { "Command index must not be negative" }
            require(!scissorI32.isEmpty) { "Path fill scissor must be non-empty" }
            when (strategy) {
                PathFillStrategy.DirectTriangle -> require(
                    geometryF32.copyDirectTriangleF32OrNull() != null &&
                        geometryF32.copyStencilEdgeFanF32OrNull() == null,
                ) { "Direct path fills require direct-triangle geometry" }
                PathFillStrategy.StencilCover -> require(
                    geometryF32.copyDirectTriangleF32OrNull() == null &&
                        geometryF32.copyStencilEdgeFanF32OrNull() != null,
                ) { "Stencil path fills require edge-fan geometry" }
            }
            return PathFillDraw(commandIndex, color, geometryF32, strategy, scissorI32)
        }
    }
}

/** A sealed W4d stroke draw whose immutable geometry authority remains owned by `:math`. */
public class PathStrokeDraw private constructor(
    override public val commandIndex: Int,
    override public val color: ColorF32,
    geometryF32: PathStrokeGeometryF32,
    public val mode: PathStrokeDrawMode,
    public val styleF64: PathStrokeStyleF64,
    scissorI32: RectI32,
) : PathDraw {
    override public val coverage: CoveragePlan = CoveragePlan.FullOrScissor
    override public val sample: SamplePlan = SamplePlan.SingleSample
    override public val blend: BlendPlan = BlendPlan.SrcOver
    private val geometrySnapshotF32: PathStrokeGeometryF32 = geometryF32
    private val scissorSnapshotI32 = scissorI32.copy()
    override val strategy: PathFillStrategy = pathFillStrategy(geometryF32.copyFillGeometryF32())

    public fun copyGeometryF32(): PathStrokeGeometryF32 = geometrySnapshotF32

    override fun copyPathGeometry(): PathDrawGeometry = PathDrawGeometry.Stroke(geometrySnapshotF32)

    override fun copyScissorI32(): RectI32 = scissorSnapshotI32.copy()

    public companion object {
        public fun of(
            commandIndex: Int,
            color: ColorF32,
            geometryF32: PathStrokeGeometryF32,
            scissorI32: RectI32,
            mode: PathStrokeDrawMode = PathStrokeDrawMode.Stroke,
            styleF64: PathStrokeStyleF64 = PathStrokeStyleF64(
                PathStrokeWidthF64.Hairline, PathStrokeCap.Butt, PathStrokeJoin.Miter, 4.0,
            ),
        ): PathStrokeDraw {
            require(commandIndex >= 0) { "Command index must not be negative" }
            require(!scissorI32.isEmpty) { "Path stroke scissor must be non-empty" }
            pathFillStrategy(geometryF32.copyFillGeometryF32())
            return PathStrokeDraw(commandIndex, color, geometryF32, mode, styleF64, scissorI32)
        }
    }
}

private fun pathFillStrategy(geometryF32: PathFillGeometryF32): PathFillStrategy = when {
    geometryF32.copyDirectTriangleF32OrNull() != null && geometryF32.copyStencilEdgeFanF32OrNull() == null ->
        PathFillStrategy.DirectTriangle
    geometryF32.copyDirectTriangleF32OrNull() == null && geometryF32.copyStencilEdgeFanF32OrNull() != null ->
        PathFillStrategy.StencilCover
    else -> throw IllegalArgumentException("Path geometry must select exactly one fill strategy")
}

private fun requirePathRenderGeometryForStrategy(
    geometry: PathDrawGeometry,
    strategy: PathFillStrategy,
) {
    val fillGeometry = when (geometry) {
        is PathDrawGeometry.Fill -> geometry.valueF32
        is PathDrawGeometry.Stroke -> geometry.valueF32.copyFillGeometryF32()
    }
    require(pathFillStrategy(fillGeometry) == strategy) {
        "Path geometry must select the declared fill strategy"
    }
}

public data class PlanDrawDataResources(
    public val vertex: PlanResourceId,
    public val index: PlanResourceId,
    public val uniform: PlanResourceId,
)

public sealed interface PlanPass {
    public val id: PlanPassId
    public val role: PlanPassRole
    public val ordinal: Int

    public class RenderPass(
        override val ordinal: Int,
        public val target: PlanResourceId,
        draws: List<PlanDraw>,
        public val load: AttachmentLoadPlan,
        public val store: AttachmentStorePlan,
        public val drawDataResources: PlanDrawDataResources? = null,
    ) : PlanPass {
        override val role: PlanPassRole = PlanPassRole.MainRender
        override val id: PlanPassId = checkedPassId(role, ordinal)
        private val storedDraws = immutableList(draws)
        public fun draws(): List<PlanDraw> = storedDraws
    }

    /** Clears one single-sample hard-edge coverage mask before its atomic producer sequence. */
    public class PathMaskClearPass(
        override val ordinal: Int,
        public val target: PlanResourceId,
        public val atomicGroup: PlanAtomicGroupId,
    ) : PlanPass {
        override val role: PlanPassRole = PlanPassRole.PathMaskClear
        override val id: PlanPassId = checkedPassId(role, ordinal)
        public val load: AttachmentLoadPlan = AttachmentLoadPlan.ClearTransparent
        public val store: AttachmentStorePlan = AttachmentStorePlan.Store
    }

    /** Initializes the finite coverage domain to opaque coverage before ordered clip folds. */
    public class ClipMaskInitialize(
        override public val ordinal: Int,
        public val output: PlanResourceId,
        domainI32: RectI32,
        public val clearCoverageF32: Float = 1f,
        public val atomicGroup: PlanAtomicGroupId,
    ) : PlanPass {
        override public val role: PlanPassRole = PlanPassRole.ClipMaskInitialize
        override public val id: PlanPassId = checkedPassId(role, ordinal)
        private val domainSnapshotI32: RectI32 = domainI32.copy()
        public fun copyDomainI32(): RectI32 = domainSnapshotI32.copy()
    }

    /** Rasterizes one finite clip element into its own scratch attachment. */
    public class ClipMaskProducer(
        override public val ordinal: Int,
        public val target: PlanResourceId,
        public val resolveTarget: PlanResourceId?,
        public val depthStencil: PlanResourceId?,
        public val sampleCountI32: Int,
        geometryF32: ClipGeometryF32,
        public val atomicGroup: PlanAtomicGroupId,
    ) : PlanPass {
        override public val role: PlanPassRole = PlanPassRole.ClipMaskProducer
        override public val id: PlanPassId = checkedPassId(role, ordinal)
        private val geometrySnapshotF32: ClipGeometryF32 = geometryF32.copyClipMaskGeometryF32()
        public fun copyGeometryF32(): ClipGeometryF32 = geometrySnapshotF32.copyClipMaskGeometryF32()
    }

    /** Combines the previous accumulator and one scratch producer in insertion order. */
    public class ClipMaskFold(
        override public val ordinal: Int,
        public val previous: PlanResourceId,
        public val source: PlanResourceId,
        public val output: PlanResourceId,
        public val operation: ClipCombineOperation,
        domainI32: RectI32,
        public val atomicGroup: PlanAtomicGroupId,
    ) : PlanPass {
        override public val role: PlanPassRole = PlanPassRole.ClipMaskFold
        override public val id: PlanPassId = checkedPassId(role, ordinal)
        private val domainSnapshotI32: RectI32 = domainI32.copy()
        public fun copyDomainI32(): RectI32 = domainSnapshotI32.copy()
    }

    /** Typed W4d.2 path pass for multisample AA and hard-edge mask production/color cover. */
    public class PathRenderPass(
        override val ordinal: Int,
        public val target: PlanResourceId,
        public val draw: PathRenderDraw,
        public val phase: PathRenderPhase,
        public val drawDataResources: PlanDrawDataResources,
        public val atomicGroup: PlanAtomicGroupId?,
        public val depthStencil: PlanResourceId?,
        public val load: AttachmentLoadPlan,
        public val store: AttachmentStorePlan,
        public val depthStencilAccess: PlanDepthStencilAccess?,
        public val depthStencilLoadStore: PlanDepthStencilLoadStore?,
        public val resolveTarget: PlanResourceId?,
    ) : PlanPass {
        override val role: PlanPassRole = PlanPassRole.PathRender
        override val id: PlanPassId = checkedPassId(role, ordinal)
    }

    public class StencilProducer(
        override val ordinal: Int,
        public val target: PlanResourceId,
        public val depthStencil: PlanResourceId,
        public val draw: PathDraw,
        public val drawDataResources: PlanDrawDataResources,
        public val atomicGroup: PlanAtomicGroupId,
        public val load: AttachmentLoadPlan,
        public val store: AttachmentStorePlan,
        public val depthStencilAccess: PlanDepthStencilAccess,
        public val depthStencilLoadStore: PlanDepthStencilLoadStore,
    ) : PlanPass {
        override val role: PlanPassRole = PlanPassRole.StencilProducer
        override val id: PlanPassId = checkedPassId(role, ordinal)
    }

    public class StencilCover(
        override val ordinal: Int,
        public val target: PlanResourceId,
        public val depthStencil: PlanResourceId,
        public val draw: PathDraw,
        public val drawDataResources: PlanDrawDataResources,
        public val atomicGroup: PlanAtomicGroupId,
        public val load: AttachmentLoadPlan,
        public val store: AttachmentStorePlan,
        public val depthStencilAccess: PlanDepthStencilAccess,
        public val depthStencilLoadStore: PlanDepthStencilLoadStore,
    ) : PlanPass {
        override val role: PlanPassRole = PlanPassRole.StencilCover
        override val id: PlanPassId = checkedPassId(role, ordinal)
    }

    public data class TextureCopy(
        override val ordinal: Int,
        public val source: PlanResourceId,
        public val destination: PlanResourceId,
    ) : PlanPass {
        override val role: PlanPassRole = PlanPassRole.TextureCopy
        override val id: PlanPassId = checkedPassId(role, ordinal)
    }

    public class FilterPass(
        override val ordinal: Int,
        inputs: List<PlanResourceId>,
        public val output: PlanResourceId,
    ) : PlanPass {
        override val role: PlanPassRole = PlanPassRole.Filter
        override val id: PlanPassId = checkedPassId(role, ordinal)
        private val storedInputs = immutableList(inputs)
        public fun inputs(): List<PlanResourceId> = storedInputs
    }

    public data class ResolvePass(
        override val ordinal: Int,
        public val source: PlanResourceId,
        public val destination: PlanResourceId,
    ) : PlanPass {
        override val role: PlanPassRole = PlanPassRole.Resolve
        override val id: PlanPassId = checkedPassId(role, ordinal)
    }

    public data class ReadbackPass(
        override val ordinal: Int,
        public val source: PlanResourceId,
        public val staging: PlanResourceId,
        public val bytesPerRow: Long,
    ) : PlanPass {
        override val role: PlanPassRole = PlanPassRole.Readback
        override val id: PlanPassId = checkedPassId(role, ordinal)
        init { require(bytesPerRow > 0) { "Readback row bytes must be positive" } }
    }
}

public data class PlanPassDependency(public val before: PlanPassId, public val after: PlanPassId)

private fun checkedPassId(role: PlanPassRole, ordinal: Int): PlanPassId {
    require(ordinal >= 0) { "Pass ordinal must be non-negative" }
    return planPassId(role, ordinal)
}

private fun ClipGeometryF32.copyClipMaskGeometryF32(): ClipGeometryF32 = when (this) {
    is ClipGeometryF32.Rect -> ClipGeometryF32.Rect(copyRectF32())
    is ClipGeometryF32.RRect -> ClipGeometryF32.RRect(copyRRectF32())
    is ClipGeometryF32.Path -> ClipGeometryF32.Path(copyPathGeometryF32())
    ClipGeometryF32.Empty -> ClipGeometryF32.Empty
}

internal fun <T> immutableList(values: List<T>): List<T> = java.util.Collections.unmodifiableList(values.toList())
