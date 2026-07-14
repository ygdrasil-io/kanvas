package org.graphiks.math.geometry

import org.graphiks.math.scalar.nearlyZero
import org.graphiks.math.vector.Vector2F32
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round

/**
 * Mutable axis-aligned float rectangle.
 *
 */
public data class RectF32(
    var left: Float,
    var top: Float,
    var right: Float,
    var bottom: Float,
) {
    /** Returns [left]. */
    public fun x(): Float = left

    /** Returns [top]. */
    public fun y(): Float = top

    /** Returns the left edge. */
    public fun left(): Float = left

    /** Returns the top edge. */
    public fun top(): Float = top

    /** Returns the right edge. */
    public fun right(): Float = right

    /** Returns the bottom edge. */
    public fun bottom(): Float = bottom

    /** Returns `right - left`. */
    public fun width(): Float = right - left

    /** Returns `bottom - top`. */
    public fun height(): Float = bottom - top

    /** Returns the x-coordinate of the center (computed in double precision). */
    public fun centerX(): Float = (0.5 * (left.toDouble() + right)).toFloat()

    /** Returns the y-coordinate of the center (computed in double precision). */
    public fun centerY(): Float = (0.5 * (top.toDouble() + bottom)).toFloat()

    /** Returns the center as a [Vector2F32]. */
    public fun center(): Vector2F32 = Vector2F32.of(centerX(), centerY())

    /** Returns the top-left corner. */
    public fun topLeft(): Vector2F32 = Vector2F32.of(left, top)

    /** Returns the top-left corner. */
    public fun TL(): Vector2F32 = Vector2F32.of(left, top)

    /** Returns the top-right corner. */
    public fun TR(): Vector2F32 = Vector2F32.of(right, top)

    /** Returns the bottom-left corner. */
    public fun BL(): Vector2F32 = Vector2F32.of(left, bottom)

    /** Returns the bottom-right corner. */
    public fun BR(): Vector2F32 = Vector2F32.of(right, bottom)

    /** `true` if the rect is empty (`left >= right` or `top >= bottom`). */
    public val isEmpty: Boolean get() = !(left < right && top < bottom)

    /** `true` if `left <= right && top <= bottom`. */
    public fun isSorted(): Boolean = left <= right && top <= bottom

    /** `true` if all edges are finite. */
    public fun isFinite(): Boolean =
        left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite()

    /** Returns `true` if [other] has the same LTRB values. */
    public fun contentEqualsLTRB(other: RectF32): Boolean =
        left == other.left && top == other.top && right == other.right && bottom == other.bottom

    /** Sets all edges to zero (empty rect). */
    public fun setEmpty() { left = 0f; top = 0f; right = 0f; bottom = 0f }

    /** Sets edges from LTRB values. */
    public fun setLTRB(l: Float, t: Float, r: Float, b: Float) {
        left = l; top = t; right = r; bottom = b
    }

    /** Sets edges from origin `(x, y)` and size `(w, h)`. */
    public fun setXYWH(x: Float, y: Float, w: Float, h: Float) {
        setLTRB(x, y, x + w, y + h)
    }

    /** Sets the rect to `(0, 0, w, h)`. */
    public fun setWH(w: Float, h: Float) {
        setLTRB(0f, 0f, w, h)
    }

    /** Copies edges from [src]. */
    public fun set(src: RectI32) {
        left = src.left.toFloat()
        top = src.top.toFloat()
        right = src.right.toFloat()
        bottom = src.bottom.toFloat()
    }

    /** Translates all edges by `(dx, dy)`. */
    public fun offset(dx: Float, dy: Float) {
        left += dx; top += dy; right += dx; bottom += dy
    }

    /** Translates by [delta]. */
    public fun offset(delta: Vector2F32) { offset(delta.x, delta.y) }

    /** Moves the rect to `(newX, newY)` preserving size. */
    public fun offsetTo(newX: Float, newY: Float) {
        right += newX - left
        bottom += newY - top
        left = newX
        top = newY
    }

    /** Insets edges by `(dx, dy)`. */
    public fun inset(dx: Float, dy: Float) {
        left += dx; top += dy; right -= dx; bottom -= dy
    }

    /** Outsets edges by `(dx, dy)` (negative inset). */
    public fun outset(dx: Float, dy: Float) { inset(-dx, -dy) }

    /** Adjusts edges individually: `(dL, dT, dR, dB)`. */
    public fun adjust(dL: Float, dT: Float, dR: Float, dB: Float) {
        left += dL; top += dT; right += dR; bottom += dB
    }

    /** Sorts edges so `left <= right` and `top <= bottom`. */
    public fun sort() {
        if (left > right) { val t = left; left = right; right = t }
        if (top > bottom) { val t = top; top = bottom; bottom = t }
    }

    /** Returns a sorted copy of this rect. */
    public fun makeSorted(): RectF32 {
        val l = minOf(left, right)
        val r = maxOf(left, right)
        val t = minOf(top, bottom)
        val b = maxOf(top, bottom)
        return RectF32(l, t, r, b)
    }

    /** Returns a copy translated by `(dx, dy)`. */
    public fun offsetBy(dx: Float, dy: Float): RectF32 =
        RectF32(left + dx, top + dy, right + dx, bottom + dy)

    /** Returns a copy translated by [v]. */
    public fun offsetBy(v: Vector2F32): RectF32 = offsetBy(v.x, v.y)

    /** Returns a copy inset by `(dx, dy)`. */
    public fun insetBy(dx: Float, dy: Float): RectF32 =
        RectF32(left + dx, top + dy, right - dx, bottom - dy)

    /** Returns a copy outset by `(dx, dy)`. */
    public fun outsetBy(dx: Float, dy: Float): RectF32 = insetBy(-dx, -dy)

    /** Returns `true` if the point `(x, y)` is inside this rect. */
    public fun contains(x: Float, y: Float): Boolean =
        x >= left && x < right && y >= top && y < bottom

    /** Returns `true` if [r] is entirely inside this rect. */
    public fun contains(r: RectF32): Boolean =
        !r.isEmpty && !this.isEmpty &&
            left <= r.left && top <= r.top && right >= r.right && bottom >= r.bottom

    /** Returns `true` if [r] is entirely inside this rect. */
    public fun contains(r: RectI32): Boolean =
        !r.isEmpty && !this.isEmpty &&
            left <= r.left.toFloat() && top <= r.top.toFloat() &&
            right >= r.right.toFloat() && bottom >= r.bottom.toFloat()

    /**
     * Intersects this rect with [r] in place. Returns `true` if the
     * result is non-empty.
     */
    public fun intersect(r: RectF32): Boolean {
        val l = maxOf(left, r.left)
        val t = maxOf(top, r.top)
        val ri = minOf(right, r.right)
        val b = minOf(bottom, r.bottom)
        if (!(l < ri && t < b)) return false
        setLTRB(l, t, ri, b)
        return true
    }

    /** Returns `true` if this rect intersects [r]. */
    public fun intersects(r: RectF32): Boolean =
        Intersects(left, top, right, bottom, r.left, r.top, r.right, r.bottom)

    /** Joins this rect with [r] in place (union). */
    public fun join(r: RectF32) {
        if (r.isEmpty) return
        if (this.isEmpty) {
            setLTRB(r.left, r.top, r.right, r.bottom)
        } else {
            joinNonEmptyArg(r)
        }
    }

    /** Joins assuming [r] is non-empty. */
    public fun joinNonEmptyArg(r: RectF32) {
        if (r.left < left) left = r.left
        if (r.top < top) top = r.top
        if (r.right > right) right = r.right
        if (r.bottom > bottom) bottom = r.bottom
    }

    /** Joins using min/max (allows empty rects). */
    public fun joinPossiblyEmptyRect(r: RectF32) {
        left = minOf(left, r.left)
        top = minOf(top, r.top)
        right = maxOf(right, r.right)
        bottom = maxOf(bottom, r.bottom)
    }

    /** Rounds all edges to the nearest integer, returning a [RectI32]. */
    public fun round(): RectI32 = RectI32(
        round(left).toInt(), round(top).toInt(),
        round(right).toInt(), round(bottom).toInt(),
    )

    /** Rounds outward (floor left/top, ceil right/bottom), returning a [RectI32]. */
    public fun roundOut(): RectI32 = RectI32(
        floor(left.toDouble()).toInt(), floor(top.toDouble()).toInt(),
        ceil(right.toDouble()).toInt(), ceil(bottom.toDouble()).toInt(),
    )

    /** Rounds inward (ceil left/top, floor right/bottom), returning a [RectI32]. */
    public fun roundIn(): RectI32 = RectI32(
        ceil(left.toDouble()).toInt(), ceil(top.toDouble()).toInt(),
        floor(right.toDouble()).toInt(), floor(bottom.toDouble()).toInt(),
    )

    public companion object {
        /** Creates a [RectF32] from LTRB values. */
        public fun ofLTRB(l: Float, t: Float, r: Float, b: Float): RectF32 =
            RectF32(l, t, r, b)

        /** Creates a [RectF32] from origin `(x, y)` and size `(w, h)`. */
        public fun ofOriginSize(x: Float, y: Float, w: Float, h: Float): RectF32 =
            RectF32(x, y, x + w, y + h)

        /** Creates a [RectF32] from size `(w, h)` at origin. */
        public fun ofSize(w: Float, h: Float): RectF32 = RectF32(0f, 0f, w, h)

        /** Creates a [RectF32] from integer size at origin. */
        public fun ofIntSize(w: Int, h: Int): RectF32 = RectF32(0f, 0f, w.toFloat(), h.toFloat())

        /** Empty rect (all zeros). */
        public val Empty: RectF32 get() = RectF32(0f, 0f, 0f, 0f)

        /** Creates a [RectF32] from a [SizeI32] at origin. */
        public fun from(size: SizeI32): RectF32 =
            RectF32(0f, 0f, size.width.toFloat(), size.height.toFloat())

        /** Creates a [RectF32] from a [RectI32]. */
        public fun from(rect: RectI32): RectF32 = RectF32(
            rect.left.toFloat(), rect.top.toFloat(),
            rect.right.toFloat(), rect.bottom.toFloat(),
        )

        /**
         * Returns `true` if the two axis-aligned rects intersect.
                 */
        public fun Intersects(
            al: Float, at: Float, ar: Float, ab: Float,
            bl: Float, bt: Float, br: Float, bb: Float,
        ): Boolean {
            val l = maxOf(al, bl)
            val r = minOf(ar, br)
            val t = maxOf(at, bt)
            val b = minOf(ab, bb)
            return l < r && t < b
        }

        /** Returns `true` if [a] and [b] intersect. */
        public fun Intersects(a: RectF32, b: RectF32): Boolean =
            Intersects(a.left, a.top, a.right, a.bottom, b.left, b.top, b.right, b.bottom)

        /**
         * Returns a [RectF32] bounding all points, or `null` if any
         * component overflows (returns [Empty] for an empty array).
         */
        public fun Bounds(points: Array<Vector2F32>): RectF32? {
            if (points.isEmpty()) return Empty
            var l = points[0].x; var r = points[0].x
            var t = points[0].y; var b = points[0].y
            var nx = 0f; var ny = 0f
            for (p in points) {
                if (p.x < l) l = p.x
                if (p.x > r) r = p.x
                if (p.y < t) t = p.y
                if (p.y > b) b = p.y
                nx *= p.x
                ny *= p.y
            }
            return if (nx == 0f && ny == 0f) RectF32(l, t, r, b) else null
        }
    }
}
