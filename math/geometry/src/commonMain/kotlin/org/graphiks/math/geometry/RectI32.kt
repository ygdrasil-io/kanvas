package org.graphiks.math.geometry

/**
 * Mutable axis-aligned integer rectangle.
 *
 */
public data class RectI32(
    var left: Int,
    var top: Int,
    var right: Int,
    var bottom: Int,
) {
    /** Returns [left]. */
    public fun x(): Int = left

    /** Returns [top]. */
    public fun y(): Int = top

    /** Returns the left edge. */
    public fun left(): Int = left

    /** Returns the top edge. */
    public fun top(): Int = top

    /** Returns the right edge. */
    public fun right(): Int = right

    /** Returns the bottom edge. */
    public fun bottom(): Int = bottom

    /** Returns `right - left`. */
    public fun width(): Int = right - left

    /** Returns `bottom - top`. */
    public fun height(): Int = bottom - top

    /** Returns width as a 64-bit value (for overflow safety). */
    public fun width64(): Long = right.toLong() - left.toLong()

    /** Returns height as a 64-bit value (for overflow safety). */
    public fun height64(): Long = bottom.toLong() - top.toLong()

    /** Returns the top-left corner. */
    public fun topLeft(): Vector2I32 = Vector2I32(left, top)

    /** `true` if the rect is empty (non-positive size or overflow). */
    public val isEmpty: Boolean get() {
        val w = width64()
        val h = height64()
        if (w <= 0L || h <= 0L) return true
        return w > Int.MAX_VALUE.toLong() || h > Int.MAX_VALUE.toLong()
    }

    /** `true` if `right <= left || bottom <= top`. */
    public fun isEmpty64(): Boolean = right <= left || bottom <= top

    /** `true` if `left <= right && top <= bottom`. */
    public fun isSorted(): Boolean = left <= right && top <= bottom

    /** Sets all edges to zero (empty rect). */
    public fun setEmpty() { left = 0; top = 0; right = 0; bottom = 0 }

    /** Sets edges from LTRB values. */
    public fun setLTRB(l: Int, t: Int, r: Int, b: Int) {
        left = l; top = t; right = r; bottom = b
    }

    /** Sets edges from origin `(x, y)` and size `(w, h)` with saturating add. */
    public fun setXYWH(x: Int, y: Int, w: Int, h: Int) {
        setLTRB(x, y, Vector2I32.saturatingAdd32(x, w), Vector2I32.saturatingAdd32(y, h))
    }

    /** Sets the rect to `(0, 0, w, h)`. */
    public fun setWH(w: Int, h: Int) { setLTRB(0, 0, w, h) }

    /** Translates by `(dx, dy)` with saturating arithmetic. */
    public fun offset(dx: Int, dy: Int) {
        left = Vector2I32.saturatingAdd32(left, dx)
        top = Vector2I32.saturatingAdd32(top, dy)
        right = Vector2I32.saturatingAdd32(right, dx)
        bottom = Vector2I32.saturatingAdd32(bottom, dy)
    }

    /** Translates by [delta]. */
    public fun offset(delta: Vector2I32) { offset(delta.x, delta.y) }

    /** Moves the rect to `(newX, newY)` preserving size with saturating arithmetic. */
    public fun offsetTo(newX: Int, newY: Int) {
        right = Vector2I32.pinToInt32(right.toLong() + newX.toLong() - left.toLong())
        bottom = Vector2I32.pinToInt32(bottom.toLong() + newY.toLong() - top.toLong())
        left = newX
        top = newY
    }

    /** Insets by `(dx, dy)` with saturating arithmetic. */
    public fun inset(dx: Int, dy: Int) {
        left = Vector2I32.saturatingAdd32(left, dx)
        top = Vector2I32.saturatingAdd32(top, dy)
        right = Vector2I32.saturatingSub32(right, dx)
        bottom = Vector2I32.saturatingSub32(bottom, dy)
    }

    /** Outsets by `(dx, dy)` with saturating arithmetic. */
    public fun outset(dx: Int, dy: Int) {
        left = Vector2I32.saturatingSub32(left, dx)
        top = Vector2I32.saturatingSub32(top, dy)
        right = Vector2I32.saturatingAdd32(right, dx)
        bottom = Vector2I32.saturatingAdd32(bottom, dy)
    }

    /** Adjusts edges individually with saturating arithmetic. */
    public fun adjust(dL: Int, dT: Int, dR: Int, dB: Int) {
        left = Vector2I32.saturatingAdd32(left, dL)
        top = Vector2I32.saturatingAdd32(top, dT)
        right = Vector2I32.saturatingAdd32(right, dR)
        bottom = Vector2I32.saturatingAdd32(bottom, dB)
    }

    /** Sorts edges so `left <= right` and `top <= bottom`. */
    public fun sort() {
        if (left > right) { val t = left; left = right; right = t }
        if (top > bottom) { val t = top; top = bottom; bottom = t }
    }

    /** Returns a sorted copy of this rect. */
    public fun makeSorted(): RectI32 = RectI32(
        minOf(left, right), minOf(top, bottom),
        maxOf(left, right), maxOf(top, bottom),
    )

    /** Returns a copy translated by `(dx, dy)` with saturating arithmetic. */
    public fun offsetBy(dx: Int, dy: Int): RectI32 = RectI32(
        Vector2I32.saturatingAdd32(left, dx),
        Vector2I32.saturatingAdd32(top, dy),
        Vector2I32.saturatingAdd32(right, dx),
        Vector2I32.saturatingAdd32(bottom, dy),
    )

    /** Returns a copy inset by `(dx, dy)` with saturating arithmetic. */
    public fun insetBy(dx: Int, dy: Int): RectI32 = RectI32(
        Vector2I32.saturatingAdd32(left, dx),
        Vector2I32.saturatingAdd32(top, dy),
        Vector2I32.saturatingSub32(right, dx),
        Vector2I32.saturatingSub32(bottom, dy),
    )

    /** Returns a copy outset by `(dx, dy)` with saturating arithmetic. */
    public fun outsetBy(dx: Int, dy: Int): RectI32 = RectI32(
        Vector2I32.saturatingSub32(left, dx),
        Vector2I32.saturatingSub32(top, dy),
        Vector2I32.saturatingAdd32(right, dx),
        Vector2I32.saturatingAdd32(bottom, dy),
    )

    /** Returns `true` if the point `(x, y)` is inside this rect. */
    public fun contains(x: Int, y: Int): Boolean =
        x >= left && x < right && y >= top && y < bottom

    /** Returns `true` if [r] is entirely inside this rect. */
    public fun contains(r: RectI32): Boolean =
        !r.isEmpty && !this.isEmpty &&
            left <= r.left && top <= r.top && right >= r.right && bottom >= r.bottom

    /** Returns `true` if [r] is inside this rect (no empty check). */
    public fun containsNoEmptyCheck(r: RectI32): Boolean =
        left <= r.left && top <= r.top && right >= r.right && bottom >= r.bottom

    /**
     * Intersects this rect with [r] in place. Returns `true` if the
     * result is non-empty.
     */
    public fun intersect(r: RectI32): Boolean {
        val l = maxOf(left, r.left)
        val t = maxOf(top, r.top)
        val ri = minOf(right, r.right)
        val b = minOf(bottom, r.bottom)
        if (l >= ri || t >= b) return false
        setLTRB(l, t, ri, b)
        return true
    }

    /** Joins this rect with [r] in place (union). */
    public fun join(r: RectI32) {
        if (r.left >= r.right || r.top >= r.bottom) return
        if (left >= right || top >= bottom) {
            setLTRB(r.left, r.top, r.right, r.bottom)
        } else {
            if (r.left < left) left = r.left
            if (r.top < top) top = r.top
            if (r.right > right) right = r.right
            if (r.bottom > bottom) bottom = r.bottom
        }
    }

    public companion object {
        /** Creates a [RectI32] from LTRB values. */
        public fun ofLTRB(l: Int, t: Int, r: Int, b: Int): RectI32 = RectI32(l, t, r, b)

        /** Creates a [RectI32] from origin `(x, y)` and size `(w, h)` with saturating add. */
        public fun ofOriginSize(x: Int, y: Int, w: Int, h: Int): RectI32 =
            RectI32(x, y, Vector2I32.saturatingAdd32(x, w), Vector2I32.saturatingAdd32(y, h))

        /** Creates a [RectI32] from size `(w, h)` at origin. */
        public fun ofSize(w: Int, h: Int): RectI32 = RectI32(0, 0, w, h)

        /** Empty rect (all zeros). */
        public val Empty: RectI32 get() = RectI32(0, 0, 0, 0)

        /** Creates a [RectI32] from a [SizeI32] at origin. */
        public fun fromSize(size: SizeI32): RectI32 = RectI32(0, 0, size.width, size.height)

        /** Creates a [RectI32] from a point and size. */
        public fun fromPointSize(pt: Vector2I32, size: SizeI32): RectI32 =
            ofOriginSize(pt.x, pt.y, size.width, size.height)

        /** Returns `true` if [a] and [b] intersect. */
        public fun intersects(a: RectI32, b: RectI32): Boolean {
            val l = maxOf(a.left, b.left)
            val r = minOf(a.right, b.right)
            val t = maxOf(a.top, b.top)
            val bot = minOf(a.bottom, b.bottom)
            return l < r && t < bot
        }
    }
}
