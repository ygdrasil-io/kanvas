package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.FillRule
import org.graphiks.math.geometry.InverseInteriorCoverageF32
import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.RectI32

/** The final W4e native site is either the finite inverse interior or its color cover. */
public enum class W6InverseMaskPathPredicateV1 { Geometry, Zero }
public enum class W6InverseMaskPathProducerPipelineV1 { FullscreenNoBindings, FanGeometry }
public enum class W6InverseMaskPathProducerStencilV1 { ClearZeroReplaceOne, ClearZeroWinding, ClearZeroEvenOdd }
public enum class W6InverseMaskPathCoverStencilV1 { TestZeroKeep }
public enum class W6InverseMaskPathGroupZeroAbiV1 { NoBindings, MaskTextureThenConsumerUniform }

/** One physical native W4e site; W6 IDs prove binding but never select the pipeline. */
public sealed class W6InverseMaskPathRecipeV1 protected constructor(
    public val ownerPassId: PlanPassId,
    public val packetOrdinalI32: Int,
    public val predicate: W6InverseMaskPathPredicateV1,
    public val target: W4eClipMaskProducerPhysicalOperandV1,
    public val load: AttachmentLoadPlan,
    public val store: AttachmentStorePlan,
    public val blend: BlendPlan,
) {
    public val owner: NativeSiteOwnerV1 get() = NativeSiteOwnerV1(ownerPassId, packetOrdinalI32, 0)
    init {
        require(packetOrdinalI32 >= 0 && target.sampleCountI32 == 1 &&
            PlanResourceUsage.RenderAttachment in target.usages())
    }

    public sealed class GeometryProducer protected constructor(
        ownerPassId: PlanPassId,
        packetOrdinalI32: Int,
        target: W4eClipMaskProducerPhysicalOperandV1,
        public val depthStencil: W4eClipMaskProducerPhysicalOperandV1,
        public val vertex: W4eClipMaskProducerPhysicalOperandV1?,
        public val index: W4eClipMaskProducerPhysicalOperandV1?,
        load: AttachmentLoadPlan,
        store: AttachmentStorePlan,
        public val stencil: W6InverseMaskPathProducerStencilV1,
        public val pipeline: W6InverseMaskPathProducerPipelineV1,
    ) : W6InverseMaskPathRecipeV1(ownerPassId, packetOrdinalI32, W6InverseMaskPathPredicateV1.Geometry,
        target, load, store, BlendPlan.LegacySrcOverV1) {
        init {
            require(depthStencil.role == PlanResourceRole.DepthStencil &&
                depthStencil.format == PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8) &&
                depthStencil.sampleCountI32 == 1 && PlanResourceUsage.DepthStencilAttachment in depthStencil.usages())
            require(target.sampleCountI32 == 1 && PlanResourceUsage.RenderAttachment in target.usages())
        }

        public sealed class ScanSpans protected constructor(
            ownerPassId: PlanPassId,
            packetOrdinalI32: Int,
            target: W4eClipMaskProducerPhysicalOperandV1,
            depthStencil: W4eClipMaskProducerPhysicalOperandV1,
            originDeviceI32: Point2I32,
            domainDeviceI32: RectI32,
            domainLocalI32: RectI32,
            scissorsLocalI32: List<RectI32>,
            load: AttachmentLoadPlan,
            store: AttachmentStorePlan,
        ) : GeometryProducer(ownerPassId, packetOrdinalI32, target, depthStencil, null, null,
            load, store,
            W6InverseMaskPathProducerStencilV1.ClearZeroReplaceOne,
            W6InverseMaskPathProducerPipelineV1.FullscreenNoBindings) {
            private val origin = Point2I32(originDeviceI32.x, originDeviceI32.y)
            private val deviceDomain = domainDeviceI32.copy()
            private val localDomain = domainLocalI32.copy()
            private val scissors = java.util.Collections.unmodifiableList(scissorsLocalI32.map { it.copy() })
            public fun copyOriginDeviceI32(): Point2I32 = Point2I32(origin.x, origin.y)
            public fun copyDomainDeviceI32(): RectI32 = deviceDomain.copy()
            public fun copyDomainLocalI32(): RectI32 = localDomain.copy()
            public fun copyScissorsLocalI32(): List<RectI32> = scissors.map { it.copy() }
            public val drawCountI32: Int get() = scissors.size
            public val fullscreenVertexCountI32: Int = 3
            public val hasVertexIndexSlices: Boolean = false
            init {
                require(!localDomain.isEmpty && scissors.all { value -> !value.isEmpty && value.height64() == 1L &&
                    value.left >= localDomain.left && value.top >= localDomain.top &&
                    value.right <= localDomain.right && value.bottom <= localDomain.bottom })
            }

            public class NonEmpty internal constructor(
                ownerPassId: PlanPassId, packetOrdinalI32: Int, target: W4eClipMaskProducerPhysicalOperandV1,
                depthStencil: W4eClipMaskProducerPhysicalOperandV1, originDeviceI32: Point2I32,
                domainDeviceI32: RectI32, domainLocalI32: RectI32, scissorsLocalI32: List<RectI32>, load: AttachmentLoadPlan,
                store: AttachmentStorePlan,
            ) : ScanSpans(ownerPassId, packetOrdinalI32, target, depthStencil, originDeviceI32, domainDeviceI32,
                domainLocalI32, scissorsLocalI32, load, store) { init { require(drawCountI32 > 0) } }

            public class Empty internal constructor(
                ownerPassId: PlanPassId, packetOrdinalI32: Int, target: W4eClipMaskProducerPhysicalOperandV1,
                depthStencil: W4eClipMaskProducerPhysicalOperandV1, originDeviceI32: Point2I32,
                domainDeviceI32: RectI32, domainLocalI32: RectI32, load: AttachmentLoadPlan, store: AttachmentStorePlan,
            ) : ScanSpans(ownerPassId, packetOrdinalI32, target, depthStencil, originDeviceI32, domainDeviceI32,
                domainLocalI32, emptyList(), load, store)
        }

        public class Fan internal constructor(
            ownerPassId: PlanPassId, packetOrdinalI32: Int, target: W4eClipMaskProducerPhysicalOperandV1,
            depthStencil: W4eClipMaskProducerPhysicalOperandV1, vertex: W4eClipMaskProducerPhysicalOperandV1,
            index: W4eClipMaskProducerPhysicalOperandV1, public val geometrySlice: W4eNativeGeometrySlice,
            public val fillRule: FillRule, scissorI32: RectI32, load: AttachmentLoadPlan, store: AttachmentStorePlan,
        ) : GeometryProducer(ownerPassId, packetOrdinalI32, target, depthStencil, vertex, index, load, store,
            if (fillRule in setOf(FillRule.EVEN_ODD, FillRule.INVERSE_EVEN_ODD))
                W6InverseMaskPathProducerStencilV1.ClearZeroEvenOdd else W6InverseMaskPathProducerStencilV1.ClearZeroWinding,
            W6InverseMaskPathProducerPipelineV1.FanGeometry) {
            private val scissor = scissorI32.copy()
            public fun copyScissorI32(): RectI32 = scissor.copy()
            init {
                require(geometrySlice.purpose == W4eNativePayloadPlan.STENCIL_PRODUCER && !scissor.isEmpty)
                require(vertex.role == PlanResourceRole.VertexData && PlanResourceUsage.Vertex in vertex.usages())
                require(index.role == PlanResourceRole.IndexData && PlanResourceUsage.Index in index.usages())
            }
        }
    }

    public class GeometryCover internal constructor(
        ownerPassId: PlanPassId, packetOrdinalI32: Int, target: W4eClipMaskProducerPhysicalOperandV1,
        public val depthStencil: W4eClipMaskProducerPhysicalOperandV1, public val mask: W4eClipMaskProducerPhysicalOperandV1,
        public val uniform: W4eClipMaskProducerPhysicalOperandV1, public val uniformSlice: W4eNativeUniformSlice,
        domainI32: RectI32, load: AttachmentLoadPlan, store: AttachmentStorePlan, blend: BlendPlan,
    ) : W6InverseMaskPathRecipeV1(ownerPassId, packetOrdinalI32, W6InverseMaskPathPredicateV1.Geometry,
        target, load, store, blend) {
        private val domain = domainI32.copy()
        public val stencil: W6InverseMaskPathCoverStencilV1 = W6InverseMaskPathCoverStencilV1.TestZeroKeep
        public val groupZeroAbi: W6InverseMaskPathGroupZeroAbiV1 = W6InverseMaskPathGroupZeroAbiV1.MaskTextureThenConsumerUniform
        public val fullscreenVertexCountI32: Int = 3
        public fun copyDomainI32(): RectI32 = domain.copy()
        init {
            require(!domain.isEmpty && uniformSlice.purpose == W4eNativePayloadPlan.STENCIL_COVER_UNIFORM &&
                uniformSlice.byteSize == 32L && mask.format == PlanTextureFormat.CoverageMask &&
                PlanResourceUsage.Sampled in mask.usages() && uniform.role == PlanResourceRole.UniformData &&
                PlanResourceUsage.Uniform in uniform.usages() && depthStencil.role == PlanResourceRole.DepthStencil &&
                depthStencil.format == PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8) &&
                PlanResourceUsage.DepthStencilAttachment in depthStencil.usages())
        }
    }

    public class ZeroCover internal constructor(
        ownerPassId: PlanPassId, packetOrdinalI32: Int, target: W4eClipMaskProducerPhysicalOperandV1,
        public val mask: W4eClipMaskProducerPhysicalOperandV1, public val uniform: W4eClipMaskProducerPhysicalOperandV1,
        public val uniformSlice: W4eNativeUniformSlice, domainI32: RectI32, load: AttachmentLoadPlan,
        store: AttachmentStorePlan, blend: BlendPlan,
    ) : W6InverseMaskPathRecipeV1(ownerPassId, packetOrdinalI32, W6InverseMaskPathPredicateV1.Zero,
        target, load, store, blend) {
        private val domain = domainI32.copy()
        public val groupZeroAbi: W6InverseMaskPathGroupZeroAbiV1 = W6InverseMaskPathGroupZeroAbiV1.MaskTextureThenConsumerUniform
        public val fullscreenVertexCountI32: Int = 3
        public fun copyDomainI32(): RectI32 = domain.copy()
        init {
            require(!domain.isEmpty && uniformSlice.purpose == W4eNativePayloadPlan.CONSUMER_UNIFORM &&
                uniformSlice.byteSize == 32L && mask.format == PlanTextureFormat.CoverageMask &&
                PlanResourceUsage.Sampled in mask.usages() && uniform.role == PlanResourceRole.UniformData &&
                PlanResourceUsage.Uniform in uniform.usages())
        }
    }
}

