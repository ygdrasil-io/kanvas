package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.*
import org.graphiks.math.matrix.*

/** A selected W4e native lane joined to the enclosing graph's pass IDs and physical slots. */
public class PlanW4eGeometryBindingV1 internal constructor(
    public val target: PlanResourceId,
    extent: SizeI32,
    nativePassesByGraphPass: Map<PlanPassId, PlanPass>,
    public val payload: W4eNativePayloadPlan,
    materialDeviceOriginI32: Point2I32,
) {
    private val extent = extent.copy()
    private val native = java.util.Collections.unmodifiableMap(LinkedHashMap(nativePassesByGraphPass))
    private val materialDeviceOriginSnapshotI32 = Point2I32(materialDeviceOriginI32.x, materialDeviceOriginI32.y)
    public fun copyExtentI32(): SizeI32 = extent.copy()
    public fun copyMaterialDeviceOriginI32(): Point2I32 = Point2I32(
        materialDeviceOriginSnapshotI32.x,
        materialDeviceOriginSnapshotI32.y,
    )
    public fun nativePasses(): List<PlanPass> = immutableList(native.values.toList())
    public fun graphPassIds(): Set<PlanPassId> = immutableSet(native.keys)
    public fun nativePass(graphPassId: PlanPassId): PlanPass? = native[graphPassId]
    internal fun bindSources(draws: Map<Int, PlanDraw>): PlanW4eGeometryBindingV1 = PlanW4eGeometryBindingV1(target, extent,
        native.mapValues { (_, pass) -> if (pass is PlanPass.PathRenderPass)
            pass.rebindW4eV6(pass.ordinal, { it }, null, null, draws.getValue(pass.draw.commandIndex).materialAuthority)
            else pass }, payload, materialDeviceOriginSnapshotI32)
}

/** Rebind only identity, target coordinates and issued source; W4e's selected strategy is retained. */
internal fun PlanPass.rebindW4eV6(ordinalI32: Int, resource: (PlanResourceId) -> PlanResourceId,
    mapping: LayerMappingF64?, targetDomainI32: RectI32?, material: PlanDrawMaterialAuthority? = null): PlanPass {
    fun domain(value: RectI32): RectI32 = if (mapping == null) value else requireNotNull(
        mapping.mapDeviceDomainToLayerI32OrNull(value, requireNotNull(targetDomainI32)))
    fun geometry(value: PathDrawGeometry): PathDrawGeometry = if (mapping == null) value else when (value) {
        is PathDrawGeometry.Fill -> PathDrawGeometry.Fill(requireNotNull(value.valueF32.relativeToOriginI32OrNull(mapping.copyLayerOriginDeviceI32())))
        is PathDrawGeometry.Stroke -> PathDrawGeometry.Stroke(requireNotNull(value.valueF32.relativeToOriginI32OrNull(mapping.copyLayerOriginDeviceI32())))
        is PathDrawGeometry.InverseDomainSource -> PathDrawGeometry.InverseDomainSource.of(value.copySourcePath(),
            requireNotNull(value.copySourceTransform().relativeToOriginI32OrNull(mapping.copyLayerOriginDeviceI32())))
        PathDrawGeometry.Empty -> value
    }
    fun clip(value: ClipPlanStrategy): ClipPlanStrategy = when (value) {
        is ClipPlanStrategy.Scissor -> ClipPlanStrategy.Scissor(domain(value.copyDomainI32()), value.child?.let(::clip))
        is ClipPlanStrategy.Stencil -> ClipPlanStrategy.Stencil(resource(value.depthStencil), value.child?.let(::clip))
        is ClipPlanStrategy.Mask -> ClipPlanStrategy.Mask(resource(value.resource))
        is ClipPlanStrategy.InverseMask -> ClipPlanStrategy.InverseMask(if (mapping == null) value.geometryF32 else
            requireNotNull(mapping.mapDeviceInverseToLayerF32OrNull(value.geometryF32, requireNotNull(targetDomainI32))), resource(value.resource))
        is ClipPlanStrategy.InverseDomain -> ClipPlanStrategy.InverseDomain(if (mapping == null) value.geometryF32 else
            requireNotNull(mapping.mapDeviceInverseToLayerF32OrNull(value.geometryF32, requireNotNull(targetDomainI32))))
    }
    fun general(value: GeneralPathDraw): GeneralPathDraw = value.rebindGeometryV6(geometry(value.copyPathGeometry()),
        domain(value.copyScissorI32()), material ?: value.materialAuthority)
    fun path(value: PathRenderDraw): PathRenderDraw = when (value) {
        is GeneralPathDraw -> general(value)
        is ClippedGeneralPathDraw -> ClippedGeneralPathDraw.of(general(value.source), clip(value.clip))
        is BinaryMaskedPathDraw -> BinaryMaskedPathDraw.of(general(value.producer), resource(value.mask))
        is ClippedBinaryMaskedPathDraw -> ClippedBinaryMaskedPathDraw.of(
            BinaryMaskedPathDraw.of(general(value.source.producer), resource(value.source.mask)), clip(value.clip))
    }
    return when (this) {
        is PlanPass.ClipMaskInitialize -> PlanPass.ClipMaskInitialize(ordinalI32, resource(output), domain(copyDomainI32()), clearCoverageF32, atomicGroup)
        is PlanPass.ClipMaskProducer -> PlanPass.ClipMaskProducer(ordinalI32, resource(target), resolveTarget?.let(resource),
            depthStencil?.let(resource), sampleCountI32, if (mapping == null) copyGeometryF32() else
                requireNotNull(mapping.mapDeviceClipToLayerF32OrNull(copyGeometryF32())), atomicGroup, inverseCoverage, antiAlias)
        is PlanPass.ClipMaskFold -> PlanPass.ClipMaskFold(ordinalI32, resource(previous), resource(source), resource(output),
            operation, domain(copyDomainI32()), atomicGroup)
        is PlanPass.PathRenderPass -> PlanPass.PathRenderPass(ordinalI32, resource(target), path(draw), phase,
            PlanDrawDataResources(resource(drawDataResources.vertex), resource(drawDataResources.index), resource(drawDataResources.uniform)),
            atomicGroup, depthStencil?.let(resource), AttachmentLoadPlan.Load, store, depthStencilAccess, depthStencilLoadStore, resolveTarget?.let(resource))
        else -> error("Not a selected W4e native pass")
    }
}
