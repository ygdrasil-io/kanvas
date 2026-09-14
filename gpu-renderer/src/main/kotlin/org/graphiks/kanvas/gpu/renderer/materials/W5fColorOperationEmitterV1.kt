package org.graphiks.kanvas.gpu.renderer.materials

import org.graphiks.kanvas.gpu.plan.ColorOperationGraphV1

/** Syntax lowering only. Every executed operation and branch comes from the sealed graph. */
internal object W5fColorOperationEmitterV1 {
    fun emit(graph: ColorOperationGraphV1, inputRgbaExpression: String, uniformWordOffsetU32: Long): String {
        require(graph.contractId == "WgslFloatEnvelopeV1" && uniformWordOffsetU32 in 0L..UInt.MAX_VALUE.toLong())
        var nextI32 = 0
        fun expression(node: ColorOperationGraphV1.Scalar, code: StringBuilder,
            cache: MutableMap<ColorOperationGraphV1.Scalar,String>): String {
            cache[node]?.let { return it }
            val name = "colorValue${nextI32++}"
            fun arg(value: ColorOperationGraphV1.Scalar) = expression(value,code,cache)
            fun predicate(p: ColorOperationGraphV1.Predicate): String = when (p) {
                is ColorOperationGraphV1.Predicate.Equal -> "(${arg(p.a)} == ${arg(p.b)})"
                is ColorOperationGraphV1.Predicate.LessEqual -> "(${arg(p.a)} <= ${arg(p.b)})"
                is ColorOperationGraphV1.Predicate.Not -> "(!${predicate(p.value)})"
                is ColorOperationGraphV1.Predicate.And -> "(${predicate(p.a)} && ${predicate(p.b)})"
            }
            if (node is ColorOperationGraphV1.Scalar.LazyBranch) {
                val condition = predicate(node.predicate)
                code.append("var $name: f32;\nif ($condition) {\n")
                val yes = expression(node.yes,code,java.util.IdentityHashMap(cache))
                code.append("$name = $yes;\n} else {\n")
                val no = expression(node.no,code,java.util.IdentityHashMap(cache))
                code.append("$name = $no;\n}\n")
                cache[node] = name
                return name
            }
            val text = when (node) {
                is ColorOperationGraphV1.Scalar.InputLinearPremul -> "($inputRgbaExpression)[${node.channelI32}u]"
                is ColorOperationGraphV1.Scalar.DynamicF32 -> {
                    val word = Math.addExact(node.wordOffsetU32,uniformWordOffsetU32)
                    require(word in 0L..UInt.MAX_VALUE.toLong())
                    "bitcast<f32>(w5fMaterial.words[${word/4}u][${word%4}u])"
                }
                is ColorOperationGraphV1.Scalar.ConstantF32 -> "bitcast<f32>(${node.bitsI32.toUInt()}u)"
                is ColorOperationGraphV1.Scalar.Add -> "(${arg(node.a)} + ${arg(node.b)})"
                is ColorOperationGraphV1.Scalar.Subtract -> "(${arg(node.a)} - ${arg(node.b)})"
                is ColorOperationGraphV1.Scalar.Multiply -> "(${arg(node.a)} * ${arg(node.b)})"
                is ColorOperationGraphV1.Scalar.Divide -> "(${arg(node.a)} / ${arg(node.b)})"
                is ColorOperationGraphV1.Scalar.Pow -> "pow(${arg(node.a)}, ${arg(node.b)})"
                is ColorOperationGraphV1.Scalar.Clamp01 -> "clamp(${arg(node.value)}, 0.0, 1.0)"
                is ColorOperationGraphV1.Scalar.Min -> "min(${arg(node.a)}, ${arg(node.b)})"
                is ColorOperationGraphV1.Scalar.Max -> "max(${arg(node.a)}, ${arg(node.b)})"
                is ColorOperationGraphV1.Scalar.Abs -> "abs(${arg(node.value)})"
                is ColorOperationGraphV1.Scalar.Sqrt -> "sqrt(${arg(node.value)})"
                is ColorOperationGraphV1.Scalar.Floor -> "floor(${arg(node.value)})"
                is ColorOperationGraphV1.Scalar.Round -> "round(${arg(node.value)})"
                is ColorOperationGraphV1.Scalar.IntegerModulo ->
                    "f32(((i32(${arg(node.value)}) % ${node.modulusI32}i) + ${node.modulusI32}i) % ${node.modulusI32}i)"
                is ColorOperationGraphV1.Scalar.EagerSelect -> "select(${arg(node.no)}, ${arg(node.yes)}, ${predicate(node.predicate)})"
                is ColorOperationGraphV1.Scalar.TableByte -> {
                    val word = Math.addExact(node.tableWordOffsetU32,uniformWordOffsetU32)
                    require(word in 0L..UInt.MAX_VALUE.toLong()-63L)
                    val index = "tableIndex${nextI32++}"
                    code.append("let $index = u32(round(${arg(node.scaled)}));\n")
                    val address = "(${word}u + ($index >> 2u))"
                    "f32((w5fMaterial.words[$address / 4u][$address % 4u] >> (($index & 3u) * 8u)) & 255u)"
                }
                is ColorOperationGraphV1.Scalar.LazyBranch -> error("Lazy branches are lowered as control flow")
            }
            code.append("let $name = $text;\n")
            cache[node] = name
            return name
        }
        val code = StringBuilder()
        val cache = java.util.IdentityHashMap<ColorOperationGraphV1.Scalar,String>()
        val result = graph.outputs.map { expression(it,code,cache) }
        return code.append("return vec4<f32>(${result.joinToString(", ")});\n").toString()
    }
}
