package org.skia.effects.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.effects.runtime.SkRuntimeEffect.Uniform.Type

/**
 * D2.1 verification suite for [SkRuntimeEffectSignatureParser].
 *
 * **Behaviour under test** :
 *  - Every supported uniform type token decodes correctly
 *    (including SkSL aliases : `vec2` / `float2` / `half2`).
 *  - Array-typed uniforms set the [SkRuntimeEffect.Uniform.kArray_Flag]
 *    + carry the right `count`.
 *  - `layout(color)`-tagged uniforms set the
 *    [SkRuntimeEffect.Uniform.kColor_Flag].
 *  - `half`-prefixed types set the half-precision flag.
 *  - Child uniforms (`shader` / `colorFilter` / `blender`) are
 *    extracted into [SkRuntimeEffect.Child] entries with
 *    incrementing indices.
 *  - Uniform offsets honour the std140-ish alignment (each uniform
 *    aligned to its own size, capped at 16 bytes).
 *  - The `main(...)` parameter list classifies the kind correctly
 *    (1×vec2 = shader, 1×vec4 = color filter, 2×vec4 = blender).
 *  - Failure paths : missing `main`, unknown type, unrecognised
 *    `main` arity each return an [Outcome.Error] with a
 *    diagnostic message.
 */
class SkRuntimeEffectSignatureParseTest {

    private fun ok(source: String): ParsedSignature =
        when (val r = SkRuntimeEffectSignatureParser.parse(source)) {
            is SkRuntimeEffectSignatureParser.Outcome.Ok -> r.parsed
            is SkRuntimeEffectSignatureParser.Outcome.Error ->
                error("expected Ok but got Error('${r.message}')")
        }

    private fun err(source: String): String =
        when (val r = SkRuntimeEffectSignatureParser.parse(source)) {
            is SkRuntimeEffectSignatureParser.Outcome.Ok ->
                error("expected Error but got Ok with ${r.parsed}")
            is SkRuntimeEffectSignatureParser.Outcome.Error -> r.message
        }

    // ─── Uniform type decoding ───────────────────────────────────────

    @Test
    fun `every uniform type alias decodes correctly`() {
        // (sksl token → expected Type)
        val cases = mapOf(
            "float" to Type.kFloat, "half" to Type.kFloat,
            "float2" to Type.kFloat2, "half2" to Type.kFloat2, "vec2" to Type.kFloat2,
            "float3" to Type.kFloat3, "half3" to Type.kFloat3, "vec3" to Type.kFloat3,
            "float4" to Type.kFloat4, "half4" to Type.kFloat4, "vec4" to Type.kFloat4,
            "float2x2" to Type.kFloat2x2, "half2x2" to Type.kFloat2x2, "mat2" to Type.kFloat2x2,
            "float3x3" to Type.kFloat3x3, "half3x3" to Type.kFloat3x3, "mat3" to Type.kFloat3x3,
            "float4x4" to Type.kFloat4x4, "half4x4" to Type.kFloat4x4, "mat4" to Type.kFloat4x4,
            "int" to Type.kInt,
            "int2" to Type.kInt2, "ivec2" to Type.kInt2,
            "int3" to Type.kInt3, "ivec3" to Type.kInt3,
            "int4" to Type.kInt4, "ivec4" to Type.kInt4,
        )
        for ((tok, expectedType) in cases) {
            val parsed = ok(
                """
                uniform $tok foo;
                vec4 main(vec2 p) { return vec4(0); }
                """.trimIndent()
            )
            assertEquals(1, parsed.uniforms.size, "expected 1 uniform for token '$tok'")
            assertEquals(expectedType, parsed.uniforms[0].type, "wrong Type for token '$tok'")
        }
    }

    @Test
    fun `unknown uniform type yields an Error with the type name`() {
        val msg = err(
            """
            uniform notatype foo;
            vec4 main(vec2 p) { return vec4(0); }
            """.trimIndent()
        )
        assertTrue(msg.contains("notatype"), "error should mention the unknown type : $msg")
    }

    // ─── Array-typed uniforms ────────────────────────────────────────

    @Test
    fun `array uniforms set kArray_Flag and carry the count`() {
        val parsed = ok(
            """
            uniform float foo[5];
            vec4 main(vec2 p) { return vec4(0); }
            """.trimIndent()
        )
        val u = parsed.uniforms.single()
        assertEquals(5, u.count)
        assertTrue(u.isArray(), "kArray_Flag must be set")
        assertEquals(Type.kFloat, u.type)
    }

    @Test
    fun `non-array uniforms have count = 1 and no kArray_Flag`() {
        val parsed = ok(
            """
            uniform float foo;
            vec4 main(vec2 p) { return vec4(0); }
            """.trimIndent()
        )
        val u = parsed.uniforms.single()
        assertEquals(1, u.count)
        assertEquals(false, u.isArray())
    }

    // ─── layout(color) flag ──────────────────────────────────────────

    @Test
    fun `layout(color) sets kColor_Flag`() {
        val parsed = ok(
            """
            layout(color) uniform vec4 tint;
            vec4 main(vec2 p) { return vec4(0); }
            """.trimIndent()
        )
        val u = parsed.uniforms.single()
        assertTrue(u.isColor(), "kColor_Flag must be set for layout(color)")
        assertEquals(Type.kFloat4, u.type)
    }

