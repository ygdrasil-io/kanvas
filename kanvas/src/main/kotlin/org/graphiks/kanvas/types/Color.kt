package org.graphiks.kanvas.types

@JvmInline
value class Color(val packed: UInt) {
    companion object {
        fun fromArgbInt(argb: Int): Color = Color(argb.toUInt())

        fun fromArgb(a: Int, r: Int, g: Int, b: Int): Color {
            val packed = ((a and 0xFF).toUInt() shl 24) or
                ((r and 0xFF).toUInt() shl 16) or
                ((g and 0xFF).toUInt() shl 8) or
                (b and 0xFF).toUInt()
            return Color(packed)
        }

        fun fromRGBA(r: Float, g: Float, b: Float, a: Float = 1f): Color {
            fun quantize(v: Float): UInt = (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt().toUInt()
            val packed = (quantize(a) shl 24) or (quantize(r) shl 16) or (quantize(g) shl 8) or quantize(b)
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

fun Color.toArgbInt(): Int = packed.toInt()
fun Color.withAlphaByte(alpha: Int): Color = Color.fromArgb(alpha, redByte, greenByte, blueByte)
val Color.alphaByte: Int get() = ((packed shr 24) and 0xFFu).toInt()
val Color.redByte: Int get() = ((packed shr 16) and 0xFFu).toInt()
val Color.greenByte: Int get() = ((packed shr 8) and 0xFFu).toInt()
val Color.blueByte: Int get() = (packed and 0xFFu).toInt()
val Color.isOpaque: Boolean get() = alphaByte == 0xFF
val Color.r: Float get() = ((packed shr 16) and 0xFFu).toFloat() / 255f
val Color.g: Float get() = ((packed shr 8) and 0xFFu).toFloat() / 255f
val Color.b: Float get() = ((packed shr 0) and 0xFFu).toFloat() / 255f
val Color.a: Float get() = ((packed shr 24) and 0xFFu).toFloat() / 255f
