package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.color.ColorF32
import org.graphiks.kanvas.render.ir.DrawOrigin
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RectI32

public enum class CoveragePlan { FullOrScissor, AnalyticScalarAA }
public enum class SamplePlan { SingleSample }
public enum class BlendPlan { SrcOver }
public enum class AttachmentLoadPlan { ClearTransparent }
public enum class AttachmentStorePlan { Store }
public enum class PlanPassRole { MainRender, TextureCopy, Filter, Resolve, Readback }

public sealed interface PlanDraw {
    public val commandIndex: Int
    public val color: ColorF32
    public val coverage: CoveragePlan
    public val sample: SamplePlan
    public val blend: BlendPlan
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
    deviceShape: RRectF32,
    public val origin: DrawOrigin,
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
            deviceShape: RRectF32,
            origin: DrawOrigin,
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
            return AnalyticRRectDraw(commandIndex, color, deviceShape, origin, rasterBounds, scissor)
        }
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