    @Test
    fun `half-prefixed types set kHalfPrecision_Flag`() {
        val parsed = ok(
            """
            uniform half3 foo;
            vec4 main(vec2 p) { return vec4(0); }
            """.trimIndent()
        )
        val u = parsed.uniforms.single()
        assertTrue(u.isHalfPrecision(), "half3 should set the half-precision flag")
    }

    // ─── Child uniforms ──────────────────────────────────────────────

    @Test
    fun `shader child is recognised at index 0`() {
        val parsed = ok(
            """
            uniform shader child;
            vec4 main(vec2 p) { return vec4(0); }
            """.trimIndent()
        )
        val c = parsed.children.single()
        assertEquals("child", c.name)
        assertEquals(SkRuntimeEffect.ChildType.kShader, c.type)
        assertEquals(0, c.index)
    }

    @Test
    fun `multiple children get incrementing indices`() {
        val parsed = ok(
            """
            uniform shader a;
            uniform colorFilter b;
            uniform blender c;
            vec4 main(vec2 p) { return vec4(0); }
            """.trimIndent()
        )
        assertEquals(3, parsed.children.size)
        assertEquals(SkRuntimeEffect.ChildType.kShader, parsed.children[0].type)
        assertEquals(0, parsed.children[0].index)
        assertEquals(SkRuntimeEffect.ChildType.kColorFilter, parsed.children[1].type)
        assertEquals(1, parsed.children[1].index)
        assertEquals(SkRuntimeEffect.ChildType.kBlender, parsed.children[2].type)
        assertEquals(2, parsed.children[2].index)
    }

    // ─── Uniform offset alignment ────────────────────────────────────

    @Test
    fun `uniform offsets align to type size capped at 16`() {
        val parsed = ok(
            """
            uniform float a;     // offset 0, size 4
            uniform float b;     // offset 4, size 4
            uniform float2 c;    // offset 8, size 8
            uniform float4 d;    // offset 16, size 16 (aligned to 16)
            uniform float e;     // offset 32, size 4
            uniform float4x4 f;  // offset 48 (aligned to 16), size 64
            vec4 main(vec2 p) { return vec4(0); }
            """.trimIndent()
        )
        assertEquals(0, parsed.uniforms[0].offset, "a at 0")
        assertEquals(4, parsed.uniforms[1].offset, "b at 4")
        assertEquals(8, parsed.uniforms[2].offset, "c at 8 (aligned to 8)")
        assertEquals(16, parsed.uniforms[3].offset, "d at 16 (aligned to 16)")
        assertEquals(32, parsed.uniforms[4].offset, "e at 32")
        assertEquals(48, parsed.uniforms[5].offset, "f at 48 (aligned to 16)")
        assertEquals(48 + 64, parsed.uniformSize, "total = last offset + last size")
    }

    @Test
    fun `array uniform contributes count times its type size`() {
        val parsed = ok(
            """
            uniform float foo[5]; // 5 * 4 = 20 bytes total
            vec4 main(vec2 p) { return vec4(0); }
            """.trimIndent()
        )
        assertEquals(20, parsed.uniformSize)
    }

    // ─── main(...) classification ───────────────────────────────────

    @Test
    fun `single vec2 arg classifies as shader`() {
        val parsed = ok("vec4 main(vec2 p) { return vec4(0); }")
        assertEquals(SkRuntimeEffect.Kind.kShader, parsed.kind)
    }

    @Test
    fun `single float2 arg classifies as shader`() {
        val parsed = ok("vec4 main(float2 p) { return vec4(0); }")
        assertEquals(SkRuntimeEffect.Kind.kShader, parsed.kind)
    }

    @Test
    fun `single vec4 arg classifies as color filter`() {
        val parsed = ok("vec4 main(vec4 c) { return c; }")
        assertEquals(SkRuntimeEffect.Kind.kColorFilter, parsed.kind)
    }

    @Test
    fun `single half4 arg classifies as color filter`() {
        val parsed = ok("vec4 main(half4 c) { return c; }")
        assertEquals(SkRuntimeEffect.Kind.kColorFilter, parsed.kind)
    }

    @Test
    fun `two vec4 args classify as blender`() {
        val parsed = ok("vec4 main(vec4 src, vec4 dst) { return src; }")
        assertEquals(SkRuntimeEffect.Kind.kBlender, parsed.kind)
    }

    @Test
    fun `mixed half4 and vec4 args still classify as blender`() {
        val parsed = ok("vec4 main(half4 src, vec4 dst) { return src; }")
        assertEquals(SkRuntimeEffect.Kind.kBlender, parsed.kind)
    }

    @Test
    fun `missing main is an error`() {
        val msg = err("uniform float foo;")
        assertTrue(msg.contains("main"), "error should mention 'main' : $msg")
    }

    @Test
    fun `unrecognised main arity is an error`() {
        val msg = err("vec4 main(int a) { return vec4(0); }")
        assertTrue(msg.contains("unrecognised") || msg.contains("Expected"),
            "error should diagnose the wrong arity : $msg")
    }

    // ─── Comments don't break parse ─────────────────────────────────

    @Test
    fun `parser strips comments before scanning declarations`() {
        val parsed = ok(
            """
            // header
            uniform float foo; // trailing
            /* block */ uniform vec4 bar;
            vec4 main(vec2 p) { return vec4(0); /* inline */ }
            """.trimIndent()
        )
        assertEquals(2, parsed.uniforms.size)
        assertEquals("foo", parsed.uniforms[0].name)
        assertEquals("bar", parsed.uniforms[1].name)
        assertEquals(SkRuntimeEffect.Kind.kShader, parsed.kind)
    }
}
