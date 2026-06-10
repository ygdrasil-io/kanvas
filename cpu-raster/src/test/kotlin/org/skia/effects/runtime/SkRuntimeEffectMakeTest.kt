package org.skia.effects.runtime

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.effects.runtime.effects.SkBuiltinColorFilterEffects
import org.skia.effects.runtime.effects.SkBuiltinShaderEffectsSimple
import org.graphiks.math.SkColor4f
import org.graphiks.math.SkPoint
import java.nio.ByteBuffer

/**
 * D2.1 verification suite for the
 * [SkRuntimeEffect.Companion.MakeForShader] /
 * `MakeForColorFilter` / `MakeForBlender` factories — end-to-end
 * integration through the dispatch table + signature parser.
 *
 * **Behaviour under test** :
 *  - Successful resolution returns a non-null
 *    [SkRuntimeEffect] with reflection populated.
 *  - Missing impl yields `Result(effect = null, errorText = …)`
 *    with the canonical hash inlined into the error message.
 *  - Wrong factory for the SkSL kind (e.g. `MakeForBlender` on a
 *    shader-shaped SkSL) yields a kind-mismatch error.
 *  - `allowShader` / `allowColorFilter` / `allowBlender` mirror
 *    the entry-point arity classification.
 *  - `findUniform(name)` / `findChild(name)` resolve declarations
 *    by name.
 *  - `source()` returns the original (unnormalised) SkSL.
 */
class SkRuntimeEffectMakeTest {

    @AfterEach
    fun cleanup() {
        SkRuntimeEffectDispatch.clearForTest()
    }

    private val identityShader = """
        half4 main(vec2 p) { return vec4(p.x, p.y, 0.0, 1.0); }
    """.trimIndent()

    private val identityColorFilter = """
        half4 main(vec4 inColor) { return inColor; }
    """.trimIndent()

    private val averageBlender = """
        half4 main(vec4 src, vec4 dst) { return (src + dst) * 0.5; }
    """.trimIndent()

    private fun stub() = object : SkRuntimeImpl {
        override val uniforms: List<SkRuntimeEffect.Uniform> = emptyList()
        override val children: List<SkRuntimeEffect.Child> = emptyList()
        override val flags: Int = 0
        override fun shade(
            coords: SkPoint?,
            srcColor: SkColor4f?,
            dstColor: SkColor4f?,
            uniforms: ByteBuffer,
            children: Array<ChildResolver>,
        ): SkColor4f = SkColor4f.kBlack
    }

    // ─── Successful resolution ───────────────────────────────────────

    @Test
    fun `MakeForShader returns a non-null effect when the impl is registered`() {
        SkRuntimeEffectDispatch.register(identityShader) { stub() }
        val r = SkRuntimeEffect.MakeForShader(identityShader)
        assertNotNull(r.effect, "effect must be non-null on a registered hit")
        assertEquals("", r.errorText, "errorText must be empty on success")
        assertTrue(r.effect!!.allowShader())
        assertFalse(r.effect!!.allowColorFilter())
        assertFalse(r.effect!!.allowBlender())
    }

    @Test
    fun `MakeForColorFilter classifies a vec4 arg correctly`() {
        SkRuntimeEffectDispatch.register(identityColorFilter) { stub() }
        val r = SkRuntimeEffect.MakeForColorFilter(identityColorFilter)
        assertNotNull(r.effect)
        assertTrue(r.effect!!.allowColorFilter())
        assertFalse(r.effect!!.allowShader())
        assertFalse(r.effect!!.allowBlender())
    }

    @Test
    fun `MakeForBlender classifies two vec4 args correctly`() {
        SkRuntimeEffectDispatch.register(averageBlender) { stub() }
        val r = SkRuntimeEffect.MakeForBlender(averageBlender)
        assertNotNull(r.effect)
        assertTrue(r.effect!!.allowBlender())
    }

    // ─── Reflection round-trip ───────────────────────────────────────

