package org.skia.foundation

import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.math.SkScalar
import org.skia.math.SkVector

/**
 * Rounded rectangle: an [SkRect] paired with up to four corner radii
 * (one X / Y pair per corner). Faithful skeleton port of Skia's
 * `SkRRect` covering storage, factories, type classification, and the
 * radii-scaling clamp Skia applies when corner radii would overlap.
 *
 * **Out of scope for this skeleton** (deferred until callers need them):
 * `transform(SkMatrix)`, `contains(SkRect)`, `inset` / `outset`,
 * geometric serialisation. Rasterisation lives in Phase 4 — this class
 * is purely the data model.
 *
 * Indexing of the radii array follows Skia's [Corner] order:
 * `[TopLeft, TopRight, BottomRight, BottomLeft]`.
 */
public class SkRRect private constructor(
    private var fRect: SkRect,
    private val fRadii: Array<SkPoint>,
    private var fType: Type,
) {
    public constructor() : this(
        SkRect.MakeLTRB(0f, 0f, 0f, 0f),
        Array(4) { SkPoint(0f, 0f) },
        Type.kEmpty_Type,
    )

    /** Possible specialisations of [SkRRect]. Listed from most-restrictive to most-general. */
    public enum class Type {
        /** Zero width or height. */
        kEmpty_Type,

        /** Non-zero width / height, all radii are zero. */
        kRect_Type,

        /** Non-zero width / height, all radii equal and big enough to cover the rect. */
        kOval_Type,

        /** Non-zero width / height, all 4 corners share the same `(rx, ry)`. */
        kSimple_Type,

        /** Non-zero width / height, axis-aligned radii pairs (top vs bottom, left vs right). */
        kNinePatch_Type,

        /** Non-zero width / height with arbitrary per-corner radii. */
        kComplex_Type,
    }

    /** Indices of the four corners in the radii array. */
    public enum class Corner(public val index: Int) {
        kUpperLeft_Corner(0),
        kUpperRight_Corner(1),
        kLowerRight_Corner(2),
        kLowerLeft_Corner(3),
    }

    public fun getType(): Type = fType
    public fun type(): Type = fType

    public fun isEmpty(): Boolean = fType == Type.kEmpty_Type
    public fun isRect(): Boolean = fType == Type.kRect_Type
    public fun isOval(): Boolean = fType == Type.kOval_Type
    public fun isSimple(): Boolean = fType == Type.kSimple_Type
    public fun isNinePatch(): Boolean = fType == Type.kNinePatch_Type
    public fun isComplex(): Boolean = fType == Type.kComplex_Type

    public fun rect(): SkRect = fRect
    public fun width(): SkScalar = fRect.right - fRect.left
    public fun height(): SkScalar = fRect.bottom - fRect.top

    /**
     * Top-left corner radii. For [Type.kEmpty_Type], [Type.kRect_Type],
     * [Type.kOval_Type], and [Type.kSimple_Type] this value is
     * representative of every corner.
     */
    public fun getSimpleRadii(): SkVector = fRadii[0].copy()

    /** Returns a defensive copy of the corner's radii pair. */
    public fun radii(corner: Corner): SkVector = fRadii[corner.index].copy()

    public fun setEmpty() {
        fRect = SkRect.MakeLTRB(0f, 0f, 0f, 0f)
        for (i in 0 until 4) fRadii[i].set(0f, 0f)
        fType = Type.kEmpty_Type
    }

    public fun setRect(rect: SkRect) {
        if (!initializeRect(rect)) return
        for (i in 0 until 4) fRadii[i].set(0f, 0f)
        fType = Type.kRect_Type
    }

    public fun setOval(oval: SkRect) {
        if (!initializeRect(oval)) return
        val xRad = (oval.right - oval.left) * 0.5f
        val yRad = (oval.bottom - oval.top) * 0.5f
        for (i in 0 until 4) fRadii[i].set(xRad, yRad)
        fType = Type.kOval_Type
    }

    public fun setRectXY(rect: SkRect, xRad: SkScalar, yRad: SkScalar) {
        if (!initializeRect(rect)) return
        val cx = if (xRad <= 0f || yRad <= 0f) 0f else xRad
        val cy = if (xRad <= 0f || yRad <= 0f) 0f else yRad
        for (i in 0 until 4) fRadii[i].set(cx, cy)
        if (cx == 0f && cy == 0f) {
            fType = Type.kRect_Type
        } else {
            fType = Type.kSimple_Type
            scaleRadiiAndClassify()
        }
    }

    /**
     * Sets the four corner radii independently. `radii.size` must be `4`,
     * indexed per [Corner]. Negative radii clamp to zero (matching Skia).
     */
    public fun setRectRadii(rect: SkRect, radii: Array<SkVector>) {
        require(radii.size == 4) { "setRectRadii expects 4 corner radii (got ${radii.size})" }
        if (!initializeRect(rect)) return
        for (i in 0 until 4) {
            val r = radii[i]
            fRadii[i].set(if (r.fX <= 0f) 0f else r.fX, if (r.fY <= 0f) 0f else r.fY)
        }
        scaleRadiiAndClassify()
    }

    /**
     * Convenience matching Skia's `setNinePatch`: separate horizontal /
     * vertical radii for each side, applied symmetrically per-corner.
     */
    public fun setNinePatch(
        rect: SkRect,
        leftRad: SkScalar,
        topRad: SkScalar,
        rightRad: SkScalar,
        bottomRad: SkScalar,
    ) {
        val l = if (leftRad <= 0f) 0f else leftRad
        val t = if (topRad <= 0f) 0f else topRad
        val r = if (rightRad <= 0f) 0f else rightRad
        val b = if (bottomRad <= 0f) 0f else bottomRad
        if (!initializeRect(rect)) return
        fRadii[Corner.kUpperLeft_Corner.index].set(l, t)
        fRadii[Corner.kUpperRight_Corner.index].set(r, t)
        fRadii[Corner.kLowerRight_Corner.index].set(r, b)
        fRadii[Corner.kLowerLeft_Corner.index].set(l, b)
        scaleRadiiAndClassify()
    }

    private fun initializeRect(rect: SkRect): Boolean {
        // Sort the rect L/R and T/B (Skia's behaviour for degenerate input).
        val l = minOf(rect.left, rect.right)
        val t = minOf(rect.top, rect.bottom)
        val r = maxOf(rect.left, rect.right)
        val b = maxOf(rect.top, rect.bottom)
        fRect = SkRect.MakeLTRB(l, t, r, b)
        if (l >= r || t >= b) {
            for (i in 0 until 4) fRadii[i].set(0f, 0f)
            fType = Type.kEmpty_Type
            return false
        }
        return true
    }

    /**
     * Scales radii uniformly so that no two adjacent corners overlap on a
     * given edge — matches Skia's `SkRRect::scaleRadii`. Then refreshes
     * [fType] from the resulting radii.
     */
    private fun scaleRadiiAndClassify() {
        val width = width().toDouble()
        val height = height().toDouble()
        var scale = 1.0
        scale = computeMinScale(fRadii[0].fX, fRadii[1].fX, width, scale)
        scale = computeMinScale(fRadii[1].fY, fRadii[2].fY, height, scale)
        scale = computeMinScale(fRadii[2].fX, fRadii[3].fX, width, scale)
        scale = computeMinScale(fRadii[3].fY, fRadii[0].fY, height, scale)
        if (scale < 1.0) {
            for (i in 0 until 4) {
                fRadii[i].set(
                    (fRadii[i].fX.toDouble() * scale).toFloat(),
                    (fRadii[i].fY.toDouble() * scale).toFloat(),
                )
            }
        }
        fType = classifyType()
    }

    private fun computeMinScale(rad1: Float, rad2: Float, limit: Double, prev: Double): Double {
        val sum = rad1.toDouble() + rad2.toDouble()
        return if (sum > limit && sum > 0.0) minOf(prev, limit / sum) else prev
    }

    private fun classifyType(): Type {
        if (allRadiiZero()) return Type.kRect_Type
        val r0 = fRadii[0]
        val same = (1 until 4).all { fRadii[it].fX == r0.fX && fRadii[it].fY == r0.fY }
        if (same) {
            val w = width()
            val h = height()
            return if (r0.fX >= w * 0.5f && r0.fY >= h * 0.5f) Type.kOval_Type else Type.kSimple_Type
        }
        // NinePatch: corners share the same x-radius down each side, and
        // the same y-radius across each side (i.e. determined by 4 scalars
        // l, r, t, b — the canonical "9-patch" layout).
        val tl = fRadii[Corner.kUpperLeft_Corner.index]
        val tr = fRadii[Corner.kUpperRight_Corner.index]
        val br = fRadii[Corner.kLowerRight_Corner.index]
        val bl = fRadii[Corner.kLowerLeft_Corner.index]
        val ninePatch = tl.fX == bl.fX && tr.fX == br.fX && tl.fY == tr.fY && bl.fY == br.fY
        return if (ninePatch) Type.kNinePatch_Type else Type.kComplex_Type
    }

    private fun allRadiiZero(): Boolean = (0 until 4).all { fRadii[it].fX == 0f && fRadii[it].fY == 0f }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SkRRect) return false
        if (fRect != other.fRect) return false
        for (i in 0 until 4) if (fRadii[i] != other.fRadii[i]) return false
        return true
    }

    override fun hashCode(): Int {
        var result = fRect.hashCode()
        for (i in 0 until 4) result = 31 * result + fRadii[i].hashCode()
        return result
    }

    override fun toString(): String {
        val r = "(${fRect.left},${fRect.top})-(${fRect.right},${fRect.bottom})"
        val rads = (0 until 4).joinToString(", ") { "(${fRadii[it].fX},${fRadii[it].fY})" }
        return "SkRRect[$fType rect=$r radii=[$rads]]"
    }

    public companion object {
        public fun MakeEmpty(): SkRRect = SkRRect()

        public fun MakeRect(r: SkRect): SkRRect = SkRRect().apply { setRect(r) }

        public fun MakeOval(oval: SkRect): SkRRect = SkRRect().apply { setOval(oval) }

        public fun MakeRectXY(rect: SkRect, xRad: SkScalar, yRad: SkScalar): SkRRect =
            SkRRect().apply { setRectXY(rect, xRad, yRad) }

        public fun MakeRectRadii(rect: SkRect, radii: Array<SkVector>): SkRRect =
            SkRRect().apply { setRectRadii(rect, radii) }
    }
}
