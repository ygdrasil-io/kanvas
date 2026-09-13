package org.graphiks.kanvas.color

/** Backend-neutral scalar conversion recipes. Branches are lazy and constants retain F32 bits. */
public object ColorInterpolationProgramV1 {
    public enum class RecipeKind { EOTF, OETF, RGB_TO_HSL, HSL_TO_RGB, LINEAR_RGB_TO_OKLAB,
        OKLAB_TO_LINEAR_RGB, OKLAB_TO_OKLCH, OKLCH_TO_OKLAB }
    public sealed interface Scalar {
        public data object Input : Scalar
        public data class Constant(public val bitsI32: Int) : Scalar
        public data class Add(public val a: Scalar, public val b: Scalar) : Scalar
        public data class Subtract(public val a: Scalar, public val b: Scalar) : Scalar
        public data class Multiply(public val a: Scalar, public val b: Scalar) : Scalar
        public data class Divide(public val a: Scalar, public val b: Scalar) : Scalar
        public data class Pow(public val a: Scalar, public val b: Scalar) : Scalar
        public data class IfEqual(public val a: Scalar, public val b: Scalar,
            public val yes: Scalar, public val no: Scalar) : Scalar
        public data class IfLessEqual(public val a: Scalar, public val b: Scalar,
            public val yes: Scalar, public val no: Scalar) : Scalar
    }
    public class Recipe internal constructor(public val identity: String, public val root: Scalar)
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
    public fun recipe(kind: RecipeKind): Recipe = when (kind) {
        RecipeKind.EOTF -> eotf
        RecipeKind.OETF -> oetf
        else -> throw UnsupportedOperationException("Conversion recipe ${kind.name} has not been promoted")
    }
}
