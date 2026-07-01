package org.graphiks.kanvas.types

@JvmInline
value class Color(val packed: UInt) {
    companion object {
        fun fromRGBA(r: Float, g: Float, b: Float, a: Float = 1f): Color {
            fun clamp(v: Float): UInt = (v.coerceIn(0f, 1f) * 255f).toInt().toUInt()
            val packed = (clamp(a) shl 24) or (clamp(r) shl 16) or (clamp(g) shl 8) or clamp(b)
            return Color(packed)
        }

        val BLACK: Color = Color(0xFF000000u)
        val WHITE: Color = Color(0xFFFFFFFFu)
        val RED: Color = Color(0xFFFF0000u)
        val GREEN: Color = Color(0xFF00FF00u)
        val BLUE: Color = Color(0xFF0000FFu)
        val TRANSPARENT: Color = Color(0x00000000u)
    }

    override fun toString(): String {
        val a = ((packed shr 24) and 0xFFu).toInt()
        val r = ((packed shr 16) and 0xFFu).toInt()
        val g = ((packed shr 8) and 0xFFu).toInt()
        val b = ((packed shr 0) and 0xFFu).toInt()
        return "#${a.toString(16).padStart(2, '0')}${r.toString(16).padStart(2, '0')}${g.toString(16).padStart(2, '0')}${b.toString(16).padStart(2, '0')}"
    }
}

val Color.r: Float get() = ((packed shr 16) and 0xFFu).toFloat() / 255f
val Color.g: Float get() = ((packed shr 8) and 0xFFu).toFloat() / 255f
val Color.b: Float get() = ((packed shr 0) and 0xFFu).toFloat() / 255f
val Color.a: Float get() = ((packed shr 24) and 0xFFu).toFloat() / 255f
