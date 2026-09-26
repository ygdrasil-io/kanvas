package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.DrawNode
import org.graphiks.math.color.ColorF32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.SizeI32

/** Handle-free W5b source admission. This is not a Ready graph or a device capability. */
public object W5bCorePrimitiveGraph {
    public const val CAPABILITY_ID: String = "w5b.core-points.v3"

    public fun seal(id: PlanId, extent: SizeI32, capabilities: PlanCapabilitySnapshot, budget: PlanBudget,
        draws: List<PlanDraw>, material: MaterialPlanTable): RenderGraph {
        require(draws.isNotEmpty() && draws.any { it is W5bPointDraw } &&
            draws.all { it is W5bPointDraw || it is SolidRectDraw })
        require(draws.map { it.commandIndex }.distinct().size == draws.size)
        // These bounds are owned by the admitted geometry. A fully clipped square
        // has no consumer pixels and must not issue an empty destination copy.
        val visibleDraws = draws.filter { draw -> isVisible(draw, extent) }
        val targetBytesI64 = Math.multiplyExact(Math.multiplyExact(extent.width.toLong(), extent.height.toLong()), 4L)
        val widthBytesI64 = Math.multiplyExact(extent.width.toLong(), 4L)
        val alignmentI64 = capabilities.copyBytesPerRowAlignment.toLong()
        val rowBytesI64 = Math.addExact(widthBytesI64, (alignmentI64 - widthBytesI64 % alignmentI64) % alignmentI64)
        return W5bDestinationGraphSealer.seal(id, CAPABILITY_ID, extent, capabilities, budget, visibleDraws, material,
            targetBytesI64, Math.multiplyExact(rowBytesI64, extent.height.toLong()), rowBytesI64)
    }

    internal fun isVisible(draw: PlanDraw, extent: SizeI32): Boolean {
            val bounds = when (draw) {
                is W5bPointDraw -> draw.copyBoundsI32()
                is SolidRectDraw -> draw.copyVisibleBounds()
                else -> error("Unsupported admitted Core draw")
            }
            val scissor = when (draw) {
                is W5bPointDraw -> draw.copyScissorI32()
                is SolidRectDraw -> draw.copyScissor()
                else -> error("Unsupported admitted Core draw")
            }
            return maxOf(0, bounds.left, scissor.left) < minOf(extent.width, bounds.right, scissor.right) &&
                maxOf(0, bounds.top, scissor.top) < minOf(extent.height, bounds.bottom, scissor.bottom)
    }

    public fun normalizeSource(draw: DrawNode, targetClamp: BlendTargetClampV1): EffectiveMaterialPlanner.Result =
        when (val source = EffectiveMaterialPlanner.normalize(draw, targetClamp, allowDestinationCandidate = true)) {
            is EffectiveMaterialPlanner.Normalization.Source -> EffectiveMaterialPlanner.Result.Ready(source.table, source.root, source.blend)
            is EffectiveMaterialPlanner.Normalization.Refused -> EffectiveMaterialPlanner.Result.Refused(source.diagnosticCode)
            EffectiveMaterialPlanner.Normalization.NoOp -> EffectiveMaterialPlanner.Result.Refused("invalid.w5b.noop-source")
        }
}

