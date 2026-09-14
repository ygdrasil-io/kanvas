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
        /** Actual fragment position; its enclosure is supplied by the authenticated draw bounds. */
        public data class DevicePositionF32(public val channelI32: Int) : Scalar {
            init { require(channelI32 in 0..1) }
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
        /** Existing binary-parts / fraction-divide / frexp / ldexp coordinate schedule. */
        public class ProjectiveDivide(public val a: Scalar,public val b: Scalar) : Scalar
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
        public data class Atan2(public val y: Scalar, public val x: Scalar) : Scalar
        public data class Sin(public val value: Scalar) : Scalar
        public data class Cos(public val value: Scalar) : Scalar
        /** A lexical operand of the selected-stop interpolation region, never a uniform. */
        public class StopInterpolationInput internal constructor(public val slotI32: Int) : Scalar {
            init { require(slotI32 in 0..8) }
        }
        /** Checked shared-range search followed by the straight-domain interpolation recipe. */
        public data class GradientStopComponent(public val selection: GradientStopSelection,
            public val channelI32: Int) : Scalar {
            init { require(channelI32 in 0..3) }
        }
        /** One vector-valued control-flow region, shared by all four channel readers. */
        public data class BranchComponent(public val branch: BranchVector,public val channelI32: Int) : Scalar {
            init { require(channelI32 in 0..3) }
        }
        public data class Floor(public val value: Scalar) : Scalar
        public data class Round(public val value: Scalar) : Scalar
        public data class IntegerModulo(public val value: Floor, public val modulusI32: Int) : Scalar {
            init { require(modulusI32 in 1..16777216) }
        }
        /** Both alternatives execute, exactly as WGSL select requires. */
        public data class EagerSelect(public val predicate: Predicate, public val yes: Scalar, public val no: Scalar) : Scalar
        public data class LazyBranch(public val predicate: Predicate, public val yes: Scalar, public val no: Scalar) : Scalar
    }
    public class GradientStopSelection internal constructor(
        public val numerator: Scalar,
        public val scale: Scalar,
        public val parameter: Scalar,
        public val rangeWordOffsetU32: Long,
        public val domain: org.graphiks.kanvas.render.ir.ColorInterpolation,
        public val firstOnly: Boolean = false,
    ) {
        public val countBoundU32: UInt = 65_538u
        init { require(rangeWordOffsetU32 in 0L..UInt.MAX_VALUE.toLong()-1L) }
        public val interpolationInputs: List<Scalar.StopInterpolationInput> = immutableList(List(9) { Scalar.StopInterpolationInput(it) })
        public val interpolationGraph: ColorOperationGraphV1 = ColorOperationGraphV1(interpolate(
            interpolationInputs.take(4),interpolationInputs.drop(4).take(4),interpolationInputs[8]))

        /** The sole rounded interpolation schedule, instantiated by proof and emitter. */
        public fun interpolate(left: List<Scalar>,right: List<Scalar>,weight: Scalar): List<Scalar> {
            require(left.size == 4 && right.size == 4)
            val inverse = Scalar.Subtract(constant(1f),weight)
            val channels = List(4) { Scalar.Add(Scalar.Multiply(inverse,left[it]),Scalar.Multiply(weight,right[it])) as Scalar }.toMutableList()
            if (domain == org.graphiks.kanvas.render.ir.ColorInterpolation.HSL ||
                domain == org.graphiks.kanvas.render.ir.ColorInterpolation.OKLCH) {
                val hue = if (domain == org.graphiks.kanvas.render.ir.ColorInterpolation.HSL) 0 else 2
                val zero = constant(0f)
                val h0 = Scalar.LazyBranch(Predicate.Equal(left[1],zero),right[hue],left[hue])
                val h1 = Scalar.LazyBranch(Predicate.Equal(right[1],zero),h0,right[hue])
                val delta = Scalar.Subtract(h1,h0)
                // Endpoints are normalized turns. This is the equivalent shortest
                // difference without the lossy +1.5 translation at an exact tie.
                val shortest = Scalar.LazyBranch(Predicate.LessEqual(delta,constant(-.5f)),
                    Scalar.Add(delta,constant(1f)),Scalar.LazyBranch(Predicate.LessEqual(delta,constant(.5f)),
                        delta,Scalar.Subtract(delta,constant(1f))))
                channels[hue] = Scalar.Add(h0,Scalar.Multiply(weight,shortest))
                // The inverse recipe wraps the hue; no S/L/C or RGB clamp occurs here.
            }
            return channels
        }
    }
    public class BranchVector internal constructor(public val predicate: Predicate,yes: List<Scalar>,no: List<Scalar>) {
        public val yes: List<Scalar> = immutableList(yes)
        public val no: List<Scalar> = immutableList(no)
        init { require(yes.size == 4 && no.size == 4) }
    }
    public sealed interface Predicate {
        /** A typed integer flag is never interpreted through DynamicF32. */
        public data class UniformU32Equal(public val wordOffsetU32: Long, public val expectedU32: UInt) : Predicate {
            init { require(wordOffsetU32 in 0L..UInt.MAX_VALUE.toLong()) }
        }
        public data class Equal(public val a: Scalar, public val b: Scalar) : Predicate
        public data class LessEqual(public val a: Scalar, public val b: Scalar) : Predicate
        public data class Not(public val value: Predicate) : Predicate
        public data class And(public val a: Predicate, public val b: Predicate) : Predicate
        public data class Finite(public val value: Scalar) : Predicate
        public data class ProjectiveValid(public val division: Scalar.ProjectiveDivide) : Predicate
    }
    public val canonicalIdentity: String by lazy {
        val identities = java.util.IdentityHashMap<Scalar, String>()
        fun identity(node: Scalar): String = identities[node] ?: run {
            fun predicate(p: Predicate): String = when (p) {
                is Predicate.UniformU32Equal -> "u32-eq:${p.wordOffsetU32}:${p.expectedU32}"
                is Predicate.Equal -> "eq:${identity(p.a)}:${identity(p.b)}"
                is Predicate.LessEqual -> "le:${identity(p.a)}:${identity(p.b)}"
                is Predicate.Not -> "not:${predicate(p.value)}"
                is Predicate.And -> "and:${predicate(p.a)}:${predicate(p.b)}"
                is Predicate.Finite -> "finite:${identity(p.value)}"
                is Predicate.ProjectiveValid -> "projective-valid:${identity(p.division)}"
            }
            val recipe = when (node) {
                is Scalar.InputLinearPremul -> "input:${node.channelI32}"
                is Scalar.DevicePositionF32 -> "device-position:${node.channelI32}"
                is Scalar.DynamicF32 -> "dynamic:${node.wordOffsetU32}"
                is Scalar.ConstantF32 -> "constant:${node.bitsI32}"
                is Scalar.Add -> "add:${identity(node.a)}:${identity(node.b)}"
                is Scalar.Subtract -> "sub:${identity(node.a)}:${identity(node.b)}"
                is Scalar.Multiply -> "mul:${identity(node.a)}:${identity(node.b)}"
                is Scalar.Divide -> "div:${identity(node.a)}:${identity(node.b)}"
                is Scalar.ProjectiveDivide -> "w5d-binary-parts-fraction-div-frexp-ldexp-v1:${identity(node.a)}:${identity(node.b)}"
                is Scalar.Pow -> "pow:${identity(node.a)}:${identity(node.b)}"
                is Scalar.Clamp01 -> "clamp:${identity(node.value)}"
                is Scalar.TableByte -> "table-byte-round-v1:${node.tableWordOffsetU32}:${identity(node.scaled)}"
                is Scalar.Min -> "min:${identity(node.a)}:${identity(node.b)}"
                is Scalar.Max -> "max:${identity(node.a)}:${identity(node.b)}"
                is Scalar.Abs -> "abs:${identity(node.value)}"
                is Scalar.Sqrt -> "sqrt:${identity(node.value)}"
                is Scalar.Atan2 -> "atan2:${identity(node.y)}:${identity(node.x)}"
                is Scalar.Sin -> "sin:${identity(node.value)}"
                is Scalar.Cos -> "cos:${identity(node.value)}"
                is Scalar.StopInterpolationInput -> "stop-interpolation-input:${node.slotI32}"
                is Scalar.GradientStopComponent -> node.selection.let { selected ->
                    "gradient-upper-bound-65538-scaled-le-interpolate-v4:${selected.domain}:${selected.rangeWordOffsetU32}:first=${selected.firstOnly}:" +
                        "${identity(selected.numerator)}:${identity(selected.scale)}:${identity(selected.parameter)}:${selected.interpolationGraph.canonicalIdentity}:${node.channelI32}" }
                is Scalar.BranchComponent -> "vector-lazy:${predicate(node.branch.predicate)}:" +
                    "${node.branch.yes.joinToString(",",transform=::identity)}:${node.branch.no.joinToString(",",transform=::identity)}:${node.channelI32}"
                is Scalar.Floor -> "floor:${identity(node.value)}"
                is Scalar.Round -> "round:${identity(node.value)}"
                is Scalar.IntegerModulo -> "integer-modulo:${node.modulusI32}:${identity(node.value)}"
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
        val selections = java.util.IdentityHashMap<GradientStopSelection, GradientStopSelection>()
        val vectors = java.util.IdentityHashMap<BranchVector,BranchVector>()
        fun bind(value: Scalar): Scalar {
            fun predicate(p: Predicate): Predicate = when (p) {
                is Predicate.UniformU32Equal -> Predicate.UniformU32Equal(Math.addExact(p.wordOffsetU32,filterWordOffsetU32),p.expectedU32)
                is Predicate.Equal -> Predicate.Equal(bind(p.a),bind(p.b))
                is Predicate.LessEqual -> Predicate.LessEqual(bind(p.a),bind(p.b))
                is Predicate.Not -> Predicate.Not(predicate(p.value))
                is Predicate.And -> Predicate.And(predicate(p.a),predicate(p.b))
                is Predicate.Finite -> Predicate.Finite(bind(p.value))
                is Predicate.ProjectiveValid -> Predicate.ProjectiveValid(bind(p.division) as Scalar.ProjectiveDivide)
            }
            return bound[value] ?: when (value) {
            is Scalar.InputLinearPremul -> prefix.outputs[value.channelI32]
            is Scalar.DevicePositionF32 -> value
            is Scalar.DynamicF32 -> Scalar.DynamicF32(Math.addExact(value.wordOffsetU32, filterWordOffsetU32))
            is Scalar.ConstantF32 -> value
            is Scalar.Add -> Scalar.Add(bind(value.a), bind(value.b))
            is Scalar.Subtract -> Scalar.Subtract(bind(value.a), bind(value.b))
            is Scalar.Multiply -> Scalar.Multiply(bind(value.a), bind(value.b))
            is Scalar.Divide -> Scalar.Divide(bind(value.a), bind(value.b))
            is Scalar.ProjectiveDivide -> Scalar.ProjectiveDivide(bind(value.a),bind(value.b))
            is Scalar.Pow -> Scalar.Pow(bind(value.a), bind(value.b))
            is Scalar.Clamp01 -> Scalar.Clamp01(bind(value.value))
            is Scalar.TableByte -> Scalar.TableByte(bind(value.scaled),Math.addExact(value.tableWordOffsetU32,filterWordOffsetU32))
            is Scalar.Min -> Scalar.Min(bind(value.a),bind(value.b))
            is Scalar.Max -> Scalar.Max(bind(value.a),bind(value.b))
            is Scalar.Abs -> Scalar.Abs(bind(value.value))
            is Scalar.Sqrt -> Scalar.Sqrt(bind(value.value))
            is Scalar.Atan2 -> Scalar.Atan2(bind(value.y),bind(value.x))
            is Scalar.Sin -> Scalar.Sin(bind(value.value))
            is Scalar.Cos -> Scalar.Cos(bind(value.value))
            is Scalar.StopInterpolationInput -> value
            is Scalar.GradientStopComponent -> Scalar.GradientStopComponent(selections.getOrPut(value.selection) {
                GradientStopSelection(bind(value.selection.numerator),bind(value.selection.scale),bind(value.selection.parameter),
                    Math.addExact(value.selection.rangeWordOffsetU32,filterWordOffsetU32),value.selection.domain,value.selection.firstOnly)
            },value.channelI32)
            is Scalar.BranchComponent -> Scalar.BranchComponent(vectors.getOrPut(value.branch) {
                BranchVector(predicate(value.branch.predicate),value.branch.yes.map(::bind),value.branch.no.map(::bind))
            },value.channelI32)
            is Scalar.Floor -> Scalar.Floor(bind(value.value))
            is Scalar.Round -> Scalar.Round(bind(value.value))
            is Scalar.IntegerModulo -> Scalar.IntegerModulo(bind(value.value) as Scalar.Floor,value.modulusI32)
            is Scalar.EagerSelect -> Scalar.EagerSelect(predicate(value.predicate),bind(value.yes),bind(value.no))
            is Scalar.LazyBranch -> Scalar.LazyBranch(predicate(value.predicate),bind(value.yes),bind(value.no))
        }.also { bound[value] = it }
        }
        return ColorOperationGraphV1(outputs.map(::bind))
    }
    internal companion object {
        fun constant(valueF32: Float): Scalar = Scalar.ConstantF32(valueF32.toRawBits())
        fun eotf(input: Scalar): Scalar = conversion(input,ColorInterpolationProgramV1.RecipeKind.EOTF)
        private fun conversion(input: Scalar, kind: ColorInterpolationProgramV1.RecipeKind): Scalar =
            conversion(listOf(input),kind).first()
        internal fun conversion(inputs: List<Scalar>, kind: ColorInterpolationProgramV1.RecipeKind): List<Scalar> {
            val cache = java.util.IdentityHashMap<ColorInterpolationProgramV1.Scalar,Scalar>()
            fun adapt(value: ColorInterpolationProgramV1.Scalar): Scalar = cache[value] ?: when (value) {
                ColorInterpolationProgramV1.Scalar.Input -> inputs.single()
                is ColorInterpolationProgramV1.Scalar.Component -> inputs[value.indexI32]
                is ColorInterpolationProgramV1.Scalar.Constant -> Scalar.ConstantF32(value.bitsI32)
                is ColorInterpolationProgramV1.Scalar.Add -> Scalar.Add(adapt(value.a), adapt(value.b))
                is ColorInterpolationProgramV1.Scalar.Subtract -> Scalar.Subtract(adapt(value.a), adapt(value.b))
                is ColorInterpolationProgramV1.Scalar.Multiply -> Scalar.Multiply(adapt(value.a), adapt(value.b))
                is ColorInterpolationProgramV1.Scalar.Divide -> Scalar.Divide(adapt(value.a), adapt(value.b))
                is ColorInterpolationProgramV1.Scalar.Pow -> Scalar.Pow(adapt(value.a), adapt(value.b))
                is ColorInterpolationProgramV1.Scalar.SignedCbrt -> error("Signed cube root is Host-only stop preparation")
                is ColorInterpolationProgramV1.Scalar.IfOriginalEncodedGray -> error("Original encoded gray is Host-only stop preparation")
                is ColorInterpolationProgramV1.Scalar.Sqrt -> adapt(value.value).let { operand ->
                    Scalar.LazyBranch(Predicate.Equal(operand,constant(0f)),constant(0f),Scalar.Sqrt(operand)) }
                is ColorInterpolationProgramV1.Scalar.Atan2 -> Scalar.Atan2(adapt(value.y),adapt(value.x))
                is ColorInterpolationProgramV1.Scalar.Sin -> Scalar.Sin(adapt(value.value))
                is ColorInterpolationProgramV1.Scalar.Cos -> Scalar.Cos(adapt(value.value))
                is ColorInterpolationProgramV1.Scalar.Min -> Scalar.Min(adapt(value.a),adapt(value.b))
                is ColorInterpolationProgramV1.Scalar.Max -> Scalar.Max(adapt(value.a),adapt(value.b))
                is ColorInterpolationProgramV1.Scalar.Abs -> Scalar.Abs(adapt(value.value))
                is ColorInterpolationProgramV1.Scalar.Floor -> Scalar.Floor(adapt(value.value))
                is ColorInterpolationProgramV1.Scalar.IntegerModulo -> Scalar.IntegerModulo(adapt(value.value) as Scalar.Floor,value.modulusI32)
                is ColorInterpolationProgramV1.Scalar.IfEqual -> Scalar.LazyBranch(Predicate.Equal(adapt(value.a), adapt(value.b)), adapt(value.yes), adapt(value.no))
                is ColorInterpolationProgramV1.Scalar.IfLessEqual -> Scalar.LazyBranch(Predicate.LessEqual(adapt(value.a), adapt(value.b)), adapt(value.yes), adapt(value.no))
            }.also { cache[value] = it }
            return ColorInterpolationProgramV1.recipe(kind).outputs.map(::adapt)
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
        fun hsla(): ColorOperationGraphV1 {
            val straight = straightInput()
            val input = conversion(straight.take(3),ColorInterpolationProgramV1.RecipeKind.RGB_TO_HSL)+straight[3]
            val rows = List(4) { row ->
                val products = List(4) { Scalar.Multiply(Scalar.DynamicF32(row*5L+it),input[it]) }
                Scalar.Add(products.drop(1).fold(products.first() as Scalar) { sum,product -> Scalar.Add(sum,product) },
                    Scalar.DynamicF32(row*5L+4L))
            }
            val rgb = conversion(rows.take(3),ColorInterpolationProgramV1.RecipeKind.HSL_TO_RGB)
            return premultiply(List(4) { Scalar.Clamp01(if (it == 3) rows[3] else rgb[it]) })
        }
        fun highContrast(): ColorOperationGraphV1 {
            val input = straightInput()
            return premultiply(List(4) { if (it == 3) input[3] else Scalar.Clamp01(Scalar.Add(constant(.5f),
                Scalar.Multiply(constant(3f),Scalar.Subtract(input[it],constant(.5f))))) })
        }
        fun luma(): ColorOperationGraphV1 {
            val input = straightInput()
            val coefficients = listOf(.2126f,.7152f,.0722f)
            val products = List(3) { Scalar.Multiply(input[it],constant(coefficients[it])) }
            val dot = Scalar.Add(Scalar.Add(products[0],products[1]),products[2])
            return ColorOperationGraphV1(listOf(constant(0f),constant(0f),constant(0f),Scalar.Multiply(input[3],dot)))
        }
        fun overdraw(): ColorOperationGraphV1 {
            val index = Scalar.Min(Scalar.Round(Scalar.Multiply(Scalar.Clamp01(Scalar.InputLinearPremul(3)),constant(255f))),constant(5f))
            val palette = listOf(listOf(1f,0f,0f),listOf(0f,1f,0f),listOf(0f,0f,1f),
                listOf(1f,1f,0f),listOf(0f,1f,1f),listOf(1f,0f,1f))
            return premultiply(List(4) { channel -> if (channel == 3) constant(128f/255f) else
                (4 downTo 0).fold(constant(palette[5][channel])) { rest,i -> Scalar.LazyBranch(
                    Predicate.Equal(index,constant(i.toFloat())),constant(palette[i][channel]),rest) } })
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