/** One catalog entry per native W4e path pass, never one wrapper for a W6 proxy pair. */
public class W6InverseMaskPathNativeSiteRecipeV1 internal constructor(public val host: W6InverseMaskPathRecipeV1) : NativeSiteRecipeV1 {
    override val versionI32: Int = 1
    override val owner: NativeSiteOwnerV1 = host.owner
    override val family: NativeSiteRecipeFamilyV1 = when (host) {
        is W6InverseMaskPathRecipeV1.GeometryProducer -> NativeSiteRecipeFamilyV1.W6InverseMaskGeometryProducer
        is W6InverseMaskPathRecipeV1.GeometryCover -> NativeSiteRecipeFamilyV1.W6InverseMaskGeometryCover
        is W6InverseMaskPathRecipeV1.ZeroCover -> NativeSiteRecipeFamilyV1.W6InverseMaskZeroCover
    }
    override val canonicalLogicalEncodingV1: String = nativeSiteEncodingV1(family) {
        fun operand(prefix: String, value: W4eClipMaskProducerPhysicalOperandV1) {
            text("$prefix.id", value.id.value); enum("$prefix.role", value.role); text("$prefix.format", value.format.toString())
            value.copyExtentI32()?.let { extent -> int("$prefix.width", extent.width); int("$prefix.height", extent.height) }
            int("$prefix.samples", value.sampleCountI32); long("$prefix.bytes", value.byteSizeI64); enum("$prefix.lifetime", value.lifetime)
            value.usages().sortedBy { it.name }.forEachIndexed { index, usage -> enum("$prefix.usage.$index", usage) }
        }
        text("owner", host.ownerPassId.value); int("packet", host.packetOrdinalI32); enum("predicate", host.predicate)
        operand("target", host.target); enum("load", host.load); enum("store", host.store); blend("blend", host.blend)
        when (host) {
            is W6InverseMaskPathRecipeV1.GeometryProducer.ScanSpans -> {
                operand("depth", host.depthStencil); enum("pipeline", host.pipeline); enum("stencil", host.stencil)
                point("origin", host.copyOriginDeviceI32()); rect("domain.device", host.copyDomainDeviceI32()); rect("domain.local", host.copyDomainLocalI32())
                int("draw.count", host.drawCountI32); int("vertices", host.fullscreenVertexCountI32); int("has-vi", 0)
                host.copyScissorsLocalI32().forEachIndexed { index, scissor -> rect("scissor.$index", scissor) }
            }
            is W6InverseMaskPathRecipeV1.GeometryProducer.Fan -> {
                operand("depth", host.depthStencil); operand("vertex", requireNotNull(host.vertex)); operand("index", requireNotNull(host.index))
                enum("pipeline", host.pipeline); enum("stencil", host.stencil); enum("fill", host.fillRule); rect("scissor", host.copyScissorI32())
                int("vertex.first", host.geometrySlice.baseVertex); int("vertex.count", host.geometrySlice.vertexCount)
                int("index.first", host.geometrySlice.firstIndex); int("index.count", host.geometrySlice.indexCount); int("max-index", host.geometrySlice.maxLocalIndex)
            }
            is W6InverseMaskPathRecipeV1.GeometryCover -> {
                operand("depth", host.depthStencil); operand("mask", host.mask); operand("uniform", host.uniform); rect("domain", host.copyDomainI32())
                long("uniform.offset", host.uniformSlice.offsetBytes); long("uniform.bytes", host.uniformSlice.byteSize)
                enum("abi", host.groupZeroAbi); enum("stencil", host.stencil); int("vertices", host.fullscreenVertexCountI32)
            }
            is W6InverseMaskPathRecipeV1.ZeroCover -> {
                operand("mask", host.mask); operand("uniform", host.uniform); rect("domain", host.copyDomainI32())
                long("uniform.offset", host.uniformSlice.offsetBytes); long("uniform.bytes", host.uniformSlice.byteSize)
                enum("abi", host.groupZeroAbi); int("vertices", host.fullscreenVertexCountI32); text("depth.present", "false"); text("vi.present", "false")
            }
        }
    }
}

