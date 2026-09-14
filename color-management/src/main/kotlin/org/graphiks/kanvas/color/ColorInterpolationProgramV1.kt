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
        /** Signed real cube root is a Host-only stop-preparation operation. */
        public data class SignedCbrt(public val value: Scalar) : Scalar
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

    /** Author's 2021-01-25 sRGB matrices, each decimal captured once as F32 bits. */
    public const val OKLAB_RECIPE_VERSION: String = "oklab-srgb-2021-v1"

    private fun matrixRows(inputs: List<Scalar>, rows: List<List<Float>>): List<Scalar> = rows.map { row ->
        val products = row.mapIndexed { i, coefficient -> Scalar.Multiply(constant(coefficient), inputs[i]) }
        products.drop(1).fold(products.first() as Scalar) { sum, product -> Scalar.Add(sum, product) }
    }

    private fun linearRgbToOklab(): Recipe {
        val lms = matrixRows(List(3) { Scalar.Component(it) }, listOf(
            listOf(.4122214708f, .5363325363f, .0514459929f),
            listOf(.2119034982f, .6806995451f, .1073969566f),
            listOf(.0883024619f, .2817188376f, .6299787005f),
        )).map(Scalar::SignedCbrt)
        val lab = matrixRows(lms, listOf(
            listOf(.2104542553f, .7936177850f, -.0040720468f),
            listOf(1.9779984951f, -2.4285922050f, .4505937099f),
            listOf(.0259040371f, .7827717662f, -.8086757660f),
        ))
        return Recipe("$OKLAB_RECIPE_VERSION:linear-rgb-to-oklab:host-signed-cbrt-ordered-f32", lab[0], lab)
    }

    private fun oklabToLinearRgb(): Recipe {
        val lmsRoots = matrixRows(List(3) { Scalar.Component(it) }, listOf(
            listOf(1f, .3963377774f, .2158037573f),
            listOf(1f, -.1055613458f, -.0638541728f),
            listOf(1f, -.0894841775f, -1.2914855480f),
        ))
        // Real signed cube: no pow(abs(x),3), no sign reconstruction and no intermediate clamp.
        val lms = lmsRoots.map { Scalar.Multiply(Scalar.Multiply(it, it), it) }
        val rgb = matrixRows(lms, listOf(
            listOf(4.0767416621f, -3.3077115913f, .2309699292f),
            listOf(-1.2684380046f, 2.6097574011f, -.3413193965f),
            listOf(-.0041960863f, -.7034186147f, 1.7076147010f),
        ))
        return Recipe("$OKLAB_RECIPE_VERSION:oklab-to-linear-rgb:two-multiply-signed-cube", rgb[0], rgb)
    }

    private val rgbLab = linearRgbToOklab()
    private val labRgb = oklabToLinearRgb()

    /** Interprets the very same recipe; every arithmetic node rounds to F32, with no FMA. */
    public fun evaluateHostF32(kind: RecipeKind, inputsF32: List<Float>): List<Float> {
        require(inputsF32.isNotEmpty() && inputsF32.all(Float::isFinite))
        val cache = java.util.IdentityHashMap<Scalar, Float>()
        fun evaluate(node: Scalar): Float = cache[node] ?: when (node) {
            Scalar.Input -> inputsF32.single()
            is Scalar.Component -> inputsF32[node.indexI32]
            is Scalar.Constant -> Float.fromBits(node.bitsI32)
            is Scalar.Add -> evaluate(node.a) + evaluate(node.b)
            is Scalar.Subtract -> evaluate(node.a) - evaluate(node.b)
            is Scalar.Multiply -> evaluate(node.a) * evaluate(node.b)
            is Scalar.Divide -> evaluate(node.a) / evaluate(node.b)
            is Scalar.Pow -> StrictMath.pow(evaluate(node.a).toDouble(), evaluate(node.b).toDouble()).toFloat()
            is Scalar.SignedCbrt -> StrictMath.cbrt(evaluate(node.value).toDouble()).toFloat()
            is Scalar.Min -> minOf(evaluate(node.a), evaluate(node.b))
            is Scalar.Max -> maxOf(evaluate(node.a), evaluate(node.b))
            is Scalar.Abs -> kotlin.math.abs(evaluate(node.value))
            is Scalar.Floor -> StrictMath.floor(evaluate(node.value).toDouble()).toFloat()
            is Scalar.IntegerModulo -> {
                val value = evaluate(node.value)
                require(value.isFinite() && value.toDouble() in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble())
                Math.floorMod(value.toInt(), node.modulusI32).toFloat()
            }
            is Scalar.IfEqual -> if (evaluate(node.a) == evaluate(node.b)) evaluate(node.yes) else evaluate(node.no)
            is Scalar.IfLessEqual -> if (evaluate(node.a) <= evaluate(node.b)) evaluate(node.yes) else evaluate(node.no)
        }.also { value -> require(value.isFinite()); cache[node] = value }
        return recipe(kind).outputs.map(::evaluate)
    }

    public fun recipe(kind: RecipeKind): Recipe = when (kind) {
        RecipeKind.EOTF -> eotf
        RecipeKind.OETF -> oetf
        RecipeKind.RGB_TO_HSL -> rgbHsl
        RecipeKind.HSL_TO_RGB -> hslRgb
        RecipeKind.LINEAR_RGB_TO_OKLAB -> rgbLab
        RecipeKind.OKLAB_TO_LINEAR_RGB -> labRgb
        else -> throw UnsupportedOperationException("Conversion recipe ${kind.name} has not been promoted")
    }
}
