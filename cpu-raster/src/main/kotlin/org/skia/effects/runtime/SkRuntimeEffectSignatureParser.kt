package org.skia.effects.runtime

/**
 * Regex-only mini-parser for SkSL **signatures** : top-level
 * `uniform` declarations and the `main(...)` entry-point arity.
 * Does **not** parse the function body — that's deliberately
 * delegated to the per-effect [SkRuntimeImpl] hand-port.
 *
 * The parse is cheap (one pass over the comment-stripped source
 * with a handful of regex matchers) and runs once per
 * [SkRuntimeEffect.Companion.MakeForShader] /
 * `MakeForColorFilter` / `MakeForBlender` call.
 *
 * **Grammar recognised** (per upstream SkSL spec) :
 *
 *  - `uniform <type> <name>;`
 *    — type ∈ {`half`, `half2`, `half3`, `half4`, `float`,
 *    `float2`, `float3`, `float4`, `vec2`, `vec3`, `vec4`,
 *    `half2x2`, `half3x3`, `half4x4`, `float2x2`, `float3x3`,
 *    `float4x4`, `mat2`, `mat3`, `mat4`, `int`, `int2`, `int3`,
 *    `int4`, `ivec2`, `ivec3`, `ivec4`}.
 *
 *  - `uniform <type> <name>[<count>];`
 *    — array-typed uniform (count ≥ 1).
 *
 *  - `uniform shader|colorFilter|blender <name>;`
 *    — child slot (the index is the order of declaration).
 *
 *  - `layout(color) uniform <type> <name>;` /
 *    `layout(color) uniform <type> <name>[<count>];`
 *    — uniform tagged as a colour (the [SkRuntimeEffect.Uniform.kColor_Flag]
 *    bit is set ; bindings auto-convert sRGB → working-space).
 *
 *  - One `main(...)` declaration ; the parameter list determines
 *    the [SkRuntimeEffect.Kind] :
 *      - `vec2|float2 inCoords` (1 arg) → [SkRuntimeEffect.Kind.kShader]
 *      - `vec4|half4|float4 inColor` (1 arg) → [SkRuntimeEffect.Kind.kColorFilter]
 *      - `vec4|half4 src, vec4|half4 dst` (2 args) → [SkRuntimeEffect.Kind.kBlender]
 *
 * **Layout** : uniform offsets are computed in declaration order
 * with each uniform aligned to its own size, capped at 16 bytes
 * (matches Skia's `SkRuntimeEffect::Uniform::offset` field).
 *
 * **Failure modes** :
 *  - Missing `main(...)` → error.
 *  - Unrecognised `main` parameter list → error.
 *  - Unknown uniform type token → error.
 *  - Empty uniform name → error.
 *  - Negative or zero array count → error.
 *
 * Errors carry a short human-readable diagnostic ; the SkRuntimeEffect
 * factory wraps them into [SkRuntimeEffect.Result.errorText] before
 * returning to the caller.
 */
internal object SkRuntimeEffectSignatureParser {

    sealed interface Outcome {
        class Ok(val parsed: ParsedSignature) : Outcome
        class Error(val message: String) : Outcome
    }

