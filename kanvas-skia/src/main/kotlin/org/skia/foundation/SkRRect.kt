package org.skia.foundation

import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.math.SkScalar
import org.skia.math.SkScalarHalf
import org.skia.math.SkScalarNearlyEqual
import org.skia.math.SkVector

/**
 * Iso-aligned port of Skia's `SkRRect`
 * ([include/core/SkRRect.h](https://github.com/google/skia/blob/main/include/core/SkRRect.h) +
 * [src/core/SkRRect.cpp](https://github.com/google/skia/blob/main/src/core/SkRRect.cpp)).
 *
 * Stores an axis-aligned rect with up to four corner radii (one (X, Y)
 * pair per corner). Indexing follows Skia's [Corner] order:
 * `[UpperLeft, UpperRight, LowerRight, LowerLeft]`.
 *
 * Algorithms mirror upstream:
 * - `setRectXY` / `setRectRadii` collapse to `setRect` on non-finite
 *   inputs (matches `SkIsFinite` short-circuit).
 * - `scaleRadii` runs `clamp_to_zero` ⇒ `compute_min_scale` ⇒
 *   `flush_to_zero` ⇒ `AdjustRadii` (with ULP-tweaked redistribution)
 *   so that `a + b <= limit` holds exactly in float.
 * - `computeType` and `isValid` invariants match the C++ predicates
 *   (`allCornersSquare`, `allRadiiSame`, `radii_are_nine_patch`).
 *
 * **Out of scope** (deferred): `transform(SkMatrix)`, serialisation
 * (`writeToMemory` / `readFromMemory`), `SkRRectPriv` helpers.
 * `transform` would pull in the full SkMatrix+SkPath machinery; the
 * raster pipeline uses path conversion instead today.
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
        kEmpty_Type,
        kRect_Type,
        kOval_Type,
        kSimple_Type,
        kNinePatch_Type,
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
    public fun getBounds(): SkRect = fRect

    public fun width(): SkScalar = fRect.width()
    public fun height(): SkScalar = fRect.height()

    /** Top-left radii (representative for kEmpty/kRect/kOval/kSimple). */
    public fun getSimpleRadii(): SkVector = fRadii[0].copy()

    /** Defensive copy of one corner's radii. */
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
        val xRad = SkScalarHalf(fRect.width())
        val yRad = SkScalarHalf(fRect.height())
        if (xRad == 0f || yRad == 0f) {
            for (i in 0 until 4) fRadii[i].set(0f, 0f)
            fType = Type.kRect_Type
        } else {
            for (i in 0 until 4) fRadii[i].set(xRad, yRad)
            fType = Type.kOval_Type
        }
    }

    public fun setRectXY(rect: SkRect, xRad: SkScalar, yRad: SkScalar) {
        if (!initializeRect(rect)) return
        var rx = xRad
        var ry = yRad
        if (!rx.isFinite() || !ry.isFinite()) {
            rx = 0f; ry = 0f
        }

        val w = fRect.width()
        val h = fRect.height()
        if (w < rx + rx || h < ry + ry) {
            // Skia: at most one division is by zero, and neither numerator is zero.
            val sx = if (rx + rx == 0f) Float.POSITIVE_INFINITY else w / (rx + rx)
            val sy = if (ry + ry == 0f) Float.POSITIVE_INFINITY else h / (ry + ry)
            val scale = minOf(sx, sy)
            rx *= scale
            ry *= scale
        }

        if (rx <= 0f || ry <= 0f) {
            setRect(rect)
            return
        }

        for (i in 0 until 4) fRadii[i].set(rx, ry)
        fType = Type.kSimple_Type
        if (rx >= SkScalarHalf(fRect.width()) && ry >= SkScalarHalf(fRect.height())) {
            fType = Type.kOval_Type
        }
    }

    public fun setRectRadii(rect: SkRect, radii: Array<SkVector>) {
        require(radii.size == 4) { "setRectRadii expects 4 corner radii (got ${radii.size})" }
        if (!initializeRect(rect)) return

        // Skia: revert to plain rect on any non-finite radius.
        var allFinite = true
        for (r in radii) {
            if (!r.fX.isFinite() || !r.fY.isFinite()) { allFinite = false; break }
        }
        if (!allFinite) { setRect(rect); return }

        for (i in 0 until 4) fRadii[i].set(radii[i].fX, radii[i].fY)

        if (clampToZero(fRadii)) { setRect(rect); return }

        scaleRadii()

        if (!isValid()) {
            setRect(rect)
        }
    }

    public fun setNinePatch(
        rect: SkRect,
        leftRad: SkScalar,
        topRad: SkScalar,
        rightRad: SkScalar,
        bottomRad: SkScalar,
    ) {
        if (!initializeRect(rect)) return
        if (!leftRad.isFinite() || !topRad.isFinite() ||
            !rightRad.isFinite() || !bottomRad.isFinite()) {
            setRect(rect); return
        }

        var l = maxOf(leftRad, 0f)
        var t = maxOf(topRad, 0f)
        var r = maxOf(rightRad, 0f)
        var b = maxOf(bottomRad, 0f)

        var scale = 1f
        if (l + r > fRect.width()) scale = fRect.width() / (l + r)
        if (t + b > fRect.height()) scale = minOf(scale, fRect.height() / (t + b))
        if (scale < 1f) {
            l *= scale; t *= scale; r *= scale; b *= scale
        }

        if (l == r && t == b) {
            if (l >= SkScalarHalf(fRect.width()) && t >= SkScalarHalf(fRect.height())) {
                fType = Type.kOval_Type
            } else if (l == 0f || t == 0f) {
                fType = Type.kRect_Type
                l = 0f; t = 0f; r = 0f; b = 0f
            } else {
                fType = Type.kSimple_Type
            }
        } else {
            fType = Type.kNinePatch_Type
        }

        fRadii[Corner.kUpperLeft_Corner.index].set(l, t)
        fRadii[Corner.kUpperRight_Corner.index].set(r, t)
        fRadii[Corner.kLowerRight_Corner.index].set(r, b)
        fRadii[Corner.kLowerLeft_Corner.index].set(l, b)
        if (clampToZero(fRadii)) { setRect(rect); return }
        if (fType == Type.kNinePatch_Type && !radiiAreNinePatch(fRadii)) {
            fType = Type.kComplex_Type
        }
    }

    // ─── Geometric mutators ────────────────────────────────────────────

    public fun offset(dx: SkScalar, dy: SkScalar) {
        fRect.offset(dx, dy)
    }

    public fun makeOffset(dx: SkScalar, dy: SkScalar): SkRRect {
        val dst = SkRRect()
        dst.fRect = fRect.makeOffset(dx, dy)
        for (i in 0 until 4) dst.fRadii[i].set(fRadii[i].fX, fRadii[i].fY)
        dst.fType = fType
        return dst
    }

    /**
     * Inset by (dx, dy) in place. Each corner radius shrinks by the
     * matching axis amount when non-zero; a zero radius stays zero so
     * a square corner doesn't suddenly become rounded. Mirrors
     * Skia's `SkRRect::inset(dx, dy, &dst)`.
     */
    public fun inset(dx: SkScalar, dy: SkScalar) { inset(dx, dy, this) }

    public fun outset(dx: SkScalar, dy: SkScalar) { inset(-dx, -dy, this) }

    public fun inset(dx: SkScalar, dy: SkScalar, dst: SkRRect) {
        var r = fRect.makeInset(dx, dy)
        var degenerate = false
        if (r.right <= r.left) {
            degenerate = true
            val mid = (0.5 * (r.left.toDouble() + r.right)).toFloat()
            r = SkRect.MakeLTRB(mid, r.top, mid, r.bottom)
        }
        if (r.bottom <= r.top) {
            degenerate = true
            val mid = (0.5 * (r.top.toDouble() + r.bottom)).toFloat()
            r = SkRect.MakeLTRB(r.left, mid, r.right, mid)
        }
        if (degenerate) {
            dst.fRect = r
            for (i in 0 until 4) dst.fRadii[i].set(0f, 0f)
            dst.fType = Type.kEmpty_Type
            return
        }
        if (!r.isFinite()) {
            dst.setEmpty()
            return
        }

        val newRadii = Array(4) { i ->
            val rx = if (fRadii[i].fX != 0f) fRadii[i].fX - dx else 0f
            val ry = if (fRadii[i].fY != 0f) fRadii[i].fY - dy else 0f
            SkPoint(rx, ry)
        }
        dst.setRectRadii(r, newRadii)
    }

    public fun outset(dx: SkScalar, dy: SkScalar, dst: SkRRect) { inset(-dx, -dy, dst) }

    // ─── Containment ───────────────────────────────────────────────────

    /**
     * `true` if the axis-aligned `rect` lies entirely within `this`,
     * including the rounded corner curves. Mirrors Skia's
     * [`SkRRect::contains(rect)`](https://github.com/google/skia/blob/main/src/core/SkRRect.cpp).
     */
    public fun contains(rect: SkRect): Boolean {
        if (!getBounds().contains(rect)) return false
        if (isRect()) return true
        return checkCornerContainment(rect.left, rect.top) &&
            checkCornerContainment(rect.right, rect.top) &&
            checkCornerContainment(rect.right, rect.bottom) &&
            checkCornerContainment(rect.left, rect.bottom)
    }

    private fun checkCornerContainment(x: SkScalar, y: SkScalar): Boolean {
        val cx: Float
        val cy: Float
        val index: Int

        if (fType == Type.kOval_Type) {
            cx = x - fRect.centerX()
            cy = y - fRect.centerY()
            index = Corner.kUpperLeft_Corner.index
        } else {
            val ul = fRadii[Corner.kUpperLeft_Corner.index]
            val ur = fRadii[Corner.kUpperRight_Corner.index]
            val lr = fRadii[Corner.kLowerRight_Corner.index]
            val ll = fRadii[Corner.kLowerLeft_Corner.index]
            when {
                x < fRect.left + ul.fX && y < fRect.top + ul.fY -> {
                    index = Corner.kUpperLeft_Corner.index
                    cx = x - (fRect.left + ul.fX)
                    cy = y - (fRect.top + ul.fY)
                }
                x < fRect.left + ll.fX && y > fRect.bottom - ll.fY -> {
                    index = Corner.kLowerLeft_Corner.index
                    cx = x - (fRect.left + ll.fX)
                    cy = y - (fRect.bottom - ll.fY)
                }
                x > fRect.right - ur.fX && y < fRect.top + ur.fY -> {
                    index = Corner.kUpperRight_Corner.index
                    cx = x - (fRect.right - ur.fX)
                    cy = y - (fRect.top + ur.fY)
                }
                x > fRect.right - lr.fX && y > fRect.bottom - lr.fY -> {
                    index = Corner.kLowerRight_Corner.index
                    cx = x - (fRect.right - lr.fX)
                    cy = y - (fRect.bottom - lr.fY)
                }
                else -> return true   // not in any rounded corner region
            }
        }
        // Ellipse test: b²x² + a²y² <= (ab)²
        val a = fRadii[index].fX
        val b = fRadii[index].fY
        val dist = cx * cx * (b * b) + cy * cy * (a * a)
        val ab = a * b
        return dist <= ab * ab
    }

    // ─── Validity ──────────────────────────────────────────────────────

    public fun isValid(): Boolean {
        if (!AreRectAndRadiiValid(fRect, fRadii)) return false

        var allRadiiZero = fRadii[0].fX == 0f && fRadii[0].fY == 0f
        var allCornersSquare = fRadii[0].fX == 0f || fRadii[0].fY == 0f
        var allRadiiSame = true
        for (i in 1 until 4) {
            if (fRadii[i].fX != 0f || fRadii[i].fY != 0f) allRadiiZero = false
            if (fRadii[i].fX != fRadii[i - 1].fX || fRadii[i].fY != fRadii[i - 1].fY) allRadiiSame = false
            if (fRadii[i].fX != 0f && fRadii[i].fY != 0f) allCornersSquare = false
        }
        val patchesOfNine = radiiAreNinePatch(fRadii)

        return when (fType) {
            Type.kEmpty_Type -> fRect.isEmpty && allRadiiZero && allRadiiSame && allCornersSquare
            Type.kRect_Type -> !fRect.isEmpty && allRadiiZero && allRadiiSame && allCornersSquare
            Type.kOval_Type -> !fRect.isEmpty && !allRadiiZero && allRadiiSame && !allCornersSquare &&
                (0 until 4).all {
                    SkScalarNearlyEqual(fRadii[it].fX, SkScalarHalf(fRect.width())) &&
                        SkScalarNearlyEqual(fRadii[it].fY, SkScalarHalf(fRect.height()))
                }
            Type.kSimple_Type -> !fRect.isEmpty && !allRadiiZero && allRadiiSame && !allCornersSquare
            Type.kNinePatch_Type -> !fRect.isEmpty && !allRadiiZero && !allRadiiSame &&
                !allCornersSquare && patchesOfNine
            Type.kComplex_Type -> !fRect.isEmpty && !allRadiiZero && !allRadiiSame &&
                !allCornersSquare && !patchesOfNine
        }
    }

    // ─── Internal algorithms ───────────────────────────────────────────

    private fun initializeRect(rect: SkRect): Boolean {
        // Skia: check finite BEFORE sorting (sort can hide NaNs).
        if (!rect.isFinite()) {
            fRect = SkRect.MakeLTRB(0f, 0f, 0f, 0f)
            for (i in 0 until 4) fRadii[i].set(0f, 0f)
            fType = Type.kEmpty_Type
            return false
        }
        fRect = rect.makeSorted()
        if (fRect.isEmpty) {
            for (i in 0 until 4) fRadii[i].set(0f, 0f)
            fType = Type.kEmpty_Type
            return false
        }
        return true
    }

    /** Mirrors `SkRRect::scaleRadii`. */
    private fun scaleRadii(): Boolean {
        var scale = 1.0
        val w = fRect.width().toDouble()
        val h = fRect.height().toDouble()
        scale = computeMinScale(fRadii[0].fX, fRadii[1].fX, w, scale)
        scale = computeMinScale(fRadii[1].fY, fRadii[2].fY, h, scale)
        scale = computeMinScale(fRadii[2].fX, fRadii[3].fX, w, scale)
        scale = computeMinScale(fRadii[3].fY, fRadii[0].fY, h, scale)

        flushToZero(fRadii, 0, 1, axisX = true)
        flushToZero(fRadii, 1, 2, axisX = false)
        flushToZero(fRadii, 2, 3, axisX = true)
        flushToZero(fRadii, 3, 0, axisX = false)

        if (scale < 1.0) {
            adjustRadii(w, scale, fRadii, 0, 1, axisX = true)
            adjustRadii(h, scale, fRadii, 1, 2, axisX = false)
            adjustRadii(w, scale, fRadii, 2, 3, axisX = true)
            adjustRadii(h, scale, fRadii, 3, 0, axisX = false)
        }

        clampToZero(fRadii)
        computeType()
        return scale < 1.0
    }

    private fun computeType() {
        if (fRect.isEmpty) {
            for (i in 0 until 4) fRadii[i].set(0f, 0f)
            fType = Type.kEmpty_Type
            return
        }

        var allRadiiEqual = true
        var allCornersSquare = fRadii[0].fX == 0f || fRadii[0].fY == 0f
        for (i in 1 until 4) {
            if (fRadii[i].fX != 0f && fRadii[i].fY != 0f) allCornersSquare = false
            if (fRadii[i].fX != fRadii[i - 1].fX || fRadii[i].fY != fRadii[i - 1].fY) allRadiiEqual = false
        }

        if (allCornersSquare) { fType = Type.kRect_Type; return }
        if (allRadiiEqual) {
            fType = if (fRadii[0].fX >= SkScalarHalf(fRect.width()) &&
                fRadii[0].fY >= SkScalarHalf(fRect.height())) Type.kOval_Type else Type.kSimple_Type
            return
        }
        fType = if (radiiAreNinePatch(fRadii)) Type.kNinePatch_Type else Type.kComplex_Type
    }

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

        /**
         * Validates that `rect` is finite + sorted and each radius fits
         * its side. Mirrors Skia's `AreRectAndRadiiValid` ([SkRRect.cpp](https://github.com/google/skia/blob/main/src/core/SkRRect.cpp)).
         */
        public fun AreRectAndRadiiValid(rect: SkRect, radii: Array<SkPoint>): Boolean {
            if (!rect.isFinite() || !rect.isSorted()) return false
            for (i in 0 until 4) {
                if (!radiusCheckPredicates(radii[i].fX, rect.left, rect.right) ||
                    !radiusCheckPredicates(radii[i].fY, rect.top, rect.bottom)) return false
            }
            return true
        }

        /** Skia: `min <= max && rad <= max-min && min+rad <= max && max-rad >= min && rad >= 0`. */
        private fun radiusCheckPredicates(rad: SkScalar, min: SkScalar, max: SkScalar): Boolean =
            min <= max && rad <= max - min && min + rad <= max && max - rad >= min && rad >= 0f

        /** Returns true if all corners ended up "square" (one radius zero). Mirrors `clamp_to_zero`. */
        private fun clampToZero(radii: Array<SkPoint>): Boolean {
            var allCornersSquare = true
            for (i in 0 until 4) {
                if (radii[i].fX <= 0f || radii[i].fY <= 0f) {
                    radii[i].set(0f, 0f)
                } else {
                    allCornersSquare = false
                }
            }
            return allCornersSquare
        }

        /** Skia's `radii_are_nine_patch` predicate. */
        private fun radiiAreNinePatch(r: Array<SkPoint>): Boolean =
            r[Corner.kUpperLeft_Corner.index].fX == r[Corner.kLowerLeft_Corner.index].fX &&
                r[Corner.kUpperLeft_Corner.index].fY == r[Corner.kUpperRight_Corner.index].fY &&
                r[Corner.kUpperRight_Corner.index].fX == r[Corner.kLowerRight_Corner.index].fX &&
                r[Corner.kLowerLeft_Corner.index].fY == r[Corner.kLowerRight_Corner.index].fY

        /** Skia's `compute_min_scale`. Doubles to avoid float underflow on huge/tiny pairs. */
        private fun computeMinScale(rad1: Float, rad2: Float, limit: Double, prev: Double): Double {
            val sum = rad1.toDouble() + rad2.toDouble()
            return if (sum > limit && sum > 0.0) minOf(prev, limit / sum) else prev
        }

        /** Skia's `flush_to_zero`: if `a + b == a` (b too small to matter), force `b = 0`. */
        private fun flushToZero(radii: Array<SkPoint>, i: Int, j: Int, axisX: Boolean) {
            val a = if (axisX) radii[i].fX else radii[i].fY
            val b = if (axisX) radii[j].fX else radii[j].fY
            if (a + b == a) {
                if (axisX) radii[j].fX = 0f else radii[j].fY = 0f
            } else if (a + b == b) {
                if (axisX) radii[i].fX = 0f else radii[i].fY = 0f
            }
        }

        /**
         * Skia's `SkScaleToSides::AdjustRadii`. After `a *= scale` and
         * `b *= scale`, if `a + b > limit` due to rounding, reduces the
         * larger radius an ULP at a time until it fits.
         */
        private fun adjustRadii(
            limit: Double,
            scale: Double,
            radii: Array<SkPoint>,
            iA: Int,
            iB: Int,
            axisX: Boolean,
        ) {
            // Read.
            val readA = if (axisX) radii[iA].fX else radii[iA].fY
            val readB = if (axisX) radii[iB].fX else radii[iB].fY
            var a = (readA.toDouble() * scale).toFloat()
            var b = (readB.toDouble() * scale).toFloat()

            if (a + b > limit) {
                // Identify min/max
                val aIsMin = a <= b
                val newMin = if (aIsMin) a else b
                var newMax = (limit - newMin).toFloat()
                while (newMax + newMin > limit) {
                    newMax = Math.nextAfter(newMax, 0.0)
                }
                if (aIsMin) b = newMax else a = newMax
            }
            // Write back.
            if (axisX) {
                radii[iA].fX = a; radii[iB].fX = b
            } else {
                radii[iA].fY = a; radii[iB].fY = b
            }
        }
    }
}
