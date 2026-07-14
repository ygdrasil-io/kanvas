package org.graphiks.math.geometry

public data class SizeI32(val width: Int, val height: Int) {
    public fun isEmpty(): Boolean = width <= 0 || height <= 0
    public fun isZero(): Boolean = width == 0 && height == 0

    public companion object {
        public val Empty: SizeI32 = SizeI32(0, 0)
        public fun of(width: Int, height: Int): SizeI32 = SizeI32(width, height)
    }
}

public data class SizeF32(val width: Float, val height: Float) {
    public fun isEmpty(): Boolean = width <= 0f || height <= 0f

    public companion object {
        public val Empty: SizeF32 = SizeF32(0f, 0f)
        public fun of(width: Float, height: Float): SizeF32 = SizeF32(width, height)
        public fun from(size: SizeI32): SizeF32 =
            SizeF32(size.width.toFloat(), size.height.toFloat())
    }
}
