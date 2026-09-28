package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.RectI32

/** The sealed native producer form for an inverse Geometry path with integer scan spans. */
public enum class W6InverseMaskPathProducerPipelineV1 { FullscreenNoBindings }
public enum class W6InverseMaskPathProducerStencilV1 { ClearZeroReplaceOne }
public enum class W6InverseMaskPathCoverStencilV1 { TestZero }

/**
 * Final physical recipe for one W6 inverse-mask scan-span pair.  It intentionally freezes
 * a producer and cover as distinct owners: the producer may be Empty while the cover remains
 * the normal fullscreen inverse-mask consumer.
 */
public class W6InverseMaskPathRecipeV1 internal constructor(
    public val producer: GeometryProducer,
    public val cover: GeometryCover,
) {
    public sealed class GeometryProducer protected constructor(
        public val ownerPassId: PlanPassId,
        public val packetOrdinalI32: Int,
        public val target: W4eClipMaskProducerPhysicalOperandV1,
        public val depthStencil: W4eClipMaskProducerPhysicalOperandV1,
        originDeviceI32: Point2I32,
        domainDeviceI32: RectI32,
        domainLocalI32: RectI32,
        scissorsLocalI32: List<RectI32>,
        public val drawCountI32: Int,
    ) {
        private val originSnapshotI32 = Point2I32(originDeviceI32.x, originDeviceI32.y)
        private val deviceDomainSnapshotI32 = domainDeviceI32.copy()
        private val localDomainSnapshotI32 = domainLocalI32.copy()
        private val scissorsSnapshotI32 = java.util.Collections.unmodifiableList(scissorsLocalI32.map { it.copy() })

        public fun copyOriginDeviceI32(): Point2I32 = Point2I32(originSnapshotI32.x, originSnapshotI32.y)
        public fun copyDomainDeviceI32(): RectI32 = deviceDomainSnapshotI32.copy()
        public fun copyDomainLocalI32(): RectI32 = localDomainSnapshotI32.copy()
        public fun copyScissorsLocalI32(): List<RectI32> = scissorsSnapshotI32.map { it.copy() }

        init {
            require(packetOrdinalI32 >= 0 && drawCountI32 >= 0) { "W6 inverse scan-span producer needs a non-negative owner ordinal and draw count." }
            require(target.sampleCountI32 == 1 && PlanResourceUsage.RenderAttachment in target.usages()) {
                "W6 inverse scan-span producer target must be a single-sample render attachment."
            }
            require(depthStencil.role == PlanResourceRole.DepthStencil &&
                depthStencil.format == PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8) &&
                depthStencil.sampleCountI32 == 1 && PlanResourceUsage.DepthStencilAttachment in depthStencil.usages()) {
                "W6 inverse scan-span producer must use a single-sample D24S8 render attachment."
            }
            require(!localDomainSnapshotI32.isEmpty) { "W6 inverse scan-span local domain must be non-empty." }
            require(scissorsSnapshotI32.size == drawCountI32) { "W6 inverse scan-span draw count must equal its scissor count." }
            require(scissorsSnapshotI32.all { scissor ->
                !scissor.isEmpty && scissor.height64() == 1L &&
                    scissor.left >= localDomainSnapshotI32.left && scissor.top >= localDomainSnapshotI32.top &&
                    scissor.right <= localDomainSnapshotI32.right && scissor.bottom <= localDomainSnapshotI32.bottom
            }) { "W6 inverse scan-span scissors must be ordered unit rows inside the sealed local domain." }
        }

        public class NonEmpty internal constructor(
            ownerPassId: PlanPassId,
            packetOrdinalI32: Int,
            target: W4eClipMaskProducerPhysicalOperandV1,
            depthStencil: W4eClipMaskProducerPhysicalOperandV1,
            originDeviceI32: Point2I32,
            domainDeviceI32: RectI32,
            domainLocalI32: RectI32,
            scissorsLocalI32: List<RectI32>,
        ) : GeometryProducer(
            ownerPassId, packetOrdinalI32, target, depthStencil, originDeviceI32, domainDeviceI32,
            domainLocalI32, scissorsLocalI32, scissorsLocalI32.size,
        ) {
            init { require(drawCountI32 > 0) { "NonEmpty W6 inverse scan-span producer must draw at least one row." } }
        }

        public class Empty internal constructor(
            ownerPassId: PlanPassId,
            packetOrdinalI32: Int,
            target: W4eClipMaskProducerPhysicalOperandV1,
            depthStencil: W4eClipMaskProducerPhysicalOperandV1,
            originDeviceI32: Point2I32,
            domainDeviceI32: RectI32,
            domainLocalI32: RectI32,
        ) : GeometryProducer(
            ownerPassId, packetOrdinalI32, target, depthStencil, originDeviceI32, domainDeviceI32,
            domainLocalI32, emptyList(), 0,
        )

        public val pipeline: W6InverseMaskPathProducerPipelineV1 = W6InverseMaskPathProducerPipelineV1.FullscreenNoBindings
        public val stencil: W6InverseMaskPathProducerStencilV1 = W6InverseMaskPathProducerStencilV1.ClearZeroReplaceOne
        public val fullscreenVertexCountI32: Int = 3
        public val hasVertexIndexSlices: Boolean = false
    }

    public class GeometryCover internal constructor(
        public val ownerPassId: PlanPassId,
        public val packetOrdinalI32: Int,
        public val target: W4eClipMaskProducerPhysicalOperandV1,
        public val depthStencil: W4eClipMaskProducerPhysicalOperandV1,
        scissorLocalI32: RectI32,
    ) {
        private val scissorSnapshotI32 = scissorLocalI32.copy()
        public val stencil: W6InverseMaskPathCoverStencilV1 = W6InverseMaskPathCoverStencilV1.TestZero
        public val fullscreenVertexCountI32: Int = 3

        public fun copyScissorLocalI32(): RectI32 = scissorSnapshotI32.copy()

        init {
            require(packetOrdinalI32 >= 0 && !scissorSnapshotI32.isEmpty) { "W6 inverse scan-span cover must have an owner and non-empty scissor." }
            require(target.sampleCountI32 == 1 && target.id != depthStencil.id) { "W6 inverse scan-span cover target must differ from D24S8." }
            require(depthStencil.role == PlanResourceRole.DepthStencil &&
                depthStencil.format == PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8) &&
                depthStencil.sampleCountI32 == 1 && PlanResourceUsage.DepthStencilAttachment in depthStencil.usages()) {
                "W6 inverse scan-span cover must use single-sample D24S8."
            }
        }
    }

    init {
        require(producer.ownerPassId != cover.ownerPassId && producer.target.id == cover.target.id &&
            producer.depthStencil.id == cover.depthStencil.id) {
            "W6 inverse scan-span producer and cover must be distinct owners over the same target and D24S8."
        }
    }
}

