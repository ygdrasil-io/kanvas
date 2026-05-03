package org.skia.math

public data class SkRect(
    var left: SkScalar,
    var top: SkScalar,
    var right: SkScalar,
    var bottom: SkScalar,
) {
    public val width: SkScalar get() = right - left
    public val height: SkScalar get() = bottom - top
    public val centerX: SkScalar get() = (left + right) * 0.5f
    public val centerY: SkScalar get() = (top + bottom) * 0.5f
    public val isEmpty: Boolean get() = left >= right || top >= bottom

    public fun offset(dx: SkScalar, dy: SkScalar) {
        left += dx; top += dy; right += dx; bottom += dy
    }

    public fun inset(dx: SkScalar, dy: SkScalar) {
        left += dx; top += dy; right -= dx; bottom -= dy
    }

    public fun contains(x: SkScalar, y: SkScalar): Boolean =
        x >= left && x < right && y >= top && y < bottom

    public companion object {
        public fun MakeLTRB(l: SkScalar, t: SkScalar, r: SkScalar, b: SkScalar): SkRect =
            SkRect(l, t, r, b)

        public fun MakeXYWH(x: SkScalar, y: SkScalar, w: SkScalar, h: SkScalar): SkRect =
            SkRect(x, y, x + w, y + h)

        public fun MakeWH(w: SkScalar, h: SkScalar): SkRect = SkRect(0f, 0f, w, h)

        public fun MakeEmpty(): SkRect = SkRect(0f, 0f, 0f, 0f)

        public fun Make(size: SkISize): SkRect =
            SkRect(0f, 0f, size.width.toFloat(), size.height.toFloat())
    }
}

public data class SkIRect(
    var left: Int,
    var top: Int,
    var right: Int,
    var bottom: Int,
) {
    public val width: Int get() = right - left
    public val height: Int get() = bottom - top
    public val isEmpty: Boolean get() = left >= right || top >= bottom

    public companion object {
        public fun MakeLTRB(l: Int, t: Int, r: Int, b: Int): SkIRect = SkIRect(l, t, r, b)
        public fun MakeXYWH(x: Int, y: Int, w: Int, h: Int): SkIRect = SkIRect(x, y, x + w, y + h)
        public fun MakeWH(w: Int, h: Int): SkIRect = SkIRect(0, 0, w, h)
        public fun MakeEmpty(): SkIRect = SkIRect(0, 0, 0, 0)
        public fun MakeSize(size: SkISize): SkIRect = SkIRect(0, 0, size.width, size.height)
    }
}
