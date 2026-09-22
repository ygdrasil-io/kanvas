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
 * and every enclosing Picture draw; Task 3 may materialize it but must not rediscover a source
 * from the mutable root attachment.
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
        var transform = Matrix3x3F32.Identity
        val clips = mutableListOf<PictureClipCoordinateV1>()
        val paints = mutableListOf<PaintNode?>()
        outerPicturesSnapshot.forEach { picture ->
            clips += PictureClipCoordinateV1(picture.clip, transform)
            paints += picture.paint
            transform *= picture.transform
        }
        sourceDraw?.let { draw ->
            if (includeSourceDrawClip) clips += PictureClipCoordinateV1(draw.clip, transform)
            transform *= draw.transform
        }
        return PictureW5CoordinatesV1(transform, clips, paints)
    }

    /** Applies known outer Picture transforms/clips to MaskShader's W5 coordinate carrier. */
    internal fun materialCoordinateDrawOrNull(material: MaterialNode): DrawNode? {
        val draw = sourceDraw ?: return null
        if (outerPicturesSnapshot.isEmpty()) return draw.copy(material = material)
        var transform = Matrix3x3F32.Identity
        val entries = mutableListOf<ClipEntry>()
        fun appendClip(clip: ClipStackNode, coordinateTransform: Matrix3x3F32): Boolean = when (clip) {
            ClipStackNode.Empty -> true
            is ClipStackNode.DeviceRect -> {
                entries += ClipEntry(
                    GeometryNode.Rect.of(clip.copyBounds()),
                    ClipOperation.INTERSECT,
                    clip.antiAlias,
                    ClipTransformSnapshot.Known.of(coordinateTransform),
                )
                true
            }
            is ClipStackNode.Operations -> clip.all { entry ->
                val known = entry.transform as? ClipTransformSnapshot.Known ?: return@all false
                entries += entry.copy(transform = ClipTransformSnapshot.Known.of(
                    coordinateTransform * known.copyMatrixF32(),
                ))
                true
            }
        }
        outerPicturesSnapshot.forEach { picture ->
            if (!appendClip(picture.clip, transform)) return null
            transform *= picture.transform
        }
        if (!appendClip(draw.clip, transform)) return null
        transform *= draw.transform
        val clip = if (entries.isEmpty()) ClipStackNode.Empty else ClipStackNode.Operations.of(entries)
        return draw.copy(material = material, clip = clip, transform = transform)
    }
}
