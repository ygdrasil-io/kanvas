package org.skia.effects.runtime

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkColor4f
import org.skia.foundation.SkColorFilter
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * D2.3 verification suite for [SkRuntimeEffectBuilder].
 *
 * **Behaviour under test** :
 *  - Construction : default zero-init buffer ; explicit
 *    [SkData] init (size match enforced).
 *  - Named uniform writes (every supported type) write at the
 *    right byte offset, in native byte order.
 *  - Type mismatch on [UniformAccessor.set] throws.
 *  - Wrong-size array on [UniformAccessor.set] throws.
 *  - Missing uniform / child name throws on accessor lookup.
 *  - Child slot type-mismatch throws on [ChildAccessor.set].
 *  - [makeShader] / [makeColorFilter] / [makeBlender] return
 *    `null` for the wrong effect kind.
 *  - End-to-end : build a shader via the Builder, install on
 *    a paint, verify the impl received the bound uniforms.
 */
class SkRuntimeEffectBuilderTest {

    @AfterEach fun cleanup() { SkRuntimeEffectDispatch.clearForTest() }

    // ─── Construction ────────────────────────────────────────────────

    private val singleFloatSksl = """
        uniform float gain;
        half4 main(vec2 p) { return vec4(0); }
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

    @Test
    fun `default constructor zero-inits the uniform buffer`() {
        SkRuntimeEffectDispatch.register(singleFloatSksl) { stub() }
        val effect = SkRuntimeEffect.MakeForShader(singleFloatSksl).effect!!
        val builder = SkRuntimeEffectBuilder(effect)
        // 1 float = 4 bytes.
        val bytes = builder.uniformBytesSnapshot()
        assertEquals(4, bytes.size)
        for (b in bytes) assertEquals(0.toByte(), b, "zero-init")
    }

    // ─── Float uniform write ─────────────────────────────────────────

    @Test
    fun `uniform set Float writes a float at the declared offset`() {
        SkRuntimeEffectDispatch.register(singleFloatSksl) { stub() }
        val effect = SkRuntimeEffect.MakeForShader(singleFloatSksl).effect!!
        val builder = SkRuntimeEffectBuilder(effect)
        builder.uniform("gain").set(3.14f)

        val bytes = builder.uniformBytesSnapshot()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder())
        assertEquals(3.14f, buf.float, 1e-6f)
    }

    @Test
    fun `uniform set FloatArray respects offset alignment`() {
        val sksl = """
            uniform float a;
            uniform vec4 tint;
            half4 main(vec2 p) { return vec4(0); }
        """.trimIndent()
        SkRuntimeEffectDispatch.register(sksl) { stub() }
        val effect = SkRuntimeEffect.MakeForShader(sksl).effect!!
        val builder = SkRuntimeEffectBuilder(effect)
        builder.uniform("a").set(2f)
        builder.uniform("tint").set(floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f))

        val buf = ByteBuffer.wrap(builder.uniformBytesSnapshot()).order(ByteOrder.nativeOrder())
        // a at offset 0 ; tint at offset 16 (vec4 alignment).
        buf.position(0)
        assertEquals(2f, buf.float, 1e-6f)
        // tint starts at offset 16 (vec4 aligned to 16 bytes per upstream rules).
        buf.position(16)
        assertEquals(0.1f, buf.float, 1e-6f)
        assertEquals(0.2f, buf.float, 1e-6f)
        assertEquals(0.3f, buf.float, 1e-6f)
        assertEquals(0.4f, buf.float, 1e-6f)
    }

    @Test
    fun `uniform set SkColor4f writes the 4 channels in RGBA order`() {
        val sksl = """
            uniform vec4 tint;
            half4 main(vec2 p) { return vec4(0); }
        """.trimIndent()
        SkRuntimeEffectDispatch.register(sksl) { stub() }
        val effect = SkRuntimeEffect.MakeForShader(sksl).effect!!
        val builder = SkRuntimeEffectBuilder(effect)
        builder.uniform("tint").set(SkColor4f(0.7f, 0.3f, 0.1f, 0.8f))

        val buf = ByteBuffer.wrap(builder.uniformBytesSnapshot()).order(ByteOrder.nativeOrder())
        assertEquals(0.7f, buf.float, 1e-6f)
        assertEquals(0.3f, buf.float, 1e-6f)
        assertEquals(0.1f, buf.float, 1e-6f)
        assertEquals(0.8f, buf.float, 1e-6f)
    }

    @Test
    fun `uniform set SkMatrix writes a column-major mat3`() {
        val sksl = """
            uniform mat3 transform;
            half4 main(vec2 p) { return vec4(0); }
        """.trimIndent()
        SkRuntimeEffectDispatch.register(sksl) { stub() }
        val effect = SkRuntimeEffect.MakeForShader(sksl).effect!!
        val builder = SkRuntimeEffectBuilder(effect)
        // [ sx kx tx ]   [ 1 2 3 ]
        // [ ky sy ty ] = [ 4 5 6 ]
        // [ p0 p1 p2 ]   [ 7 8 9 ]
        val m = SkMatrix(
            sx = 1f, kx = 2f, tx = 3f,
            ky = 4f, sy = 5f, ty = 6f,
            persp0 = 7f, persp1 = 8f, persp2 = 9f,
        )
        builder.uniform("transform").set(m)

        // Column-major reads : col0 = (sx, ky, persp0), col1 = (kx, sy, persp1), col2 = (tx, ty, persp2).
        val buf = ByteBuffer.wrap(builder.uniformBytesSnapshot()).order(ByteOrder.nativeOrder())
        val expected = floatArrayOf(1f, 4f, 7f, 2f, 5f, 8f, 3f, 6f, 9f)
        for ((i, v) in expected.withIndex()) {
            assertEquals(v, buf.float, 1e-6f, "mat3 element $i")
        }
    }

    // ─── Int uniform write ──────────────────────────────────────────

    @Test
    fun `uniform set Int writes a 32-bit integer at the declared offset`() {
        val sksl = """
            uniform int count;
            half4 main(vec2 p) { return vec4(0); }
        """.trimIndent()
        SkRuntimeEffectDispatch.register(sksl) { stub() }
        val effect = SkRuntimeEffect.MakeForShader(sksl).effect!!
        val builder = SkRuntimeEffectBuilder(effect)
        builder.uniform("count").set(42)
        assertEquals(
            42,
            ByteBuffer.wrap(builder.uniformBytesSnapshot()).order(ByteOrder.nativeOrder()).int,
        )
    }

    @Test
    fun `uniform set IntArray for ivec3 writes 3 ints`() {
        val sksl = """
            uniform ivec3 v;
            half4 main(vec2 p) { return vec4(0); }
        """.trimIndent()
        SkRuntimeEffectDispatch.register(sksl) { stub() }
        val effect = SkRuntimeEffect.MakeForShader(sksl).effect!!
        val builder = SkRuntimeEffectBuilder(effect)
        builder.uniform("v").set(intArrayOf(1, 2, 3))

        val buf = ByteBuffer.wrap(builder.uniformBytesSnapshot()).order(ByteOrder.nativeOrder())
        assertEquals(1, buf.int)
        assertEquals(2, buf.int)
        assertEquals(3, buf.int)
    }

    // ─── Failure paths : type / size mismatch ───────────────────────

    @Test
    fun `set Int on a Float uniform throws`() {
        SkRuntimeEffectDispatch.register(singleFloatSksl) { stub() }
        val effect = SkRuntimeEffect.MakeForShader(singleFloatSksl).effect!!
        val builder = SkRuntimeEffectBuilder(effect)
        assertThrows(IllegalArgumentException::class.java) {
            builder.uniform("gain").set(42)
        }
    }

    @Test
    fun `set Float on an Int uniform throws`() {
        val sksl = """
            uniform int count;
            half4 main(vec2 p) { return vec4(0); }
        """.trimIndent()
        SkRuntimeEffectDispatch.register(sksl) { stub() }
        val effect = SkRuntimeEffect.MakeForShader(sksl).effect!!
        val builder = SkRuntimeEffectBuilder(effect)
        assertThrows(IllegalArgumentException::class.java) {
            builder.uniform("count").set(3.14f)
        }
    }

    @Test
    fun `set FloatArray with wrong size throws`() {
        val sksl = """
            uniform vec4 tint;
            half4 main(vec2 p) { return vec4(0); }
        """.trimIndent()
        SkRuntimeEffectDispatch.register(sksl) { stub() }
        val effect = SkRuntimeEffect.MakeForShader(sksl).effect!!
        val builder = SkRuntimeEffectBuilder(effect)
        // 3 floats for a vec4 — must throw.
        assertThrows(IllegalArgumentException::class.java) {
            builder.uniform("tint").set(floatArrayOf(1f, 2f, 3f))
        }
    }

    @Test
    fun `set IntArray on a float uniform throws`() {
        val sksl = """
            uniform vec3 v;
            half4 main(vec2 p) { return vec4(0); }
        """.trimIndent()
        SkRuntimeEffectDispatch.register(sksl) { stub() }
        val effect = SkRuntimeEffect.MakeForShader(sksl).effect!!
        val builder = SkRuntimeEffectBuilder(effect)
        assertThrows(IllegalArgumentException::class.java) {
            builder.uniform("v").set(intArrayOf(1, 2, 3))
        }
    }

    @Test
    fun `missing uniform name throws on accessor lookup`() {
        SkRuntimeEffectDispatch.register(singleFloatSksl) { stub() }
        val effect = SkRuntimeEffect.MakeForShader(singleFloatSksl).effect!!
        val builder = SkRuntimeEffectBuilder(effect)
        val ex = assertThrows(IllegalArgumentException::class.java) {
            builder.uniform("nope")
        }
        assertTrue(ex.message?.contains("nope") == true,
            "error must name the missing uniform : ${ex.message}")
    }

    // ─── Child slots ─────────────────────────────────────────────────

    @Test
    fun `child set with matching type binds the slot`() {
        val sksl = """
            uniform shader src;
            half4 main(vec2 p) { return vec4(0); }
        """.trimIndent()
        SkRuntimeEffectDispatch.register(sksl) { stub() }
        SkRuntimeEffectDispatch.register("half4 main(vec2 p) { return vec4(0); }") { stub() }
        val effect = SkRuntimeEffect.MakeForShader(sksl).effect!!
        val childEffect = SkRuntimeEffect.MakeForShader("half4 main(vec2 p) { return vec4(0); }").effect!!
        val childShader = childEffect.makeShader(null)!!
        val builder = SkRuntimeEffectBuilder(effect)
        builder.child("src").set(childShader)

        assertEquals(childShader, builder.childSlot(0))
    }

    @Test
    fun `child set with wrong type throws`() {
        val sksl = """
            uniform shader src;
            half4 main(vec2 p) { return vec4(0); }
        """.trimIndent()
        SkRuntimeEffectDispatch.register(sksl) { stub() }
        val effect = SkRuntimeEffect.MakeForShader(sksl).effect!!
        val builder = SkRuntimeEffectBuilder(effect)
        // Pass a SkColorFilter into a shader-typed slot — must throw.
        val cf = object : SkColorFilter() {
            override fun filterColor4f(src: SkColor4f): SkColor4f = src
        }
        assertThrows(IllegalArgumentException::class.java) {
            builder.child("src").set(cf)
        }
    }

    @Test
    fun `missing child name throws on accessor lookup`() {
        SkRuntimeEffectDispatch.register(singleFloatSksl) { stub() }
        val effect = SkRuntimeEffect.MakeForShader(singleFloatSksl).effect!!
        val builder = SkRuntimeEffectBuilder(effect)
        val ex = assertThrows(IllegalArgumentException::class.java) {
            builder.child("nope")
        }
        assertTrue(ex.message?.contains("nope") == true)
    }

    // ─── makeXxx kind gating ───────────────────────────────────────

    @Test
    fun `makeShader returns null for a colorFilter effect`() {
        val sksl = "half4 main(vec4 c) { return c; }"
        SkRuntimeEffectDispatch.register(sksl) { stub() }
        val effect = SkRuntimeEffect.MakeForColorFilter(sksl).effect!!
        val builder = SkRuntimeEffectBuilder(effect)
        assertNull(builder.makeShader())
    }

    @Test
    fun `makeColorFilter returns null for a shader effect`() {
        SkRuntimeEffectDispatch.register(singleFloatSksl) { stub() }
        val effect = SkRuntimeEffect.MakeForShader(singleFloatSksl).effect!!
        val builder = SkRuntimeEffectBuilder(effect)
        assertNull(builder.makeColorFilter())
    }

    @Test
    fun `makeBlender returns null for a shader effect`() {
        SkRuntimeEffectDispatch.register(singleFloatSksl) { stub() }
        val effect = SkRuntimeEffect.MakeForShader(singleFloatSksl).effect!!
        val builder = SkRuntimeEffectBuilder(effect)
        assertNull(builder.makeBlender())
    }

    // ─── End-to-end : impl receives the bound uniforms ──────────────

    @Test
    fun `builder makeShader passes bound uniforms through to the impl`() {
        val sksl = """
            uniform float scale;
            uniform vec4 tint;
            half4 main(vec2 p) { return vec4(0); }
        """.trimIndent()
        var sawScale: Float? = null
        var sawTintR: Float? = null
        SkRuntimeEffectDispatch.register(sksl) {
            object : SkRuntimeImpl {
                override val uniforms: List<SkRuntimeEffect.Uniform> = emptyList()
                override val children: List<SkRuntimeEffect.Child> = emptyList()
                override val flags: Int = 0
                override fun shade(
                    coords: SkPoint?,
                    srcColor: SkColor4f?,
                    dstColor: SkColor4f?,
                    uniforms: ByteBuffer,
                    children: Array<ChildResolver>,
                ): SkColor4f {
                    if (sawScale == null) {
                        // scale at offset 0 (4 bytes), tint at offset 16 (16 bytes).
                        uniforms.position(0)
                        sawScale = uniforms.float
                        uniforms.position(16)
                        sawTintR = uniforms.float
                    }
                    return SkColor4f.kBlack
                }
            }
        }
        val effect = SkRuntimeEffect.MakeForShader(sksl).effect!!
        val builder = SkRuntimeEffectBuilder(effect)
        builder.uniform("scale").set(0.75f)
        builder.uniform("tint").set(SkColor4f(0.5f, 0f, 0f, 1f))

        val shader = builder.makeShader()!!
        // Drive one shadeRow call to force the impl to read uniforms.
        shader.setupForDraw(SkMatrix.Identity, SkRuntimeEffect.identityXform)
        shader.shadeRow(0, 0, 1, IntArray(1))

        assertEquals(0.75f, sawScale!!, 1e-6f)
        assertEquals(0.5f, sawTintR!!, 1e-6f)
    }

    @Test
    fun `each makeShader call snapshots the current uniforms`() {
        // Build twice with different uniforms ; the first shader's
        // captured bytes should reflect the first set, not be
        // mutated by subsequent edits.
        val sksl = """
            uniform float gain;
            half4 main(vec2 p) { return vec4(0); }
        """.trimIndent()
        val capturedGains = mutableListOf<Float>()
        SkRuntimeEffectDispatch.register(sksl) {
            object : SkRuntimeImpl {
                override val uniforms: List<SkRuntimeEffect.Uniform> = emptyList()
                override val children: List<SkRuntimeEffect.Child> = emptyList()
                override val flags: Int = 0
                override fun shade(
                    coords: SkPoint?,
                    srcColor: SkColor4f?,
                    dstColor: SkColor4f?,
                    uniforms: ByteBuffer,
                    children: Array<ChildResolver>,
                ): SkColor4f {
                    uniforms.position(0)
                    capturedGains.add(uniforms.float)
                    return SkColor4f.kBlack
                }
            }
        }
        val effect = SkRuntimeEffect.MakeForShader(sksl).effect!!
        val builder = SkRuntimeEffectBuilder(effect)
        builder.uniform("gain").set(1.0f)
        val s1 = builder.makeShader()!!
        builder.uniform("gain").set(2.0f)
        val s2 = builder.makeShader()!!

        // Each shader gets its own snapshot.
        s1.setupForDraw(SkMatrix.Identity, SkRuntimeEffect.identityXform)
        s1.shadeRow(0, 0, 1, IntArray(1))
        s2.setupForDraw(SkMatrix.Identity, SkRuntimeEffect.identityXform)
        s2.shadeRow(0, 0, 1, IntArray(1))

        assertEquals(2, capturedGains.size)
        assertEquals(1.0f, capturedGains[0], 1e-6f)
        assertEquals(2.0f, capturedGains[1], 1e-6f)
    }
}