/** Versioned catalog entry for the entire producer/cover pair. */
public class W6InverseMaskPathNativeSiteRecipeV1 internal constructor(
    public val host: W6InverseMaskPathRecipeV1,
) : NativeSiteRecipeV1 {
    override val versionI32: Int = 1
    override val owner: NativeSiteOwnerV1 = NativeSiteOwnerV1(
        host.producer.ownerPassId, host.producer.packetOrdinalI32, 0,
    )
    override val family: NativeSiteRecipeFamilyV1 = NativeSiteRecipeFamilyV1.W6InverseMaskPath
    override val canonicalLogicalEncodingV1: String = nativeSiteEncodingV1(family) {
        fun operand(prefix: String, value: W4eClipMaskProducerPhysicalOperandV1) {
            text("$prefix.id", value.id.value); enum("$prefix.role", value.role); text("$prefix.format", value.format.toString())
            value.copyExtentI32()?.let { extent -> int("$prefix.width", extent.width); int("$prefix.height", extent.height) }
            int("$prefix.samples", value.sampleCountI32); long("$prefix.bytes", value.byteSizeI64); enum("$prefix.lifetime", value.lifetime)
            value.usages().sortedBy { it.name }.forEachIndexed { indexI32, usage -> enum("$prefix.usage.$indexI32", usage) }
        }
        val producer = host.producer
        text("producer.owner", producer.ownerPassId.value); int("producer.packet", producer.packetOrdinalI32)
        text("producer.kind", producer::class.simpleName ?: "unknown")
        operand("producer.target", producer.target); operand("producer.depth", producer.depthStencil)
        point("producer.origin", producer.copyOriginDeviceI32()); rect("producer.domain-device", producer.copyDomainDeviceI32())
        rect("producer.domain-local", producer.copyDomainLocalI32()); int("producer.draw-count", producer.drawCountI32)
        producer.copyScissorsLocalI32().forEachIndexed { indexI32, scissor -> rect("producer.scissor.$indexI32", scissor) }
        enum("producer.pipeline", producer.pipeline); enum("producer.stencil", producer.stencil)
        int("producer.vertices", producer.fullscreenVertexCountI32); int("producer.has-vi", if (producer.hasVertexIndexSlices) 1 else 0)
        val cover = host.cover
        text("cover.owner", cover.ownerPassId.value); int("cover.packet", cover.packetOrdinalI32)
        operand("cover.target", cover.target); operand("cover.depth", cover.depthStencil); rect("cover.scissor", cover.copyScissorLocalI32())
        enum("cover.stencil", cover.stencil); int("cover.vertices", cover.fullscreenVertexCountI32)
    }
}

