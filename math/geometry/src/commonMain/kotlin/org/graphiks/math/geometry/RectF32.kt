package org.graphiks.math.geometry

import org.graphiks.math.scalar.nearlyZero
import org.graphiks.math.vector.Vector2F32
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round

public data class RectF32(
    var left: Float,
    var top: Float,
    var right: Float,
    var bottom: Float,
) {
    public fun x(): Float = left
    public fun y(): Float = top
    public fun left(): Float = left
    public fun top(): Float = top
    public fun right(): Float = right
    public fun bottom(): Float = bottom

    public fun width(): Float = right - left

    public fun height(): Float = bottom - top

    public fun centerX(): Float = (0.5 * (left.toDouble() + right)).toFloat()

    public fun centerY(): Float = (0.5 * (top.toDouble() + bottom)).toFloat()

    public fun center(): Vector2F32 = Vector2F32.of(centerX(), centerY())

    public fun topLeft(): Vector2F32 = Vector2F32.of(left, top)

    public fun TL(): Vector2F32 = Vector2F32.of(left, top)
    public fun TR(): Vector2F32 = Vector2F32.of(right, top)
    public fun BL(): Vector2F32 = Vector2F32.of(left, bottom)
    public fun BR(): Vector2F32 = Vector2F32.of(right, bottom)

    public val isEmpty: Boolean get() = !(left < right && top < bottom)

    public fun isSorted(): Boolean = left <= right && top <= bottom

    public fun isFinite(): Boolean =
        left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite()

    public fun contentEqualsLTRB(other: RectF32): Boolean =
        left == other.left && top == other.top && right == other.right && bottom == other.bottom

    public fun setEmpty() { left = 0f; top = 0f; right = 0f; bottom = 0f }

    public fun setLTRB(l: Float, t: Float, r: Float, b: Float) {
        left = l; top = t; right = r; bottom = b
    }

    public fun setXYWH(x: Float, y: Float, w: Float, h: Float) {
        setLTRB(x, y, x + w, y + h)
    }

    public fun setWH(w: Float, h: Float) {
        setLTRB(0f, 0f, w, h)
    }

    public fun set(src: RectI32) {
        left = src.left.toFloat()
        top = src.top.toFloat()
        right = src.right.toFloat()
        bottom = src.bottom.toFloat()
    }

    public fun offset(dx: Float, dy: Float) {
        left += dx; top += dy; right += dx; bottom += dy
    }

    public fun offset(delta: Vector2F32) { offset(delta.x, delta.y) }

    public fun offsetTo(newX: Float, newY: Float) {
        right += newX - left
        bottom += newY - top
        left = newX
        top = newY
    }

    public fun inset(dx: Float, dy: Float) {
        left += dx; top += dy; right -= dx; bottom -= dy
    }

    public fun outset(dx: Float, dy: Float) { inset(-dx, -dy) }

    public fun adjust(dL: Float, dT: Float, dR: Float, dB: Float) {
        left += dL; top += dT; right += dR; bottom += dB
    }

    public fun sort() {
        if (left > right) { val t = left; left = right; right = t }
        if (top > bottom) { val t = top; top = bottom; bottom = t }
    }

    public fun makeSorted(): RectF32 {
        val l = minOf(left, right)
        val r = maxOf(left, right)
        val t = minOf(top, bottom)
        val b = maxOf(top, bottom)
        return RectF32(l, t, r, b)
    }

    public fun offsetBy(dx: Float, dy: Float): RectF32 =
        RectF32(left + dx, top + dy, right + dx, bottom + dy)

    public fun offsetBy(v: Vector2F32): RectF32 = offsetBy(v.x, v.y)

    public fun insetBy(dx: Float, dy: Float): RectF32 =
        RectF32(left + dx, top + dy, right - dx, bottom - dy)

    public fun outsetBy(dx: Float, dy: Float): RectF32 = insetBy(-dx, -dy)

    public fun contains(x: Float, y: Float): Boolean =
        x >= left && x < right && y >= top && y < bottom

    public fun contains(r: RectF32): Boolean =
        !r.isEmpty && !this.isEmpty &&
            left <= r.left && top <= r.top && right >= r.right && bottom >= r.bottom

    public fun contains(r: RectI32): Boolean =
        !r.isEmpty && !this.isEmpty &&
            left <= r.left.toFloat() && top <= r.top.toFloat() &&
            right >= r.right.toFloat() && bottom >= r.bottom.toFloat()

    public fun intersect(r: RectF32): Boolean {
        val l = maxOf(left, r.left)
        val t = maxOf(top, r.top)
        val ri = minOf(right, r.right)
        val b = minOf(bottom, r.bottom)
        if (!(l < ri && t < b)) return false
        setLTRB(l, t, ri, b)
        return true
    }

    public fun intersects(r: RectF32): Boolean =
        Intersects(left, top, right, bottom, r.left, r.top, r.right, r.bottom)

    public fun join(r: RectF32) {
        if (r.isEmpty) return
        if (this.isEmpty) {
            setLTRB(r.left, r.top, r.right, r.bottom)
        } else {
            joinNonEmptyArg(r)
        }
    }

    public fun joinNonEmptyArg(r: RectF32) {
        if (r.left < left) left = r.left
        if (r.top < top) top = r.top
        if (r.right > right) right = r.right
        if (r.bottom > bottom) bottom = r.bottom
    }

    public fun joinPossiblyEmptyRect(r: RectF32) {
        left = minOf(left, r.left)
        top = minOf(top, r.top)
        right = maxOf(right, r.right)
        bottom = maxOf(bottom, r.bottom)
    }

    public fun round(): RectI32 = RectI32(
        round(left).toInt(), round(top).toInt(),
        round(right).toInt(), round(bottom).toInt(),
    )

    public fun roundOut(): RectI32 = RectI32(
        floor(left.toDouble()).toInt(), floor(top.toDouble()).toInt(),
        ceil(right.toDouble()).toInt(), ceil(bottom.toDouble()).toInt(),
    )

    public fun roundIn(): RectI32 = RectI32(
        ceil(left.toDouble()).toInt(), ceil(top.toDouble()).toInt(),
        floor(right.toDouble()).toInt(), floor(bottom.toDouble()).toInt(),
    )

    public companion object {
        public fun ofLTRB(l: Float, t: Float, r: Float, b: Float): RectF32 =
            RectF32(l, t, r, b)

        public fun ofOriginSize(x: Float, y: Float, w: Float, h: Float): RectF32 =
            RectF32(x, y, x + w, y + h)

        public fun ofSize(w: Float, h: Float): RectF32 = RectF32(0f, 0f, w, h)

        public fun ofIntSize(w: Int, h: Int): RectF32 = RectF32(0f, 0f, w.toFloat(), h.toFloat())

        public val Empty: RectF32 = RectF32(0f, 0f, 0f, 0f)

        public fun from(size: SizeI32): RectF32 =
            RectF32(0f, 0f, size.width.toFloat(), size.height.toFloat())

        public fun from(rect: RectI32): RectF32 = RectF32(
            rect.left.toFloat(), rect.top.toFloat(),
            rect.right.toFloat(), rect.bottom.toFloat(),
        )

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

        public fun Intersects(a: RectF32, b: RectF32): Boolean =
            Intersects(a.left, a.top, a.right, a.bottom, b.left, b.top, b.right, b.bottom)

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
