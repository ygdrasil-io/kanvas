package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.SizeI32

/** Per-target initialization, ordering and restore validation at the publication boundary. */
internal fun validateW6aLayerTopology(resources: List<PlanResource>, passes: List<PlanPass>,
    dependencies: List<PlanPassDependency>, extent: SizeI32, visualCountI32: Int, copyRowAlignmentI32: Int) {
    val byId = resources.associateBy { it.id }
    val root = resources.single { it.role == PlanResourceRole.LogicalTarget }
    require(root.copyExtent() == extent)
    val layers = resources.filter { it.role == PlanResourceRole.LayerTarget }
    // A proven-empty explicit restore clip owns no target or layer pass. Root draws before or
    // after that scope remain valid and still flow through the same W6a graph.
    val initialized = mutableSetOf<PlanResourceId>()
    val restored = mutableSetOf<PlanResourceId>()
    val commands = mutableListOf<Int>()
    val versions = mutableMapOf<PlanResourceId, Long>()
    passes.forEach { pass -> when (pass) {
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
            versions[target.id] = Math.addExact(versions[target.id] ?: 0L, pass.draws().size.toLong())
            if (target.id == root.id) require(pass.destinationVersionAfter?.valueI64 == versions[target.id])
        }
        is PlanPass.LayerComposite -> {
            val source = byId.getValue(pass.source)
            val target = byId.getValue(pass.destination)
            require(source.role == PlanResourceRole.LayerTarget && target === root && source.id in initialized && target.id in initialized)
            require(restored.add(source.id) && PlanResourceUsage.Sampled in source.usages())
            val sourceExtent = requireNotNull(source.copyExtent())
            val bounds = pass.copySourceBoundsLayerI32()
            val origin = pass.copyDestinationOriginParentI32()
            require(bounds.left >= 0 && bounds.top >= 0 && bounds.right <= sourceExtent.width && bounds.bottom <= sourceExtent.height)
            require(origin.x >= 0 && origin.y >= 0 && origin.x.toLong() + bounds.width() <= extent.width && origin.y.toLong() + bounds.height() <= extent.height)
            require(pass.load == AttachmentLoadPlan.Load && pass.store == AttachmentStorePlan.Store)
            require(pass.restore.alphaF32 == 1f && pass.restore.colorFilter == null && pass.restore.blend == BlendPlan.LegacySrcOverV1)
            require(!pass.restore.readsPriorDevice && !pass.restore.restoreAffectsTransparentBlack)
            require(pass.restore.parentVersionBefore.valueI64 == versions[target.id])
            versions[target.id] = Math.addExact(requireNotNull(versions[target.id]), 1L)
            require(pass.destinationVersionAfter == pass.restore.parentVersionAfter && pass.destinationVersionAfter.valueI64 == versions[target.id])
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
    require(dependencies == passes.zipWithNext { a, b -> PlanPassDependency(a.id, b.id) })
}
