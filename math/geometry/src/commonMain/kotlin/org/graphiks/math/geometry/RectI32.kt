package org.graphiks.math.geometry

public data class RectI32(
    var left: Int,
    var top: Int,
    var right: Int,
    var bottom: Int,
) {
    public fun x(): Int = left
    public fun y(): Int = top
    public fun left(): Int = left
    public fun top(): Int = top
    public fun right(): Int = right
    public fun bottom(): Int = bottom

    public fun width(): Int = right - left
    public fun height(): Int = bottom - top

    public fun width64(): Long = right.toLong() - left.toLong()
    public fun height64(): Long = bottom.toLong() - top.toLong()

    public fun topLeft(): Vector2I32 = Vector2I32(left, top)

    public val isEmpty: Boolean get() {
        val w = width64()
        val h = height64()
        if (w <= 0L || h <= 0L) return true
        return w > Int.MAX_VALUE.toLong() || h > Int.MAX_VALUE.toLong()
    }

    public fun isEmpty64(): Boolean = right <= left || bottom <= top

    public fun isSorted(): Boolean = left <= right && top <= bottom

    public fun setEmpty() { left = 0; top = 0; right = 0; bottom = 0 }

    public fun setLTRB(l: Int, t: Int, r: Int, b: Int) {
        left = l; top = t; right = r; bottom = b
    }

    public fun setXYWH(x: Int, y: Int, w: Int, h: Int) {
        setLTRB(x, y, Vector2I32.sk32SatAdd(x, w), Vector2I32.sk32SatAdd(y, h))
    }

    public fun setWH(w: Int, h: Int) { setLTRB(0, 0, w, h) }

    public fun offset(dx: Int, dy: Int) {
        left = Vector2I32.sk32SatAdd(left, dx)
        top = Vector2I32.sk32SatAdd(top, dy)
        right = Vector2I32.sk32SatAdd(right, dx)
        bottom = Vector2I32.sk32SatAdd(bottom, dy)
    }

    public fun offset(delta: Vector2I32) { offset(delta.x, delta.y) }

    public fun offsetTo(newX: Int, newY: Int) {
        right = Vector2I32.sk64PinToS32(right.toLong() + newX.toLong() - left.toLong())
        bottom = Vector2I32.sk64PinToS32(bottom.toLong() + newY.toLong() - top.toLong())
        left = newX
        top = newY
    }

    public fun inset(dx: Int, dy: Int) {
        left = Vector2I32.sk32SatAdd(left, dx)
        top = Vector2I32.sk32SatAdd(top, dy)
        right = Vector2I32.sk32SatSub(right, dx)
        bottom = Vector2I32.sk32SatSub(bottom, dy)
    }

    public fun outset(dx: Int, dy: Int) { inset(-dx, -dy) }

    public fun adjust(dL: Int, dT: Int, dR: Int, dB: Int) {
        left = Vector2I32.sk32SatAdd(left, dL)
        top = Vector2I32.sk32SatAdd(top, dT)
        right = Vector2I32.sk32SatAdd(right, dR)
        bottom = Vector2I32.sk32SatAdd(bottom, dB)
    }

    public fun sort() {
        if (left > right) { val t = left; left = right; right = t }
        if (top > bottom) { val t = top; top = bottom; bottom = t }
    }

    public fun makeSorted(): RectI32 = RectI32(
        minOf(left, right), minOf(top, bottom),
        maxOf(left, right), maxOf(top, bottom),
    )

    public fun offsetBy(dx: Int, dy: Int): RectI32 = RectI32(
        Vector2I32.sk32SatAdd(left, dx),
        Vector2I32.sk32SatAdd(top, dy),
        Vector2I32.sk32SatAdd(right, dx),
        Vector2I32.sk32SatAdd(bottom, dy),
    )

    public fun insetBy(dx: Int, dy: Int): RectI32 = RectI32(
        Vector2I32.sk32SatAdd(left, dx),
        Vector2I32.sk32SatAdd(top, dy),
        Vector2I32.sk32SatSub(right, dx),
        Vector2I32.sk32SatSub(bottom, dy),
    )

    public fun outsetBy(dx: Int, dy: Int): RectI32 = insetBy(-dx, -dy)

    public fun contains(x: Int, y: Int): Boolean =
        x >= left && x < right && y >= top && y < bottom

    public fun contains(r: RectI32): Boolean =
        !r.isEmpty && !this.isEmpty &&
            left <= r.left && top <= r.top && right >= r.right && bottom >= r.bottom

    public fun containsNoEmptyCheck(r: RectI32): Boolean =
        left <= r.left && top <= r.top && right >= r.right && bottom >= r.bottom

    public fun intersect(r: RectI32): Boolean {
        val l = maxOf(left, r.left)
        val t = maxOf(top, r.top)
        val ri = minOf(right, r.right)
        val b = minOf(bottom, r.bottom)
        if (l >= ri || t >= b) return false
        setLTRB(l, t, ri, b)
        return true
    }

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
        public fun ofLTRB(l: Int, t: Int, r: Int, b: Int): RectI32 = RectI32(l, t, r, b)
        public fun ofOriginSize(x: Int, y: Int, w: Int, h: Int): RectI32 =
            RectI32(x, y, Vector2I32.sk32SatAdd(x, w), Vector2I32.sk32SatAdd(y, h))
        public fun ofSize(w: Int, h: Int): RectI32 = RectI32(0, 0, w, h)
        public val Empty: RectI32 = RectI32(0, 0, 0, 0)
        public fun fromSize(size: SizeI32): RectI32 = RectI32(0, 0, size.width, size.height)
        public fun fromPointSize(pt: Vector2I32, size: SizeI32): RectI32 =
            ofOriginSize(pt.x, pt.y, size.width, size.height)

        public fun Intersects(a: RectI32, b: RectI32): Boolean {
            val l = maxOf(a.left, b.left)
            val r = minOf(a.right, b.right)
            val t = maxOf(a.top, b.top)
            val bot = minOf(a.bottom, b.bottom)
            return l < r && t < bot
        }
    }
}
