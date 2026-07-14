package org.graphiks.math.geometry

public data class SizeI32(val width: Int, val height: Int) {
    public fun isEmpty(): Boolean = width <= 0 || height <= 0
    public fun isZero(): Boolean = width == 0 && height == 0

    public companion object {
        public fun Make(w: Int, h: Int): SizeI32 = SizeI32(w, h)
        public fun MakeEmpty(): SizeI32 = SizeI32(0, 0)
    }
}

public data class SizeF32(val width: Float, val height: Float) {
    public fun isEmpty(): Boolean = width <= 0f || height <= 0f

    public companion object {
        public fun Make(w: Float, h: Float): SizeF32 = SizeF32(w, h)
        public fun Make(size: SizeI32): SizeF32 =
            SizeF32(size.width.toFloat(), size.height.toFloat())
        public fun MakeEmpty(): SizeF32 = SizeF32(0f, 0f)
    }
}
