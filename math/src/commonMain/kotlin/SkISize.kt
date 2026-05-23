package org.graphiks.math

public data class SkISize(val width: Int, val height: Int) {
    public fun isEmpty(): Boolean = width <= 0 || height <= 0
    public fun isZero(): Boolean = width == 0 && height == 0

    public companion object {
        public fun Make(w: Int, h: Int): SkISize = SkISize(w, h)
        public fun MakeEmpty(): SkISize = SkISize(0, 0)
    }
}

public data class SkSize(val width: SkScalar, val height: SkScalar) {
    public fun isEmpty(): Boolean = width <= 0f || height <= 0f

    public companion object {
        public fun Make(w: SkScalar, h: SkScalar): SkSize = SkSize(w, h)
        public fun Make(size: SkISize): SkSize =
            SkSize(size.width.toFloat(), size.height.toFloat())
        public fun MakeEmpty(): SkSize = SkSize(0f, 0f)
    }
}