/** Exact W5a device squares/fans retained independently from SolidRect. */
public class W5bPointDraw private constructor(
    override public val commandIndex: Int,
    override public val materialAuthority: PlanDrawMaterialAuthority,
    verticesF32: FloatArray,
    indicesI32: IntArray,
    contourStartsI32: IntArray,
    boundsI32: RectI32,
    scissorI32: RectI32,
    override public val blend: BlendPlan,
    public val clipOnly: W4eClipOnlyPlan?,
) : PlanDraw {
    private val vertices = verticesF32.copyOf()
    private val indices = indicesI32.copyOf()
    private val contours = contourStartsI32.copyOf()
    private val bounds = boundsI32.copy()
    private val scissor = scissorI32.copy()
    override public val color: ColorF32 get() = error("W5b Point carries raw material authority only")
    override public val sample: SamplePlan = SamplePlan.SingleSample
    override public val coverage: CoveragePlan = CoveragePlan.FullOrScissor
    public fun copyVerticesF32(): FloatArray = vertices.copyOf()
    public fun copyIndicesI32(): IntArray = indices.copyOf()
    public fun copyContourStartsI32(): IntArray = contours.copyOf()
    public fun copyBoundsI32(): RectI32 = bounds.copy()
    public fun copyScissorI32(): RectI32 = scissor.copy()
    internal fun relativeToOriginI32OrNull(originI32: org.graphiks.math.geometry.Point2I32, scissorI32: RectI32): W5bPointDraw? {
        require(clipOnly == null)
        val local = org.graphiks.math.geometry.PointSquaresF32.fromDeviceQuadsF32OrNull(vertices, bounds)
            ?.relativeToOriginI32OrNull(originI32) ?: return null
        return W5bPointDraw(commandIndex, materialAuthority, local.copyVerticesF32(), indices, contours,
            local.copyBoundsI32(), scissorI32, blend, null)
    }
    internal fun withBlend(plan: BlendPlan): W5bPointDraw = W5bPointDraw(commandIndex, materialAuthority,
        vertices, indices, contours, bounds, scissor, plan, clipOnly)
    internal fun withMaterialRef(ref: MaterialPlanRef, composedV5: Boolean = materialAuthority is PlanDrawMaterialAuthority.MaterialV5): W5bPointDraw =
        W5bPointDraw(commandIndex, if (composedV5) PlanDrawMaterialAuthority.MaterialV5(ref)
            else PlanDrawMaterialAuthority.MaterialV1(ref), vertices, indices, contours, bounds, scissor, blend, clipOnly)

    /** Rebind only the occurrence identity; retain the selected geometry and material authority. */
    internal fun withCommandIndexI32(indexI32: Int): W5bPointDraw {
        require(indexI32 >= 0)
        return W5bPointDraw(indexI32, materialAuthority, vertices, indices, contours, bounds, scissor, blend, clipOnly)
    }

    public companion object {
        public fun of(commandIndexI32: Int, material: MaterialPlanRef, verticesF32: FloatArray,
            indicesI32: IntArray, contourStartsI32: IntArray, boundsI32: RectI32,
            scissorI32: RectI32, blend: BlendPlan, clipOnly: W4eClipOnlyPlan? = null, composedV5: Boolean = false): W5bPointDraw {
            require(commandIndexI32 >= 0 && !boundsI32.isEmpty && !scissorI32.isEmpty)
            require(contourStartsI32.size in 1..64 && verticesF32.size == contourStartsI32.size * 8 &&
                indicesI32.size == contourStartsI32.size * 6 && verticesF32.all(Float::isFinite))
            require(contourStartsI32.withIndex().all { (indexI32, startI32) -> startI32 == indexI32 * 4 })
            require(indicesI32.withIndex().all { (indexI32, vertexI32) ->
                vertexI32 == indexI32 / 6 * 4 + intArrayOf(0, 1, 2, 0, 2, 3)[indexI32 % 6]
            })
            require(blend != BlendPlan.NoOpV1)
            val finalBlend = if (clipOnly == null) blend else {
                val mode = when (blend) {
                    is BlendPlan.FixedFunctionV1 -> blend.mode
                    is BlendPlan.DestinationReadV1 -> blend.mode
                    else -> error("unsupported.w5b.point-mask-blend")
                }
                (requireNotNull(FinalBlendPlanner.plan(org.graphiks.kanvas.render.ir.BlendNode.Mode(mode),
                    CoveragePlan.AnalyticScalarAA, SamplePlan.SingleSample, BlendTargetClampV1.Unavailable)) as BlendPlan.DestinationReadV1)
                    .copy(compositionAbiI32 = 4)
            }
            return W5bPointDraw(commandIndexI32, if (composedV5) PlanDrawMaterialAuthority.MaterialV5(material)
                else PlanDrawMaterialAuthority.MaterialV1(material),
                verticesF32, indicesI32, contourStartsI32, boundsI32, scissorI32, finalBlend, clipOnly)
        }
    }
}
