package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.*
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.matrix.*

/** PictureRecorder's initial clipRect is its cull carrier, not an explicit inner clip. */
internal fun FilterOccurrenceSourceV1.recordedInnerClipWithoutCull(): ClipStackNode {
    val clip = sourceDraw?.clip ?: return layerDescriptor?.clip ?: ClipStackNode.Empty
    val picture = outerPictures().lastOrNull()?.geometry as? GeometryNode.Picture ?: return clip
    return withoutPictureCull(clip, picture.copyCullRect())
}

internal fun withoutPictureCull(clip: ClipStackNode, cull: org.graphiks.math.geometry.RectF32): ClipStackNode {
    return when (clip) {
        is ClipStackNode.DeviceRect -> if (clip.copyBounds() == cull) ClipStackNode.Empty else clip
        is ClipStackNode.Operations -> {
            val entries = clip.toList()
            val first = entries.firstOrNull()
            val initialCull = first?.operation == ClipOperation.INTERSECT &&
                (first.geometry as? GeometryNode.Rect)?.copyBounds() == cull &&
                (first.transform as? ClipTransformSnapshot.Known)?.copyMatrixF32() == Matrix3x3F32.Identity
            if (initialCull) entries.drop(1).let { if (it.isEmpty()) ClipStackNode.Empty else ClipStackNode.Operations.of(it) }
            else clip
        }
        ClipStackNode.Empty -> clip
    }
}

/** Pre-publication input to the existing W4/W5 authorities; never a renderer replay recipe. */
internal class OccurrenceSourceInputV1(
    val plannedCommandId: FramePlannedCommandIdI32,
    val locator: PictureSourceLocatorV1,
    val captured: FilterOccurrenceSourceV1,
    val mapping: LayerMappingF64,
    val enclosingMappingF64: Matrix3x3F64,
    targetDomainDeviceI32: RectI32,
    demandDeviceI32: RectI32,
    val recordedInnerClip: ClipStackNode,
    val deferredCompositeClip: ClipStackNode,
    val target: PlanResourceId,
    val commandIndexI32: Int,
    val sourceOnly: Boolean,
) {
    private val domain = targetDomainDeviceI32.copy()
    private val demand = demandDeviceI32.copy()
    fun copyDomainDeviceI32(): RectI32 = domain.copy()
    fun copyDemandDeviceI32(): RectI32 = demand.copy()

    /** F64 composition and rebasing precede the checked projection to the existing W4 ABI. */
    fun materialCoordinateDraw(): DrawNode {
        val draw = requireNotNull(captured.sourceDraw)
        val clipMapping = requireNotNull(mapping.copyDeviceToLayerF64().timesCheckedOrNull(enclosingMappingF64))
        fun transformClip(clip: ClipStackNode, clipMapping: Matrix3x3F64): ClipStackNode = when (clip) {
            ClipStackNode.Empty -> clip
            is ClipStackNode.DeviceRect -> {
                val bounds = clip.copyBounds()
                val axisAligned = clipMapping.kxF64 == 0.0 && clipMapping.kyF64 == 0.0 &&
                    clipMapping.persp0F64 == 0.0 && clipMapping.persp1F64 == 0.0
                if (axisAligned) {
                    val mapped = requireNotNull(clipMapping.mapRectBoundsF64OrNull(
                        org.graphiks.math.geometry.RectF64(bounds.left.toDouble(), bounds.top.toDouble(),
                            bounds.right.toDouble(), bounds.bottom.toDouble())))
                    ClipStackNode.DeviceRect.of(org.graphiks.math.geometry.RectF32(mapped.left.toFloat(),
                        mapped.top.toFloat(), mapped.right.toFloat(), mapped.bottom.toFloat()), clip.antiAlias)
                } else ClipStackNode.Operations.of(listOf(ClipEntry(
                    GeometryNode.Rect.of(bounds), ClipOperation.INTERSECT, clip.antiAlias,
                    ClipTransformSnapshot.Known.of(requireNotNull(clipMapping.toFiniteMatrix3x3F32OrNull())),
                )))
            }
            is ClipStackNode.Operations -> ClipStackNode.Operations.of(clip.map { entry ->
                val recorded = (entry.transform as? ClipTransformSnapshot.Known)?.copyMatrixF32()
                    ?: throw IllegalArgumentException(W6aPlanDiagnostics.UnsupportedChild)
                entry.copy(transform = ClipTransformSnapshot.Known.of(requireNotNull(
                    clipMapping.timesCheckedOrNull(recorded.toMatrix3x3F64())?.toFiniteMatrix3x3F32OrNull(),
                )))
            })
        }
        val clips = mutableListOf<ClipStackNode>()
        val pictures = captured.outerPictures()
        val lastIsolated = pictures.indexOfLast { it.paint != null }
        pictures.forEachIndexed { index, picture -> if (index > lastIsolated && picture.paint == null) {
            val cull = (pictures.getOrNull(index - 1)?.geometry as? GeometryNode.Picture)?.copyCullRect()
            val clip = cull?.let { withoutPictureCull(picture.clip, it) } ?: picture.clip
            val enclosing = composeInOrderF64(pictures.take(index).map { it.transform })
            clips += transformClip(clip, requireNotNull(mapping.copyDeviceToLayerF64().timesCheckedOrNull(enclosing)))
        } }
        clips += transformClip(recordedInnerClip, clipMapping)
        val active = clips.filterNot { it == ClipStackNode.Empty }
        val inner = if (active.size <= 1) active.singleOrNull() ?: ClipStackNode.Empty else ClipStackNode.Operations.of(
            active.flatMap { clip -> when (clip) {
                ClipStackNode.Empty -> emptyList()
                is ClipStackNode.DeviceRect -> listOf(ClipEntry(GeometryNode.Rect.of(clip.copyBounds()),
                    ClipOperation.INTERSECT, clip.antiAlias))
                is ClipStackNode.Operations -> clip.toList()
            } })
        return draw.copy(
            transform = requireNotNull(mapping.copyLocalToLayerF64().toFiniteMatrix3x3F32OrNull()),
            clip = inner,
            paint = draw.paint?.copy(imageFilter = null, maskFilter = null),
            effects = (draw.effects as? EffectStack.Entries)?.let { entries ->
                EffectStack.of(entries.filterNot { it is CapturedFilterRootV1 || it is MaskFilterNode })
            } ?: draw.effects,
            blend = if (sourceOnly) BlendNode.SrcOver else draw.blend,
        )
    }
}
