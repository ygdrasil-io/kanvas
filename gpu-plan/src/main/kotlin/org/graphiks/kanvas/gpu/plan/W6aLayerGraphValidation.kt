package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.SizeI32

/** Per-target initialization, ordering, attachment/sampling and restore validation at freeze. */
internal fun validateW6aLayerTopology(
    resources: List<PlanResource>,
    passes: List<PlanPass>,
    dependencies: List<PlanPassDependency>,
    extent: SizeI32,
    visualCountI32: Int,
    copyRowAlignmentI32: Int,
) {
    val byId = resources.associateBy { it.id }
    val root = resources.single { it.role == PlanResourceRole.LogicalTarget }
    require(root.copyExtent() == extent)
    val layers = resources.filter { it.role == PlanResourceRole.LayerTarget }
    val initialized = mutableSetOf<PlanResourceId>()
    val restored = mutableSetOf<PlanResourceId>()
    val commands = mutableListOf<Int>()
    val versions = mutableMapOf<PlanResourceId, Long>()

    passes.forEachIndexed { indexI32, pass -> when (pass) {
        is PlanPass.RenderPass -> {
            val target = byId.getValue(pass.target)
            require(target.role in setOf(PlanResourceRole.LogicalTarget, PlanResourceRole.LayerTarget))
            require(target.id !in restored && target.sampleCountI32 == 1 && PlanResourceUsage.RenderAttachment in target.usages())
            require(pass.load == if (initialized.add(target.id)) AttachmentLoadPlan.ClearTransparent else AttachmentLoadPlan.Load)
            require(pass.store == AttachmentStorePlan.Store)
            val targetExtent = requireNotNull(target.copyExtent())
            pass.draws().forEach { draw ->
                require(draw is SolidRectDraw && draw.sample == SamplePlan.SingleSample && draw.blend !is BlendPlan.DestinationReadV1)
                val bounds = draw.copyScissor()
                require(bounds.left >= 0 && bounds.top >= 0 && bounds.right <= targetExtent.width && bounds.bottom <= targetExtent.height)
                commands += draw.commandIndex
            }
            val after = Math.addExact(versions[target.id] ?: 0L, pass.draws().size.toLong())
            versions[target.id] = after
            require(pass.destinationVersionAfter?.valueI64 == after)
        }
        is PlanPass.LayerComposite -> {
            val source = byId.getValue(pass.source)
            val target = byId.getValue(pass.destination)
            require(source.role == PlanResourceRole.LayerTarget && target.role in setOf(PlanResourceRole.LogicalTarget, PlanResourceRole.LayerTarget))
            require(source.id != target.id && source.id in initialized && target.id in initialized)
            require(restored.add(source.id) && PlanResourceUsage.Sampled in source.usages())
            val sourceExtent = requireNotNull(source.copyExtent())
            val destinationExtent = requireNotNull(target.copyExtent())
            val bounds = pass.copySourceBoundsLayerI32()
            val origin = pass.copyDestinationOriginParentI32()
            require(bounds.left >= 0 && bounds.top >= 0 && bounds.right <= sourceExtent.width && bounds.bottom <= sourceExtent.height)
            require(origin.x >= 0 && origin.y >= 0 && origin.x.toLong() + bounds.width() <= destinationExtent.width &&
                origin.y.toLong() + bounds.height() <= destinationExtent.height)
            require(pass.load == AttachmentLoadPlan.Load && pass.store == AttachmentStorePlan.Store)
            require(pass.restore.alphaF32.isFinite())
            require(pass.restore.readsPriorDevice == pass.restore.blend.compositionFacts.readsPriorDevice)
            require(pass.restore.writesParentDevice == pass.restore.blend.compositionFacts.writesParentDevice)
            require(pass.restore.restoreAffectsTransparentBlack ==
                pass.restore.blend.finalRestoreAffectsTransparentBlackV1(pass.restore.colorFilter))
            require((pass.restore.colorFilter == null) == (pass.restore.colorFilterUniformOffsetI64 == null))
            require(pass.restore.parentVersionBefore.valueI64 == versions[target.id])
            if (pass.restore.writesParentDevice) versions[target.id] = Math.addExact(requireNotNull(versions[target.id]), 1L)
            require(pass.destinationVersionAfter == pass.restore.parentVersionAfter && pass.destinationVersionAfter.valueI64 == versions[target.id])
        }
        is PlanPass.TextureCopy -> {
            val source = byId.getValue(pass.source)
            val destination = byId.getValue(pass.destination)
            require(source.role in setOf(PlanResourceRole.LogicalTarget, PlanResourceRole.LayerTarget) &&
                destination.role == PlanResourceRole.DestinationSnapshot)
            require(source.id in initialized && PlanResourceUsage.CopySource in source.usages() &&
                PlanResourceUsage.CopyDestination in destination.usages())
            val sourceExtent = requireNotNull(source.copyExtent())
            require(pass.copySourceBoundsI32() == RectI32(0, 0, sourceExtent.width, sourceExtent.height))
            require(pass.copyDestinationOriginI32() == Point2I32.Origin && destination.copyExtent() == sourceExtent)
            require(pass.destinationVersion?.valueI64 == versions[source.id])
            val consumer = passes.getOrNull(indexI32 + 1) as? PlanPass.LayerComposite
            require(consumer?.destination == source.id && consumer.restore.readsPriorDevice)
            val blend = requireNotNull(consumer.restore.blend.takeIf { it.compositionFacts.readsPriorDevice })
            require(blend.destinationReadSnapshotResourceV1() == destination.id &&
                blend.requiredDestinationVersionV1() == pass.destinationVersion)
        }
        is PlanPass.ReadbackPass -> {
            require(pass === passes.last() && pass.source == root.id)
            require(byId.getValue(pass.staging).role == PlanResourceRole.ReadbackStaging)
            require(pass.bytesPerRow % copyRowAlignmentI32 == 0L && pass.bytesPerRow >= extent.width.toLong() * 4L &&
                byId.getValue(pass.staging).byteSize == Math.multiplyExact(pass.bytesPerRow, extent.height.toLong()))
            require(pass.mappedBytesI64 == Math.addExact(Math.multiplyExact(pass.bytesPerRow, (extent.height - 1).toLong()), extent.width.toLong() * 4L))
        }
        else -> error("w6a.layer.unsupported_child")
    } }
    require(restored == layers.map { it.id }.toSet())
    require(commands.size == visualCountI32 && commands.zipWithNext().all { (a, b) -> a < b })
    require(passes.filterIsInstance<PlanPass.ReadbackPass>().size == 1)
    require(dependencies == passes.zipWithNext { first, second -> PlanPassDependency(first.id, second.id) })
}