    @Test
    fun `findUniform resolves declarations by name`() {
        val sksl = """
            uniform float gain;
            uniform vec4 tint;
            half4 main(vec2 p) { return vec4(0); }
        """.trimIndent()
        SkRuntimeEffectDispatch.register(sksl) { stub() }
        val effect = SkRuntimeEffect.MakeForShader(sksl).effect!!
        val gain = effect.findUniform("gain")
        assertNotNull(gain)
        assertEquals(SkRuntimeEffect.Uniform.Type.kFloat, gain!!.type)
        val tint = effect.findUniform("tint")
        assertNotNull(tint)
        assertEquals(SkRuntimeEffect.Uniform.Type.kFloat4, tint!!.type)
        assertNull(effect.findUniform("nope"), "missing names return null")
    }

    @Test
    fun `findChild resolves child slots by name`() {
        val sksl = """
            uniform shader src;
            uniform colorFilter cf;
            half4 main(vec2 p) { return vec4(0); }
        """.trimIndent()
        SkRuntimeEffectDispatch.register(sksl) { stub() }
        val effect = SkRuntimeEffect.MakeForShader(sksl).effect!!
        val src = effect.findChild("src")
        assertNotNull(src)
        assertEquals(SkRuntimeEffect.ChildType.kShader, src!!.type)
        assertEquals(0, src.index)
        val cf = effect.findChild("cf")
        assertNotNull(cf)
        assertEquals(SkRuntimeEffect.ChildType.kColorFilter, cf!!.type)
        assertEquals(1, cf.index)
        assertNull(effect.findChild("nope"))
    }

    @Test
    fun `source returns the original SkSL verbatim`() {
        val sksl = "// header\nhalf4 main(vec2 p) { return vec4(0); }"
        SkRuntimeEffectDispatch.register(sksl) { stub() }
        val effect = SkRuntimeEffect.MakeForShader(sksl).effect!!
        assertEquals(sksl, effect.source(), "source() must return the input verbatim, not normalised")
    }

    @Test
    fun `SimpleRT exposes descriptor with CPU and WGSL implementation ids`() {
        val effect = SkRuntimeEffect.MakeForShader(SkBuiltinShaderEffectsSimple.SIMPLE_RT_SKSL).effect
        assertNotNull(effect)
        val descriptor = effect!!.descriptor()
        assertNotNull(descriptor)
        assertEquals("runtime.simple_rt", descriptor!!.stableId)
        assertEquals("kotlin/simple_rt", descriptor.cpuImplementationId)
        assertEquals("wgsl/runtime_simple_rt", descriptor.wgslImplementationId)
        assertEquals(SkRuntimeEffect.Kind.kShader, descriptor.kind)
        assertEquals(listOf("gColor"), descriptor.uniforms.map { it.name })
        assertTrue(descriptor.children.isEmpty())
    }

    @Test
    fun `SpiralRT and LinearGradient expose descriptors`() {
        val spiral = SkRuntimeEffect.MakeForShader(SkBuiltinShaderEffectsSimple.SPIRAL_RT_SKSL).effect
        val linearGradient = SkRuntimeEffect.MakeForShader(SkBuiltinShaderEffectsSimple.LINEAR_GRADIENT_RT_SKSL).effect

        assertNotNull(spiral)
        assertNotNull(linearGradient)
        val spiralDescriptor = spiral!!.descriptor()
        assertNotNull(spiralDescriptor)
        assertEquals("runtime.spiral_rt", spiralDescriptor!!.stableId)
        assertEquals("kotlin/spiral_rt", spiralDescriptor.cpuImplementationId)
        assertEquals("wgsl/runtime_spiral_rt", spiralDescriptor.wgslImplementationId)
        assertEquals(listOf("rad_scale", "in_center", "in_colors0", "in_colors1"), spiralDescriptor.uniforms.map { it.name })
        val linearGradientDescriptor = linearGradient!!.descriptor()
        assertNotNull(linearGradientDescriptor)
        assertEquals("runtime.linear_gradient_rt", linearGradientDescriptor!!.stableId)
        assertEquals("kotlin/linear_gradient_rt", linearGradientDescriptor.cpuImplementationId)
        assertEquals("wgsl/runtime_linear_gradient_rt", linearGradientDescriptor.wgslImplementationId)
        assertEquals(listOf("in_colors0", "in_colors1"), linearGradientDescriptor.uniforms.map { it.name })
    }