private fun inverseMaskOperandV1(row: PlanResource): W4eClipMaskProducerPhysicalOperandV1 =
    W4eClipMaskProducerPhysicalOperandV1(row.id, row.role, row.format, row.copyExtent(), row.sampleCountI32, row.byteSize, row.lifetime, row.usages())

private fun PathRenderDraw.inverseMaskOrNullV1(): ClipPlanStrategy.InverseMask? = when (this) {
    is ClippedGeneralPathDraw -> clip as? ClipPlanStrategy.InverseMask
    is ClippedBinaryMaskedPathDraw -> clip as? ClipPlanStrategy.InverseMask
    is GeneralPathDraw, is BinaryMaskedPathDraw -> null
}

/** Freezes every published native W4e inverse-mask path site, including fan and Zero. */
public fun freezeW6InverseMaskPathRecipesV1(
    bindings: List<PlanW4eGeometryBindingV1>, resources: List<PlanResource>,
): Map<PlanPassId, W6InverseMaskPathRecipeV1> {
    val rows = resources.associateBy { it.id }
    val result = linkedMapOf<PlanPassId, W6InverseMaskPathRecipeV1>()
    fun add(value: W6InverseMaskPathRecipeV1) {
        require(result.put(value.ownerPassId, value) == null) { "One native W4e inverse-mask owner may publish only one recipe." }
    }
    bindings.forEach { binding -> binding.nativePasses().forEach { candidate ->
        val pass = candidate as? PlanPass.PathRenderPass ?: return@forEach
        val inverse = pass.draw.inverseMaskOrNullV1() ?: return@forEach
        val target = inverseMaskOperandV1(rows.getValue(pass.target))
        val mask = inverseMaskOperandV1(rows.getValue(inverse.resource))
        val uniform = inverseMaskOperandV1(rows.getValue(binding.payload.uniformResourceId))
        when (val interior = inverse.geometryF32.interiorCoverageF32) {
            is InverseInteriorCoverageF32.Geometry -> when (pass.phase) {
                PathRenderPhase.SingleSampleStencilProducer -> {
                    val depth = inverseMaskOperandV1(rows.getValue(requireNotNull(pass.depthStencil)))
                    val spans = pass.scanSpansDeviceI32
                    if (spans != null) {
                        val local = requireNotNull(spans.localScissorsI32OrNull(binding.copyMaterialDeviceOriginI32(), binding.copyExtentI32()))
                        add(if (spans.spanCountI32 == 0)
                            W6InverseMaskPathRecipeV1.GeometryProducer.ScanSpans.Empty(pass.id, pass.ordinal, target, depth,
                                local.copyOriginDeviceI32(), spans.copyDomainI32(), local.copyDomainI32(), pass.load, pass.store)
                        else W6InverseMaskPathRecipeV1.GeometryProducer.ScanSpans.NonEmpty(pass.id, pass.ordinal, target, depth,
                            local.copyOriginDeviceI32(), spans.copyDomainI32(), local.copyDomainI32(), local.copyScissorsI32(), pass.load, pass.store))
                    } else {
                        val geometry = interior.copyGeometryF32()
                        if (geometry.copyStencilEdgeFanF32OrNull() != null) {
                            val slice = requireNotNull(binding.payload.geometrySlice(pass.id.value, W4eNativePayloadPlan.STENCIL_PRODUCER))
                            add(W6InverseMaskPathRecipeV1.GeometryProducer.Fan(pass.id, pass.ordinal, target, depth,
                                inverseMaskOperandV1(rows.getValue(binding.payload.vertexResourceId)),
                                inverseMaskOperandV1(rows.getValue(binding.payload.indexResourceId)), slice, geometry.fillRule,
                                pass.draw.copyScissorI32(), pass.load, pass.store))
                        }
                    }
                }
                PathRenderPhase.SingleSampleStencilColorCover -> {
                    val slice = requireNotNull(binding.payload.uniformSlice(pass.id.value, W4eNativePayloadPlan.STENCIL_COVER_UNIFORM))
                    add(W6InverseMaskPathRecipeV1.GeometryCover(pass.id, pass.ordinal, target,
                        inverseMaskOperandV1(rows.getValue(requireNotNull(pass.depthStencil))), mask, uniform, slice,
                        pass.draw.copyScissorI32(), pass.load, pass.store, pass.draw.blend))
                }
                else -> Unit
            }
            InverseInteriorCoverageF32.Zero -> if (pass.phase == PathRenderPhase.SingleSampleDirectColor &&
                pass.draw.copyPathGeometry() == PathDrawGeometry.Empty) {
                val slice = requireNotNull(binding.payload.uniformSlice(pass.id.value, W4eNativePayloadPlan.CONSUMER_UNIFORM))
                add(W6InverseMaskPathRecipeV1.ZeroCover(pass.id, pass.ordinal, target, mask, uniform, slice,
                    pass.draw.copyScissorI32(), pass.load, pass.store, pass.draw.blend))
            }
        }
    } }
    return java.util.Collections.unmodifiableMap(LinkedHashMap(result))
}
