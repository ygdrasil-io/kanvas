package org.graphiks.kanvas.color

/** Backend-neutral scalar conversion recipes. Branches are lazy and constants retain F32 bits. */
public object ColorInterpolationProgramV1 {
    public enum class RecipeKind { EOTF, OETF, RGB_TO_HSL, HSL_TO_RGB, LINEAR_RGB_TO_OKLAB,
        OKLAB_TO_LINEAR_RGB, OKLAB_TO_OKLCH, OKLCH_TO_OKLAB }
    public sealed interface Scalar {
        public data object Input : Scalar
        public data class Component(public val indexI32: Int) : Scalar {
            init { require(indexI32 in 0..2) }
        }
        public data class Constant(public val bitsI32: Int) : Scalar
        public data class Add(public val a: Scalar, public val b: Scalar) : Scalar
        public data class Subtract(public val a: Scalar, public val b: Scalar) : Scalar
        public data class Multiply(public val a: Scalar, public val b: Scalar) : Scalar
        public data class Divide(public val a: Scalar, public val b: Scalar) : Scalar
        public data class Pow(public val a: Scalar, public val b: Scalar) : Scalar
        public data class Min(public val a: Scalar, public val b: Scalar) : Scalar
        public data class Max(public val a: Scalar, public val b: Scalar) : Scalar
        public data class Abs(public val value: Scalar) : Scalar
        public data class Floor(public val value: Scalar) : Scalar
        /** Euclidean integer remainder after a proved, representable floor. */
        public data class IntegerModulo(public val value: Floor, public val modulusI32: Int) : Scalar {
            init { require(modulusI32 in 1..16777216) }
        }
        public data class IfEqual(public val a: Scalar, public val b: Scalar,
            public val yes: Scalar, public val no: Scalar) : Scalar
        public data class IfLessEqual(public val a: Scalar, public val b: Scalar,
            public val yes: Scalar, public val no: Scalar) : Scalar
    }
    public class Recipe internal constructor(public val identity: String, public val root: Scalar,
        outputs: List<Scalar> = listOf(root)) {
        public val outputs: List<Scalar> = java.util.Collections.unmodifiableList(ArrayList(outputs))
    }
    private fun constant(valueF32: Float): Scalar = Scalar.Constant(valueF32.toRawBits())
    private fun transfer(kind: RecipeKind): Recipe {
        val x = Scalar.Input
        val zero = constant(0f)
        val one = constant(1f)
        val curve = if (kind == RecipeKind.EOTF) Scalar.IfLessEqual(x, constant(0.04045f),
            Scalar.Divide(x, constant(12.92f)),
            Scalar.Pow(Scalar.Divide(Scalar.Add(x, constant(0.055f)), constant(1.055f)), constant(2.4f)))
        else Scalar.IfLessEqual(x, constant(0.0031308f), Scalar.Multiply(x, constant(12.92f)),
            Scalar.Subtract(Scalar.Multiply(constant(1.055f), Scalar.Pow(x, constant(1f / 2.4f))), constant(0.055f)))
        return Recipe("color-interpolation-v1:${kind.name}:f32-endpoint-lazy-v1",
            Scalar.IfEqual(x, zero, zero, Scalar.IfEqual(x, one, one, curve)))
    }
    private val eotf = transfer(RecipeKind.EOTF)
    private val oetf = transfer(RecipeKind.OETF)
    private fun modulo(value: Scalar, modulus: Float): Scalar {
        require(modulus == 1f || modulus == 2f)
        val quotient = if (modulus == 1f) value else Scalar.Multiply(value,constant(.5f))
        return Scalar.Subtract(value,Scalar.Multiply(Scalar.Floor(quotient),constant(modulus)))
    }
    private fun rgbToHsl(): Recipe {
        val r = Scalar.Component(0); val g = Scalar.Component(1); val b = Scalar.Component(2)
        val max = Scalar.Max(r,Scalar.Max(g,b)); val min = Scalar.Min(r,Scalar.Min(g,b))
        val delta = Scalar.Subtract(max,min)
        val light = Scalar.Divide(Scalar.Add(max,min),constant(2f))
        val saturation = Scalar.Divide(delta,Scalar.Subtract(constant(1f),
            Scalar.Abs(Scalar.Subtract(Scalar.Multiply(constant(2f),light),constant(1f)))))
        val hue = Scalar.IfEqual(max,r,Scalar.Divide(Scalar.Subtract(g,b),delta),
            Scalar.IfEqual(max,g,Scalar.Add(Scalar.Divide(Scalar.Subtract(b,r),delta),constant(2f)),
                Scalar.Add(Scalar.Divide(Scalar.Subtract(r,g),delta),constant(4f))))
        val h = Scalar.IfEqual(delta,constant(0f),constant(0f),modulo(Scalar.Divide(hue,constant(6f)),1f))
        val s = Scalar.IfEqual(delta,constant(0f),constant(0f),saturation)
        return Recipe("color-interpolation-v1:RGB_TO_HSL:max-r-g-b-delta-lazy-v1",h,listOf(h,s,light))
    }
    private fun hslToRgb(): Recipe {
        val h = modulo(Scalar.Component(0),1f); val s = Scalar.Component(1); val l = Scalar.Component(2)
        val c = Scalar.Multiply(Scalar.Subtract(constant(1f),Scalar.Abs(
            Scalar.Subtract(Scalar.Multiply(constant(2f),l),constant(1f)))),s)
        val sixH = Scalar.Multiply(constant(6f),h)
        val x = Scalar.Multiply(c,Scalar.Subtract(constant(1f),Scalar.Abs(Scalar.Subtract(modulo(sixH,2f),constant(1f)))))
        val sector = Scalar.IntegerModulo(Scalar.Floor(sixH),6)
        val m = Scalar.Subtract(l,Scalar.Divide(c,constant(2f)))
        val zero = constant(0f)
        val sectors = listOf(listOf(c,x,zero),listOf(x,c,zero),listOf(zero,c,x),
            listOf(zero,x,c),listOf(x,zero,c),listOf(c,zero,x))
        val output = List(3) { channel ->
            val selected = (4 downTo 0).fold(sectors[5][channel]) { rest,i ->
                Scalar.IfEqual(sector,constant(i.toFloat()),sectors[i][channel],rest) }
            Scalar.Add(selected,m)
        }
        return Recipe("color-interpolation-v1:HSL_TO_RGB:h-wrap-sector-final-unclamped-v1",output[0],output)
    }
    private val rgbHsl = rgbToHsl()
    private val hslRgb = hslToRgb()
    public fun recipe(kind: RecipeKind): Recipe = when (kind) {
        RecipeKind.EOTF -> eotf
        RecipeKind.OETF -> oetf
        RecipeKind.RGB_TO_HSL -> rgbHsl
        RecipeKind.HSL_TO_RGB -> hslRgb
        else -> throw UnsupportedOperationException("Conversion recipe ${kind.name} has not been promoted")
    }
}
