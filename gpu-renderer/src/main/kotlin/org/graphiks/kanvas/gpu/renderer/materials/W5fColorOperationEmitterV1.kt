package org.graphiks.kanvas.gpu.renderer.materials

import org.graphiks.kanvas.gpu.plan.ColorOperationGraphV1

/** Syntax lowering only. Every executed operation and branch comes from the sealed graph. */
internal object W5fColorOperationEmitterV1 {
    /** Exact finite lattice plumbing; floating operations remain in the common typed graph. */
    val noiseDeclarations: String = """
        struct NoiseShiftV1 { value: vec4<u32>, carry: u32, }
        fn w5gNoiseShift(a: vec4<u32>) -> NoiseShiftV1 {
            return NoiseShiftV1(vec4<u32>(a.x << 1u, (a.y << 1u) | (a.x >> 31u),
                (a.z << 1u) | (a.y >> 31u), (a.w << 1u) | (a.z >> 31u)), a.w >> 31u);
        }
        fn w5gNoiseLess(a: vec4<u32>, b: vec4<u32>) -> bool {
            for(var i = 4u; i > 0u; i = i - 1u) {
                if(a[i-1u] != b[i-1u]) { return a[i-1u] < b[i-1u]; }
            }
            return false;
        }
        fn w5gNoiseSubtract(a: vec4<u32>, b: vec4<u32>) -> vec4<u32> {
            var result = vec4<u32>(0u); var borrow = 0u;
            for(var i = 0u; i < 4u; i = i + 1u) {
                result[i] = a[i] - b[i] - borrow;
                borrow = u32(a[i] < b[i] || (borrow != 0u && a[i] == b[i]));
            }
            return result;
        }
        fn w5gNoiseRemainder(a: vec4<u32>, divisor: vec4<u32>) -> vec4<u32> {
            var remainder = vec4<u32>(0u);
            for(var bit = 128u; bit > 0u; bit = bit - 1u) {
                let shifted = w5gNoiseShift(remainder);
                remainder = shifted.value;
                remainder.x = remainder.x | ((a[(bit-1u)/32u] >> ((bit-1u)%32u)) & 1u);
                // Retain the temporary 129th bit. Modular subtraction then yields the exact <divisor result.
                if(shifted.carry != 0u || !w5gNoiseLess(remainder, divisor)) {
                    remainder = w5gNoiseSubtract(remainder, divisor);
                }
            }
            return remainder;
        }
        fn w5gNoiseIntegral(q: f32) -> bool {
            let bits = bitcast<u32>(q) & 0x7fffffffu;
            if(bits == 0u) { return true; }
            let exponent = bits >> 23u;
            if(exponent >= 150u) { return true; }
            if(exponent < 127u) { return false; }
            return (bits & ((1u << (150u-exponent))-1u)) == 0u;
        }
        fn w5gNoiseMagnitude(q: f32) -> vec4<u32> {
            let bits = bitcast<u32>(q) & 0x7fffffffu;
            if(bits == 0u) { return vec4<u32>(0u); }
            let shift = i32(bits >> 23u)-150i;
            let significand = (bits & 0x7fffffu) | 0x800000u;
            var result = vec4<u32>(0u);
            for(var bit = 0u; bit < 24u; bit = bit + 1u) {
                let position = i32(bit)+shift;
                if(position >= 0i && position < 128i && ((significand >> bit) & 1u) != 0u) {
                    result[u32(position)/32u] = result[u32(position)/32u] | (1u << (u32(position)%32u));
                }
            }
            return result;
        }
        fn w5gNoiseAddress(lattice: f32, corner: u32, period: vec4<u32>) -> u32 {
            var magnitude = w5gNoiseMagnitude(lattice);
            var negative = lattice < 0.0;
            if(corner != 0u) {
                if(negative && any(magnitude != vec4<u32>(0u))) {
                    magnitude = w5gNoiseSubtract(magnitude, vec4<u32>(1u,0u,0u,0u));
                } else {
                    negative = false; var carry = 1u;
                    for(var i = 0u; i < 4u; i = i + 1u) {
                        let previous = magnitude[i]; magnitude[i] = previous+carry;
                        carry = u32(carry != 0u && magnitude[i] < previous);
                    }
                    if(carry != 0u) { discard; }
                }
            }
            if(any(period != vec4<u32>(0u))) {
                magnitude = w5gNoiseRemainder(magnitude,period);
                if(negative && any(magnitude != vec4<u32>(0u))) { magnitude = w5gNoiseSubtract(period,magnitude); }
                return magnitude.x & 255u;
            }
            if(negative) { return (0u-magnitude.x) & 255u; }
            return magnitude.x & 255u;
        }
        fn w5gNoiseByte(base: u32, index: u32) -> u32 {
            let word = base+index/4u;
            return (w5gNoiseWords[word/4u].words[word%4u] >> ((index%4u)*8u)) & 255u;
        }
        fn w5gNoiseGradient(base: u32, x: u32, y: u32, channel: u32, axis: u32) -> u32 {
            let permutation = w5gNoiseByte(base,x);
            let index = (permutation+y) & 255u;
            let offset = 256u+channel*1024u+index*4u+axis*2u;
            return w5gNoiseByte(base,offset) | (w5gNoiseByte(base,offset+1u) << 8u);
        }
    """.trimIndent()
    fun emit(graph: ColorOperationGraphV1, inputRgbaExpression: String, uniformWordOffsetU32: Long,
        imageEncodedRgbaExpression: String? = null, resultChannelI32: Int? = null,
        composedProof: org.graphiks.kanvas.gpu.plan.ColorSourceProofV1? = null): String {
        require(graph.contractId == "WgslFloatEnvelopeV1" && uniformWordOffsetU32 in 0L..UInt.MAX_VALUE.toLong())
        require(resultChannelI32 == null || resultChannelI32 in 0..3)
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
            fun imageAddress(read: org.graphiks.kanvas.gpu.plan.ImageNumericOperationGraphV1.TexelRead): String =
                cache[read] ?: run {
                    val x = arg(read.baseX); val y = arg(read.baseY)
                    val width = arg(read.width); val height = arg(read.height)
                    val address = "imageAddress${nextI32++}"
                    val functionName=when(read.resource) {
                        is org.graphiks.kanvas.gpu.plan.ImageNumericOperationGraphV1.TexelResource.Legacy -> "w5e_address_texel"
                        is org.graphiks.kanvas.gpu.plan.ImageNumericOperationGraphV1.TexelResource.Logical ->
                            "w5g_address_texel_${requireNotNull(composedProof).resolveComposedImage(read).resource.bindingI32}_${read.tileModes.x.name.lowercase()}_${read.tileModes.y.name.lowercase()}"
                    }
                    code.append("let $address = $functionName(i32($x) + ${read.offsetXI32}i, i32($y) + ${read.offsetYI32}i, i32($width), i32($height));\n")
                    cache[read] = address
                    address
                }
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
            if (node is ColorOperationGraphV1.Scalar.NoiseComponent) {
                val vector=cache[node.region] ?: run {
                    val region=node.region
                    val prefix="noiseRegion${nextI32++}"
                    val count=word(region.requestedOctavesWordOffsetU32)
                    code.append("var ${prefix}Result: vec4<f32>;\n")
                    region.states.forEachIndexed { index,_ -> code.append("var ${prefix}State$index: f32 = ${if(index == 2) "1.0" else "0.0"};\n") }
                    code.append("if ($count != 0u) {\n")
                    val initialCache=java.util.IdentityHashMap(cache)
                    region.initialState.take(2).forEachIndexed { index,value ->
                        val initial=expression(value,code,initialCache); code.append("${prefix}State$index = $initial;\n") }
                    for(axis in 0..1) code.append("var ${prefix}Period$axis = vec4<u32>(${(0..3).joinToString(", ") { word(region.wordOffsetU32+8L+axis*4L+it) }});\n")
                    code.append("for(var ${prefix}Octave = 0u; ${prefix}Octave < $count; ${prefix}Octave = ${prefix}Octave + 1u) {\n")
                    val bodyCache=java.util.IdentityHashMap(cache)
                    region.states.forEachIndexed { index,state -> bodyCache[state]="${prefix}State$index" }
                    bodyCache[region.xPhase]="${prefix}Period0"; bodyCache[region.yPhase]="${prefix}Period1"
                    val integral=expression(region.integral,code,bodyCache)
                    val phases=listOf(region.xPhase,region.yPhase)
                    phases.forEachIndexed { axis,_ ->
                        code.append("var ${prefix}Floor$axis: f32 = 0.0;\nvar ${prefix}Fraction$axis: f32 = 0.0;\n")
                    }
                    // Materialize the SAME original phase operations once, shared by every
                    // channel/branch/address. The integral tail keeps these dead operations absent.
                    code.append("if ($integral != 1.0) {\n")
                    val phaseCache=java.util.IdentityHashMap(bodyCache)
                    phases.forEachIndexed { axis,phase ->
                        val floor=expression(phase.floor,code,phaseCache)
                        val fraction=expression(phase.fraction,code,phaseCache)
                        code.append("${prefix}Floor$axis = $floor;\n${prefix}Fraction$axis = $fraction;\n")
                        bodyCache[phase.floor]="${prefix}Floor$axis"
                        bodyCache[phase.fraction]="${prefix}Fraction$axis"
                    }
                    code.append("}\n")
                    val next=region.nextState.drop(2).map { expression(it,code,bodyCache) }
                    // The unused final q-double is absent; accumulator Adds and amplitude-half are never omitted.
                    code.append("if (${prefix}Octave + 1u < $count) {\n")
                    val qCache=java.util.IdentityHashMap(bodyCache)
                    val q=region.nextState.take(2).map { expression(it,code,qCache) }
                    q.forEachIndexed { index,value -> code.append("${prefix}State$index = $value;\n") }
                    code.append("if (!(w5gNoiseIntegral(${prefix}State0) && w5gNoiseIntegral(${prefix}State1))) {\n")
                    for(axis in 0..1) {
                        code.append("let ${prefix}Doubled$axis = w5gNoiseShift(${prefix}Period$axis);\n")
                        code.append("if (${prefix}Doubled$axis.carry != 0u) { discard; }\n${prefix}Period$axis = ${prefix}Doubled$axis.value;\n")
                    }
                    code.append("}\n}\n")
                    next.forEachIndexed { index,value -> code.append("${prefix}State${index+2} = $value;\n") }
                    code.append("}\n}\n")
                    val finalCache=java.util.IdentityHashMap(cache)
                    region.states.forEachIndexed { index,state -> finalCache[state]="${prefix}State$index" }
                    val output=region.outputs.map { expression(it,code,finalCache) }
                    code.append("${prefix}Result = vec4<f32>(${output.joinToString(", ")});\n")
                    "${prefix}Result".also { cache[region]=it }
                }
                return "$vector[${node.channelI32}u]".also { cache[node]=it }
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
                is ColorOperationGraphV1.Scalar.NoiseComponent -> error("Noise region must be emitted together")
                is ColorOperationGraphV1.Scalar.NoiseStateF32 -> error("Unbound Noise state")
                is ColorOperationGraphV1.Scalar.NoiseIntegralF32 -> "f32(w5gNoiseIntegral(${arg(node.x.q)}) && w5gNoiseIntegral(${arg(node.y.q)}))"
                is ColorOperationGraphV1.Scalar.NoisePhaseComponent -> {
                    val q=arg(node.phase.q)
                    if(node.kind == org.graphiks.kanvas.gpu.plan.NoiseOperationGraphV1.PhaseKind.FLOOR) "floor($q)" else "($q - ${arg(node.phase.floor)})"
                }
                is ColorOperationGraphV1.Scalar.NoiseGradientU16 -> {
                    val read=node.read
                    val x="w5gNoiseAddress(${arg(read.x.phase.floor)}, ${read.x.offsetI32}u, ${requireNotNull(cache[read.x.phase])})"
                    val y="w5gNoiseAddress(${arg(read.y.phase.floor)}, ${read.y.offsetI32}u, ${requireNotNull(cache[read.y.phase])})"
                    "f32(w5gNoiseGradient(${word(read.tableRangeWordOffsetU32)}, $x, $y, ${read.channelI32}u, ${read.axisI32}u))"
                }
                is ColorOperationGraphV1.Scalar.InputLinearPremul -> "($inputRgbaExpression)[${node.channelI32}u]"
                is ColorOperationGraphV1.Scalar.ImageEncodedInput ->
                    "(${requireNotNull(imageEncodedRgbaExpression)})[${node.channelI32}u]"
                is ColorOperationGraphV1.Scalar.ImageTexelValid -> "f32(${imageAddress(node.read)}.z)"
                is ColorOperationGraphV1.Scalar.ImageEncodedComponent -> {
                    val encoded = cache[node.read.encoded] ?: run {
                        val address = imageAddress(node.read)
                        val texel = "imageEncoded${nextI32++}"
                        val textureName=when(node.read.resource) {
                            is org.graphiks.kanvas.gpu.plan.ImageNumericOperationGraphV1.TexelResource.Legacy -> "w5eTexture"
                            is org.graphiks.kanvas.gpu.plan.ImageNumericOperationGraphV1.TexelResource.Logical ->
                                "w5gTexture${requireNotNull(composedProof).resolveComposedImage(node.read).resource.bindingI32}"
                        }
                        code.append("let $texel: vec4<f32> = textureLoad($textureName, $address.xy, 0);\n")
                        cache[node.read.encoded] = texel
                        texel
                    }
                    "$encoded[${node.channelI32}u]"
                }
                is ColorOperationGraphV1.Scalar.ImageSampleComponent -> arg(node.region.outputs[node.channelI32])
                is ColorOperationGraphV1.Scalar.ImageIntegerOffset -> "f32(i32(${arg(node.base)}) + ${node.offsetI32}i)"
                ColorOperationGraphV1.Scalar.DiscardF32 -> { code.append("discard;\n"); "0.0" }
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
                is ColorOperationGraphV1.Scalar.Sin -> "sin(${arg(node.value)})"
                is ColorOperationGraphV1.Scalar.Cos -> "cos(${arg(node.value)})"
                is ColorOperationGraphV1.Scalar.StopInterpolationInput -> error("Unbound selected-stop interpolation operand")
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
                        val interpolationCache = java.util.IdentityHashMap(cache)
                        selected.interpolationInputs.forEachIndexed { index,input -> interpolationCache[input] = when {
                            index < 4 -> "${prefix}Left.straightColor[${index}u]"
                            index < 8 -> "${prefix}Right.straightColor[${index-4}u]"
                            else -> "${prefix}Weight"
                        } }
                        val interpolated = selected.interpolationGraph.outputs.map { expression(it,code,interpolationCache) }
                        code.append("$prefix = vec4<f32>(${interpolated.joinToString(", ")});\n}\n")
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
        val output = resultChannelI32?.let { result[it] } ?: "vec4<f32>(${result.joinToString(", ")})"
        return code.append("return $output;\n").toString()
    }
}
