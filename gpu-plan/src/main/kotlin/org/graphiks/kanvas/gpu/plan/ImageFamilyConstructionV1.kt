package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.*
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32

/** Physical keys are private construction ordinals, never original scene command indices. */
public class ImageConstructionEntryV1 internal constructor(
    public val originalCommandIndexI32: Int,
    public val entryOrdinalI32: Int,
    public val constructionIndexI32: Int,
    public val originalDraw: DrawNode,
    public val cell: ImageCellPlanV1?,
    transformF32: Matrix3x3F32,
    public val atlasEntryColor: org.graphiks.math.color.ColorARGB?,
) {
    private val transform = transformF32.copy()
    public fun copyTransformF32(): Matrix3x3F32 = transform.copy()
    public val canonicalIdentity: String = "$originalCommandIndexI32:$entryOrdinalI32:$constructionIndexI32:" +
        originalDraw.canonicalId.value + ":" + cell?.canonicalIdentity.orEmpty() + ":" +
        listOf(transform.sx, transform.kx, transform.tx, transform.ky, transform.sy, transform.ty,
            transform.persp0, transform.persp1, transform.persp2).joinToString(",") { it.toRawBits().toString() } +
        ":color=${atlasEntryColor?.value}"
}

/** One immutable original operation authority, with its exact ordered contribution inventory. */
public class ImageFamilyCommandV1 internal constructor(public val commandIndex: Int,
    public val originalDraw: DrawNode, entries: List<ImageConstructionEntryV1>) {
    public val entries: List<ImageConstructionEntryV1> = immutableList(entries)
    public val canonicalIdentity: String = "$commandIndex:${originalDraw.canonicalId.value}:" + entries.joinToString(";") { it.canonicalIdentity }
    init {
        require(entries.withIndex().all { (ordinalI32, entry) -> entry.originalCommandIndexI32 == commandIndex &&
            entry.originalDraw === originalDraw && entry.entryOrdinalI32 == ordinalI32 } &&
            entries.zipWithNext().all { (a, b) -> a.constructionIndexI32 < b.constructionIndexI32 }) {
            W5eImagePlanDiagnostics.InvalidContract
        }
    }
}
