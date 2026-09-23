package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.DrawNode
import org.graphiks.kanvas.render.ir.ClipEntry
import org.graphiks.kanvas.render.ir.ClipOperation
import org.graphiks.kanvas.render.ir.ClipStackNode
import org.graphiks.kanvas.render.ir.ClipTransformSnapshot
import org.graphiks.kanvas.render.ir.GeometryNode
import org.graphiks.kanvas.render.ir.LayerDescriptor
import org.graphiks.kanvas.render.ir.MaterialNode
import org.graphiks.kanvas.render.ir.PaintNode
import org.graphiks.kanvas.render.ir.SceneSnapshot
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.matrix.composeInOrderF64
import org.graphiks.math.matrix.toFiniteMatrix3x3F32OrNull
import org.graphiks.math.matrix.Matrix3x3F64
import org.graphiks.math.matrix.timesCheckedOrNull
import org.graphiks.math.matrix.toMatrix3x3F64

/** Exact outer-Picture coordinate state consumed by the W5 source hand-off in Task 3. */
public class PictureW5CoordinatesV1 internal constructor(
    effectiveTransformF32: Matrix3x3F32,
    clips: List<PictureClipCoordinateV1>,
    paints: List<PaintNode?>,
) {
    private val effectiveTransformSnapshotF32 = effectiveTransformF32.copy()
    private val clipSnapshot = immutableList(clips)
    private val paintSnapshot = immutableList(paints)

    public fun copyEffectiveTransformF32(): Matrix3x3F32 = effectiveTransformSnapshotF32.copy()
    public fun clips(): List<PictureClipCoordinateV1> = clipSnapshot
    public fun paints(): List<PaintNode?> = paintSnapshot
}

/** A clip remains typed and is evaluated in this exact enclosing Picture coordinate transform. */
public class PictureClipCoordinateV1 internal constructor(
    public val clip: ClipStackNode,
    transformF32: Matrix3x3F32,
) {
    private val transformSnapshotF32 = transformF32.copy()
    public fun copyTransformF32(): Matrix3x3F32 = transformSnapshotF32.copy()
}

/**
 * Captured provenance for one W6b coverage generation.  It retains the actual immutable scene
 * and every enclosing Picture draw for provenance. Ordinary Picture draws execute only their
 * frozen W4/W5 entry source pass; this object is never a renderer-side planning input.
 */
public class FilterOccurrenceSourceV1 internal constructor(
    public val scene: SceneSnapshot,
    public val sourceCommandIndexI32: Int,
    public val sourceDraw: DrawNode?,
    outerPictures: List<DrawNode>,
    public val layerDescriptor: LayerDescriptor? = null,
    picturePathI32: List<Int> = emptyList(),
) {
    private val outerPicturesSnapshot = immutableList(outerPictures)
    private val picturePathSnapshot = immutableList(picturePathI32)

    init {
        require(sourceCommandIndexI32 >= 0)
        require((sourceDraw == null) == (layerDescriptor != null)) {
            "A W6b occurrence is either a captured draw/Picture or a captured layer."
        }
    }

    public fun outerPictures(): List<DrawNode> = outerPicturesSnapshot
    /** Occurrence-local command path; provenance only, never a renderer cache key. */
    public fun picturePathI32(): List<Int> = picturePathSnapshot

    /**
     * Keeps outer Picture transforms, clips, and paints as one explicit W5 coordinate contract;
     * later materialization consumes this instead of rediscovering the parent SceneSnapshot.
     */
    public fun pictureW5CoordinatesOrNull(includeSourceDrawClip: Boolean = true): PictureW5CoordinatesV1? {
        if (outerPicturesSnapshot.isEmpty()) return null
        var transform = Matrix3x3F64()
        val clips = mutableListOf<PictureClipCoordinateV1>()
        val paints = mutableListOf<PaintNode?>()
        val lastIsolated = outerPicturesSnapshot.indexOfLast { it.paint != null }
        outerPicturesSnapshot.forEachIndexed { index, picture ->
            // A painted ancestor owns its boundary clip at its terminal, after filtering.
            // Only inline ancestors since that boundary can clip this source's raw coverage.
            if (index > lastIsolated) {
                val cull = (outerPicturesSnapshot.getOrNull(index - 1)?.geometry as? GeometryNode.Picture)?.copyCullRect()
                val clip = cull?.let { withoutPictureCull(picture.clip, it) } ?: picture.clip
                clips += PictureClipCoordinateV1(clip, transform.toFiniteMatrix3x3F32OrNull() ?: return null)
            }
            paints += picture.paint
            transform = transform.timesCheckedOrNull(picture.transform.toMatrix3x3F64()) ?: return null
        }
        sourceDraw?.let { draw ->
            if (includeSourceDrawClip) clips += PictureClipCoordinateV1(recordedInnerClipWithoutCull(),
                transform.toFiniteMatrix3x3F32OrNull() ?: return null)
            transform = transform.timesCheckedOrNull(draw.transform.toMatrix3x3F64()) ?: return null
        }
        return PictureW5CoordinatesV1(transform.toFiniteMatrix3x3F32OrNull() ?: return null, clips, paints)
    }

    /**
     * W5 coordinate carrier only. Coverage already owns the inner clip; the Picture terminal
     * owns the deferred clip. Neither clip is reapplied while evaluating a shader/material.
     * Composition stays F64 until the existing W5 input ABI is reached.
     */
    internal fun materialCoordinateDrawOrNull(material: MaterialNode): DrawNode? {
        val draw = sourceDraw ?: return null
        val transform = composeInOrderF64(outerPicturesSnapshot.map { it.transform } + draw.transform)
            .toFiniteMatrix3x3F32OrNull() ?: return null
        return draw.copy(material = material, clip = ClipStackNode.Empty, transform = transform)
    }
}