private fun scanSpanPhysicalOperandV1(row: PlanResource): W4eClipMaskProducerPhysicalOperandV1 =
    W4eClipMaskProducerPhysicalOperandV1(
        row.id, row.role, row.format, row.copyExtent(), row.sampleCountI32, row.byteSize, row.lifetime, row.usages(),
    )

/** Freezes final W6 scan-span occurrences, never source identities. */
public fun freezeW6InverseMaskPathRecipesV1(
    passes: List<PlanPass>,
    resources: List<PlanResource>,
): Map<PlanPassId, W6InverseMaskPathRecipeV1> {
    val rows = resources.associateBy { it.id }
    val recipes = linkedMapOf<PlanPassId, W6InverseMaskPathRecipeV1>()
    passes.forEachIndexed { indexI32, raw ->
        val producer = raw as? PlanPass.StencilGeometryProducerV3 ?: return@forEachIndexed
        val spans = producer.scanSpansDeviceI32 ?: return@forEachIndexed
        val scissors = requireNotNull(producer.scanScissorsLocalI32)
        val cover = passes.getOrNull(indexI32 + 1) as? PlanPass.StencilCover ?: error(
            "W6 inverse scan-span producer must be followed by its cover.",
        )
        require(cover.target == producer.target && cover.depthStencil == producer.depthStencil) {
            "W6 inverse scan-span producer and following cover must share target and D24S8."
        }
        val target = scanSpanPhysicalOperandV1(rows.getValue(producer.target))
        val depthStencil = scanSpanPhysicalOperandV1(rows.getValue(producer.depthStencil))
        val producerRecipe = if (spans.spanCountI32 == 0) {
            W6InverseMaskPathRecipeV1.GeometryProducer.Empty(
                producer.id, producer.ordinal, target, depthStencil, scissors.copyOriginDeviceI32(),
                spans.copyDomainI32(), scissors.copyDomainI32(),
            )
        } else {
            W6InverseMaskPathRecipeV1.GeometryProducer.NonEmpty(
                producer.id, producer.ordinal, target, depthStencil, scissors.copyOriginDeviceI32(),
                spans.copyDomainI32(), scissors.copyDomainI32(), scissors.copyScissorsI32(),
            )
        }
        val coverRecipe = W6InverseMaskPathRecipeV1.GeometryCover(
            cover.id, cover.ordinal, target, depthStencil, cover.draw.copyScissorI32(),
        )
        require(recipes.put(producer.id, W6InverseMaskPathRecipeV1(producerRecipe, coverRecipe)) == null)
    }
    return java.util.Collections.unmodifiableMap(recipes)
}
