package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.color.ColorF32
import org.graphiks.math.geometry.PathFillGeometryF32
import org.graphiks.math.geometry.PathStrokeGeometryF32
import org.graphiks.kanvas.render.ir.DrawOrigin
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RectI32

public enum class CoveragePlan { FullOrScissor, AnalyticScalarAA }
public enum class SamplePlan { SingleSample }
public enum class BlendPlan { SrcOver }
public enum class AttachmentLoadPlan { ClearTransparent, Load }
public enum class AttachmentStorePlan { Store }
public enum class PlanDepthStencilLoadStore { ClearZeroStore, LoadStoreTestReset }
public enum class PlanDepthStencilAccess { Write, ReadWrite }
public enum class PlanPassRole { MainRender, StencilProducer, StencilCover, TextureCopy, Filter, Resolve, Readback }
public enum class PathFillStrategy { DirectTriangle, StencilCover }

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

/** Common sealed contract for W4c fills and W4d stroke snapshots. */
public sealed interface PathDraw : PlanDraw {
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
        ): PathStrokeDraw {
            require(commandIndex >= 0) { "Command index must not be negative" }
            require(!scissorI32.isEmpty) { "Path stroke scissor must be non-empty" }
            pathFillStrategy(geometryF32.copyFillGeometryF32())
            return PathStrokeDraw(commandIndex, color, geometryF32, scissorI32)
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

internal fun <T> immutableList(values: List<T>): List<T> = java.util.Collections.unmodifiableList(values.toList())
