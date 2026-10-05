package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.*
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.toFiniteNonEmptyRectF32OrNull
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

/**
 * The capture representation preserves an explicit clipRect as a one-entry operation stream.
 * An empty intersect is terminally a no-op under every finite mapping, so publish that exact
 * rectangle as a DeviceRect.  Non-empty operation streams remain opaque: Task 3 never turns
 * a complex clip into a scissor or an AABB.
 */
internal fun ClipStackNode.terminalDeferredClip(): ClipStackNode = when (this) {
    ClipStackNode.Empty, is ClipStackNode.DeviceRect -> this
    is ClipStackNode.Operations -> toList().singleOrNull()?.let { entry ->
        val rectangle = entry.geometry as? GeometryNode.Rect
        val matrix = (entry.transform as? ClipTransformSnapshot.Known)?.copyMatrixF32()?.toMatrix3x3F64()
        if (entry.operation == ClipOperation.INTERSECT && rectangle?.copyBounds()?.isEmpty == true &&
            matrix?.isFinite() == true) {
            ClipStackNode.DeviceRect.of(rectangle.copyBounds(), entry.antiAlias)
        } else this
    } ?: this
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
    /** Authenticated parent composition target; occurrence reconstruction must not default to LINEAR. */
    val renderTarget: RenderTargetDescriptor,
    val commandIndexI32: Int,
    val sourceOnly: Boolean,
    /**
     * Closed W7-only carrier form.  It may canonicalize one authenticated hard rectangular
     * Picture clip after the capture cull has been removed; generic W4d/W5 paths retain the
     * original operation stream.
     */
    val w7InverseAaCoverageCarrier: Boolean = false,
) {
    private val domain = targetDomainDeviceI32.copy()
    private val demand = demandDeviceI32.copy()
    fun copyDomainDeviceI32(): RectI32 = domain.copy()
    fun copyDemandDeviceI32(): RectI32 = demand.copy()

    /** Only the real-Picture W7 admission site may request this carrier representation. */
    fun withW7InverseAaCoverageCarrier(): OccurrenceSourceInputV1 = if (w7InverseAaCoverageCarrier) this else
        OccurrenceSourceInputV1(plannedCommandId, locator, captured, mapping, enclosingMappingF64, domain, demand,
            recordedInnerClip, deferredCompositeClip, target, renderTarget, commandIndexI32, sourceOnly, true)

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
        val pictures = captured.outerPictures()
        val clips = captured.pictureSourceClipScopesV1().map { scope ->
            val sourceMapping = scope.outerPrefixSize?.let { prefixSize ->
                val enclosing = composeInOrderF64(pictures.take(prefixSize).map { it.transform })
                requireNotNull(mapping.copyDeviceToLayerF64().timesCheckedOrNull(enclosing))
            } ?: clipMapping
            transformClip(scope.clip, sourceMapping)
        }
        val active = clips.filterNot { it == ClipStackNode.Empty }
        val inner = if (active.size <= 1) active.singleOrNull() ?: ClipStackNode.Empty else ClipStackNode.Operations.of(
            active.flatMap { clip -> when (clip) {
                ClipStackNode.Empty -> emptyList()
                is ClipStackNode.DeviceRect -> listOf(ClipEntry(GeometryNode.Rect.of(clip.copyBounds()),
                    ClipOperation.INTERSECT, clip.antiAlias))
                is ClipStackNode.Operations -> clip.toList()
            } })
        val carrierClip = if (w7InverseAaCoverageCarrier) inner.w7InverseAaCoverageDeviceRectOrNull() ?: inner else inner
        return draw.copy(
            transform = requireNotNull(mapping.copyLocalToLayerF64().toFiniteMatrix3x3F32OrNull()),
            clip = carrierClip,
            paint = draw.paint?.copy(imageFilter = null, maskFilter = null),
            effects = (draw.effects as? EffectStack.Entries)?.let { entries ->
                EffectStack.of(entries.filterNot { it is CapturedFilterRootV1 || it is MaskFilterNode })
            } ?: draw.effects,
            blend = if (sourceOnly) BlendNode.SrcOver else draw.blend,
        )
    }
}

/**
 * The W7 source owns one finite hard `clipRect` from the captured real Picture.  This is not a
 * general clip simplifier: only one exact INTERSECT Rect with a finite axis-aligned transform is
 * converted, and only on the W7-only occurrence carrier above.  Multiple operations, AA clips,
 * perspective, rotation/skew, legacy transforms and every non-rect geometry stay opaque.
 */
private fun ClipStackNode.w7InverseAaCoverageDeviceRectOrNull(): ClipStackNode.DeviceRect? = when (this) {
    is ClipStackNode.DeviceRect -> takeIf { !antiAlias && !copyBounds().isEmpty }
    is ClipStackNode.Operations -> toList().singleOrNull()?.let { entry ->
        val rect = entry.geometry as? GeometryNode.Rect ?: return@let null
        val matrix = (entry.transform as? ClipTransformSnapshot.Known)?.copyMatrixF32()?.toMatrix3x3F64()
            ?: return@let null
        if (entry.operation != ClipOperation.INTERSECT || entry.antiAlias ||
            !matrix.isFinite() || matrix.kxF64 != 0.0 || matrix.kyF64 != 0.0 ||
            matrix.persp0F64 != 0.0 || matrix.persp1F64 != 0.0) return@let null
        val bounds = rect.copyBounds()
        val mapped = matrix.mapRectBoundsF64OrNull(org.graphiks.math.geometry.RectF64(
            bounds.left.toDouble(), bounds.top.toDouble(), bounds.right.toDouble(), bounds.bottom.toDouble(),
        )) ?: return@let null
        mapped.toFiniteNonEmptyRectF32OrNull()?.let { mappedF32 -> ClipStackNode.DeviceRect.of(mappedF32, false) }
    }
    ClipStackNode.Empty -> null
}
