package org.graphiks.kanvas.gpu.plan

import java.util.IdentityHashMap
import org.graphiks.kanvas.render.ir.CanonicalId
import org.graphiks.kanvas.render.ir.SceneCommand
import org.graphiks.kanvas.render.ir.SceneSnapshot
import org.graphiks.math.geometry.RectF32

/** Only compiler-issued identity formulas may bind a prepared lane to its final occurrence. */
internal typealias PreparedSceneIdentityV1 = (CanonicalId, Int, SourceDeferredRenderConstructionV4?) -> PlanId

/** Reindex the one synthetic occurrence carrier; never revisit its original captured scene. */
internal fun SceneSnapshot.rebindOccurrenceCommandV1(commandIndexI32: Int): SceneSnapshot {
    val draw = filterIsInstance<SceneCommand.Draw>().singleOrNull()
    require(all { it is SceneCommand.Draw || it is SceneCommand.Annotation })
    return SceneSnapshot.of(extent, colorSpace, List(Math.addExact(commandIndexI32, 1)) { index ->
        if (index == commandIndexI32 && draw != null) draw
        else SceneCommand.Annotation.of(RectF32(0f, 0f, 0f, 0f), "w6.occurrence", index.toString())
    }, graphLimits)
}

/** Shared caches preserve W4e's native-color-pass reference identity across both lane levels. */
internal class PreparedCommandRebindingV1(private val commandIndexI32: Int) {
    private val draws = IdentityHashMap<PlanDraw, PlanDraw>()
    private val passes = IdentityHashMap<PlanPass, PlanPass>()
    private val groups = mutableMapOf<PlanAtomicGroupId, PlanAtomicGroupId>()

    fun draw(source: PlanDraw): PlanDraw = draws.getOrPut(source) {
        when (source) {
            is SolidRectDraw -> source.withCommandIndexI32(commandIndexI32)
            is AnalyticRectDraw -> source.withCommandIndexI32(commandIndexI32)
            is AnalyticRRectDraw -> source.withCommandIndexI32(commandIndexI32)
            is PathFillDraw -> source.withCommandIndexI32(commandIndexI32)
            is PathStrokeDraw -> source.withCommandIndexI32(commandIndexI32)
            is GeneralPathDraw -> source.withCommandIndexI32(commandIndexI32)
            is W5bPointDraw -> source.withCommandIndexI32(commandIndexI32)
            is W5bVerticesDraw -> source.withCommandIndexI32(commandIndexI32)
            is ClippedPlanDraw -> ClippedPlanDraw.of(draw(source.source), source.strategy)
            is ClippedGeneralPathDraw -> ClippedGeneralPathDraw.of(draw(source.source) as GeneralPathDraw, source.clip)
            is BinaryMaskedPathDraw -> BinaryMaskedPathDraw.of(draw(source.producer) as GeneralPathDraw, source.mask)
            is ClippedBinaryMaskedPathDraw -> ClippedBinaryMaskedPathDraw.of(draw(source.source) as BinaryMaskedPathDraw, source.clip)
            is W5bW4ePathDraw -> W5bW4ePathDraw(pass(source.nativeColorPass) as PlanPass.PathRenderPass, source.blend)
            is ImageDrawV1 -> error("Legacy ImageDraw cannot occur in an unissued W4/W5 occurrence lane.")
        }
    }

    fun prepareGroups(source: List<PlanPass>) {
        source.forEach { pass -> when (pass) {
            is PlanPass.StencilProducer -> groups[pass.atomicGroup] = canonicalPathAtomicGroup(draw(pass.draw) as PathDraw)
            is PlanPass.StencilCover -> groups[pass.atomicGroup] = canonicalPathAtomicGroup(draw(pass.draw) as PathDraw)
            is PlanPass.PathRenderPass -> pass.atomicGroup?.let { group ->
                val original = when (val path = pass.draw) {
                    is GeneralPathDraw -> path
                    is ClippedGeneralPathDraw -> path.source
                    is BinaryMaskedPathDraw -> path.producer
                    is ClippedBinaryMaskedPathDraw -> path.source.producer
                }
                groups[group] = canonicalGeneralPathAtomicGroup(draw(original) as GeneralPathDraw)
            }
            else -> Unit
        } }
    }

    fun pass(source: PlanPass): PlanPass = passes.getOrPut(source) {
        fun group(value: PlanAtomicGroupId): PlanAtomicGroupId = groups[value] ?: value
        when (source) {
            is PlanPass.RenderPass -> PlanPass.RenderPass(source.ordinal, source.target, source.draws().map(::draw),
                source.load, source.store, source.drawDataResources, source.destinationVersionAfter,
                source.coverageSource, source.w6bMaskSourceBinding, source.plannedCommandId, source.copyMaterialDeviceOriginI32())
            is PlanPass.StencilProducer -> PlanPass.StencilProducer(source.ordinal, source.target, source.depthStencil,
                draw(source.draw) as PathDraw, source.drawDataResources, group(source.atomicGroup), source.load, source.store,
                source.depthStencilAccess, source.depthStencilLoadStore)
            is PlanPass.StencilGeometryProducerV3 -> PlanPass.StencilGeometryProducerV3(source.ordinal, source.target,
                source.depthStencil, commandIndexI32, source.copyGeometry(), source.copyScissorI32(),
                source.drawDataResources, group(source.atomicGroup), source.load, source.store)
            is PlanPass.StencilCover -> PlanPass.StencilCover(source.ordinal, source.target, source.depthStencil,
                draw(source.draw) as PathDraw, source.drawDataResources, group(source.atomicGroup), source.load, source.store,
                source.depthStencilAccess, source.depthStencilLoadStore, source.destinationVersionAfter,
                source.coverageSource, source.plannedCommandId)
            is PlanPass.PathRenderPass -> PlanPass.PathRenderPass(source.ordinal, source.target, draw(source.draw) as PathRenderDraw,
                source.phase, source.drawDataResources, source.atomicGroup?.let(::group), source.depthStencil,
                source.load, source.store, source.depthStencilAccess, source.depthStencilLoadStore, source.resolveTarget)
            is PlanPass.PathMaskClearPass -> PlanPass.PathMaskClearPass(source.ordinal, source.target, group(source.atomicGroup))
            // Clip groups index the selected clip stack, not the occurrence command. Keep them and
            // their exact pass objects shared by the geometry lane and its final-color envelope.
            else -> source
        }
    }
}