    @Test
    fun `LumaToAlpha runtime color filter exposes CPU and WGSL descriptor`() {
        val effect = SkRuntimeEffect.MakeForColorFilter(SkBuiltinColorFilterEffects.LUMA_SRC_SKSL).effect

        assertNotNull(effect)
        val descriptor = effect!!.descriptor()
        assertNotNull(descriptor)
        assertEquals("runtime.color_filter_luma_to_alpha", descriptor!!.stableId)
        assertEquals("kotlin/color_filter_luma_to_alpha", descriptor.cpuImplementationId)
        assertEquals("wgsl/runtime_color_filter_luma_to_alpha", descriptor.wgslImplementationId)
        assertEquals(SkRuntimeEffect.Kind.kColorFilter, descriptor.kind)
        assertTrue(descriptor.uniforms.isEmpty())
        assertTrue(descriptor.children.isEmpty())
    }

    // ─── Failure paths ───────────────────────────────────────────────

    @Test
    fun `MakeForShader on unregistered SkSL returns null effect with hash diagnostic`() {
        val sksl = "half4 main(vec2 p) { return vec4(0.5); }"
        // No register call.
        val r = SkRuntimeEffect.MakeForShader(sksl)
        assertNull(r.effect, "effect must be null on a registry miss")
        assertTrue(
            r.errorText.contains("SkSL not registered"),
            "errorText must mention the failure mode : ${r.errorText}",
        )
        // Hash should be in the message as a 16-hex string.
        assertTrue(
            r.errorText.contains("0x"),
            "errorText must include the canonical hash : ${r.errorText}",
        )
        assertEquals(
            "Runtime effect descriptor not registered: ${SkRuntimeEffectDispatch.canonicalHash(sksl)}",
            SkRuntimeEffectDescriptorRegistry.missingDiagnostic(sksl),
        )
    }

    @Test
    fun `wrong factory for the kind yields a kind-mismatch error`() {
        SkRuntimeEffectDispatch.register(identityShader) { stub() }
        // identityShader is a shader (vec2 main arg). MakeForColorFilter
        // should reject it before even trying to look up the impl.
        val r = SkRuntimeEffect.MakeForColorFilter(identityShader)
        assertNull(r.effect)
        assertTrue(
            r.errorText.contains("MakeForColorFilter") ||
                r.errorText.contains("kColorFilter"),
            "errorText must diagnose the kind mismatch : ${r.errorText}",
        )
    }

    @Test
    fun `parser-error SkSL yields an Error with diagnostic`() {
        val sksl = "uniform notatype foo; half4 main(vec2 p) { return vec4(0); }"
        // Register stub so the dispatch lookup wouldn't itself fail.
        SkRuntimeEffectDispatch.register(sksl) { stub() }
        val r = SkRuntimeEffect.MakeForShader(sksl)
        assertNull(r.effect, "parser error → null effect")
        assertTrue(r.errorText.isNotEmpty(), "errorText must carry the parser diagnostic")
    }

    // ─── Whitespace-variant lookup ───────────────────────────────────

    @Test
    fun `whitespace-variant SkSL resolves the same impl`() {
        SkRuntimeEffectDispatch.register(identityShader) { stub() }
        // Caller passes a whitespace-variant ; canonical hash must
        // collapse to the same key as the registered source.
        val r = SkRuntimeEffect.MakeForShader(
            """
            // some banner I added in the GM port
            half4 main(vec2 p) { return vec4(p.x, p.y, 0.0, 1.0); }
            """.trimIndent()
        )
        assertNotNull(r.effect, "whitespace-variant lookup should hit the same impl")
    }
}
