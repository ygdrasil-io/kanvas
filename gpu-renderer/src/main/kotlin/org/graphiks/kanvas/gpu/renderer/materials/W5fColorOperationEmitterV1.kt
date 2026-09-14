package org.graphiks.kanvas.gpu.renderer.materials

import org.graphiks.kanvas.gpu.plan.ColorOperationGraphV1

/** Syntax lowering only. Every executed operation and branch comes from the sealed graph. */
internal object W5fColorOperationEmitterV1 {
    fun emit(graph: ColorOperationGraphV1, inputRgbaExpression: String, uniformWordOffsetU32: Long): String {
        require(graph.contractId == "WgslFloatEnvelopeV1" && uniformWordOffsetU32 in 0L..UInt.MAX_VALUE.toLong())
        var nextI32 = 0
        fun word(offset: Long): String {
            val absolute = Math.addExact(offset,uniformWordOffsetU32)
            require(absolute in 0L..UInt.MAX_VALUE.toLong())
            return "w5fMaterial.words[${absolute/4}u][${absolute%4}u]"
        }
        fun expression(node: ColorOperationGraphV1.Scalar, code: StringBuilder,
            cache: MutableMap<Any,String>): String {
            cache[node]?.let { return it }
            val name = "colorValue${nextI32++}"
            fun arg(value: ColorOperationGraphV1.Scalar) = expression(value,code,cache)
            fun predicate(p: ColorOperationGraphV1.Predicate): String = when (p) {
                is ColorOperationGraphV1.Predicate.UniformU32Equal -> "(${word(p.wordOffsetU32)} == ${p.expectedU32}u)"
                is ColorOperationGraphV1.Predicate.Equal -> "(${arg(p.a)} == ${arg(p.b)})"
                is ColorOperationGraphV1.Predicate.LessEqual -> "(${arg(p.a)} <= ${arg(p.b)})"
                is ColorOperationGraphV1.Predicate.Not -> "(!${predicate(p.value)})"
                is ColorOperationGraphV1.Predicate.And -> "(${predicate(p.a)} && ${predicate(p.b)})"
                is ColorOperationGraphV1.Predicate.Finite -> "((bitcast<u32>(${arg(p.value)}) & 0x7f800000u) != 0x7f800000u)"
                is ColorOperationGraphV1.Predicate.ProjectiveValid -> "${arg(p.division).removeSuffix(".valueF32")}.valid"
            }
            if (node is ColorOperationGraphV1.Scalar.ProjectiveDivide) {
                val a = arg(node.a); val b = arg(node.b)
                code.append("let $name = w5dSafeDivideF32($a, $b);\n")
                return "$name.valueF32".also { cache[node] = it }
            }
            if (node is ColorOperationGraphV1.Scalar.BranchComponent) {
                val vector = cache[node.branch] ?: run {
                    val condition = predicate(node.branch.predicate)
                    code.append("var $name: vec4<f32>;\nif ($condition) {\n")
                    val yesCache = java.util.IdentityHashMap(cache)
                    val yes = node.branch.yes.map { expression(it,code,yesCache) }
                    code.append("$name = vec4<f32>(${yes.joinToString(", ")});\n} else {\n")
                    val noCache = java.util.IdentityHashMap(cache)
                    val no = node.branch.no.map { expression(it,code,noCache) }
                    code.append("$name = vec4<f32>(${no.joinToString(", ")});\n}\n")
                    cache[node.branch] = name
                    name
                }
                return "$vector[${node.channelI32}u]".also { cache[node] = it }
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
                is ColorOperationGraphV1.Scalar.DevicePositionF32 -> "localPosition[${node.channelI32}u]"
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
                is ColorOperationGraphV1.Scalar.ProjectiveDivide -> error("Projective pair must be emitted together")
                is ColorOperationGraphV1.Scalar.Pow -> "pow(${arg(node.a)}, ${arg(node.b)})"
                is ColorOperationGraphV1.Scalar.Clamp01 -> "clamp(${arg(node.value)}, 0.0, 1.0)"
                is ColorOperationGraphV1.Scalar.Min -> "min(${arg(node.a)}, ${arg(node.b)})"
                is ColorOperationGraphV1.Scalar.Max -> "max(${arg(node.a)}, ${arg(node.b)})"
                is ColorOperationGraphV1.Scalar.Abs -> "abs(${arg(node.value)})"
                is ColorOperationGraphV1.Scalar.Sqrt -> "sqrt(${arg(node.value)})"
                is ColorOperationGraphV1.Scalar.Atan2 -> "atan2(${arg(node.y)}, ${arg(node.x)})"
                is ColorOperationGraphV1.Scalar.GradientStopComponent -> {
                    val selected = node.selection
                    val vector = cache[selected] ?: run {
                        require(selected.countBoundU32 == 65_538u)
                        val prefix = "gradientSelection${nextI32++}"
                        if (selected.firstOnly) {
                            code.append("let $prefix = w5cStops[${word(selected.rangeWordOffsetU32)}].straightColor;\n")
                            cache[selected] = prefix
                            return@run prefix
                        }
                        val numerator = arg(selected.numerator)
                        val scale = arg(selected.scale)
                        val parameter = arg(selected.parameter)
                        code.append("let ${prefix}Base = ${word(selected.rangeWordOffsetU32)};\n")
                        code.append("let ${prefix}Count = ${word(selected.rangeWordOffsetU32+1L)};\n")
                        code.append("var ${prefix}Low = 0u; var ${prefix}High = ${prefix}Count;\n")
                        code.append("for (var ${prefix}Iteration = 0u; ${prefix}Iteration < ${prefix}Count; ${prefix}Iteration = ${prefix}Iteration + 1u) {\n")
                        code.append("if (${prefix}Low >= ${prefix}High) { break; }\n")
                        code.append("let ${prefix}Probe = ${prefix}Low + (${prefix}High - ${prefix}Low) / 2u;\n")
                        code.append("if (w5cStops[${prefix}Base + ${prefix}Probe].positionAndReserved.x * $scale <= $numerator) {\n")
                        code.append("${prefix}Low = ${prefix}Probe + 1u; } else { ${prefix}High = ${prefix}Probe; }\n}\n")
                        code.append("let ${prefix}Left = w5cStops[${prefix}Base + min(max(${prefix}Low, 1u) - 1u, ${prefix}Count - 1u)];\n")
                        code.append("let ${prefix}Right = w5cStops[${prefix}Base + min(${prefix}Low, ${prefix}Count - 1u)];\n")
                        code.append("var $prefix: vec4<f32>;\n")
                        code.append("if (${prefix}Right.positionAndReserved.x <= ${prefix}Left.positionAndReserved.x) {\n")
                        code.append("$prefix = ${prefix}Right.straightColor;\n")
                        code.append("} else if ($parameter == ${prefix}Left.positionAndReserved.x || all(${prefix}Left.straightColor == ${prefix}Right.straightColor)) {\n")
                        code.append("$prefix = ${prefix}Left.straightColor;\n} else {\n")
                        code.append("let ${prefix}Weight = clamp(($parameter - ${prefix}Left.positionAndReserved.x) / (${prefix}Right.positionAndReserved.x - ${prefix}Left.positionAndReserved.x), 0.0, 1.0);\n")
                        code.append("$prefix = (1.0 - ${prefix}Weight) * ${prefix}Left.straightColor + ${prefix}Weight * ${prefix}Right.straightColor;\n}\n")
                        cache[selected] = prefix
                        prefix
                    }
                    "$vector[${node.channelI32}u]"
                }
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
                is ColorOperationGraphV1.Scalar.BranchComponent -> error("Vector branches are lowered as control flow")
            }
            code.append("let $name = $text;\n")
            cache[node] = name
            return name
        }
        val code = StringBuilder()
        val cache = java.util.IdentityHashMap<Any,String>()
        val result = graph.outputs.map { expression(it,code,cache) }
        return code.append("return vec4<f32>(${result.joinToString(", ")});\n").toString()
    }
}