    /**
     * Tokenise + extract the uniforms / children / main signature.
     * Returns either [Outcome.Ok] with a populated [ParsedSignature]
     * or [Outcome.Error] with a diagnostic message.
     */
    fun parse(source: String): Outcome {
        val src = stripComments(source)

        val uniforms = mutableListOf<SkRuntimeEffect.Uniform>()
        val children = mutableListOf<SkRuntimeEffect.Child>()
        var byteOffset = 0
        var childIndex = 0

        // Walk every top-level `uniform ...` and `layout(...) uniform ...`
        // declaration. The regex captures :
        //   1. Optional `layout(<flags>)` prefix.
        //   2. Required `uniform`.
        //   3. Required type token (or shader/colorFilter/blender for children).
        //   4. Required name.
        //   5. Optional `[<count>]` array suffix.
        // We use a non-greedy match terminated by `;` so a malformed
        // declaration doesn't gobble the rest of the source.
        val uniformRe = Regex(
            """(?:layout\s*\(\s*([a-zA-Z]+)\s*\)\s+)?uniform\s+(\w+(?:\s*x\s*\w+)?)\s+(\w+)\s*(?:\[\s*(\d+)\s*\])?\s*;""",
        )
        for (m in uniformRe.findAll(src)) {
            val layoutTag = m.groupValues[1].takeIf { it.isNotEmpty() }
            val typeTok = m.groupValues[2]
            val name = m.groupValues[3]
            val arrayCountStr = m.groupValues[4]

            if (name.isEmpty()) {
                return Outcome.Error("uniform declaration missing name : ${m.value}")
            }

            // Children : type token is shader / colorFilter / blender.
            val childType = when (typeTok) {
                "shader" -> SkRuntimeEffect.ChildType.kShader
                "colorFilter" -> SkRuntimeEffect.ChildType.kColorFilter
                "blender" -> SkRuntimeEffect.ChildType.kBlender
                else -> null
            }
            if (childType != null) {
                if (arrayCountStr.isNotEmpty()) {
                    return Outcome.Error("child uniforms can't be array-typed : ${m.value}")
                }
                children.add(SkRuntimeEffect.Child(name, childType, childIndex++))
                continue
            }

            // Plain uniform : decode the type token.
            val type = decodeUniformType(typeTok)
                ?: return Outcome.Error("unknown uniform type '$typeTok' in : ${m.value}")
            val count = if (arrayCountStr.isEmpty()) 1
            else arrayCountStr.toIntOrNull()?.takeIf { it >= 1 }
                ?: return Outcome.Error("invalid array count in : ${m.value}")

            // Flags from layout tag + type qualifier.
            var flags = 0
            if (arrayCountStr.isNotEmpty()) flags = flags or SkRuntimeEffect.Uniform.kArray_Flag
            if (layoutTag == "color") flags = flags or SkRuntimeEffect.Uniform.kColor_Flag
            // `half`-typed uniforms get the half-precision flag (matches
            // upstream's `SkRuntimeEffect::Uniform::kHalfPrecision_Flag`).
            if (typeTok.startsWith("half")) flags = flags or SkRuntimeEffect.Uniform.kHalfPrecision_Flag

            // Align byteOffset to the type's size, capped at 16 bytes.
            val alignment = type.sizeBytes.coerceAtMost(16)
            byteOffset = ((byteOffset + alignment - 1) / alignment) * alignment

            uniforms.add(
                SkRuntimeEffect.Uniform(
                    name = name,
                    offset = byteOffset,
                    type = type,
                    count = count,
                    flags = flags,
                ),
            )
            byteOffset += type.sizeBytes * count
        }

        // Locate the main(...) declaration.
        val mainRe = Regex(
            """(?:half4|vec4|float4)\s+main\s*\(\s*([^)]*)\s*\)""",
        )
        val mainMatch = mainRe.find(src)
            ?: return Outcome.Error("SkSL source has no `main(...)` entry point")
        val params = mainMatch.groupValues[1].trim()
        val kind = classifyMainArity(params)
            ?: return Outcome.Error(
                "SkSL `main(...)` parameter list is unrecognised : '$params'. " +
                    "Expected one of: `(vec2 coords)` (shader), `(vec4 color)` " +
                    "(color filter), `(vec4 src, vec4 dst)` (blender).",
            )

        return Outcome.Ok(
            ParsedSignature(
                uniforms = uniforms.toList(),
                children = children.toList(),
                kind = kind,
                uniformSize = byteOffset,
            ),
        )
    }

