package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.color.ColorInterpolationProgramV1

/** One immutable typed expression graph, shared by proof and WGSL emission. */
public class ColorOperationGraphV1 internal constructor(outputs: List<Scalar>) {
    public val outputs: List<Scalar> = immutableList(outputs)
    public val contractId: String = "WgslFloatEnvelopeV1"
    init { require(outputs.size == 4) }
    public sealed interface Scalar {
        public data class InputLinearPremul(public val channelI32: Int) : Scalar {
            init { require(channelI32 in 0..3) }
        }
        /** Word index relative to this graph's record; source prefix slots are absolute. */
        public data class DynamicF32(public val wordOffsetU32: Long) : Scalar {
            init { require(wordOffsetU32 in 0L..UInt.MAX_VALUE.toLong()) }
        }
        public data class ConstantF32(public val bitsI32: Int) : Scalar {
            init { require(Float.fromBits(bitsI32).isFinite()) }
        }
        public data class Add(public val a: Scalar, public val b: Scalar) : Scalar
        public data class Subtract(public val a: Scalar, public val b: Scalar) : Scalar
        public data class Multiply(public val a: Scalar, public val b: Scalar) : Scalar
        public data class Divide(public val a: Scalar, public val b: Scalar) : Scalar
        public data class Pow(public val a: Scalar, public val b: Scalar) : Scalar
        public data class Clamp01(public val value: Scalar) : Scalar
        /** round(scaled) is checked in [0,255] and selects a byte from a typed Table record. */
        public data class TableByte(public val scaled: Scalar, public val tableWordOffsetU32: Long) : Scalar {
            init { require(tableWordOffsetU32 in 0L..UInt.MAX_VALUE.toLong()-63L) }
        }
        public data class Min(public val a: Scalar, public val b: Scalar) : Scalar
        public data class Max(public val a: Scalar, public val b: Scalar) : Scalar
        public data class Abs(public val value: Scalar) : Scalar
        public data class Sqrt(public val value: Scalar) : Scalar
        /** Both alternatives execute, exactly as WGSL select requires. */
        public data class EagerSelect(public val predicate: Predicate, public val yes: Scalar, public val no: Scalar) : Scalar
        public data class LazyBranch(public val predicate: Predicate, public val yes: Scalar, public val no: Scalar) : Scalar
    }
    public sealed interface Predicate {
        public data class Equal(public val a: Scalar, public val b: Scalar) : Predicate
        public data class LessEqual(public val a: Scalar, public val b: Scalar) : Predicate
        public data class Not(public val value: Predicate) : Predicate
        public data class And(public val a: Predicate, public val b: Predicate) : Predicate
    }
    public val canonicalIdentity: String by lazy {
        val identities = java.util.IdentityHashMap<Scalar, String>()
        fun identity(node: Scalar): String = identities[node] ?: run {
            fun predicate(p: Predicate): String = when (p) {
                is Predicate.Equal -> "eq:${identity(p.a)}:${identity(p.b)}"
                is Predicate.LessEqual -> "le:${identity(p.a)}:${identity(p.b)}"
                is Predicate.Not -> "not:${predicate(p.value)}"
                is Predicate.And -> "and:${predicate(p.a)}:${predicate(p.b)}"
            }
            val recipe = when (node) {
                is Scalar.InputLinearPremul -> "input:${node.channelI32}"
                is Scalar.DynamicF32 -> "dynamic:${node.wordOffsetU32}"
                is Scalar.ConstantF32 -> "constant:${node.bitsI32}"
                is Scalar.Add -> "add:${identity(node.a)}:${identity(node.b)}"
                is Scalar.Subtract -> "sub:${identity(node.a)}:${identity(node.b)}"
                is Scalar.Multiply -> "mul:${identity(node.a)}:${identity(node.b)}"
                is Scalar.Divide -> "div:${identity(node.a)}:${identity(node.b)}"
                is Scalar.Pow -> "pow:${identity(node.a)}:${identity(node.b)}"
                is Scalar.Clamp01 -> "clamp:${identity(node.value)}"
                is Scalar.TableByte -> "table-byte-round-v1:${node.tableWordOffsetU32}:${identity(node.scaled)}"
                is Scalar.Min -> "min:${identity(node.a)}:${identity(node.b)}"
                is Scalar.Max -> "max:${identity(node.a)}:${identity(node.b)}"
                is Scalar.Abs -> "abs:${identity(node.value)}"
                is Scalar.Sqrt -> "sqrt:${identity(node.value)}"
                is Scalar.EagerSelect -> "select:${predicate(node.predicate)}:${identity(node.yes)}:${identity(node.no)}"
                is Scalar.LazyBranch -> "lazy:${predicate(node.predicate)}:${identity(node.yes)}:${identity(node.no)}"
            }
            java.security.MessageDigest.getInstance("SHA-256").digest(recipe.encodeToByteArray())
                .joinToString("") { "%02x".format(it) }.also { identities[node] = it }
        }
        "color-operation-v1:$contractId:${outputs.joinToString(";", transform = ::identity)}"
    }
    internal fun bindInput(prefix: ColorOperationGraphV1, filterWordOffsetU32: Long): ColorOperationGraphV1 {
        val bound = java.util.IdentityHashMap<Scalar, Scalar>()
        fun bind(value: Scalar): Scalar {
            fun predicate(p: Predicate): Predicate = when (p) {
                is Predicate.Equal -> Predicate.Equal(bind(p.a),bind(p.b))
                is Predicate.LessEqual -> Predicate.LessEqual(bind(p.a),bind(p.b))
                is Predicate.Not -> Predicate.Not(predicate(p.value))
                is Predicate.And -> Predicate.And(predicate(p.a),predicate(p.b))
            }
            return bound[value] ?: when (value) {
            is Scalar.InputLinearPremul -> prefix.outputs[value.channelI32]
            is Scalar.DynamicF32 -> Scalar.DynamicF32(Math.addExact(value.wordOffsetU32, filterWordOffsetU32))
            is Scalar.ConstantF32 -> value
            is Scalar.Add -> Scalar.Add(bind(value.a), bind(value.b))
            is Scalar.Subtract -> Scalar.Subtract(bind(value.a), bind(value.b))
            is Scalar.Multiply -> Scalar.Multiply(bind(value.a), bind(value.b))
            is Scalar.Divide -> Scalar.Divide(bind(value.a), bind(value.b))
            is Scalar.Pow -> Scalar.Pow(bind(value.a), bind(value.b))
            is Scalar.Clamp01 -> Scalar.Clamp01(bind(value.value))
            is Scalar.TableByte -> Scalar.TableByte(bind(value.scaled),Math.addExact(value.tableWordOffsetU32,filterWordOffsetU32))
            is Scalar.Min -> Scalar.Min(bind(value.a),bind(value.b))
            is Scalar.Max -> Scalar.Max(bind(value.a),bind(value.b))
            is Scalar.Abs -> Scalar.Abs(bind(value.value))
            is Scalar.Sqrt -> Scalar.Sqrt(bind(value.value))
            is Scalar.EagerSelect -> Scalar.EagerSelect(predicate(value.predicate),bind(value.yes),bind(value.no))
            is Scalar.LazyBranch -> Scalar.LazyBranch(predicate(value.predicate),bind(value.yes),bind(value.no))
        }.also { bound[value] = it }
        }
        return ColorOperationGraphV1(outputs.map(::bind))
    }
    internal companion object {
        fun constant(valueF32: Float): Scalar = Scalar.ConstantF32(valueF32.toRawBits())
        fun eotf(input: Scalar): Scalar = conversion(input,ColorInterpolationProgramV1.RecipeKind.EOTF)
        private fun conversion(input: Scalar, kind: ColorInterpolationProgramV1.RecipeKind): Scalar {
            fun adapt(value: ColorInterpolationProgramV1.Scalar): Scalar = when (value) {
                ColorInterpolationProgramV1.Scalar.Input -> input
                is ColorInterpolationProgramV1.Scalar.Constant -> Scalar.ConstantF32(value.bitsI32)
                is ColorInterpolationProgramV1.Scalar.Add -> Scalar.Add(adapt(value.a), adapt(value.b))
                is ColorInterpolationProgramV1.Scalar.Subtract -> Scalar.Subtract(adapt(value.a), adapt(value.b))
                is ColorInterpolationProgramV1.Scalar.Multiply -> Scalar.Multiply(adapt(value.a), adapt(value.b))
                is ColorInterpolationProgramV1.Scalar.Divide -> Scalar.Divide(adapt(value.a), adapt(value.b))
                is ColorInterpolationProgramV1.Scalar.Pow -> Scalar.Pow(adapt(value.a), adapt(value.b))
                is ColorInterpolationProgramV1.Scalar.IfEqual -> Scalar.LazyBranch(Predicate.Equal(adapt(value.a), adapt(value.b)), adapt(value.yes), adapt(value.no))
                is ColorInterpolationProgramV1.Scalar.IfLessEqual -> Scalar.LazyBranch(Predicate.LessEqual(adapt(value.a), adapt(value.b)), adapt(value.yes), adapt(value.no))
            }
            return adapt(ColorInterpolationProgramV1.recipe(kind).root)
        }
        private fun straightInput(): List<Scalar> {
            val input = List(4) { Scalar.InputLinearPremul(it) }
            return List(4) { if (it == 3) input[3] else Scalar.LazyBranch(
                Predicate.Equal(input[3],constant(0f)),constant(0f),Scalar.LazyBranch(
                    Predicate.Equal(input[3],constant(1f)),input[it],Scalar.Divide(input[it],input[3]))) }
        }
        private fun premultiply(straight: List<Scalar>) = ColorOperationGraphV1(List(4) {
            if (it == 3) straight[3] else Scalar.Multiply(straight[it],straight[3]) })
        fun table(): ColorOperationGraphV1 = premultiply(straightInput().map {
            Scalar.Divide(Scalar.TableByte(Scalar.Multiply(Scalar.Clamp01(it),constant(255f)),0L),constant(255f)) })
        fun lighting(): ColorOperationGraphV1 {
            val input = straightInput()
            return premultiply(List(4) { if (it == 3) input[3] else Scalar.Clamp01(Scalar.Add(
                Scalar.Multiply(input[it],eotf(Scalar.DynamicF32(it.toLong()))),eotf(Scalar.DynamicF32(it+4L)))) })
        }
        fun transferFilter(decode: Boolean): ColorOperationGraphV1 {
            val input = straightInput()
            return premultiply(List(4) { if (it == 3) input[3] else conversion(input[it],
                if (decode) ColorInterpolationProgramV1.RecipeKind.EOTF else ColorInterpolationProgramV1.RecipeKind.OETF) })
        }
        fun matrix(): ColorOperationGraphV1 {
            val input = List(4) { Scalar.InputLinearPremul(it) }
            val zero = constant(0f)
            val one = constant(1f)
            val straight = List(4) { if (it == 3) input[3] else Scalar.LazyBranch(
                Predicate.Equal(input[3], zero), zero,
                Scalar.LazyBranch(Predicate.Equal(input[3], one), input[it], Scalar.Divide(input[it], input[3]))) }
            val rows = List(4) { rowI32 ->
                val products = List(4) { Scalar.Multiply(Scalar.DynamicF32(rowI32 * 5L + it), straight[it]) }
                Scalar.Clamp01(Scalar.Add(products.drop(1).fold(products.first() as Scalar) { sum, product ->
                    Scalar.Add(sum, product)
                }, Scalar.DynamicF32(rowI32 * 5L + 4)))
            }
            return ColorOperationGraphV1(List(4) { if (it == 3) rows[3] else Scalar.Multiply(rows[it], rows[3]) })
        }
    }
}