    /**
     * Map an SkSL type token to its [SkRuntimeEffect.Uniform.Type]
     * counterpart. Returns null for unknown tokens. Both Skia
     * aliases are recognised (e.g. `vec3` ↔ `float3` ↔ `half3`,
     * `mat3` ↔ `float3x3`, `ivec3` ↔ `int3`).
     */
    private fun decodeUniformType(tok: String): SkRuntimeEffect.Uniform.Type? {
        val normalised = tok.replace("\\s+".toRegex(), "")
        return when (normalised) {
            "half", "float" -> SkRuntimeEffect.Uniform.Type.kFloat
            "half2", "float2", "vec2" -> SkRuntimeEffect.Uniform.Type.kFloat2
            "half3", "float3", "vec3" -> SkRuntimeEffect.Uniform.Type.kFloat3
            "half4", "float4", "vec4" -> SkRuntimeEffect.Uniform.Type.kFloat4
            "half2x2", "float2x2", "mat2" -> SkRuntimeEffect.Uniform.Type.kFloat2x2
            "half3x3", "float3x3", "mat3" -> SkRuntimeEffect.Uniform.Type.kFloat3x3
            "half4x4", "float4x4", "mat4" -> SkRuntimeEffect.Uniform.Type.kFloat4x4
            "int" -> SkRuntimeEffect.Uniform.Type.kInt
            "int2", "ivec2" -> SkRuntimeEffect.Uniform.Type.kInt2
            "int3", "ivec3" -> SkRuntimeEffect.Uniform.Type.kInt3
            "int4", "ivec4" -> SkRuntimeEffect.Uniform.Type.kInt4
            else -> null
        }
    }

    /**
     * Classify a `main` parameter list into one of the three
     * supported [SkRuntimeEffect.Kind] cases. Whitespace is
     * normalised before matching ; SkSL aliases (`vec2` / `float2`,
     * `vec4` / `half4` / `float4`) are all accepted.
     *
     * Recognised patterns :
     *  - `vec2 X` / `float2 X` → shader
     *  - `vec4 X` / `half4 X` / `float4 X` → color filter
     *  - `vec4 X, vec4 Y` (or aliases) → blender
     *
     * Whitespace inside the list is collapsed before matching.
     */
    private fun classifyMainArity(params: String): SkRuntimeEffect.Kind? {
        val cleaned = params.replace("\\s+".toRegex(), " ").trim()
        if (cleaned.isEmpty()) return null
        val parts = cleaned.split(',').map { it.trim() }
        return when (parts.size) {
            1 -> {
                val tok = parts[0].split(' ').firstOrNull() ?: return null
                when (tok) {
                    "vec2", "float2" -> SkRuntimeEffect.Kind.kShader
                    "vec4", "half4", "float4" -> SkRuntimeEffect.Kind.kColorFilter
                    else -> null
                }
            }
            2 -> {
                val a = parts[0].split(' ').firstOrNull() ?: return null
                val b = parts[1].split(' ').firstOrNull() ?: return null
                if (a in vec4Aliases && b in vec4Aliases) SkRuntimeEffect.Kind.kBlender
                else null
            }
            else -> null
        }
    }

    private val vec4Aliases = setOf("vec4", "half4", "float4")

    /**
     * Duplicate the comment-strip step from [SkRuntimeEffectDispatch]
     * here so the parser sees the same input the dispatch table
     * hashes. Could be deduped via a shared internal helper ; for
     * now both copies are tiny + identical, with the same edge cases.
     */
    private fun stripComments(source: String): String {
        val out = StringBuilder(source.length)
        var i = 0
        val n = source.length
        while (i < n) {
            val c = source[i]
            if (c == '/' && i + 1 < n) {
                val next = source[i + 1]
                if (next == '/') {
                    i += 2
                    while (i < n && source[i] != '\n') i++
                    if (i < n) {
                        out.append(' ')
                        i++
                    }
                    continue
                } else if (next == '*') {
                    i += 2
                    while (i + 1 < n && !(source[i] == '*' && source[i + 1] == '/')) i++
                    if (i + 1 < n) {
                        out.append(' ')
                        i += 2
                    } else {
                        i = n
                    }
                    continue
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }
}
